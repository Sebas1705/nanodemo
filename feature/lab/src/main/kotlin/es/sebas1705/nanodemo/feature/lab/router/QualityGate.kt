package es.sebas1705.nanodemo.feature.lab.router

import java.text.Normalizer

/** How the cascade double-checks an answer that already passed the cheap heuristics. */
enum class ConfidenceMode(val label: String) {
    NONE("Solo heurísticas"),
    SELF_ASSESS("Autoevaluación"),
    JUDGE("Juez"),
}

data class GateCheck(val id: String, val label: String, val passed: Boolean, val detail: String? = null)

data class GateResult(val checks: List<GateCheck>) {
    val accepted: Boolean get() = checks.all { it.passed }
    val failures: List<GateCheck> get() = checks.filterNot { it.passed }

    fun summary(): String =
        if (accepted) "aceptada (${checks.size} comprobaciones)" else "rechazada: " + failures.joinToString("; ") { it.detail ?: it.label }
}

/**
 * Cheap, deterministic checks that decide whether an answer is good enough to keep or the cascade should try the
 * next engine. No model is involved; the optional self-assessment / judge comes on top of this.
 */
object QualityGate {

    private val refusalPhrases = listOf(
        "i can't", "i cannot", "i can not", "i'm unable", "i am unable", "i'm sorry, but", "i am sorry, but", "sorry, but i",
        "as an ai", "as a language model", "i don't have access", "i do not have access", "i'm not able to",
        "no puedo", "no soy capaz", "lo siento, pero", "lo siento, no", "como modelo de lenguaje", "como una ia",
        "como inteligencia artificial", "no tengo acceso", "no me es posible", "no estoy en condiciones",
    )

    private fun fold(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace('’', '\'')

    /** Minimum useful answer length per complexity; a one-word answer is fine for a trivial question. */
    fun minChars(complexity: Complexity): Int = when (complexity) {
        Complexity.TRIVIAL -> 2
        Complexity.MODERATE -> 30
        Complexity.COMPLEX -> 80
    }

    fun evaluate(answer: String, complexity: Complexity, expectJson: Boolean = false): GateResult {
        val checks = mutableListOf<GateCheck>()
        val text = answer.trim()

        checks += GateCheck("not_empty", "no está vacía", text.isNotEmpty(), if (text.isEmpty()) "respuesta vacía" else null)
        if (text.isEmpty()) return GateResult(checks)

        val refusal = refusalIn(text)
        checks += GateCheck("not_refusal", "no es una negativa", refusal == null, refusal?.let { "negativa (\"$it\")" })

        val min = if (expectJson) 2 else minChars(complexity)
        checks += GateCheck("long_enough", "longitud suficiente", text.length >= min, if (text.length < min) "demasiado corta (${text.length} < $min caracteres)" else null)

        if (expectJson) {
            val json = JsonSyntax.extract(text)
            checks += GateCheck("valid_json", "JSON válido", json != null && JsonSyntax.isValid(json), "no es JSON válido")
        }

        val repetition = Repetition.describe(text)
        checks += GateCheck("no_repetition", "sin repeticiones", repetition == null, repetition)
        return GateResult(checks)
    }

    /** The refusal phrase if the answer opens with one (the first 160 characters), else null. */
    fun refusalIn(answer: String): String? {
        val head = fold(answer.trim()).take(160)
        return refusalPhrases.firstOrNull { head.contains(it) }
    }
}

/** Detects a model stuck in a loop. */
object Repetition {
    /** Describes the repetition found, or null when the text looks healthy. */
    fun describe(text: String): String? {
        val lines = text.lines().map { it.trim() }.filter { it.length > 8 }
        lines.groupingBy { it }.eachCount().entries.firstOrNull { it.value >= 3 }?.let {
            return "la misma línea se repite ${it.value} veces"
        }
        val words = text.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.size >= 24) {
            val trigrams = words.windowed(3).map { it.joinToString(" ") }
            val ratio = trigrams.toSet().size.toDouble() / trigrams.size
            if (ratio < 0.45) return "texto muy repetitivo (${(ratio * 100).toInt()}% de tramos distintos)"
        }
        val tail = text.trimEnd()
        for (period in 4..60) {
            if (tail.length < period * 3) break
            val chunk = tail.takeLast(period)
            if (tail.takeLast(period * 3) == chunk + chunk + chunk && chunk.any { it.isLetterOrDigit() }) {
                return "el final se repite en bucle"
            }
        }
        return null
    }
}

/** Tiny JSON syntax checker (no object model): the JVM unit tests have no org.json, and only validity matters here. */
object JsonSyntax {

    /** Takes the JSON out of a fenced block or surrounding prose: from the first `{`/`[` to its matching close. */
    fun extract(text: String): String? {
        val unfenced = text.replace("```json", "").replace("```JSON", "").replace("```", "").trim()
        val start = unfenced.indexOfFirst { it == '{' || it == '[' }
        if (start < 0) return null
        val end = unfenced.lastIndexOf(if (unfenced[start] == '{') '}' else ']')
        if (end < start) return null
        return unfenced.substring(start, end + 1)
    }

    fun isValid(json: String): Boolean = try {
        Parser(json).parseDocument()
    } catch (_: IllegalArgumentException) {
        false
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun parseDocument(): Boolean {
            skipWs()
            value(0)
            skipWs()
            return i == s.length
        }

        private fun fail(): Nothing = throw IllegalArgumentException("bad json at $i")
        private fun skipWs() { while (i < s.length && s[i] in " \t\r\n") i++ }
        private fun peek(): Char = if (i < s.length) s[i] else fail()

        private fun value(depth: Int) {
            if (depth > 64) fail()
            when (peek()) {
                '{' -> obj(depth)
                '[' -> arr(depth)
                '"' -> string()
                't' -> literal("true")
                'f' -> literal("false")
                'n' -> literal("null")
                else -> number()
            }
        }

        private fun literal(word: String) {
            if (!s.startsWith(word, i)) fail()
            i += word.length
        }

        private fun obj(depth: Int) {
            i++ // {
            skipWs()
            if (peek() == '}') { i++; return }
            while (true) {
                skipWs()
                if (peek() != '"') fail()
                string()
                skipWs()
                if (peek() != ':') fail()
                i++
                skipWs()
                value(depth + 1)
                skipWs()
                when (peek()) {
                    ',' -> i++
                    '}' -> { i++; return }
                    else -> fail()
                }
            }
        }

        private fun arr(depth: Int) {
            i++ // [
            skipWs()
            if (peek() == ']') { i++; return }
            while (true) {
                skipWs()
                value(depth + 1)
                skipWs()
                when (peek()) {
                    ',' -> i++
                    ']' -> { i++; return }
                    else -> fail()
                }
            }
        }

        private fun string() {
            i++ // opening quote
            while (true) {
                val c = peek()
                i++
                when {
                    c == '"' -> return
                    c == '\\' -> {
                        val e = peek()
                        i++
                        if (e == 'u') {
                            repeat(4) { if (peek().digitToIntOrNull(16) == null) fail(); i++ }
                        } else if (e !in "\"\\/bfnrt") fail()
                    }
                    c < ' ' -> fail()
                }
            }
        }

        private fun number() {
            val start = i
            if (peek() == '-') i++
            when {
                peek() == '0' -> i++
                peek() in '1'..'9' -> while (i < s.length && s[i].isDigit()) i++
                else -> fail()
            }
            if (i < s.length && s[i] == '.') {
                i++
                val d = i
                while (i < s.length && s[i].isDigit()) i++
                if (i == d) fail()
            }
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                i++
                if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
                val d = i
                while (i < s.length && s[i].isDigit()) i++
                if (i == d) fail()
            }
            if (i == start) fail()
        }
    }
}

/** Reads the 1-5 (or 0-10, 0-100, %) score a model gives when asked to rate an answer. */
object ConfidenceParser {
    private val number = Regex("""(\d+(?:[.,]\d+)?)\s*(?:(/|de|out of)\s*(\d+)|(%))?""", RegexOption.IGNORE_CASE)

    /** Normalised to 0..1, or null when the text holds no usable number. */
    fun parse(raw: String): Double? {
        val m = number.find(raw.trim()) ?: return null
        val value = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val outOf = m.groupValues[3].toDoubleOrNull()
        val percent = m.groupValues[4] == "%"
        val decimal = m.groupValues[1].any { it == '.' || it == ',' }
        val normalised = when {
            outOf != null && outOf > 0 -> value / outOf
            percent -> value / 100
            decimal && value <= 1.0 -> value // "0.7": already a fraction
            value == 0.0 -> 0.0
            value <= 5.0 -> value / 5 // the prompt asks for 1..5, so a bare "1" is the worst score
            value <= 10.0 -> value / 10
            value <= 100.0 -> value / 100
            else -> return null
        }
        return normalised.coerceIn(0.0, 1.0)
    }

    /** Self-assessment prompt, compact enough for a 1k-token engine. */
    fun selfAssessPrompt(question: String, answer: String): String =
        "Question: ${question.take(250)}\nAnswer: ${answer.take(450)}\n" +
            "How likely is the answer correct and complete? Reply with ONLY a number from 1 (wrong) to 5 (certainly right)."

    fun judgePrompt(question: String, answer: String): String =
        "You are a strict grader.\nQuestion: ${question.take(250)}\nAnswer: ${answer.take(450)}\n" +
            "Score the answer from 1 (wrong or useless) to 5 (correct and complete). Reply with ONLY the number."
}
