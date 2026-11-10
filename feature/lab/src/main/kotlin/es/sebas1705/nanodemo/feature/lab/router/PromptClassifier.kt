package es.sebas1705.nanodemo.feature.lab.router

import androidx.annotation.StringRes
import es.sebas1705.nanodemo.feature.lab.R
import java.text.Normalizer

enum class Complexity(@StringRes val labelRes: Int) {
    TRIVIAL(R.string.router_complexity_trivial),
    MODERATE(R.string.router_complexity_moderate),
    COMPLEX(R.string.router_complexity_complex),
}

enum class PromptLanguage(@StringRes val labelRes: Int) {
    ES(R.string.router_language_es),
    EN(R.string.router_language_en),
    OTHER(R.string.router_language_other),
}

/** Raw signals read from the prompt text. */
data class PromptFeatures(
    val chars: Int,
    val words: Int,
    val estimatedTokens: Int,
    val hasCode: Boolean,
    val hasMath: Boolean,
    val multiStep: Boolean,
    val needsFreshFacts: Boolean,
    val longForm: Boolean,
    val language: PromptLanguage,
    /** Personal data found by the PII detector, by kind. */
    val pii: Map<PiiKind, Int>,
) {
    val piiCount: Int get() = pii.values.sum()
}

enum class ClassificationSource { HEURISTIC, LLM }

/**
 * The router's reading of a prompt: how hard it is (`complexity` + `score` 0..100), whether it must be handled as
 * sensitive, and the human-readable `reasons` that produced those numbers (shown in the trace).
 */
data class Classification(
    val complexity: Complexity,
    val score: Int,
    val sensitive: Boolean,
    val needsFreshFacts: Boolean,
    val features: PromptFeatures,
    val reasons: List<String>,
    val source: ClassificationSource = ClassificationSource.HEURISTIC,
) {
    fun summary(texts: RouterTexts): String = buildList {
        add(texts.string(R.string.router_summary_complexity, texts.string(complexity.labelRes), score))
        if (sensitive) add(texts.string(R.string.router_summary_sensitive))
        if (needsFreshFacts) add(texts.string(R.string.router_summary_fresh))
        if (source == ClassificationSource.LLM) add(texts.string(R.string.router_summary_via_llm))
    }.joinToString(" · ")
}

/** Cheap, deterministic, explainable classifier. It is also the fallback when the optional LLM classifier fails. */
object PromptClassifier {

    private fun fold(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    private val codeWords = Regex("""\b(kotlin|java|python|javascript|typescript|sql|regex|compila|compilar|bug|stack ?trace|exception|nullpointer|funcion|function|class|clase|algoritmo|algorithm|refactor\w*|codigo|code|script|api|json|xml|html|css|gradle)\b""")
    private val codeSyntax = Regex("""```|\bfun\s+\w+\s*\(|\bdef\s+\w+\s*\(|\bclass\s+\w+|=>|\{\s*\n|;\s*\n|\bval\s+\w+\s*[:=]|\bimport\s+[\w.]+|</?\w+>""")
    private val mathWords = Regex("""\b(integral|derivad\w*|ecuacion\w*|resuelve|resolver|calcula\w*|probabilidad|demuestra|demostrar|matriz|logaritmo|raiz cuadrada|porcentaje|solve|prove|equation|derivative|probability|percent\w*)\b""")
    private val mathSyntax = Regex("""\d\s*[+*^×÷=]\s*\d|\d\s+[-/]\s+\d|\b\d+\s*x\b|\d+\s*%""")
    private val multiStepWords = Regex("""\b(paso a paso|step by step|primero|despues|luego|finalmente|analiza|compara\w*|ventajas e inconvenientes|pros and cons|plan de|disena|disenar|design|explica por que|justifica|evalua|recomienda|ademas|then)\b""")
    private val listMarkers = Regex("""(^|\n)\s*(\d+[.)]|[-*•])\s+""")
    private val freshWords = Regex("""\b(hoy|ahora|actual\w*|ultim[oa]s?|reciente\w*|noticias?|precio|cotizacion|tiempo que hace|clima|today|latest|current\w*|news|weather|price|stock|right now|this week|esta semana)\b""")
    private val longFormWords = Regex("""\b(ensayo|articulo|redacta\w*|essay|article|write an? (long|detailed)|en \d{3,} palabras|\d{3,} words|informe|resumen largo|detallad[oa])\b""")
    private val reasoningWords = Regex("""\b(por que|why|justifica|razona)\b""")

    private val esStopwords = setOf("el", "la", "los", "las", "de", "que", "y", "en", "un", "una", "es", "por", "con", "para", "como", "mi", "se", "del", "al", "lo", "su", "pero", "mas", "esto", "cual", "cuando", "donde")
    private val enStopwords = setOf("the", "and", "of", "to", "in", "is", "that", "for", "it", "with", "as", "on", "this", "are", "be", "what", "how", "my", "you", "from", "or", "an", "which", "when", "where")

    fun detectLanguage(text: String): PromptLanguage {
        val nonLatin = text.count { it.isLetter() && it.code > 0x24F }
        if (nonLatin > 0 && nonLatin * 4 > text.length) return PromptLanguage.OTHER
        val words = Regex("""[a-z]+""").findAll(fold(text)).map { it.value }.toList()
        val es = words.count { it in esStopwords }
        val en = words.count { it in enStopwords }
        return when {
            words.size < 4 -> PromptLanguage.ES // too little text to tell: assume the app's main language
            es == 0 && en == 0 -> PromptLanguage.OTHER
            es >= en -> PromptLanguage.ES
            else -> PromptLanguage.EN
        }
    }

    fun features(text: String): PromptFeatures {
        val folded = fold(text)
        val pii = PiiDetector.detect(text).groupingBy { it.kind }.eachCount()
        val words = text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
        return PromptFeatures(
            chars = text.length,
            words = words,
            estimatedTokens = CloudPricing.tokensFromChars(text.length),
            hasCode = codeSyntax.containsMatchIn(text) || codeWords.findAll(folded).count() >= 2,
            hasMath = mathSyntax.containsMatchIn(text) || mathWords.containsMatchIn(folded),
            multiStep = multiStepWords.containsMatchIn(folded) || listMarkers.findAll(text).count() >= 2 || text.count { it == '?' } >= 3,
            needsFreshFacts = freshWords.containsMatchIn(folded),
            longForm = longFormWords.containsMatchIn(folded),
            language = detectLanguage(text),
            pii = pii,
        )
    }

    fun classify(text: String, markedSensitive: Boolean, texts: RouterTexts): Classification {
        val f = features(text)
        val reasons = mutableListOf<String>()
        var score = 0

        fun add(points: Int, reason: String) {
            score += points
            reasons += texts.string(R.string.router_reason_points, reason, points)
        }

        when {
            f.estimatedTokens > 500 -> add(40, texts.string(R.string.router_reason_len_very_long, f.estimatedTokens))
            f.estimatedTokens > 200 -> add(30, texts.string(R.string.router_reason_len_long, f.estimatedTokens))
            f.estimatedTokens > 80 -> add(20, texts.string(R.string.router_reason_len_medium, f.estimatedTokens))
            f.estimatedTokens > 20 -> add(10, texts.string(R.string.router_reason_len_short, f.estimatedTokens))
            else -> reasons += texts.string(R.string.router_reason_len_very_short, f.estimatedTokens)
        }
        if (f.hasCode) add(20, texts.string(R.string.router_reason_code))
        if (f.hasMath) add(20, texts.string(R.string.router_reason_math))
        if (f.multiStep) add(15, texts.string(R.string.router_reason_multi_step))
        if (f.longForm) add(10, texts.string(R.string.router_reason_long_form))
        if (f.language == PromptLanguage.OTHER) add(10, texts.string(R.string.router_reason_language))
        if (reasoningWords.containsMatchIn(fold(text))) add(5, texts.string(R.string.router_reason_reasoning))
        if (f.needsFreshFacts) reasons += texts.string(R.string.router_reason_fresh)

        score = score.coerceIn(0, 100)
        if (f.piiCount > 0) {
            reasons += texts.string(R.string.router_reason_pii, f.pii.entries.joinToString(", ") { "${it.value} ${texts.string(it.key.labelRes)}" })
        }
        if (markedSensitive) reasons += texts.string(R.string.router_reason_marked_sensitive)

        val complexity = when {
            score < 25 -> Complexity.TRIVIAL
            score < 55 -> Complexity.MODERATE
            else -> Complexity.COMPLEX
        }
        return Classification(
            complexity = complexity,
            score = score,
            sensitive = f.piiCount > 0 || markedSensitive,
            needsFreshFacts = f.needsFreshFacts,
            features = f,
            reasons = reasons,
        )
    }

    /**
     * Combines the heuristic reading with an LLM's. The LLM may raise or lower complexity, but it can only ADD
     * sensitivity or freshness: a datum the detector found is never un-flagged by a model.
     */
    fun merge(heuristic: Classification, llm: LlmClassification, texts: RouterTexts): Classification {
        val complexity = llm.complexity ?: heuristic.complexity
        val score = if (complexity == heuristic.complexity) heuristic.score else when (complexity) {
            Complexity.TRIVIAL -> minOf(heuristic.score, 20)
            Complexity.MODERATE -> heuristic.score.coerceIn(25, 54)
            Complexity.COMPLEX -> maxOf(heuristic.score, 60)
        }
        val reasons = heuristic.reasons + buildList {
            llm.complexity?.let {
                add(
                    if (it != heuristic.complexity) {
                        texts.string(R.string.router_reason_llm_complexity_corrects, texts.string(it.labelRes), texts.string(heuristic.complexity.labelRes))
                    } else {
                        texts.string(R.string.router_reason_llm_complexity, texts.string(it.labelRes))
                    },
                )
            }
            if (llm.sensitive == true && !heuristic.sensitive) add(texts.string(R.string.router_reason_llm_sensitive))
            if (llm.fresh == true && !heuristic.needsFreshFacts) add(texts.string(R.string.router_reason_llm_fresh))
        }
        return heuristic.copy(
            complexity = complexity,
            score = score,
            sensitive = heuristic.sensitive || llm.sensitive == true,
            needsFreshFacts = heuristic.needsFreshFacts || llm.fresh == true,
            reasons = reasons,
            source = ClassificationSource.LLM,
        )
    }
}

/** What an LLM classifier said; every field is null when the model did not state it. */
data class LlmClassification(val complexity: Complexity?, val sensitive: Boolean?, val fresh: Boolean?)

/** Prompt and parser for the optional LLM classifier step (kept tiny: MediaPipe has 1024 tokens in total). */
object LlmClassifier {
    private const val MAX_PROMPT_CHARS = 400

    fun prompt(text: String): String {
        val body = if (text.length <= MAX_PROMPT_CHARS) text else text.take(MAX_PROMPT_CHARS) + "…"
        return "Classify the user prompt below. Reply with ONLY this JSON: " +
            "{\"complexity\":\"trivial|moderate|complex\",\"sensitive\":true|false,\"fresh\":true|false}\n" +
            "sensitive = contains personal data or secrets. fresh = needs up-to-date facts.\n" +
            "Prompt: \"\"\"$body\"\"\""
    }

    private val complexityKey = Regex("""["']?complexity["']?\s*[:=]\s*["']?([A-Za-zÁÉÍÓÚáéíóú]+)""", RegexOption.IGNORE_CASE)
    private val sensitiveKey = Regex("""["']?(?:sensitive|sensible|private|pii)["']?\s*[:=]\s*["']?([A-Za-zÍí0-9]+)""", RegexOption.IGNORE_CASE)
    private val freshKey = Regex("""["']?(?:fresh|needs_?fresh|recent)["']?\s*[:=]\s*["']?([A-Za-zÍí0-9]+)""", RegexOption.IGNORE_CASE)

    private fun complexityOf(word: String): Complexity? = when (word.lowercase()) {
        "trivial", "simple", "easy", "low", "facil", "fácil", "sencilla", "sencillo" -> Complexity.TRIVIAL
        "moderate", "medium", "media", "moderada", "moderado", "mid" -> Complexity.MODERATE
        "complex", "hard", "high", "difficult", "compleja", "complejo", "dificil", "difícil" -> Complexity.COMPLEX
        else -> null
    }

    private fun boolOf(word: String): Boolean? = when (word.lowercase()) {
        "true", "yes", "si", "sí", "1", "y" -> true
        "false", "no", "0", "n" -> false
        else -> null
    }

    /**
     * Tolerant reader for small-model output: code fences, text around the JSON, single quotes, unquoted keys,
     * a truncated closing brace, `yes/no` booleans. Falls back to a bare label ("complex") when there is no JSON
     * at all. Returns null when nothing usable was said.
     */
    fun parse(raw: String): LlmClassification? {
        val text = raw.replace("```json", "").replace("```", "").trim()
        if (text.isEmpty()) return null
        val body = text.indexOf('{').let { open ->
            if (open < 0) text else text.substring(open, text.lastIndexOf('}').takeIf { it > open }?.plus(1) ?: text.length)
        }
        var complexity = complexityKey.find(body)?.let { complexityOf(it.groupValues[1]) }
        val sensitive = sensitiveKey.find(body)?.let { boolOf(it.groupValues[1]) }
        val fresh = freshKey.find(body)?.let { boolOf(it.groupValues[1]) }
        if (complexity == null && !body.contains("complexity", ignoreCase = true)) {
            // No key at all: accept a lone label only when the whole answer is basically that word.
            val bare = Regex("""[A-Za-zÁÉÍÓÚáéíóú]+""").findAll(text).map { it.value }.toList()
            if (bare.size <= 3) complexity = bare.firstNotNullOfOrNull(::complexityOf)
        }
        return if (complexity == null && sensitive == null && fresh == null) null else LlmClassification(complexity, sensitive, fresh)
    }
}
