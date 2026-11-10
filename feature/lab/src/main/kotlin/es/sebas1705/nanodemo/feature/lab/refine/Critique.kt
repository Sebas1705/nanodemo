package es.sebas1705.nanodemo.feature.lab.refine

import kotlinx.serialization.json.JsonObject

enum class Verdict { ACCEPT, REVISE, UNKNOWN }

/**
 * What a critic said about a draft. `structured` is true when it came from a JSON object and false when it
 * was recovered from free text (a looser, less trustworthy reading).
 */
data class Critique(val verdict: Verdict, val issues: List<String>, val structured: Boolean)

private const val MAX_ISSUES = 5
private const val MAX_ISSUE_CHARS = 200

private val VERDICT_KEYS = arrayOf("verdict", "decision", "status", "result", "veredicto", "decisión")
private val ISSUE_KEYS = arrayOf(
    "issues", "problems", "feedback", "suggestions", "errors", "improvements", "comments", "critique",
    "problemas", "sugerencias", "errores",
)
private val BOOLEAN_ACCEPT_KEYS = arrayOf("accept", "accepted", "approved", "pass", "passed", "ok", "aprobado")

private val VERDICT_TEXT = Regex("(?i)\\b(?:verdict|veredicto|decision|decisión)\\b[^A-Za-z]{0,6}([A-Za-zÀ-ÿ_]+)")
private val BARE_VERDICT = Regex(
    "(?i)\\b(accept(?:ed)?|approved?|lgtm|revise|revision|needs? (?:revision|improvement|changes)|reject(?:ed)?|aceptad[oa]|aprobad[oa]|revisar|rechazad[oa])\\b",
)
private val BULLET = Regex("^\\s*(?:[-*•]|\\d+[.)])\\s+(.+)$")

/** Maps a verdict word in English or Spanish to a [Verdict]; `UNKNOWN` when it means nothing to us. */
fun verdictFromWord(word: String): Verdict {
    val w = word.plain().trim().trim('"', '\'', '.', '!', ' ', '*', '_')
    if (w.isEmpty()) return Verdict.UNKNOWN
    val revise = listOf("revis", "reject", "fail", "improv", "chang", "need", "redo", "rechaz", "mejor", "incorrect", "correg", "wrong", "invalid", "invalido")
    val accept = listOf("accept", "approv", "pass", "ok", "lgtm", "good", "correct", "acept", "aprob", "bien", "valid")
    return when {
        revise.any { w.startsWith(it) } -> Verdict.REVISE
        accept.any { w.startsWith(it) } -> Verdict.ACCEPT
        else -> Verdict.UNKNOWN
    }
}

private fun cleanIssues(raw: List<String>): List<String> = raw
    .map { line -> line.trim().replace(Regex("^(?:[-*•]|\\d+[.)])\\s*"), "").trim() }
    .filter { it.isNotEmpty() }
    .map { it.fit(MAX_ISSUE_CHARS) }
    .take(MAX_ISSUES)

/**
 * Tolerant reading of a critic's reply. Preferred shape: `{"verdict":"accept"|"revise","issues":[...]}`;
 * synonyms, Spanish keys, a boolean `accept`, bullet lists and prose such as `Verdict: REVISE` are understood too.
 * A reply that lists issues but names no verdict counts as "revise"; one with nothing usable is [Verdict.UNKNOWN],
 * which the pipeline treats as "stop, the critic is unreadable" instead of looping.
 */
fun parseCritique(raw: String): Critique {
    extractJsonObject(raw)?.let { json -> readCritiqueJson(json)?.let { return it } }
    return readCritiqueText(raw)
}

private fun readCritiqueJson(json: JsonObject): Critique? {
    val issues = cleanIssues(json.getIgnoreCase(*ISSUE_KEYS)?.stringList().orEmpty())
    var verdict = json.getIgnoreCase(*VERDICT_KEYS)?.textOrNull()?.let(::verdictFromWord) ?: Verdict.UNKNOWN
    if (verdict == Verdict.UNKNOWN) {
        json.getIgnoreCase(*BOOLEAN_ACCEPT_KEYS)?.boolOrNull()?.let { verdict = if (it) Verdict.ACCEPT else Verdict.REVISE }
    }
    if (verdict == Verdict.UNKNOWN && issues.isNotEmpty()) verdict = Verdict.REVISE
    // A JSON object with neither verdict nor issues says nothing: let the text reading try the raw reply.
    return if (verdict == Verdict.UNKNOWN) null else Critique(verdict, issues, structured = true)
}

private fun readCritiqueText(raw: String): Critique {
    val bullets = cleanIssues(raw.lines().mapNotNull { BULLET.find(it)?.groupValues?.get(1) })
    var verdict = VERDICT_TEXT.find(raw)?.groupValues?.get(1)?.let(::verdictFromWord) ?: Verdict.UNKNOWN
    if (verdict == Verdict.UNKNOWN) {
        verdict = BARE_VERDICT.find(raw)?.groupValues?.get(1)?.let(::verdictFromWord) ?: Verdict.UNKNOWN
    }
    if (verdict == Verdict.UNKNOWN && bullets.isNotEmpty()) verdict = Verdict.REVISE
    return Critique(verdict, if (verdict == Verdict.UNKNOWN) emptyList() else bullets, structured = false)
}
