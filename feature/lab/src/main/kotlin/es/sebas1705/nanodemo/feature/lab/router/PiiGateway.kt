package es.sebas1705.nanodemo.feature.lab.router

/** One distinct datum that was masked: every occurrence of the same value shares `placeholder`. */
data class Replacement(
    val placeholder: String,
    val kind: PiiKind,
    val original: String,
    val occurrences: Int,
)

/**
 * Result of masking a text. `redacted` is the only thing allowed to leave the device; `replacements` is the
 * reversible map kept locally to restore the cloud answer.
 */
data class RedactionResult(
    val original: String,
    val redacted: String,
    val replacements: List<Replacement>,
    /** The detected spans in `original`, in order: used by the UI to highlight what was masked. */
    val spans: List<PiiMatch>,
) {
    val isEmpty: Boolean get() = replacements.isEmpty()

    /** Distinct values masked. */
    val distinctCount: Int get() = replacements.size

    /** Total occurrences masked (the same email twice counts twice). */
    val occurrenceCount: Int get() = replacements.sumOf { it.occurrences }

    fun countOf(kind: PiiKind): Int = replacements.count { it.kind == kind }
}

/**
 * Result of putting the original values back into a cloud answer. `missing` are masks the model dropped (not
 * necessarily a problem); `unknown` look like placeholders but are not in the map (the model invented one), and
 * are left untouched so nothing wrong is ever substituted.
 */
data class RehydrationResult(
    val text: String,
    val restored: List<Replacement>,
    val missing: List<Replacement>,
    val unknown: List<String>,
)

/**
 * The privacy gateway: mask personal data and secrets with stable placeholders before a prompt goes to a cloud
 * engine, then restore them locally in the answer. Pure; the originals never leave the [RedactionResult].
 */
object PiiGateway {

    fun redact(text: String, kinds: Set<PiiKind> = PiiKind.entries.toSet()): RedactionResult {
        val matches = PiiDetector.detect(text, kinds)
        if (matches.isEmpty()) return RedactionResult(text, text, emptyList(), emptyList())

        val placeholderByKey = LinkedHashMap<String, String>()
        val originalByKey = HashMap<String, String>()
        val kindByKey = HashMap<String, PiiKind>()
        val occurrences = HashMap<String, Int>()
        val counters = HashMap<PiiKind, Int>()

        val out = StringBuilder()
        var cursor = 0
        for (match in matches) {
            val key = "${match.kind}|${normalize(match)}"
            val placeholder = placeholderByKey.getOrPut(key) {
                originalByKey[key] = match.value
                kindByKey[key] = match.kind
                nextPlaceholder(match.kind, counters, text)
            }
            occurrences[key] = (occurrences[key] ?: 0) + 1
            out.append(text, cursor, match.start).append(placeholder)
            cursor = match.end
        }
        out.append(text, cursor, text.length)

        val replacements = placeholderByKey.map { (key, placeholder) ->
            Replacement(placeholder, kindByKey.getValue(key), originalByKey.getValue(key), occurrences.getValue(key))
        }
        return RedactionResult(text, out.toString(), replacements, matches)
    }

    /** `<EMAIL_1>`, `<EMAIL_2>`...: skips any index the user text already contains literally, so a restore is never ambiguous. */
    private fun nextPlaceholder(kind: PiiKind, counters: MutableMap<PiiKind, Int>, source: String): String {
        var n = counters[kind] ?: 0
        var candidate: String
        do {
            n += 1
            candidate = "<${kind.tag}_$n>"
        } while (source.contains(candidate, ignoreCase = true))
        counters[kind] = n
        return candidate
    }

    /** Equivalent spellings of the same datum share a placeholder (same address in other case, phone with or without +34). */
    private fun normalize(match: PiiMatch): String = when (match.kind) {
        PiiKind.EMAIL -> match.value.lowercase()
        PiiKind.PHONE -> {
            val digits = match.value.filter { it.isDigit() }
            when {
                digits.startsWith("0034") -> digits.drop(4)
                digits.startsWith("34") && digits.length == 11 -> digits.drop(2)
                else -> digits
            }
        }
        PiiKind.IBAN, PiiKind.CARD, PiiKind.DNI, PiiKind.NIE ->
            match.value.filter { it.isLetterOrDigit() }.uppercase()
        else -> match.value
    }

    /** Anything personal still detectable in text that is about to leave the device (should be empty after [redact]). */
    fun residual(outgoing: String, kinds: Set<PiiKind> = PiiKind.entries.toSet()): List<PiiMatch> =
        PiiDetector.detect(outgoing, kinds)

    // ---- rehydration --------------------------------------------------------------------------------------

    private val tagAlternation = PiiKind.entries.joinToString("|") { Regex.escape(it.tag) }

    /**
     * Matches a placeholder the way models tend to mangle it: other bracket styles (`[EMAIL_1]`, `{EMAIL_1}`),
     * lower case, spaces or a dash instead of the underscore, markdown-escaped (`\<EMAIL\_1\>`) or HTML-escaped
     * (`&lt;EMAIL_1&gt;`). A bare word is only accepted in the exact `EMAIL_1` form, so prose such as "phone 1" or
     * "IP 2" is left alone.
     */
    private val placeholderRegex = Regex(
        """(?:(&lt;|\\?[<\[{‹«])\s*)?\b($tagAlternation)(\s*(?:\\?_|[\-#])?\s*)(\d{1,3})\b(?:\s*(&gt;|\\?[>\]}›»]))?""",
        RegexOption.IGNORE_CASE,
    )

    fun rehydrate(answer: String, redaction: RedactionResult): RehydrationResult {
        if (redaction.isEmpty) return RehydrationResult(answer, emptyList(), emptyList(), emptyList())
        val byPlaceholder = redaction.replacements.associateBy { it.placeholder }
        val seen = LinkedHashSet<String>()
        val unknown = LinkedHashSet<String>()

        val restoredText = placeholderRegex.replace(answer) { m ->
            val open = m.groupValues[1]
            val tag = m.groupValues[2]
            val separator = m.groupValues[3]
            val number = m.groupValues[4].toInt()
            val close = m.groupValues[5]
            val canonicalBare = tag == tag.uppercase() && separator.replace("\\", "").trim() == "_"
            if (open.isEmpty() && close.isEmpty() && !canonicalBare) return@replace m.value

            val key = "<${tag.uppercase()}_$number>"
            val replacement = byPlaceholder[key]
            if (replacement == null) {
                unknown += key
                m.value
            } else {
                seen += key
                replacement.original
            }
        }
        val restored = redaction.replacements.filter { it.placeholder in seen }
        val missing = redaction.replacements.filter { it.placeholder !in seen }
        return RehydrationResult(restoredText, restored, missing, unknown.toList())
    }
}
