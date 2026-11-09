package es.sebas1705.nanodemo.feature.lab.bench

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import java.text.Normalizer
import kotlin.math.abs

/** Scores a model answer between 0.0 (wrong) and 1.0 (fully right). A task passes at 1.0. */
fun interface Checker {
    fun score(answer: String): Double
}

/** Lowercase, accents removed, punctuation collapsed: what two "same" answers share. */
fun normalizeText(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9.,:\\-+/%'\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

/**
 * Every number written in `text`, understanding both "1,234.5" and "1.234,5" and a decimal comma ("399,5").
 * A lone separator followed by exactly three digits is read as a thousands separator ("1.234" -> 1234).
 */
fun extractNumbers(text: String): List<Double> =
    Regex("-?\\d+(?:[.,]\\d+)*").findAll(text).mapNotNull { parseLocalizedNumber(it.value) }.toList()

internal fun parseLocalizedNumber(raw: String): Double? {
    val lastDot = raw.lastIndexOf('.')
    val lastComma = raw.lastIndexOf(',')
    val normalized = when {
        lastDot >= 0 && lastComma >= 0 -> {
            val decimalIsComma = lastComma > lastDot
            if (decimalIsComma) raw.replace(".", "").replace(',', '.') else raw.replace(",", "")
        }
        lastComma >= 0 -> decimalOrThousands(raw, ',')
        lastDot >= 0 -> decimalOrThousands(raw, '.')
        else -> raw
    }
    return normalized.toDoubleOrNull()
}

private fun decimalOrThousands(raw: String, separator: Char): String {
    val parts = raw.split(separator)
    val looksThousands = parts.size > 2 || (parts.size == 2 && parts[1].length == 3 && parts[0].length in 1..3 && parts[0] != "0")
    return if (looksThousands) raw.replace(separator.toString(), "") else raw.replace(separator, '.')
}

/** The JSON object or array inside a reply, tolerating Markdown code fences and prose around it. */
fun extractJson(text: String): JsonElement? {
    val unfenced = text.replace(Regex("```[a-zA-Z]*"), "")
    val start = unfenced.indexOfFirst { it == '{' || it == '[' }
    if (start < 0) return null
    val closing = if (unfenced[start] == '{') '}' else ']'
    val end = unfenced.lastIndexOf(closing)
    if (end <= start) return null
    return runCatching { Json.parseToJsonElement(unfenced.substring(start, end + 1)) }.getOrNull()
}

fun wordCount(text: String): Int = text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }

object Checkers {

    /** All the fragments appear (case and accent insensitive); the score is the fraction found. */
    fun contains(vararg parts: String): Checker = Checker { answer ->
        val haystack = normalizeText(answer)
        parts.count { normalizeText(it) in haystack }.toDouble() / parts.size
    }

    /** At least one of the fragments appears. */
    fun containsAny(vararg parts: String): Checker = Checker { answer ->
        val haystack = normalizeText(answer)
        if (parts.any { normalizeText(it) in haystack }) 1.0 else 0.0
    }

    /** The word appears as a whole word (so "no" does not match "nombre"). */
    fun word(expected: String): Checker = Checker { answer ->
        val pattern = Regex("(?<![a-z0-9])" + Regex.escape(normalizeText(expected)) + "(?![a-z0-9])")
        if (pattern.containsMatchIn(normalizeText(answer))) 1.0 else 0.0
    }

    /** The first or the last number in the answer equals `expected`: models either answer first or conclude. */
    fun number(expected: Double, tolerance: Double = 1e-6): Checker = Checker { answer ->
        val numbers = extractNumbers(answer)
        val candidates = listOfNotNull(numbers.firstOrNull(), numbers.lastOrNull())
        if (candidates.any { abs(it - expected) <= tolerance }) 1.0 else 0.0
    }

    fun regex(pattern: String): Checker {
        val compiled = Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        return Checker { answer -> if (compiled.containsMatchIn(answer)) 1.0 else 0.0 }
    }

    fun wordsAtMost(max: Int): Checker = Checker { answer ->
        val n = wordCount(answer)
        if (n in 1..max) 1.0 else 0.0
    }

    fun wordsExactly(count: Int): Checker = Checker { answer ->
        if (wordCount(answer.replace(',', ' ')) == count) 1.0 else 0.0
    }

    /** Exactly `count` non-empty lines. */
    fun lines(count: Int): Checker = Checker { answer ->
        if (answer.lines().count { it.isNotBlank() } == count) 1.0 else 0.0
    }

    fun doesNotEndWith(suffix: Char): Checker = Checker { answer ->
        if (answer.trim().isNotEmpty() && !answer.trim().endsWith(suffix)) 1.0 else 0.0
    }

    /**
     * The reply holds a JSON object whose `fields` have the expected values (numbers compared numerically,
     * booleans as booleans, strings normalised). The score is the fraction of fields right, 0 if not JSON.
     */
    fun json(fields: Map<String, Any>): Checker = Checker { answer ->
        val element = extractJson(answer) as? JsonObject ?: return@Checker 0.0
        fields.count { (key, expected) -> element[key]?.let { matches(it, expected) } == true }.toDouble() / fields.size
    }

    private fun matches(actual: JsonElement, expected: Any): Boolean {
        val primitive = actual as? JsonPrimitive ?: return false
        return when (expected) {
            is Boolean -> !primitive.isString && primitive.booleanOrNull == expected
            is Number -> !primitive.isString && primitive.doubleOrNull?.let { abs(it - expected.toDouble()) < 1e-9 } == true
            else -> normalizeText(primitive.content) == normalizeText(expected.toString())
        }
    }

    /** Rough language check by stop-word share: `es` or `en`. Enough to tell a translation from an echo. */
    fun language(code: String): Checker = Checker { answer ->
        val words = normalizeText(answer).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return@Checker 0.0
        val es = words.count { it in SPANISH_STOPWORDS }
        val en = words.count { it in ENGLISH_STOPWORDS }
        val ok = if (code == "es") es > en else en > es
        if (ok) 1.0 else 0.0
    }

    /** Average of the checks: partial credit, and a task passes only when every constraint holds. */
    fun allOf(vararg checks: Checker): Checker = Checker { answer -> checks.map { it.score(answer) }.average() }
}

private val SPANISH_STOPWORDS = setOf("el", "la", "los", "las", "de", "que", "y", "en", "un", "una", "es", "por", "con", "para", "hoy", "esta", "hace")
private val ENGLISH_STOPWORDS = setOf("the", "is", "are", "of", "and", "to", "in", "a", "it", "today", "this", "for", "with", "where", "good")
