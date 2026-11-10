package es.sebas1705.nanodemo.feature.lab.rag

import es.sebas1705.nanodemo.domain.models.ChatMessage
import es.sebas1705.nanodemo.domain.models.GenerationMetric
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.Role
import es.sebas1705.nanodemo.domain.models.StepKind
import es.sebas1705.nanodemo.feature.lab.trace.LocalOutcome
import es.sebas1705.nanodemo.feature.lab.trace.PipelineRunner
import kotlinx.coroutines.CancellationException

/** Everything one answer produced: the text, how it was made and (for RAG) what it was based on. */
data class AnswerRun(
    val answer: String,
    val metric: GenerationMetric?,
    val error: String? = null,
    /** Retrieved chunks in rank order; empty for the no-retrieval baseline. */
    val retrieved: List<ScoredChunk> = emptyList(),
    /** How many of `retrieved` fit the prompt (they are cited [1]..[n] in that order). */
    val citedChunks: Int = 0,
    val contextChars: Int = 0,
    val contextBudgetChars: Int = 0,
    val embedMs: Double = 0.0,
    val searchMs: Double = 0.0,
    /** True when retrieval found nothing above the score threshold, so the model was not called at all. */
    val modelSkipped: Boolean = false,
    val grounding: Groundedness? = null,
    val groundingMetric: GenerationMetric? = null,
    val groundingError: String? = null,
) {
    val ok: Boolean get() = error == null
    val retrievalMs: Double get() = embedMs + searchMs
}

/**
 * The RAG experiment's pipeline. With retrieval: RETRIEVE (embed the question, search the index) -> build the
 * prompt with numbered citations -> GENERATE -> optionally JUDGE the groundedness of the answer. Without: a single
 * GENERATE with the bare question. Every model call goes through [PipelineRunner] so both runs share one trace.
 *
 * `embedQuery` is a function (not the embedder) so tests can script query vectors.
 */
class RagPipeline(
    private val runner: PipelineRunner,
    private val embedQuery: suspend (String) -> FloatArray?,
    private val nanoClock: () -> Long = System::nanoTime,
) {
    private class Retrieval(val hits: List<ScoredChunk>, val embedMs: Double, val searchMs: Double)

    /** The baseline of the A/B: the same question and engine, no documents. */
    suspend fun answerPlain(question: String, tier: InferenceTier, contextTokens: Int?): AnswerRun {
        val result = runner.generate(
            kind = StepKind.GENERATE,
            title = "Sin recuperación: respuesta",
            tier = tier,
            messages = userMessage(RagPrompts.plainPrompt(question)),
            config = RagPrompts.answerConfig(contextTokens),
        )
        return AnswerRun(answer = result.text.trim(), metric = result.metric, error = result.error)
    }

    suspend fun answerWithRag(
        question: String,
        tier: InferenceTier,
        contextTokens: Int?,
        index: List<IndexedChunk>,
        options: RetrievalOptions,
        judgeTier: InferenceTier? = null,
    ): AnswerRun {
        val retrieval = try {
            retrieve(question, index, options)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return AnswerRun(answer = "", metric = null, error = e.message ?: "Falló la recuperación")
        }

        if (retrieval.hits.isEmpty()) {
            runner.recorder.skip(
                StepKind.GENERATE, "Con recuperación: respuesta",
                "Ningún fragmento supera el umbral de similitud: no se llama al modelo", tier,
            )
            return AnswerRun(
                answer = RagPrompts.NOT_FOUND, metric = null, retrieved = emptyList(),
                embedMs = retrieval.embedMs, searchMs = retrieval.searchMs, modelSkipped = true,
            )
        }

        val maxAnswer = RagPrompts.maxAnswerTokens(contextTokens)
        val budget = RagPrompts.contextBudgetChars(contextTokens, maxAnswer, question.length)
        val context = runner.local(StepKind.TOOL, "Construir el prompt con citas", input = "${retrieval.hits.size} fragmentos") {
            val built = RagPrompts.buildContext(retrieval.hits, budget)
            val prompt = RagPrompts.ragPrompt(question, built)
            LocalOutcome(
                value = built to prompt,
                output = prompt,
                detail = buildList {
                    add("${built.used.size} de ${retrieval.hits.size} fragmentos en el contexto")
                    add("${built.text.length} de ${budget} car")
                    if (built.firstTruncated) add("el primero se recortó")
                }.joinToString(" · "),
            )
        }
        val (built, prompt) = context

        val result = runner.generate(
            kind = StepKind.GENERATE,
            title = "Con recuperación: respuesta",
            tier = tier,
            messages = userMessage(prompt),
            config = RagPrompts.answerConfig(contextTokens),
        )
        var run = AnswerRun(
            answer = result.text.trim(), metric = result.metric, error = result.error,
            retrieved = retrieval.hits, citedChunks = built.used.size,
            contextChars = built.text.length, contextBudgetChars = budget,
            embedMs = retrieval.embedMs, searchMs = retrieval.searchMs,
        )
        if (judgeTier != null && result.ok && run.answer.isNotBlank()) {
            run = judge(run, question, built, judgeTier, contextTokens)
        }
        return run
    }

    private suspend fun retrieve(question: String, index: List<IndexedChunk>, options: RetrievalOptions): Retrieval =
        runner.local(StepKind.RETRIEVE, "Buscar fragmentos", input = question) {
            val start = nanoClock()
            val vector = embedQuery(question) ?: error("No se pudo calcular el embedding de la pregunta")
            val embedded = nanoClock()
            val hits = Retriever.search(vector, index, options)
            val done = nanoClock()
            val embedMs = (embedded - start) / NANOS_PER_MS
            val searchMs = (done - embedded) / NANOS_PER_MS
            LocalOutcome(
                value = Retrieval(hits, embedMs, searchMs),
                output = if (hits.isEmpty()) "Sin resultados por encima del umbral" else describeHits(hits),
                detail = buildList {
                    add("embedding ${"%.1f".format(embedMs)} ms")
                    add("búsqueda ${"%.1f".format(searchMs)} ms")
                    add("${index.size} fragmentos en el índice")
                    add("k=${options.topK}")
                    if (options.mmr) add("MMR")
                    if (options.minScore > 0) add("umbral ${"%.2f".format(options.minScore)}")
                }.joinToString(" · "),
            )
        }

    private suspend fun judge(
        run: AnswerRun,
        question: String,
        context: PromptContext,
        judgeTier: InferenceTier,
        contextTokens: Int?,
    ): AnswerRun {
        // The judge reads context and answer together, so the room for context shrinks by the answer's length.
        val budget = RagPrompts.contextBudgetChars(contextTokens, RagPrompts.JUDGE_MAX_TOKENS, question.length, extraChars = run.answer.length)
        val judgedContext = RagPrompts.buildContext(context.used, budget)
        val verdictResult = runner.generate(
            kind = StepKind.JUDGE,
            title = "Comprobar que la respuesta está respaldada",
            tier = judgeTier,
            messages = userMessage(RagPrompts.judgePrompt(question, judgedContext.text, run.answer)),
            config = RagPrompts.judgeConfig(),
        )
        if (!verdictResult.ok) return run.copy(groundingError = verdictResult.error)
        val grounding = runner.local(StepKind.VALIDATE, "Interpretar el veredicto", input = verdictResult.text) {
            val parsed = GroundednessParser.parse(verdictResult.text)
            LocalOutcome(parsed, output = describe(parsed))
        }
        return run.copy(grounding = grounding, groundingMetric = verdictResult.metric)
    }

    private fun userMessage(text: String) = listOf(ChatMessage(Role.USER, text))

    private companion object {
        const val NANOS_PER_MS = 1_000_000.0
    }
}

/** `[1] 0.83 · Heading` per hit, one per line; the same numbering the prompt's citations use. */
fun describeHits(hits: List<ScoredChunk>): String =
    hits.mapIndexed { i, h -> "[${i + 1}] ${"%.2f".format(h.score)} · ${h.chunk.label}" }.joinToString("\n")

fun describe(grounding: Groundedness): String {
    val verdict = when (grounding.verdict) {
        GroundingVerdict.SUPPORTED -> "respaldada"
        GroundingVerdict.PARTIAL -> "parcialmente respaldada"
        GroundingVerdict.UNSUPPORTED -> "no respaldada"
        GroundingVerdict.UNKNOWN -> "veredicto no interpretable"
    }
    val score = grounding.score?.let { " · ${(it * 10).let { s -> "%.1f".format(s) }}/10" }.orEmpty()
    return verdict + score
}
