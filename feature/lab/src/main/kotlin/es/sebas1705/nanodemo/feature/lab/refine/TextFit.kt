package es.sebas1705.nanodemo.feature.lab.refine

import es.sebas1705.nanodemo.domain.models.InferenceTier

/** Rough size of a token in characters; the same estimate the engines use when they cannot count exactly. */
const val CHARS_PER_TOKEN = 4

fun approxTokens(text: String): Int = if (text.isEmpty()) 0 else (text.length + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN

/** Trims and cuts the text to at most `maxChars` characters, ending with an ellipsis when something was cut. */
fun String.fit(maxChars: Int): String {
    val clean = trim()
    if (clean.length <= maxChars) return clean
    if (maxChars <= 0) return ""
    if (maxChars == 1) return "…"
    return clean.take(maxChars - 1).trimEnd() + "…"
}

/**
 * Shares `room` characters among several texts so that the total fits: texts shorter than their fair share
 * keep everything, and the room they leave unused goes to the longer ones (max-min fairness). Short, important
 * parts (the task) are therefore never sacrificed to a long draft.
 */
fun fitShares(texts: List<String>, room: Int): List<String> {
    val clean = texts.map { it.trim() }
    val allocation = IntArray(clean.size)
    var remaining = room.coerceAtLeast(0)
    val pending = clean.indices.sortedBy { clean[it].length }.toMutableList()
    while (pending.isNotEmpty()) {
        val share = remaining / pending.size
        val shortest = pending.first()
        if (clean[shortest].length <= share) {
            allocation[shortest] = clean[shortest].length
            remaining -= allocation[shortest]
            pending.removeAt(0)
        } else {
            // Everything left is longer than the fair share: split what remains evenly.
            pending.forEach { allocation[it] = share }
            break
        }
    }
    return clean.mapIndexed { index, text -> text.fit(allocation[index]) }
}

private const val DEFAULT_CONTEXT_TOKENS = 3_000
private const val SAFETY_TOKENS = 64
private const val MIN_PROMPT_CHARS = 200

/**
 * What a pipeline needs to know about an engine to size its prompts. `contextTokens` is the TOTAL budget
 * (prompt + answer) of the small engines (MediaPipe 1024, LiteRT-LM 2048) and `null` when it is large or
 * unknown (Nano, cloud); then a conservative default is assumed so prompts stay compact anyway.
 */
data class EngineProfile(val contextTokens: Int? = null, val structuredOutput: Boolean = false) {

    /** Answer length to request: `wanted`, but never more than a quarter of a small context. */
    fun outputTokens(wanted: Int): Int = contextTokens?.let { minOf(wanted, it / 4) } ?: wanted

    /** Characters left for the variable parts of a prompt once the answer, a safety margin and the fixed text are reserved. */
    fun promptChars(outputTokens: Int, fixedChars: Int = 0): Int {
        val context = contextTokens ?: DEFAULT_CONTEXT_TOKENS
        return ((context - outputTokens - SAFETY_TOKENS) * CHARS_PER_TOKEN - fixedChars).coerceAtLeast(MIN_PROMPT_CHARS)
    }

    /** Fits the variable `texts` of a prompt whose fixed wording is `template` into the engine's budget. */
    fun fitParts(outputTokens: Int, template: String, vararg texts: String): List<String> =
        fitShares(texts.toList(), promptChars(outputTokens, template.length))
}

/** How a pipeline looks up the profile of the engine it is about to call. */
typealias ProfileOf = (InferenceTier) -> EngineProfile
