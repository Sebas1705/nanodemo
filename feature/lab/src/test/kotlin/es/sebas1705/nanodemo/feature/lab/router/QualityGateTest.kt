package es.sebas1705.nanodemo.feature.lab.router

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityGateTest {

    private val good = "París es la capital de Francia y su ciudad más poblada, situada a orillas del Sena."

    private fun eval(answer: String, c: Complexity = Complexity.MODERATE, json: Boolean = false) =
        QualityGate.evaluate(answer, c, json, testTexts)

    private fun failedIds(r: GateResult) = r.failures.map { it.id }

    @Test
    fun `a normal answer is accepted`() {
        val r = eval(good)
        assertTrue(r.accepted)
        assertTrue(r.summary().startsWith("accepted"))
    }

    @Test
    fun `empty and blank answers are rejected`() {
        assertEquals(listOf("not_empty"), failedIds(eval("")))
        assertEquals(listOf("not_empty"), failedIds(eval("  \n ")))
    }

    @Test
    fun `refusals are rejected in english and spanish`() {
        listOf(
            "I'm sorry, but I can't help with that request, however here is some padding text to be long enough.",
            "As an AI language model I do not have personal opinions about the matter at hand today.",
            "Lo siento, pero no puedo ayudarte con esa petición, aunque aquí hay suficiente texto de relleno.",
            "No tengo acceso a información en tiempo real, así que no sé decirte el precio actual de nada.",
        ).forEach { assertTrue("should refuse: $it", "not_refusal" in failedIds(eval(it))) }
    }

    @Test
    fun `a refusal phrase deep inside a good answer is not a refusal`() {
        val answer = "Sí, es posible hacerlo con una corrutina y un canal, y funciona bien en la práctica porque el canal " +
            "aplica contrapresión de forma natural y la corrutina se suspende sin bloquear ningún hilo del sistema. " +
            "Otra cosa es que no puedo garantizar el rendimiento sin medirlo en tu dispositivo concreto."
        assertTrue(eval(answer).accepted)
    }

    @Test
    fun `minimum length depends on the complexity`() {
        assertTrue(eval("París", Complexity.TRIVIAL).accepted)
        assertEquals(listOf("long_enough"), failedIds(eval("Sí, claro.", Complexity.MODERATE)))
        assertEquals(listOf("long_enough"), failedIds(eval("a".repeat(60) + " fin", Complexity.COMPLEX)))
        assertTrue(eval("Una respuesta razonablemente larga y completa que supera sin problema el umbral de ochenta caracteres exigido.", Complexity.COMPLEX).accepted)
    }

    @Test
    fun `json is required when expected`() {
        assertTrue(eval("""{"nombre":"Lucía","ciudad":"Valencia"}""", Complexity.TRIVIAL, json = true).accepted)
        assertTrue(eval("```json\n{\"a\": [1, 2, {\"b\": null}]}\n```", Complexity.TRIVIAL, json = true).accepted)
        assertTrue(eval("Aquí tienes: {\"a\": 1.5e3}", Complexity.TRIVIAL, json = true).accepted)
        assertEquals(listOf("valid_json"), failedIds(eval("{nombre: 'Lucía'}", Complexity.TRIVIAL, json = true)))
        assertEquals(listOf("valid_json"), failedIds(eval("Lucía vive en Valencia", Complexity.TRIVIAL, json = true)))
    }

    @Test
    fun `json is not checked when not requested`() {
        assertTrue(eval("{esto no es json pero nadie lo pidió, y es bastante largo para pasar", Complexity.MODERATE).accepted)
    }

    @Test
    fun `json syntax validator accepts and rejects the right things`() {
        listOf("{}", "[]", "[1,2,3]", """{"a":"b\"c","d":[true,false,null],"e":-0.5E+2}""", """{"u":"\u00e9"}""", "  { \"a\" : 1 }  ")
            .forEach { assertTrue(it, JsonSyntax.isValid(it)) }
        listOf("", "{", "{\"a\":}", "{\"a\":1,}", "[1,]", "{'a':1}", "{\"a\":01}", "{\"a\":1} x", "[1 2]", "{\"a\":\"\\q\"}", "{\"a\":tru}", "{\"a\"\n:\"line\nbreak\"}")
            .forEach { assertFalse(it, JsonSyntax.isValid(it)) }
    }

    @Test
    fun `deeply nested json does not overflow the stack`() {
        assertFalse(JsonSyntax.isValid("[".repeat(5000) + "]".repeat(5000)))
    }

    @Test
    fun `json extraction finds the object inside fences and prose`() {
        assertEquals("{\"a\":1}", JsonSyntax.extract("```json\n{\"a\":1}\n```"))
        assertEquals("[1,2]", JsonSyntax.extract("Resultado: [1,2] fin"))
        assertNull(JsonSyntax.extract("sin json"))
    }

    @Test
    fun `a repeated line is a loop`() {
        val loop = "Primera frase buena que explica algo.\n" + "Y otra vez la misma línea repetida.\n".repeat(3)
        assertTrue("no_repetition" in failedIds(eval(loop)))
    }

    @Test
    fun `highly repetitive text is a loop`() {
        val text = "la respuesta es la respuesta es ".repeat(10)
        assertNotNull(Repetition.describe(text))
    }

    @Test
    fun `a tail repeated in a loop is detected`() {
        assertNotNull(Repetition.describe("Hola, esto empieza bien y luego... tail tail tail tail"))
        assertNotNull(Repetition.describe("Texto normal que degenera en: abcdefabcdefabcdef"))
    }

    @Test
    fun `varied prose is not repetition`() {
        assertNull(Repetition.describe(good))
        assertNull(Repetition.describe("Uno\nDos\nTres\nCuatro es el siguiente elemento de la lista de cosas"))
        assertNull(Repetition.describe("- punto uno detallado\n- punto dos detallado\n- punto tres detallado"))
    }

    @Test
    fun `summary lists the reasons for rejection`() {
        val r = eval("Lo siento, pero no puedo.", Complexity.COMPLEX)
        assertFalse(r.accepted)
        assertTrue(r.summary().startsWith("rejected"))
        assertTrue(r.summary().contains("refusal"))
        assertTrue(r.summary().contains("short"))
    }

    // ---- confidence ---------------------------------------------------------------------------------------

    @Test
    fun `confidence parses the usual shapes`() {
        assertEquals(0.8, ConfidenceParser.parse("4")!!, 1e-9)
        assertEquals(0.8, ConfidenceParser.parse("4/5")!!, 1e-9)
        assertEquals(0.8, ConfidenceParser.parse("8 out of 10")!!, 1e-9)
        assertEquals(0.85, ConfidenceParser.parse("85%")!!, 1e-9)
        assertEquals(0.7, ConfidenceParser.parse("0.7")!!, 1e-9)
        assertEquals(0.7, ConfidenceParser.parse("0,7")!!, 1e-9)
        assertEquals(0.6, ConfidenceParser.parse("Score: 3.")!!, 1e-9)
        assertEquals(0.2, ConfidenceParser.parse("1")!!, 1e-9)
        assertEquals(0.0, ConfidenceParser.parse("0")!!, 1e-9)
        assertEquals(0.8, ConfidenceParser.parse("8")!!, 1e-9)
    }

    @Test
    fun `confidence is null without a usable number and clamped otherwise`() {
        assertNull(ConfidenceParser.parse("no idea"))
        assertNull(ConfidenceParser.parse(""))
        assertNull(ConfidenceParser.parse("1234"))
        assertEquals(1.0, ConfidenceParser.parse("7/5")!!, 1e-9)
    }

    @Test
    fun `assessment prompts are truncated to fit small contexts`() {
        val p = ConfidenceParser.selfAssessPrompt("q".repeat(2000), "a".repeat(2000))
        assertTrue(p.length < 900)
        assertTrue(ConfidenceParser.judgePrompt("q".repeat(2000), "a".repeat(2000)).length < 900)
    }
}
