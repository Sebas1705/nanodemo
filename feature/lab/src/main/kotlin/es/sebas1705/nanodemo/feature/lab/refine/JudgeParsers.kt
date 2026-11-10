package es.sebas1705.nanodemo.feature.lab.refine

import kotlinx.serialization.json.JsonObject
import kotlin.math.roundToInt

enum class Criterion(val label: String) {
    CORRECTNESS("Corrección"),
    COMPLETENESS("Completitud"),
    CLARITY("Claridad"),
}

/** Rubric scores from 1 to 5; a criterion the judge did not score is `null`. */
data class RubricScores(val correctness: Int?, val completeness: Int?, val clarity: Int?) {
    operator fun get(criterion: Criterion): Int? = when (criterion) {
        Criterion.CORRECTNESS -> correctness
        Criterion.COMPLETENESS -> completeness
        Criterion.CLARITY -> clarity
    }

    /** Mean of the criteria that were scored; `null` when none was. */
    val average: Double?
        get() = listOfNotNull(correctness, completeness, clarity).takeIf { it.isNotEmpty() }?.average()
}

/** The judge's choice in a head-to-head, in terms of position (not of candidate). */
enum class Pick { FIRST, SECOND, TIE }

private val CRITERION_NAMES = mapOf(
    Criterion.CORRECTNESS to listOf("correctness", "accuracy", "correct", "correccion", "corrección", "exactitud"),
    Criterion.COMPLETENESS to listOf("completeness", "complete", "completitud", "completo"),
    Criterion.CLARITY to listOf("clarity", "clear", "claridad", "claro"),
)

private val NUMBER = Regex("\\d+(?:[.,]\\d+)?")

private fun scoreFrom(text: String?): Int? {
    val number = text?.let { NUMBER.find(it)?.value }?.replace(',', '.')?.toDoubleOrNull() ?: return null
    val rounded = number.roundToInt()
    return rounded.takeIf { it in 1..5 }
}

/**
 * Tolerant reading of a rubric reply: `{"correctness":4,"completeness":3,"clarity":5}`, scores nested under a
 * key such as "scores", values like `"4/5"`, Spanish key names, or `correctness: 4` lines in prose. Values outside
 * 1..5 are discarded (we cannot know the scale the judge meant). `null` when no criterion could be read.
 */
fun parseRubric(raw: String): RubricScores? {
    extractJsonObject(raw)?.let { json -> fromJson(json)?.let { return it } }
    return fromText(raw)
}

private fun fromJson(json: JsonObject): RubricScores? {
    val flat = scoresIn(json)
    if (flat != null) return flat
    // Scores nested one level down ("scores": {...}).
    return json.values.filterIsInstance<JsonObject>().firstNotNullOfOrNull { scoresIn(it) }
}

private fun scoresIn(json: JsonObject): RubricScores? {
    fun read(c: Criterion) = scoreFrom(json.getIgnoreCase(*CRITERION_NAMES.getValue(c).toTypedArray())?.textOrNull())
    val scores = RubricScores(read(Criterion.CORRECTNESS), read(Criterion.COMPLETENESS), read(Criterion.CLARITY))
    return scores.takeIf { it.average != null }
}

private fun fromText(raw: String): RubricScores? {
    fun read(c: Criterion): Int? {
        val names = CRITERION_NAMES.getValue(c).joinToString("|")
        val match = Regex("(?i)\\b(?:$names)\\b[^0-9A-Za-z]{0,6}(\\d+(?:[.,]\\d+)?)").find(raw)
        return scoreFrom(match?.groupValues?.get(1))
    }
    val scores = RubricScores(read(Criterion.CORRECTNESS), read(Criterion.COMPLETENESS), read(Criterion.CLARITY))
    return scores.takeIf { it.average != null }
}

private val PICK_KEYS = arrayOf("winner", "best", "better", "preferred", "choice", "answer", "ganador", "mejor")

private fun pickFromToken(token: String): Pick? {
    val t = token.trim().trim('"', '\'', '.', '*', '(', ')', ' ').lowercase()
    return when {
        t in setOf("a", "1", "first", "answer a", "response a", "candidate a", "respuesta a", "primera") -> Pick.FIRST
        t in setOf("b", "2", "second", "answer b", "response b", "candidate b", "respuesta b", "segunda") -> Pick.SECOND
        t in setOf("tie", "draw", "equal", "both", "empate", "igual", "ninguna") -> Pick.TIE
        else -> null
    }
}

// Letter A/B must be uppercase in prose so the article "a" is not read as a vote.
private const val NOUN = "(?i:(?:answer|response|candidate|respuesta|opci[oó]n)\\s+)?"
private const val COPULA = "(?i:(?:is|es)\\s+)?"
private val PICK_AFTER_KEYWORD = Regex(
    "(?i:\\b(?:winner|better|best|preferred|ganador|mejor)\\b)[^A-Za-z0-9]{0,10}$NOUN$COPULA$NOUN\\(?\\s*" +
        "(?:(A|B)\\b|(1|2)\\b|((?i:tie|draw|empate)))",
)
private val PICK_SENTENCE = Regex("\\b(?:Answer|Response|Respuesta)\\s+\\(?(A|B)\\)?\\s+(?i:is|es)\\s+(?i:better|best|clearly|more|mejor|superior)")

/**
 * Tolerant reading of a head-to-head reply: `{"winner":"A"|"B"|"tie"}` (also "first", "1", "empate"...), or prose such as
 * "Winner: B" / "Answer A is better", or a bare "B". `null` when the reply does not say.
 */
fun parsePick(raw: String): Pick? {
    extractJsonObject(raw)?.let { json ->
        json.getIgnoreCase(*PICK_KEYS)?.textOrNull()?.let(::pickFromToken)?.let { return it }
    }
    PICK_AFTER_KEYWORD.find(raw)?.let { m ->
        val (letter, digit, tie) = m.destructured
        return when {
            letter.isNotEmpty() -> if (letter == "A") Pick.FIRST else Pick.SECOND
            digit.isNotEmpty() -> if (digit == "1") Pick.FIRST else Pick.SECOND
            tie.isNotEmpty() -> Pick.TIE
            else -> null
        }
    }
    PICK_SENTENCE.find(raw)?.let { return if (it.groupValues[1] == "A") Pick.FIRST else Pick.SECOND }
    return pickFromToken(raw.trim().trimEnd('.', '!'))
}
