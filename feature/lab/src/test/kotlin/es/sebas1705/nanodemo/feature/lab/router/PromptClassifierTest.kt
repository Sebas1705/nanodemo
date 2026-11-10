package es.sebas1705.nanodemo.feature.lab.router

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptClassifierTest {

    private fun classify(text: String, marked: Boolean = false) = PromptClassifier.classify(text, marked)

    @Test
    fun `a short factual question is trivial`() {
        val c = classify("¿Cuál es la capital de Francia?")
        assertEquals(Complexity.TRIVIAL, c.complexity)
        assertFalse(c.sensitive)
        assertFalse(c.needsFreshFacts)
        assertTrue(c.score < 25)
    }

    @Test
    fun `code requests are detected and raise the score`() {
        val c = classify("Escribe una función en Kotlin que invierta una lista, con un ejemplo y tests.")
        assertTrue(c.features.hasCode)
        assertTrue(c.score >= 20)
        assertTrue(c.reasons.any { it.contains("code") })
    }

    @Test
    fun `fenced code is code even without keywords`() {
        assertTrue(classify("mira\n```\nx = 1\n```").features.hasCode)
    }

    @Test
    fun `maths is detected from operators and from words`() {
        assertTrue(classify("Resuelve 3x + 7 = 28").features.hasMath)
        assertTrue(classify("Calcula el 15% de 240").features.hasMath)
        assertTrue(classify("What is the derivative of x squared?").features.hasMath)
    }

    @Test
    fun `dates and phone numbers are not mistaken for maths`() {
        assertFalse(classify("La reunión es el 2026-05-12 o el 12/05/2026").features.hasMath)
        assertFalse(classify("Mi número es 612-345-678").features.hasMath)
    }

    @Test
    fun `multi step wording and numbered lists are detected`() {
        assertTrue(classify("Explícalo paso a paso").features.multiStep)
        assertTrue(classify("Compara Room y SQLDelight").features.multiStep)
        assertTrue(classify("Haz esto:\n1. abre\n2. cierra").features.multiStep)
        assertTrue(classify("¿Qué? ¿Cómo? ¿Cuándo?").features.multiStep)
    }

    @Test
    fun `fresh facts are detected in both languages`() {
        assertTrue(classify("¿Cuál es el precio actual del bitcoin?").needsFreshFacts)
        assertTrue(classify("What is the latest news about Kotlin?").needsFreshFacts)
        assertFalse(classify("Explica qué es un monad").needsFreshFacts)
    }

    @Test
    fun `language is guessed from stop words`() {
        assertEquals(PromptLanguage.ES, PromptClassifier.detectLanguage("¿Cuál es la capital de Francia y por qué?"))
        assertEquals(PromptLanguage.EN, PromptClassifier.detectLanguage("What is the capital of France and why is it so?"))
        assertEquals(PromptLanguage.OTHER, PromptClassifier.detectLanguage("日本の首都はどこですか。そして、なぜですか。"))
        assertEquals(PromptLanguage.ES, PromptClassifier.detectLanguage("hola"))
    }

    @Test
    fun `an unusual language adds to the score`() {
        val c = classify("日本の首都はどこですか。そして、なぜですか。")
        assertTrue(c.reasons.any { it.contains("language") })
    }

    @Test
    fun `personal data makes the prompt sensitive and is listed in the reasons`() {
        val c = classify("Escribe a ana@example.com y llámala al +34 612 345 678")
        assertTrue(c.sensitive)
        assertEquals(1, c.features.pii[PiiKind.EMAIL])
        assertEquals(1, c.features.pii[PiiKind.PHONE])
        assertTrue(c.reasons.any { it.contains("personal data") })
    }

    @Test
    fun `the user can mark a prompt as sensitive`() {
        val c = classify("Resume mi diario", marked = true)
        assertTrue(c.sensitive)
        assertTrue(c.reasons.any { it.contains("marked") })
    }

    @Test
    fun `long multi part prompts are complex`() {
        val text = "Analiza paso a paso y compara estas dos arquitecturas, justifica cada decisión y escribe el código en Kotlin. " +
            "Después calcula el coste de 3 + 4 servidores y redacta un informe detallado. ".repeat(6)
        val c = classify(text)
        assertEquals(Complexity.COMPLEX, c.complexity)
        assertTrue(c.score >= 55)
    }

    @Test
    fun `score is clamped to 100 and reasons explain every point`() {
        val c = classify("x ".repeat(3000) + " código kotlin ecuación paso a paso ensayo por qué")
        assertTrue(c.score <= 100)
        assertTrue(c.reasons.isNotEmpty())
    }

    // ---- LLM classifier -----------------------------------------------------------------------------------

    @Test
    fun `clean json is parsed`() {
        val r = LlmClassifier.parse("""{"complexity":"complex","sensitive":true,"fresh":false}""")
        assertEquals(LlmClassification(Complexity.COMPLEX, true, false), r)
    }

    @Test
    fun `fences prose single quotes and synonyms are tolerated`() {
        val raw = "Sure! Here you go:\n```json\n{'complexity': 'Medium', 'sensitive': 'no', 'fresh': 'yes'}\n```\nHope it helps"
        assertEquals(LlmClassification(Complexity.MODERATE, false, true), LlmClassifier.parse(raw))
    }

    @Test
    fun `a truncated object and unquoted keys still give the fields that arrived`() {
        assertEquals(LlmClassification(Complexity.TRIVIAL, true, null), LlmClassifier.parse("{complexity: easy, sensitive: true"))
    }

    @Test
    fun `spanish values are understood`() {
        assertEquals(
            LlmClassification(Complexity.COMPLEX, true, null),
            LlmClassifier.parse("""{"complexity": "compleja", "sensible": "sí"}"""),
        )
    }

    @Test
    fun `a bare label is accepted only when the answer is basically that word`() {
        assertEquals(Complexity.COMPLEX, LlmClassifier.parse("complex")?.complexity)
        assertEquals(Complexity.TRIVIAL, LlmClassifier.parse("Trivial.")?.complexity)
        assertNull(LlmClassifier.parse("I think this prompt is quite hard to say, maybe simple, maybe not at all clear"))
    }

    @Test
    fun `garbage and empty answers yield null`() {
        assertNull(LlmClassifier.parse(""))
        assertNull(LlmClassifier.parse("   "))
        assertNull(LlmClassifier.parse("¿Podrías repetir la pregunta?"))
        assertNull(LlmClassifier.parse("{\"complexity\": \"banana\"}"))
    }

    @Test
    fun `the classifier prompt stays small enough for a 1k context`() {
        val prompt = LlmClassifier.prompt("a".repeat(5000))
        assertTrue(prompt.length < 900)
        assertTrue(prompt.contains("…"))
    }

    @Test
    fun `merging lets the llm change complexity but never remove sensitivity`() {
        val heuristic = classify("Escribe a ana@example.com") // sensitive by PII, trivial
        val merged = PromptClassifier.merge(heuristic, LlmClassification(Complexity.COMPLEX, sensitive = false, fresh = true))
        assertEquals(Complexity.COMPLEX, merged.complexity)
        assertTrue(merged.score >= 60)
        assertTrue(merged.sensitive)
        assertTrue(merged.needsFreshFacts)
        assertEquals(ClassificationSource.LLM, merged.source)
        assertNotNull(merged.reasons.firstOrNull { it.startsWith("LLM:") })
    }

    @Test
    fun `merging can make an llm sensitive flag stick`() {
        val merged = PromptClassifier.merge(classify("Mi jefe me trata mal"), LlmClassification(null, sensitive = true, fresh = null))
        assertTrue(merged.sensitive)
    }

    @Test
    fun `merging with an agreeing llm keeps the heuristic score`() {
        val h = classify("¿Cuál es la capital de Francia?")
        val merged = PromptClassifier.merge(h, LlmClassification(Complexity.TRIVIAL, false, false))
        assertEquals(h.score, merged.score)
    }
}
