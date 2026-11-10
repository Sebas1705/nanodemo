package es.sebas1705.nanodemo.feature.lab.refine

import es.sebas1705.nanodemo.domain.models.GenerationConfig
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.StepKind
import es.sebas1705.nanodemo.feature.lab.trace.LocalOutcome
import es.sebas1705.nanodemo.feature.lab.trace.PipelineRunner
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.random.Random

enum class JudgeMode { RUBRIC, PAIRWISE }

/** Where the answers to judge come from. */
sealed interface JudgeSource {
    /** Answers written by the user. */
    data class Typed(val answers: List<String>) : JudgeSource

    /** The same question run on each of these engines. */
    data class Engines(val tiers: List<InferenceTier>) : JudgeSource
}

data class JudgeResult(
    val mode: JudgeMode = JudgeMode.RUBRIC,
    val candidates: List<Candidate> = emptyList(),
    val scores: Map<Int, RubricScores?> = emptyMap(),
    val pairs: List<PairResult> = emptyList(),
    val leaderboard: List<LeaderEntry> = emptyList(),
    /** Engines whose answer could not be produced, with the reason. */
    val skipped: List<String> = emptyList(),
    val answerStats: CallStats = CallStats(),
    val judgeStats: CallStats = CallStats(),
    val finished: Boolean = false,
    val error: String? = null,
) {
    val consistency: Rate get() = consistencyRate(pairs)
    val firstShare: Rate? get() = firstPositionShare(pairs)
    val unreadable: Rate get() = unreadableRuns(pairs)
}

/**
 * LLM-as-judge. Rubric mode scores each answer on its own (correctness, completeness, clarity from 1 to 5, one
 * call per answer, so there is no position to be biased by). Pairwise mode is a round-robin tournament in which
 * every pair is judged twice with the positions swapped; a verdict that flips with the order is position bias, and
 * the share of pairs that did not flip is reported as the consistency rate. Order is randomised (`random` is
 * injectable so tests are deterministic).
 */
class JudgePipeline(
    private val runner: PipelineRunner,
    private val profileOf: ProfileOf = { EngineProfile() },
    private val random: Random = Random.Default,
    private val texts: RefineTexts = RefineTexts,
) {

    suspend fun run(
        question: String,
        source: JudgeSource,
        judge: InferenceTier,
        mode: JudgeMode,
        onUpdate: (JudgeResult) -> Unit = {},
        onProgress: (LabProgress) -> Unit = {},
    ): JudgeResult {
        var result = JudgeResult(mode = mode)
        var done = 0
        val generationCalls = (source as? JudgeSource.Engines)?.tiers?.size ?: 0
        // Upper bound: the real number of judge calls depends on how many answers survive.
        val answersCount = (source as? JudgeSource.Typed)?.answers?.size ?: generationCalls
        val judgeCalls = if (mode == JudgeMode.RUBRIC) answersCount else answersCount * (answersCount - 1)
        val total = generationCalls + judgeCalls

        fun publish(change: (JudgeResult) -> JudgeResult) {
            result = change(result)
            onUpdate(result)
        }

        suspend fun step(label: String, block: suspend () -> Unit) {
            currentCoroutineContext().ensureActive()
            onProgress(LabProgress(done, total, label))
            block()
            done++
        }

        fun stop(error: String?): JudgeResult {
            publish { it.copy(finished = true, error = error) }
            onProgress(LabProgress(total, total, if (error == null) texts.done else texts.stopped))
            return result
        }

        // 1. The answers to judge.
        val candidates = when (source) {
            is JudgeSource.Typed -> source.answers.filter { it.isNotBlank() }
                .mapIndexed { i, text -> Candidate(i + 1, texts.typedAnswerLabel(i + 1), null, text.trim()) }
            is JudgeSource.Engines -> {
                val made = mutableListOf<Candidate>()
                source.tiers.forEach { tier ->
                    step(texts.answerFrom(tier.name)) {
                        val profile = profileOf(tier)
                        val out = profile.outputTokens(ANSWER_TOKENS)
                        val reply = runner.ask(
                            StepKind.GENERATE, texts.answerTitle, tier, answerPrompt(question, profile, out),
                            GenerationConfig(temperature = 0.7f, maxOutputTokens = out), emptyReply = texts.emptyReply,
                        )
                        publish { it.copy(answerStats = it.answerStats + reply) }
                        if (reply.ok) {
                            made += Candidate(made.size + 1, tier.name, tier, reply.text)
                        } else {
                            publish { it.copy(skipped = it.skipped + "${tier.name}: ${reply.error}") }
                        }
                    }
                }
                made
            }
        }
        publish { it.copy(candidates = candidates) }
        if (candidates.size < 2) return stop(texts.needTwoAnswers)

        // 2. The judging.
        when (mode) {
            JudgeMode.RUBRIC -> candidates.shuffled(random).forEach { c ->
                step(texts.scoring(c.label)) {
                    val reply = askJudge(judge, rubricPrompt(question, c.text, profileOf(judge), JUDGE_TOKENS), texts.scoreTitle(c.label))
                    publish { it.copy(judgeStats = it.judgeStats + reply) }
                    val scores = if (reply.ok) {
                        runner.local(StepKind.VALIDATE, texts.readScore, input = reply.text) {
                            val parsed = parseRubric(reply.text)
                            LocalOutcome(parsed, output = parsed?.let { texts.scoreOutOfFive("%.1f".format(it.average)) } ?: texts.unreadable)
                        }
                    } else {
                        null
                    }
                    publish { current ->
                        val all = current.scores + (c.id to scores)
                        current.copy(scores = all, leaderboard = rubricLeaderboard(candidates, all))
                    }
                }
            }
            JudgeMode.PAIRWISE -> {
                val pairs = candidates.flatMapIndexed { i, a -> candidates.drop(i + 1).map { b -> a to b } }.shuffled(random)
                pairs.forEach { (a, b) ->
                    val firstA = random.nextBoolean()
                    val orders = listOf(if (firstA) a to b else b to a, if (firstA) b to a else a to b)
                    val runs = mutableListOf<PairRun>()
                    orders.forEachIndexed { index, (first, second) ->
                        step(texts.pairLabel(a.label, b.label, index + 1)) {
                            val prompt = pairPrompt(question, first.text, second.text, profileOf(judge), JUDGE_TOKENS)
                            val reply = askJudge(judge, prompt, texts.pairTitle(first.label, second.label))
                            publish { it.copy(judgeStats = it.judgeStats + reply) }
                            val pick = if (reply.ok) {
                                runner.local(StepKind.VALIDATE, texts.readVerdict, input = reply.text) {
                                    val parsed = parsePick(reply.text)
                                    LocalOutcome(
                                        parsed,
                                        output = when (parsed) {
                                            Pick.FIRST -> texts.winsFirst(first.label)
                                            Pick.SECOND -> texts.winsSecond(second.label)
                                            Pick.TIE -> texts.tie
                                            null -> texts.unreadable
                                        },
                                    )
                                }
                            } else {
                                null
                            }
                            runs += PairRun(first.id, second.id, pick)
                            val partial = PairResult(a.id, b.id, runs.toList())
                            publish { current ->
                                val all = current.pairs.filterNot { it.a == a.id && it.b == b.id } + partial
                                current.copy(pairs = all, leaderboard = pairwiseLeaderboard(candidates, all))
                            }
                        }
                    }
                }
            }
        }
        return stop(null)
    }

    private suspend fun askJudge(judge: InferenceTier, prompt: String, title: String): ModelReply {
        val profile = profileOf(judge)
        return runner.ask(
            StepKind.JUDGE, title, judge, prompt,
            GenerationConfig(temperature = GREEDY_TEMPERATURE, maxOutputTokens = profile.outputTokens(JUDGE_TOKENS), jsonMode = profile.structuredOutput), emptyReply = texts.emptyReply,
        )
    }

    internal companion object {
        const val ANSWER_TOKENS = 256
        const val JUDGE_TOKENS = 120

        fun answerPrompt(question: String, profile: EngineProfile, outputTokens: Int): String {
            val head = "Answer the question concisely (at most a short paragraph).\n\nQuestion: "
            val (q) = profile.fitParts(outputTokens, head, question)
            return head + q
        }

        fun rubricPrompt(question: String, answer: String, profile: EngineProfile, outputTokens: Int): String {
            val head = "You are a strict judge. Score the answer to the question from 1 (poor) to 5 (excellent) on correctness, " +
                "completeness and clarity. Reply ONLY with JSON: {\"correctness\":n,\"completeness\":n,\"clarity\":n}."
            val (q, a) = profile.fitParts(outputTokens, head, question, answer)
            return "$head\n\nQuestion: $q\n\nAnswer:\n$a"
        }

        fun pairPrompt(question: String, first: String, second: String, profile: EngineProfile, outputTokens: Int): String {
            val head = "You are an impartial judge. Decide which answer is better for the question: more correct, complete and clear. " +
                "Do not prefer an answer because of its position or its length. " +
                "Reply ONLY with JSON: {\"winner\":\"A\" or \"B\" or \"tie\",\"reason\":\"at most 15 words\"}."
            val (q, a, b) = profile.fitParts(outputTokens, head, question, first, second)
            return "$head\n\nQuestion: $q\n\nAnswer A:\n$a\n\nAnswer B:\n$b"
        }
    }
}
