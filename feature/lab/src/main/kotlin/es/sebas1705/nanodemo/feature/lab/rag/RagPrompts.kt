package es.sebas1705.nanodemo.feature.lab.rag

import es.sebas1705.nanodemo.domain.models.GenerationConfig

/** The context block of a RAG prompt: which retrieved chunks made it in and how much room they used. */
class PromptContext(
    val text: String,
    /** Chunks included, in citation order: `used[0]` is cited as [1]. */
    val used: List<ScoredChunk>,
    /** Retrieved chunks that did not fit the budget. */
    val dropped: Int,
    /** True when the first chunk had to be cut to fit. */
    val firstTruncated: Boolean,
)

/**
 * Prompt construction for the RAG experiment. Pure functions: the pipeline decides when to call the model, this
 * decides what the model reads. Prompts are in Spanish because the sample corpus is; small models follow an
 * instruction better when it is in the language of the documents.
 */
object RagPrompts {

    /** The exact sentence the model is told to answer with when the context lacks the answer. */
    const val NOT_FOUND = "No lo encuentro en los documentos."

    private const val CHARS_PER_TOKEN = 4

    /** Context budget when the engine's window is large or unknown (cloud, Nano): enough for several chunks. */
    const val DEFAULT_CONTEXT_CHARS = 6_000

    /** Room kept for the fixed instructions and framing words of a prompt. */
    private const val INSTRUCTION_RESERVE_CHARS = 500

    private const val MIN_CONTEXT_CHARS = 300

    /** Answer length used with an engine whose context is small, so the answer cannot eat the whole window. */
    private const val SMALL_ENGINE_MAX_ANSWER_TOKENS = 256
    private const val DEFAULT_MAX_ANSWER_TOKENS = 512

    /** Answer token limit for an engine: small windows get a short answer so the context has room. */
    fun maxAnswerTokens(contextTokens: Int?): Int =
        if (contextTokens == null) DEFAULT_MAX_ANSWER_TOKENS else (contextTokens / 4).coerceIn(64, SMALL_ENGINE_MAX_ANSWER_TOKENS)

    /**
     * How many characters of retrieved context fit. For an engine with a known total window (prompt + answer,
     * about 4 chars per token) it is the window minus the answer, the question and the fixed instructions; for
     * engines without a known limit it is [DEFAULT_CONTEXT_CHARS]. Never below [MIN_CONTEXT_CHARS].
     */
    fun contextBudgetChars(contextTokens: Int?, maxAnswerTokens: Int, questionChars: Int, extraChars: Int = 0): Int {
        if (contextTokens == null) return DEFAULT_CONTEXT_CHARS
        val window = contextTokens * CHARS_PER_TOKEN
        val reserved = maxAnswerTokens * CHARS_PER_TOKEN + INSTRUCTION_RESERVE_CHARS + questionChars + extraChars
        return (window - reserved).coerceAtLeast(MIN_CONTEXT_CHARS)
    }

    /**
     * Packs chunks, in rank order, into numbered entries `[n] label` + body until `maxChars` is reached. A chunk
     * that does not fit whole stops the packing (later, lower-ranked chunks are not squeezed in ahead of it); only
     * when not even the first fits is it cut, so the model always gets something.
     */
    fun buildContext(chunks: List<ScoredChunk>, maxChars: Int): PromptContext {
        val used = mutableListOf<ScoredChunk>()
        val entries = mutableListOf<String>()
        var length = 0
        var firstTruncated = false
        for (scored in chunks) {
            val header = "[${used.size + 1}] ${scored.chunk.label}\n"
            val separator = if (entries.isEmpty()) 0 else 2
            val entry = header + scored.chunk.text
            if (length + separator + entry.length <= maxChars) {
                entries += entry
                used += scored
                length += separator + entry.length
            } else {
                if (used.isEmpty()) {
                    val room = (maxChars - header.length - 1).coerceAtLeast(0) // 1 for the ellipsis
                    if (room > 0) {
                        entries += header + scored.chunk.text.take(room).trimEnd() + "…"
                        used += scored
                        firstTruncated = true
                    }
                }
                break
            }
        }
        return PromptContext(entries.joinToString("\n\n"), used, dropped = chunks.size - used.size, firstTruncated = firstTruncated)
    }

    fun ragPrompt(question: String, context: PromptContext): String = buildString {
        appendLine("Responde a la pregunta usando SOLO el contexto numerado de abajo.")
        appendLine("Cita las fuentes entre corchetes, por ejemplo [1] o [2].")
        appendLine("Si el contexto no contiene la respuesta, responde exactamente: \"$NOT_FOUND\"")
        appendLine("No inventes datos y responde en español, de forma breve.")
        appendLine()
        appendLine("Contexto:")
        appendLine(context.text)
        appendLine()
        appendLine("Pregunta: ${question.trim()}")
        append("Respuesta:")
    }

    /** The no-retrieval baseline: the same brevity and honesty instructions, but no documents. */
    fun plainPrompt(question: String): String = buildString {
        appendLine("Responde a la pregunta en español, de forma breve. Si no sabes la respuesta, dilo.")
        appendLine()
        appendLine("Pregunta: ${question.trim()}")
        append("Respuesta:")
    }

    /** Groundedness check: asks a model whether every claim in `answer` is supported by `context`. */
    fun judgePrompt(question: String, context: String, answer: String): String = buildString {
        appendLine("Eres un verificador. Comprueba si TODAS las afirmaciones de la RESPUESTA están respaldadas por el CONTEXTO.")
        appendLine("Si la respuesta dice que no encuentra la información y el contexto de verdad no la contiene, está respaldada.")
        appendLine("Contesta en exactamente dos líneas:")
        appendLine("VEREDICTO: SI (todo respaldado), PARCIAL (algo no respaldado) o NO (nada respaldado o lo contradice)")
        appendLine("PUNTUACION: un número del 0 al 10")
        appendLine()
        appendLine("CONTEXTO:")
        appendLine(context)
        appendLine()
        appendLine("PREGUNTA: ${question.trim()}")
        appendLine("RESPUESTA: ${answer.trim()}")
    }

    /** Generation settings for answers: low temperature (this is extraction, not creativity) and a window-aware length. */
    fun answerConfig(contextTokens: Int?): GenerationConfig =
        GenerationConfig(temperature = 0.2f, maxOutputTokens = maxAnswerTokens(contextTokens))

    /** The judge only emits two short lines. */
    fun judgeConfig(): GenerationConfig = GenerationConfig(temperature = 0.1f, maxOutputTokens = JUDGE_MAX_TOKENS)

    const val JUDGE_MAX_TOKENS = 48
}
