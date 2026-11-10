package es.sebas1705.nanodemo.feature.lab.refine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

private val lenientJson = Json {
    isLenient = true
    ignoreUnknownKeys = true
}

private val TRAILING_COMMA = Regex(",\\s*([}\\]])")
private val SINGLE_QUOTED = Regex("'([^'\\\\]*)'")

/**
 * Finds the first JSON object in a model reply, tolerating what small models do around it: prose before and
 * after, ```json fences, single quotes, unquoted keys and trailing commas. Returns `null` when there is none
 * (including an object cut off by the token limit).
 */
fun extractJsonObject(raw: String): JsonObject? {
    var from = 0
    while (from < raw.length) {
        val start = raw.indexOf('{', from)
        if (start < 0) return null
        val end = matchingBrace(raw, start)
        if (end != null) {
            parseObject(raw.substring(start, end + 1))?.let { return it }
        }
        from = start + 1
    }
    return null
}

private fun parseObject(candidate: String): JsonObject? {
    var repaired = candidate.replace(TRAILING_COMMA, "$1")
    // Python-style objects: only when no double quote is present, so apostrophes inside strings are left alone.
    if ('"' !in repaired) repaired = repaired.replace(SINGLE_QUOTED, "\"$1\"")
    return runCatching { lenientJson.parseToJsonElement(repaired) as? JsonObject }.getOrNull()
}

/** Index of the `}` that closes the `{` at `start`, ignoring braces inside double-quoted strings; `null` if unbalanced. */
private fun matchingBrace(text: String, start: Int): Int? {
    var depth = 0
    var inString = false
    var escaped = false
    for (i in start until text.length) {
        val c = text[i]
        if (inString) {
            when {
                escaped -> escaped = false
                c == '\\' -> escaped = true
                c == '"' -> inString = false
            }
            continue
        }
        when (c) {
            '"' -> inString = true
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) return i
            }
        }
    }
    return null
}

/** The value under the first key that matches one of `names`, ignoring case. */
fun JsonObject.getIgnoreCase(vararg names: String): JsonElement? =
    entries.firstOrNull { entry -> names.any { it.equals(entry.key, ignoreCase = true) } }?.value

/** Text of a primitive (string, number or boolean); `null` for null, objects and arrays. */
fun JsonElement.textOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull

fun JsonElement.boolOrNull(): Boolean? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.booleanOrNull

/** Strings of an array, or the lines of a single string. */
fun JsonElement.stringList(): List<String> = when (this) {
    is JsonArray -> mapNotNull { item ->
        when (item) {
            is JsonObject -> item.getIgnoreCase("issue", "problem", "description", "text", "message")?.textOrNull()
                ?: item.values.firstNotNullOfOrNull { it.textOrNull() }
            else -> item.textOrNull()
        }
    }
    is JsonPrimitive -> textOrNull()?.lines().orEmpty()
    else -> emptyList()
}
