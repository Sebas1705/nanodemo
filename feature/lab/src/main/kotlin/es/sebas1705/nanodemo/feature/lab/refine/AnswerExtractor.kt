package es.sebas1705.nanodemo.feature.lab.refine

import java.math.BigDecimal
import java.text.Normalizer

/** What shape the short final answer has; decides how it is read and normalised so equal answers vote together. */
enum class AnswerKind(val label: String) {
    AUTO("Auto"),
    NUMBER("Número"),
    YES_NO("Sí/No"),
    LETTER("Opción (letra)"),
    PHRASE("Frase corta"),
}

/**
 * A normalised final answer. `key` is what votes and checks compare (e.g. "29", "yes", "B", "canberra");
 * `display` is the same answer in a form worth showing.
 */
data class Extracted(val kind: AnswerKind, val key: String, val display: String)

/** Lowercase, no diacritics. */
fun String.plain(): String = Normalizer.normalize(this, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

/** Lowercase letters and digits and single spaces only: "14/03/2027" and "Maria.Lopez@x.com" become comparable text. */
fun normalizeLoose(text: String): String = text.plain().replace(Regex("[^a-z0-9ñ ]+"), "").replace(Regex("\\s+"), " ").trim()

private val MARKER = Regex(
    "(?i)(?:final\\s+answer|respuesta\\s+final|the\\s+answer\\s+is|la\\s+respuesta\\s+es|answer|respuesta|####)\\s*(?:is|es)?\\s*[:=]?[ \\t]*(.*)",
)
private val BOXED = Regex("\\\\boxed\\{([^}]*)}")
private val ARTICLES = setOf("the", "a", "an", "el", "la", "los", "las", "un", "una")

private fun clean(segment: String): String =
    segment.trim()
        .trimStart('*', '`', '_', '"', '\'', '«', '»', ' ')
        .trimEnd('*', '`', '_', '"', '\'', '«', '»', ' ', '.', '!')

/**
 * The short answer a model gave: whatever follows the last "Final answer:" style marker (also "Respuesta:",
 * "The answer is", "####", `\boxed{}`), or the last non-empty line when there is no marker. Markdown emphasis is stripped.
 */
fun finalSegment(text: String): String {
    BOXED.findAll(text).lastOrNull()?.let { return clean(it.groupValues[1]) }
    val lines = text.lines()
    val marker = MARKER.findAll(text).lastOrNull()
    if (marker != null) {
        val inline = clean(marker.groupValues[1])
        if (inline.isNotEmpty()) return inline
        // "Final answer:" alone on its line: the answer is on the next non-empty line.
        val after = text.substring(marker.range.last + 1).lines().map { clean(it) }.firstOrNull { it.isNotEmpty() }
        if (after != null) return after
    }
    return lines.map { clean(it) }.lastOrNull { it.isNotEmpty() } ?: ""
}

/**
 * Canonical form of a number written in either convention: "1,234.5", "1.234,5", "3,5", "12.0", "-7", "1 000" is not
 * joined. A single comma followed by exactly three digits is a thousands separator ("1,234"), otherwise a decimal
 * comma ("3,5"); several separators of one kind are thousands; a single dot is a decimal point.
 */
fun normalizeNumber(raw: String): String? {
    var t = raw.trim()
    val negative = t.startsWith("-") || t.startsWith("−")
    t = t.trimStart('-', '−', '+')
    if (t.isEmpty() || !t.first().isDigit()) return null
    val dots = t.count { it == '.' }
    val commas = t.count { it == ',' }
    val canonical = when {
        dots > 0 && commas > 0 ->
            if (t.lastIndexOf('.') > t.lastIndexOf(',')) t.replace(",", "") else t.replace(".", "").replace(',', '.')
        commas > 1 -> t.replace(",", "")
        commas == 1 -> {
            val before = t.substringBefore(',')
            val after = t.substringAfter(',')
            if (after.length == 3 && before.length in 1..3) before + after else "$before.$after"
        }
        dots > 1 -> t.replace(".", "")
        else -> t
    }
    val value = runCatching { BigDecimal(canonical) }.getOrNull() ?: return null
    if (value.signum() == 0) return "0"
    val text = value.stripTrailingZeros().toPlainString()
    return if (negative) "-$text" else text
}

private val NUMBER_TOKEN = Regex("[-−]?\\d+(?:[.,]\\d+)*")
private val YES_WORDS = setOf("yes", "si", "true", "verdadero", "correct", "correcto", "cierto")
private val NO_WORDS = setOf("no", "false", "falso", "incorrect", "incorrecto")
private val WORD = Regex("[a-z]+")
private val LETTER_START = Regex("^\\W*\\(?([A-Ea-e])(?:[).:]|\\s|$)")
private val LETTER_PHRASE = Regex("(?i)\\b(?:option|opcion|opción|letter|letra|answer|respuesta)\\s*(?:is|es)?\\s*\\(?([A-E])\\b")
private val LETTER_STANDALONE = Regex("\\b([A-E])\\b")

private fun numberIn(segment: String): String? {
    // "3 + 4 = 7": the result is what follows the last "=".
    val tail = segment.substringAfterLast('=')
    return NUMBER_TOKEN.find(tail)?.value?.let(::normalizeNumber)
}

private fun yesNoIn(segment: String): String? {
    WORD.findAll(segment.plain()).forEach { m ->
        when (m.value) {
            in YES_WORDS -> return "yes"
            in NO_WORDS -> return "no"
        }
    }
    return null
}

private fun letterIn(segment: String): String? =
    (LETTER_START.find(segment) ?: LETTER_PHRASE.find(segment) ?: LETTER_STANDALONE.find(segment))
        ?.groupValues?.get(1)?.uppercase()

private fun phraseIn(segment: String): Extracted? {
    val words = normalizeLoose(segment).split(' ').filter { it.isNotEmpty() }.dropWhile { it in ARTICLES }
    if (words.isEmpty()) return null
    return Extracted(AnswerKind.PHRASE, words.joinToString(" ").take(60), segment.trim().take(60))
}

/** Reads the short answer of `text` as `kind` (not [AnswerKind.AUTO]); `null` when there is no answer of that shape. */
fun extractAnswer(text: String, kind: AnswerKind): Extracted? {
    val segment = finalSegment(text)
    if (segment.isEmpty()) return null
    return when (kind) {
        AnswerKind.NUMBER -> numberIn(segment)?.let { Extracted(kind, it, it) }
        AnswerKind.YES_NO -> yesNoIn(segment)?.let { Extracted(kind, it, if (it == "yes") "sí" else "no") }
        AnswerKind.LETTER -> letterIn(segment)?.let { Extracted(kind, it, it) }
        AnswerKind.PHRASE -> phraseIn(segment)
        AnswerKind.AUTO -> extractAll(listOf(text), AnswerKind.AUTO).single()
    }
}

private val KIND_PRIORITY = listOf(AnswerKind.NUMBER, AnswerKind.YES_NO, AnswerKind.LETTER, AnswerKind.PHRASE)

/** Which shape a single answer looks like, for [AnswerKind.AUTO]. */
fun detectKind(text: String): AnswerKind {
    val segment = finalSegment(text)
    val firstWord = WORD.find(segment.plain())?.value
    return when {
        Regex("^\\W*\\(?[A-Ea-e]\\)?\\W*$").matches(segment) -> AnswerKind.LETTER
        firstWord != null && (firstWord in YES_WORDS || firstWord in NO_WORDS) -> AnswerKind.YES_NO
        numberIn(segment) != null -> AnswerKind.NUMBER
        else -> AnswerKind.PHRASE
    }
}

/**
 * Extracts every answer with ONE shape, so that "12", "twelve apples" and "about 12" can vote together. With
 * [AnswerKind.AUTO] the shape is the most common one among the answers (a tie goes to number, yes/no, letter, phrase in that order).
 */
fun extractAll(texts: List<String>, kind: AnswerKind): List<Extracted?> {
    val resolved = if (kind != AnswerKind.AUTO) kind else {
        val counts = texts.filter { finalSegment(it).isNotEmpty() }.groupingBy { detectKind(it) }.eachCount()
        KIND_PRIORITY.maxByOrNull { counts[it] ?: 0 } ?: AnswerKind.PHRASE
    }
    return texts.map { extractAnswer(it, resolved) }
}

/** Whether a model's answer matches the reference answer the user typed, both read with the same shape. */
fun matchesExpected(expected: String, answer: Extracted?): Boolean {
    if (answer == null || expected.isBlank()) return false
    return extractAnswer(expected, answer.kind)?.key == answer.key
}
