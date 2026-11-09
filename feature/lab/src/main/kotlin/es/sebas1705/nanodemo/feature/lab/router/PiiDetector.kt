package es.sebas1705.nanodemo.feature.lab.router

/**
 * What kind of personal or secret datum was found. `tag` is the word used inside the placeholder
 * (`<EMAIL_1>`); `label` is the Spanish name shown in the UI.
 */
enum class PiiKind(val tag: String, val label: String) {
    EMAIL("EMAIL", "correo"),
    PHONE("PHONE", "teléfono"),
    IBAN("IBAN", "IBAN"),
    CARD("CARD", "tarjeta"),
    DNI("DNI", "DNI"),
    NIE("NIE", "NIE"),
    IPV4("IP", "dirección IP"),
    URL("URL", "URL con credenciales"),
    SECRET("SECRET", "clave o secreto"),
}

/** A detected datum: `[start, end)` are offsets into the scanned text. */
data class PiiMatch(val kind: PiiKind, val start: Int, val end: Int, val value: String)

/** Checksum validators. They are what keeps order numbers, long ids and random digits from being flagged. */
object PiiValidators {
    private const val DNI_LETTERS = "TRWAGMYFPDXBNJZSQVHLCKE"

    /** Luhn mod-10 over the digits of [digits] (spaces and dashes must be removed first). */
    fun luhn(digits: String): Boolean {
        if (digits.length < 12 || digits.any { it !in '0'..'9' }) return false
        var sum = 0
        var double = false
        for (i in digits.indices.reversed()) {
            var d = digits[i] - '0'
            if (double) {
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
            double = !double
        }
        return sum % 10 == 0
    }

    /** Exact IBAN length per country for the ones we know; anything else only has to be 15..34 long. */
    private val ibanLengths = mapOf(
        "ES" to 24, "FR" to 27, "DE" to 22, "GB" to 22, "IT" to 27, "PT" to 25, "NL" to 18, "BE" to 16,
        "CH" to 21, "AT" to 20, "IE" to 22, "LU" to 20, "AD" to 24, "PL" to 28, "SE" to 24, "NO" to 15,
        "DK" to 18, "FI" to 18, "GR" to 27, "CZ" to 24,
    )

    /** ISO 13616 mod-97 check on an IBAN without separators. */
    fun iban(compact: String): Boolean {
        val s = compact.uppercase()
        if (s.length !in 15..34) return false
        if (!s[0].isLetter() || !s[1].isLetter() || !s[2].isDigit() || !s[3].isDigit()) return false
        if (s.any { !(it in 'A'..'Z' || it in '0'..'9') }) return false
        ibanLengths[s.take(2)]?.let { if (s.length != it) return false }
        val rearranged = s.substring(4) + s.substring(0, 4)
        var remainder = 0
        for (c in rearranged) {
            val value = if (c in '0'..'9') c - '0' else c - 'A' + 10
            remainder = if (value < 10) (remainder * 10 + value) % 97 else (remainder * 100 + value) % 97
        }
        return remainder == 1
    }

    /** Spanish DNI: 8 digits plus the control letter (`number mod 23`). */
    fun dni(number: String, letter: Char): Boolean {
        if (number.length != 8 || number.any { it !in '0'..'9' }) return false
        return DNI_LETTERS[number.toInt() % 23] == letter.uppercaseChar()
    }

    /** Spanish NIE: X/Y/Z, 7 digits and a control letter (X=0, Y=1, Z=2 prepended, then like a DNI). */
    fun nie(prefix: Char, number: String, letter: Char): Boolean {
        val lead = when (prefix.uppercaseChar()) {
            'X' -> '0'
            'Y' -> '1'
            'Z' -> '2'
            else -> return false
        }
        return dni(lead + number, letter)
    }
}

/**
 * Finds personal data and secrets in free text. Pure and deterministic. Detectors run in priority order and a
 * later detector never claims characters an earlier one already took, so a card number is not also reported
 * as a phone number.
 */
object PiiDetector {

    fun detect(text: String, kinds: Set<PiiKind> = PiiKind.entries.toSet()): List<PiiMatch> {
        val taken = mutableListOf<PiiMatch>()
        fun offer(candidate: PiiMatch) {
            if (candidate.kind in kinds && taken.none { it.start < candidate.end && candidate.start < it.end }) taken += candidate
        }
        urls(text).forEach(::offer)
        secrets(text).forEach(::offer)
        emails(text).forEach(::offer)
        ibans(text).forEach(::offer)
        cards(text).forEach(::offer)
        spanishIds(text).forEach(::offer)
        ips(text).forEach(::offer)
        phones(text).forEach(::offer)
        return taken.sortedBy { it.start }
    }

    // ---- e-mail -------------------------------------------------------------------------------------------

    private val emailRegex = Regex("""(?<![\w.%+\-])[A-Za-z0-9][A-Za-z0-9._%+\-]*@[A-Za-z0-9\-]+(?:\.[A-Za-z0-9\-]+)*\.[A-Za-z]{2,}""")

    private fun emails(text: String) =
        emailRegex.findAll(text).map { PiiMatch(PiiKind.EMAIL, it.range.first, it.range.last + 1, it.value) }.toList()

    // ---- IBAN ---------------------------------------------------------------------------------------------

    private val ibanRegex = Regex("""(?<![A-Za-z0-9])[A-Za-z]{2}\d{2}(?:[ \-]?[A-Za-z0-9]{1,4}){3,9}(?![A-Za-z0-9])""")

    private fun ibans(text: String): List<PiiMatch> = ibanRegex.findAll(text).mapNotNull { match ->
        // The regex may swallow trailing words ("... 1332 AND"): shrink from the right until the checksum holds.
        val groups = Regex("""[ \-]?[A-Za-z0-9]{1,4}""").findAll(match.value).toList()
        for (count in groups.size downTo 3) {
            val candidate = match.value.substring(0, groups[count - 1].range.last + 1)
            val compact = candidate.filter { it.isLetterOrDigit() }
            if (PiiValidators.iban(compact)) {
                return@mapNotNull PiiMatch(PiiKind.IBAN, match.range.first, match.range.first + candidate.length, candidate)
            }
        }
        null
    }.toList()

    // ---- payment cards ------------------------------------------------------------------------------------

    private val cardRegex = Regex("""(?<![\w\-])\d(?:[ \-]?\d){12,18}(?![\w\-]?\d)""")

    private fun cards(text: String): List<PiiMatch> = cardRegex.findAll(text).mapNotNull { match ->
        val digits = match.value.filter { it.isDigit() }
        // Real issuers start with 3..6 (Amex, Visa, Mastercard, Discover, UnionPay...) and pass Luhn: 16-digit order
        // numbers or tracking ids almost never satisfy both.
        if (digits.length in 13..19 && digits[0] in '3'..'6' && PiiValidators.luhn(digits)) {
            PiiMatch(PiiKind.CARD, match.range.first, match.range.last + 1, match.value)
        } else {
            null
        }
    }.toList()

    // ---- Spanish DNI / NIE --------------------------------------------------------------------------------

    private val dniRegex = Regex("""(?<![\w])(\d{8})[ \-]?([A-Za-z])(?![\w])""")
    private val nieRegex = Regex("""(?<![\w])([XYZxyz])[ \-]?(\d{7})[ \-]?([A-Za-z])(?![\w])""")

    private fun spanishIds(text: String): List<PiiMatch> {
        val out = mutableListOf<PiiMatch>()
        dniRegex.findAll(text).forEach {
            if (PiiValidators.dni(it.groupValues[1], it.groupValues[2][0])) {
                out += PiiMatch(PiiKind.DNI, it.range.first, it.range.last + 1, it.value)
            }
        }
        nieRegex.findAll(text).forEach {
            if (PiiValidators.nie(it.groupValues[1][0], it.groupValues[2], it.groupValues[3][0])) {
                out += PiiMatch(PiiKind.NIE, it.range.first, it.range.last + 1, it.value)
            }
        }
        return out
    }

    // ---- IPv4 ---------------------------------------------------------------------------------------------

    private val ipRegex = Regex("""(?<![\w.])(?:(?:25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)\.){3}(?:25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(?![\w]|\.\d)""")
    private val versionContext = Regex("""(?:\bv|version|versión|versao|ver\.?)\s*$""", RegexOption.IGNORE_CASE)

    private fun ips(text: String): List<PiiMatch> = ipRegex.findAll(text).mapNotNull { match ->
        val before = text.substring(maxOf(0, match.range.first - 12), match.range.first)
        val loopback = match.value.startsWith("127.") || match.value == "0.0.0.0"
        // A four-part number right after "v" / "version" is a build number, and loopback is not personal.
        if (loopback || versionContext.containsMatchIn(before)) null
        else PiiMatch(PiiKind.IPV4, match.range.first, match.range.last + 1, match.value)
    }.toList()

    // ---- phone numbers ------------------------------------------------------------------------------------

    private const val SEP = """[ .\-]?"""
    // Spanish mobiles/landlines: 9 digits starting 6-9, grouped 3-3-3, 3-2-2-2 or 2-3-2-2 (landline area codes), optional +34/0034.
    private val spanishPhone = Regex(
        """(?<![\w+\-])(?<!\d[.,])(?:(?:\+|00)34[ .\-]?)?(?:[6-9]\d{2}$SEP\d{3}$SEP\d{3}|[6-9]\d{2}$SEP\d{2}$SEP\d{2}$SEP\d{2}|9\d$SEP\d{3}$SEP\d{2}$SEP\d{2})(?![\w]|[.,\-]\d)""",
    )
    // International: a leading + and 8..15 digits in short groups, optional parentheses (+44 20 7946 0958, +1 (415) 555-2671).
    private val internationalPhone = Regex("""(?<![\w])\+\d{1,3}(?:[ .\-]?\(?\d{1,4}\)?){2,6}(?![\w]|[.\-]\d)""")
    // North American style without the +: (415) 555-2671, 415-555-2671, 415.555.2671.
    private val northAmericanPhone = Regex("""(?<![\w\-])(?:\(\d{3}\)[ ]?|\d{3}[ .\-])\d{3}[ .\-]\d{4}(?![\w]|[.\-]\d)""")

    private fun phones(text: String): List<PiiMatch> {
        val out = mutableListOf<PiiMatch>()
        for (regex in listOf(spanishPhone, internationalPhone, northAmericanPhone)) {
            regex.findAll(text).forEach { match ->
                val digits = match.value.count { it.isDigit() }
                if (digits in 9..15 && !isDateLike(match.value)) {
                    out += PiiMatch(PiiKind.PHONE, match.range.first, match.range.last + 1, match.value.trimEnd())
                }
            }
        }
        return out
    }

    private val dateLike = Regex("""^\d{4}[.\-]\d{2}[.\-]\d{2}$|^\d{2}[.\-]\d{2}[.\-]\d{4}$""")
    private fun isDateLike(value: String) = dateLike.matches(value.trim())

    // ---- URLs carrying credentials ------------------------------------------------------------------------

    private val urlRegex = Regex("""(?i)\b[a-z][a-z0-9+.\-]{1,15}://[^\s<>"'`]+""")
    private val sensitiveParam = Regex(
        """(?i)(?:^|[?&#;])(?:access[_-]?token|id[_-]?token|refresh[_-]?token|token|api[_-]?key|apikey|key|secret|client[_-]?secret|password|passwd|pwd|auth|authorization|sig|signature|session(?:id)?|sid|jwt|bearer|code|otp|x-amz-signature|x-goog-signature)=[^&#\s]{4,}""",
    )
    private val userInfo = Regex("""(?i)^[a-z]+://[^/\s:@]+:[^/\s@]+@""")
    private val tokenSegment = Regex("""/[A-Za-z0-9_\-]{24,}(?=[/?#]|$)""")

    private fun urls(text: String): List<PiiMatch> = urlRegex.findAll(text).mapNotNull { match ->
        val url = match.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}')
        val tokenLikePath = tokenSegment.findAll(url.substringBefore('?').substringBefore('#')).any { seg ->
            val s = seg.value.drop(1)
            s.any { it.isDigit() } && s.any { it.isLetter() }
        }
        if (sensitiveParam.containsMatchIn(url) || userInfo.containsMatchIn(url) || tokenLikePath) {
            PiiMatch(PiiKind.URL, match.range.first, match.range.first + url.length, url)
        } else {
            null
        }
    }.toList()

    // ---- API keys and other secrets -----------------------------------------------------------------------

    /** Provider-specific shapes: the whole token is replaced. */
    private val knownSecretShapes = listOf(
        Regex("""\bsk-[A-Za-z0-9_\-]{20,}\b"""), // OpenAI-style
        Regex("""\bsk_(?:live|test)_[A-Za-z0-9]{16,}\b"""), // Stripe secret
        Regex("""\bAIza[0-9A-Za-z_\-]{35}\b"""), // Google API key
        Regex("""\bAKIA[0-9A-Z]{16}\b"""), // AWS access key id
        Regex("""\bgh[pousr]_[A-Za-z0-9]{30,}\b"""), // GitHub tokens
        Regex("""\bgithub_pat_[A-Za-z0-9_]{40,}\b"""),
        Regex("""\bglpat-[A-Za-z0-9_\-]{20,}"""), // GitLab
        Regex("""\bxox[baprs]-[A-Za-z0-9\-]{10,}"""), // Slack
        Regex("""\beyJ[A-Za-z0-9_\-]{8,}\.eyJ[A-Za-z0-9_\-]{8,}\.[A-Za-z0-9_\-]{8,}"""), // JWT
        Regex("""-----BEGIN [A-Z ]*PRIVATE KEY-----"""),
        Regex("""(?i)\bbearer\s+[A-Za-z0-9_\-.=+/]{20,}"""),
    )

    /** `api_key = "..."`, `password: ...`: only the value is replaced, the key name stays readable. */
    private val assignment = Regex(
        """(?i)\b(?:api[_\-\s]?key|apikey|secret|client[_\-\s]?secret|access[_\-\s]?token|auth[_\-\s]?token|token|password|passwd|pwd|contraseña|contrasena|clave)\b["']?\s*[:=]\s*["']?([^\s"',;]{6,})""",
    )

    private fun secrets(text: String): List<PiiMatch> {
        val out = mutableListOf<PiiMatch>()
        for (regex in knownSecretShapes) {
            regex.findAll(text).forEach { out += PiiMatch(PiiKind.SECRET, it.range.first, it.range.last + 1, it.value) }
        }
        assignment.findAll(text).forEach { m ->
            val value = m.groups[1] ?: return@forEach
            if (looksLikeSecretValue(value.value)) {
                out += PiiMatch(PiiKind.SECRET, value.range.first, value.range.last + 1, value.value)
            }
        }
        return out
    }

    /** "password: required" is prose, "password: hunter2!" is a secret: demand a digit, a symbol, mixed case or length. */
    private fun looksLikeSecretValue(value: String): Boolean {
        if (value.length >= 24) return true
        val hasDigit = value.any { it.isDigit() }
        val hasSymbol = value.any { !it.isLetterOrDigit() }
        val hasUpper = value.any { it.isUpperCase() }
        val hasLower = value.any { it.isLowerCase() }
        return hasDigit || hasSymbol || (hasUpper && hasLower)
    }
}
