package es.sebas1705.nanodemo.feature.lab.guard

import java.util.Base64

/** One suspicious span found in a document: which rule fired and where (indexes into the ORIGINAL text). */
data class Finding(val rule: String, val start: Int, val end: Int, val excerpt: String)

/** `score` is the sum of the weights of the distinct rules that fired; a document is flagged at [FLAG_SCORE]. */
data class Detection(val score: Int, val findings: List<Finding>) {
    val flagged: Boolean get() = score >= InjectionDetector.FLAG_SCORE
    val rules: Set<String> get() = findings.map { it.rule }.toSet()
}

private class Rule(val id: String, val weight: Int, val regex: Regex)

private const val EXCERPT_CHARS = 80
private const val BASE64_MIN_CHARS = 40
private const val PRINTABLE_SHARE = 0.9

/** Length-preserving fold (lowercase, accents to base letters) so match indexes stay valid in the original text. */
internal fun foldPreservingLength(text: String): String = buildString(text.length) {
    for (c in text) {
        append(
            when (c) {
                'á', 'à', 'ä', 'â', 'Á', 'À', 'Ä', 'Â' -> 'a'
                'é', 'è', 'ë', 'ê', 'É', 'È', 'Ë', 'Ê' -> 'e'
                'í', 'ì', 'ï', 'î', 'Í', 'Ì', 'Ï', 'Î' -> 'i'
                'ó', 'ò', 'ö', 'ô', 'Ó', 'Ò', 'Ö', 'Ô' -> 'o'
                'ú', 'ù', 'ü', 'û', 'Ú', 'Ù', 'Ü', 'Û' -> 'u'
                'ñ', 'Ñ' -> 'n'
                else -> c.lowercaseChar()
            },
        )
    }
}

/**
 * Heuristic detector of prompt-injection attempts in untrusted text (a document, a web page, an email). It is
 * deliberately simple and explainable: a handful of weighted rules in English and Spanish, plus a look inside
 * base64 blobs. It will miss novel attacks and can flag odd benign text; the point of the Lab is to measure by how
 * much it helps, not to claim it is sufficient.
 */
object InjectionDetector {

    /** Total weight at which a document counts as suspicious. One weak rule alone does not flag. */
    const val FLAG_SCORE = 3

    private val RULES = listOf(
        Rule("override", 3, Regex("(ignore|disregard|forget|override)\\s+(all\\s+|any\\s+)?(of\\s+)?(the\\s+|your\\s+|my\\s+)?(previous|prior|above|earlier|preceding)\\s+(instructions?|rules?|prompts?|directions?)")),
        Rule("override_es", 3, Regex("(ignora|olvida|descarta|omite)\\s+(todas\\s+)?(las\\s+|tus\\s+)?(instrucciones|reglas|indicaciones)(\\s+(anteriores|previas))?")),
        Rule("role_hijack", 1, Regex("you are now\\s+\\w+|from now on,? you (are|will)|ahora eres\\s+\\w+|a partir de ahora")),
        Rule("jailbreak_persona", 2, Regex("(debugbot|jailbroken|developer mode|unrestricted (mode|ai|assistant)|\\bdan\\b)")),
        Rule("reveal", 3, Regex("(reveal|print|output|show|include|append|write|leak|repeat|tell me|give me)\\s+[^.\\n]{0,50}(secret|internal (reference )?code|reference code|password|system prompt|api key|credentials)")),
        Rule("reveal_es", 3, Regex("(revela|muestra|escribe|incluye|imprime|dime|repite)\\s+[^.\\n]{0,50}(secreto|codigo de referencia|codigo interno|contrasena|prompt del sistema|clave de api|credenciales)")),
        Rule("system_marker", 2, Regex("(^|\\n)\\s*(#{1,3}\\s*)?(system|assistant|sistema)\\s*:")),
        Rule("html_comment", 1, Regex("<!--[\\s\\S]{0,400}?-->")),
        Rule("exfil_image", 3, Regex("!\\[[^\\]]*]\\(https?://[^)\\s]*\\?[^)\\s]*\\)")),
        Rule("new_policy", 2, Regex("(new|updated|nueva|nuevas|actualizada)\\s+(policy|instructions|rules|politica|instrucciones|reglas)\\s*:")),
        Rule("concealment", 2, Regex("(do not|don't|never|no)\\s+(tell|mention|inform|digas|menciones|informes)\\s+(the\\s+|al\\s+)?(user|usuario)")),
        Rule("zero_width", 2, Regex("[\\u200B-\\u200F\\u2060\\uFEFF]")),
    )

    private val BASE64 = Regex("[A-Za-z0-9+/]{$BASE64_MIN_CHARS,}={0,2}")

    fun detect(text: String): Detection {
        val folded = foldPreservingLength(text)
        val findings = mutableListOf<Finding>()
        for (rule in RULES) {
            for (match in rule.regex.findAll(folded)) {
                findings += Finding(rule.id, match.range.first, match.range.last + 1, text.substring(match.range.first, match.range.last + 1).excerpt())
            }
        }
        for (blob in BASE64.findAll(text)) {
            val decoded = decodeBase64(blob.value) ?: continue
            val inner = detectRules(decoded)
            if (inner.isNotEmpty()) {
                findings += Finding("encoded_instruction", blob.range.first, blob.range.last + 1, blob.value.excerpt())
            }
        }
        val score = findings.groupBy { it.rule }.keys.sumOf { id -> weightOf(id) }
        return Detection(score, findings.sortedBy { it.start })
    }

    private fun weightOf(id: String): Int = if (id == "encoded_instruction") 3 else RULES.first { it.id == id }.weight

    private fun detectRules(text: String): List<String> {
        val folded = foldPreservingLength(text)
        return RULES.filter { it.weight >= 2 && it.regex.containsMatchIn(folded) }.map { it.id }
    }

    private fun decodeBase64(blob: String): String? = runCatching {
        val bytes = Base64.getDecoder().decode(blob.padEnd((blob.length + 3) / 4 * 4, '='))
        val text = String(bytes, Charsets.UTF_8)
        text.takeIf { s -> s.isNotEmpty() && s.count { it.isLetterOrDigit() || it.isWhitespace() || it in ".,:;!?'\"-_()" }.toDouble() / s.length >= PRINTABLE_SHARE }
    }.getOrNull()

    private fun String.excerpt() = replace(Regex("\\s+"), " ").take(EXCERPT_CHARS)

    /** Marker that replaces a removed span. */
    const val REMOVED_MARKER = "[removed: possible instruction]"

    /**
     * Cuts the sentences (or lines) that contain a finding, so the rest of the document survives. Returns the text
     * unchanged when there is nothing to remove.
     */
    fun sanitize(text: String, detection: Detection): String {
        if (detection.findings.isEmpty()) return text
        val spans = detection.findings.map { expandToSentence(text, it.start, it.end) }.sortedBy { it.first }
        val merged = mutableListOf<IntRange>()
        for (span in spans) {
            val last = merged.lastOrNull()
            if (last != null && span.first <= last.last + 1) merged[merged.size - 1] = last.first..maxOf(last.last, span.last) else merged += span
        }
        val out = StringBuilder()
        var cursor = 0
        for (span in merged) {
            out.append(text, cursor, span.first).append(REMOVED_MARKER)
            cursor = span.last + 1
        }
        out.append(text, cursor, text.length)
        return out.toString()
    }

    private fun expandToSentence(text: String, start: Int, end: Int): IntRange {
        var left = start
        while (left > 0 && text[left - 1] != '\n' && !(text[left - 1] == ' ' && left >= 2 && text[left - 2] in ".!?")) left--
        var right = end
        while (right < text.length && text[right] != '\n' && text[right] !in ".!?") right++
        if (right < text.length && text[right] in ".!?") right++
        return left until right
    }
}
