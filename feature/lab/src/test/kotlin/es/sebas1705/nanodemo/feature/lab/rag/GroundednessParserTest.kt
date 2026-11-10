package es.sebas1705.nanodemo.feature.lab.rag

import es.sebas1705.nanodemo.feature.lab.rag.GroundingVerdict.PARTIAL
import es.sebas1705.nanodemo.feature.lab.rag.GroundingVerdict.SUPPORTED
import es.sebas1705.nanodemo.feature.lab.rag.GroundingVerdict.UNKNOWN
import es.sebas1705.nanodemo.feature.lab.rag.GroundingVerdict.UNSUPPORTED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GroundednessParserTest {

    private fun verdict(raw: String) = GroundednessParser.parse(raw).verdict
    private fun score(raw: String) = GroundednessParser.parse(raw).score

    @Test
    fun `the format the prompt asks for`() {
        val g = GroundednessParser.parse("VEREDICTO: SI\nPUNTUACION: 9")
        assertEquals(SUPPORTED, g.verdict)
        assertEquals(0.9, g.score!!, 1e-9)

        assertEquals(PARTIAL, verdict("VEREDICTO: PARCIAL\nPUNTUACION: 5"))
        assertEquals(UNSUPPORTED, verdict("VEREDICTO: NO\nPUNTUACION: 1"))
    }

    @Test
    fun `accents, case and markdown emphasis do not matter`() {
        assertEquals(SUPPORTED, verdict("**Veredicto:** Sí"))
        assertEquals(SUPPORTED, verdict("veredicto: sí, todo respaldado"))
        assertEquals(UNSUPPORTED, verdict("Veredicto: **NO**"))
        assertEquals(PARTIAL, verdict("VEREDICTO: Parcialmente"))
    }

    @Test
    fun `a bare first word is enough`() {
        assertEquals(SUPPORTED, verdict("Sí. Todo lo que dice está en el contexto."))
        assertEquals(SUPPORTED, verdict("Yes, fully supported."))
        assertEquals(UNSUPPORTED, verdict("No. La respuesta inventa el precio."))
        assertEquals(UNSUPPORTED, verdict("No, it is not supported."))
        assertEquals(PARTIAL, verdict("Parcial: la cifra sí, la fecha no."))
    }

    @Test
    fun `the model echoing the options still resolves to the first one`() {
        assertEquals(SUPPORTED, verdict("VEREDICTO: SI (todo respaldado), PARCIAL (algo no respaldado) o NO"))
    }

    @Test
    fun `phrases without a decisive first word`() {
        assertEquals(UNSUPPORTED, verdict("La respuesta no está respaldada por el contexto."))
        assertEquals(UNSUPPORTED, verdict("Esto es una alucinación."))
        assertEquals(UNSUPPORTED, verdict("The answer is not supported by the context."))
        assertEquals(PARTIAL, verdict("La respuesta está en parte respaldada."))
        assertEquals(SUPPORTED, verdict("La respuesta está totalmente respaldada."))
        assertEquals(SUPPORTED, verdict("The answer is fully grounded."))
    }

    @Test
    fun `score formats`() {
        assertEquals(0.8, score("8/10")!!, 1e-9)
        assertEquals(0.75, score("3/4")!!, 1e-9)
        assertEquals(0.85, score("85%")!!, 1e-9)
        assertEquals(0.7, score("PUNTUACION: 7")!!, 1e-9)
        assertEquals(0.7, score("Puntuación: 7 de 10")!!, 1e-9)
        assertEquals(0.6, score("score: 0.6")!!, 1e-9)
        assertEquals(0.6, score("score: 0,6")!!, 1e-9)
        assertEquals(1.0, score("score: 10")!!, 1e-9)
        assertEquals(0.1, score("score: 1")!!, 1e-9) // a whole 1 on the 0-10 scale the prompt asks for
        assertEquals(0.8, score("8")!!, 1e-9)
    }

    @Test
    fun `out of range or missing scores are null`() {
        assertNull(score("puntuacion: 250"))
        assertNull(score("sin números"))
        assertNull(score(""))
    }

    @Test
    fun `with only a score the verdict is derived from it`() {
        assertEquals(SUPPORTED, verdict("9/10"))
        assertEquals(PARTIAL, verdict("5/10"))
        assertEquals(UNSUPPORTED, verdict("2/10"))
        assertEquals(SUPPORTED, verdict("PUNTUACION: 8"))
    }

    @Test
    fun `an explicit verdict beats the score`() {
        val g = GroundednessParser.parse("VEREDICTO: NO\nPUNTUACION: 9")
        assertEquals(UNSUPPORTED, g.verdict)
        assertEquals(0.9, g.score!!, 1e-9)
    }

    @Test
    fun `unintelligible replies are unknown, never guessed`() {
        assertEquals(UNKNOWN, verdict(""))
        assertEquals(UNKNOWN, verdict("   \n  "))
        assertEquals(UNKNOWN, verdict("Hmm, no estoy seguro de cómo valorarlo."))
    }

    @Test
    fun `the raw reply is kept trimmed`() {
        assertEquals("VEREDICTO: SI", GroundednessParser.parse("  VEREDICTO: SI \n").raw)
    }
}
