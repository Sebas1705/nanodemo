package es.sebas1705.nanodemo.feature.lab.rag

/** Whether an answer's claims are backed by the retrieved context, as judged by a model. */
enum class GroundingVerdict { SUPPORTED, PARTIAL, UNSUPPORTED, UNKNOWN }

/** The parsed judge reply. `score` is in [0, 1] when the judge gave one; `raw` keeps what it actually said. */
data class Groundedness(val verdict: GroundingVerdict, val score: Double?, val raw: String)

/**
 * Parses what a small model says when asked "is this answer supported by the context?". Small models do not
 * follow output formats reliably, so this accepts: a `VEREDICTO:` label or a bare first word (SI/NO/PARCIAL, with
 * or without accents, English too), phrases such as "no está respaldada" or "fully supported", and a score as
 * `8/10`, `80%`, `PUNTUACION: 7` or `0.8`. With only a score, the verdict is derived from it. A reply that
 * yields neither is [GroundingVerdict.UNKNOWN], never a guess.
 */
object GroundednessParser {

    private const val SUPPORTED_AT = 0.8
    private const val PARTIAL_AT = 0.4

    private val labelled = Regex("""(?:veredicto|verdict|resultado|result|respuesta|answer)\s*[:=\-]\s*([^\n]+)""")
    private val scoreLabelled = Regex(
        """(?:puntuacion|puntaje|score|nota|calificacion|rating)\s*[:=\-]?\s*(\d+(?:[.,]\d+)?)(?:\s*(?:/|de|out of)\s*(\d+(?:[.,]\d+)?))?""",
    )
    private val scoreFraction = Regex("""(\d+(?:[.,]\d+)?)\s*/\s*(\d+(?:[.,]\d+)?)""")
    private val scorePercent = Regex("""(\d{1,3}(?:[.,]\d+)?)\s*%""")
    private val scoreBare = Regex("""^\s*(\d+(?:[.,]\d+)?)\s*$""")

    private val negatedSupport = Regex("""\b(?:no|not|sin|non)\W+(?:\w+\W+){0,2}?(?:respaldad\w*|fundamentad\w*|sustentad\w*|supported|grounded)""")
    private val unsupportedWords = Regex("""\b(?:unsupported|ungrounded|contradice|contradicts|alucin\w*|hallucinat\w*|inventad\w*|fabricated)\b""")
    private val partialWords = Regex("""\b(?:parcial\w*|partial\w*|en parte|mayormente|mostly)\b""")
    private val supportedWords = Regex("""\b(?:totalmente|completamente|fully|entirely|supported|respaldad\w*|fundamentad\w*|sustentad\w*|grounded)\b""")

    private val yesWords = setOf("si", "yes", "correcto", "correct", "true", "verdadero", "ok")
    private val noWords = setOf("no", "false", "falso", "incorrecto", "incorrect")
    private val partialFirst = setOf("parcial", "parcialmente", "partial", "partially")

    fun parse(raw: String): Groundedness {
        val text = normalize(raw)
        val score = parseScore(text)
        val verdict = labelled.find(text)?.groupValues?.get(1)?.let(::classify)
            ?: text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }?.let(::classify)
            ?: classify(text)
        val resolved = if (verdict == GroundingVerdict.UNKNOWN && score != null) verdictFromScore(score) else verdict
        return Groundedness(resolved, score, raw.trim())
    }

    /** Lowercase, no accents, no Markdown emphasis: so `**Sí**` and `SI` read the same. */
    private fun normalize(raw: String): String =
        java.text.Normalizer.normalize(raw.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[*_`#>]+"), "")

    private fun classify(snippet: String): GroundingVerdict {
        val first = snippet.trim().split(Regex("[^a-z]+")).firstOrNull { it.isNotEmpty() }
        return when {
            first in partialFirst -> GroundingVerdict.PARTIAL
            first in yesWords -> GroundingVerdict.SUPPORTED
            first in noWords -> GroundingVerdict.UNSUPPORTED
            negatedSupport.containsMatchIn(snippet) || unsupportedWords.containsMatchIn(snippet) -> GroundingVerdict.UNSUPPORTED
            partialWords.containsMatchIn(snippet) -> GroundingVerdict.PARTIAL
            supportedWords.containsMatchIn(snippet) -> GroundingVerdict.SUPPORTED
            else -> GroundingVerdict.UNKNOWN
        }
    }

    private fun parseScore(text: String): Double? {
        scoreLabelled.find(text)?.let { return toRatio(it.groupValues[1], it.groupValues[2]) }
        scoreFraction.find(text)?.let { return toRatio(it.groupValues[1], it.groupValues[2]) }
        scorePercent.find(text)?.let { return toRatio(it.groupValues[1], "100") }
        scoreBare.find(text)?.let { return toRatio(it.groupValues[1], "") }
        return null
    }

    /**
     * `value` out of `denominator` when given; otherwise guessed from its shape: a decimal up to 1 is already a
     * ratio (`0.8`), a whole number up to 10 is out of 10 (`1` is 1/10, as the prompt asks for 0-10), up to 100 a percentage.
     */
    private fun toRatio(value: String, denominator: String): Double? {
        val v = value.replace(',', '.').toDoubleOrNull() ?: return null
        val d = denominator.replace(',', '.').toDoubleOrNull()
        val isWhole = '.' !in value && ',' !in value
        val ratio = when {
            d != null && d > 0 -> v / d
            !isWhole && v <= 1.0 -> v
            v <= 10.0 -> v / 10.0
            v <= 100.0 -> v / 100.0
            else -> return null
        }
        return ratio.takeIf { it in 0.0..1.0 }
    }

    private fun verdictFromScore(score: Double): GroundingVerdict = when {
        score >= SUPPORTED_AT -> GroundingVerdict.SUPPORTED
        score >= PARTIAL_AT -> GroundingVerdict.PARTIAL
        else -> GroundingVerdict.UNSUPPORTED
    }
}
