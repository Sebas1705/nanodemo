package es.sebas1705.nanodemo.feature.lab.refine

import es.sebas1705.nanodemo.domain.models.GenerationConfig
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.StepKind
import es.sebas1705.nanodemo.feature.lab.trace.LocalOutcome
import es.sebas1705.nanodemo.feature.lab.trace.PipelineRunner
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** One distinct answer and how many samples gave it. */
data class VoteBucket(val key: String, val display: String, val count: Int, val kind: AnswerKind = AnswerKind.PHRASE) {
    fun asExtracted() = Extracted(kind, key, display)
}

/**
 * Outcome of a majority vote. `buckets` are ordered by count (ties by first appearance); `winner` is the top
 * bucket, and `tiedKeys` lists every key that shares its count (more than one means the vote was a tie, broken
 * in favour of the preferred key when it is among them, otherwise of the answer that appeared first).
 */
data class VoteResult(val buckets: List<VoteBucket>, val valid: Int, val total: Int, val winner: VoteBucket?, val tiedKeys: List<String>) {
    val tie: Boolean get() = tiedKeys.size > 1

    /** Share of the readable answers that agree with the winner (1.0 = unanimous). */
    val agreement: Double? get() = winner?.let { it.count.toDouble() / valid }
}

/** Majority vote over the extracted answers; answers that could not be read (`null`) take no part. */
fun majorityVote(answers: List<Extracted?>, preferKey: String? = null): VoteResult {
    val valid = answers.filterNotNull()
    val order = LinkedHashMap<String, VoteBucket>()
    valid.forEach { a ->
        val current = order[a.key]
        order[a.key] = if (current == null) VoteBucket(a.key, a.display, 1, a.kind) else current.copy(count = current.count + 1)
    }
    // `sortedByDescending` is stable, so equal counts keep first-appearance order.
    val buckets = order.values.sortedByDescending { it.count }
    val top = buckets.firstOrNull()?.count
    val tied = buckets.filter { it.count == top }.map { it.key }
    val winner = buckets.firstOrNull { it.key == preferKey && it.key in tied } ?: buckets.firstOrNull()
    return VoteResult(buckets, valid.size, answers.size, winner, tied)
}

/** One answer of the run, as text and as the short answer read from it (`null` when the call failed or nothing could be read). */
data class SampleAnswer(val text: String, val extracted: Extracted?, val error: String? = null)

enum class VoteVerdict(val label: String) {
    /** Reference answer given: voting was right where the single greedy answer was wrong. */
    VOTING_BEAT_GREEDY("La votación acertó donde la respuesta voraz falló"),
    GREEDY_BEAT_VOTING("La respuesta voraz acertó y la votación falló"),
    BOTH_CORRECT("Las dos acertaron"),
    BOTH_WRONG("Las dos fallaron"),

    /** No reference answer: only whether voting changed the answer. */
    SAME_ANSWER("La votación coincide con la respuesta voraz"),
    CHANGED_ANSWER("La votación cambió la respuesta voraz"),
    NO_VOTE("No se pudo leer ninguna respuesta"),
}

data class SelfConsistencyResult(
    val kind: AnswerKind = AnswerKind.AUTO,
    val greedy: SampleAnswer? = null,
    val samples: List<SampleAnswer> = emptyList(),
    val vote: VoteResult? = null,
    val expected: String? = null,
    val verdict: VoteVerdict? = null,
    val stats: CallStats = CallStats(),
    val finished: Boolean = false,
    val error: String? = null,
) {
    val votedCorrect: Boolean?
        get() {
            val reference = expected?.takeIf { it.isNotBlank() } ?: return null
            val winner = vote?.winner ?: return null
            return matchesExpected(reference, winner.asExtracted())
        }
    val greedyCorrect: Boolean?
        get() = expected?.takeIf { it.isNotBlank() }?.let { matchesExpected(it, greedy?.extracted) }
}

/**
 * The verdict of voting against the single greedy answer. With a reference answer it is a right/wrong comparison;
 * without one it can only say whether voting changed the answer.
 */
fun judgeVote(vote: VoteResult?, greedy: Extracted?, expected: String?): VoteVerdict {
    val winner = vote?.winner ?: return VoteVerdict.NO_VOTE
    if (!expected.isNullOrBlank()) {
        val votedOk = matchesExpected(expected, winner.asExtracted())
        val greedyOk = matchesExpected(expected, greedy)
        return when {
            votedOk && !greedyOk -> VoteVerdict.VOTING_BEAT_GREEDY
            !votedOk && greedyOk -> VoteVerdict.GREEDY_BEAT_VOTING
            votedOk -> VoteVerdict.BOTH_CORRECT
            else -> VoteVerdict.BOTH_WRONG
        }
    }
    return if (greedy != null && greedy.key == winner.key) VoteVerdict.SAME_ANSWER else VoteVerdict.CHANGED_ANSWER
}

/**
 * Self-consistency: sample the same question N times at a temperature above zero, read a short final answer
 * from each, and take the majority. A greedy (near-zero temperature) run of the same prompt is the baseline the
 * vote is compared with.
 */
class SelfConsistencyPipeline(
    private val runner: PipelineRunner,
    private val profileOf: ProfileOf = { EngineProfile() },
) {

    suspend fun run(
        question: String,
        tier: InferenceTier,
        samples: Int,
        temperature: Float,
        kind: AnswerKind = AnswerKind.AUTO,
        expected: String? = null,
        onUpdate: (SelfConsistencyResult) -> Unit = {},
        onProgress: (LabProgress) -> Unit = {},
    ): SelfConsistencyResult {
        val n = samples.coerceAtLeast(1)
        val total = n + 1
        val profile = profileOf(tier)
        val out = profile.outputTokens(ANSWER_TOKENS)
        val prompt = prompt(question, profile, out)
        var result = SelfConsistencyResult(kind = kind, expected = expected?.trim()?.ifBlank { null })

        fun publish(change: (SelfConsistencyResult) -> SelfConsistencyResult) {
            result = change(result)
            onUpdate(result)
        }

        // Raw replies first (the vote needs all of them to resolve AUTO), greedy first.
        val texts = mutableListOf<String?>()
        val errors = mutableListOf<String?>()
        suspend fun sample(index: Int, label: String, title: String, temp: Float) {
            currentCoroutineContext().ensureActive()
            onProgress(LabProgress(index, total, label))
            val reply = runner.ask(StepKind.GENERATE, title, tier, prompt, GenerationConfig(temperature = temp, maxOutputTokens = out))
            texts += reply.text.takeIf { reply.ok }
            errors += reply.error
            publish { it.copy(stats = it.stats + reply) }
        }

        sample(0, "Respuesta voraz", "Respuesta voraz", GREEDY_TEMPERATURE)
        for (i in 1..n) sample(i, "Muestra $i de $n", "Muestra $i", temperature)
        onProgress(LabProgress(total, total, "Votando"))

        val extracted = runner.local(StepKind.VALIDATE, "Extraer respuestas", input = "${texts.count { it != null }} respuestas") {
            val all = extractAll(texts.map { it.orEmpty() }, kind).mapIndexed { i, e -> if (texts[i] == null) null else e }
            LocalOutcome(all, output = all.joinToString(" · ") { it?.display ?: "—" })
        }
        val answers = texts.indices.map { SampleAnswer(texts[it].orEmpty(), extracted[it], errors[it]) }
        val greedy = answers.first()
        val sampled = answers.drop(1)
        val resolvedKind = extracted.firstNotNullOfOrNull { it?.kind } ?: kind

        val vote = runner.local(StepKind.VALIDATE, "Votar", input = "${sampled.size} muestras") {
            val v = majorityVote(sampled.map { it.extracted }, preferKey = greedy.extracted?.key)
            LocalOutcome(
                v,
                output = v.winner?.let { "${it.display} (${it.count}/${v.valid})" } ?: "sin respuestas legibles",
                detail = if (v.tie) "empate entre ${v.tiedKeys.joinToString(", ")}" else null,
            )
        }
        val verdict = judgeVote(vote, greedy.extracted, result.expected)
        publish {
            it.copy(
                kind = resolvedKind, greedy = greedy, samples = sampled, vote = vote, verdict = verdict, finished = true,
                error = if (answers.all { a -> a.error != null }) answers.first().error else null,
            )
        }
        return result
    }

    internal companion object {
        const val ANSWER_TOKENS = 256

        fun prompt(question: String, profile: EngineProfile, outputTokens: Int): String {
            val tail = "\n\nThink step by step, then finish with a last line exactly like: Final answer: <answer only>"
            val (q) = profile.fitParts(outputTokens, "Question: $tail", question)
            return "Question: $q$tail"
        }
    }
}
