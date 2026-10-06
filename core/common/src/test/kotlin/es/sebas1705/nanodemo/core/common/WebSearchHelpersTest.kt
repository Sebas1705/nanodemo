package es.sebas1705.nanodemo.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchHelpersTest {

    @Test
    fun `cleanHtml saca las etiquetas y decodifica entidades`() {
        assertEquals(
            "Gemini & \"Nano\"",
            WebSearchTool.cleanHtml("<strong>Gemini</strong> &amp; &quot;Nano&quot;"),
        )
    }

    @Test
    fun `cleanHtml colapsa espacios`() {
        assertEquals("a b c", WebSearchTool.cleanHtml("  a \n  b\t c  "))
    }

    @Test
    fun `una pregunta larga es la consulta tal cual`() {
        assertEquals(
            "cuál es el clima en Buenos Aires hoy",
            buildQuery("cuál es el clima en Buenos Aires hoy", "otra cosa"),
        )
    }

    @Test
    fun `una pregunta muy corta se completa con la anterior`() {
        assertEquals("clima en Buenos Aires y en Madrid", buildQuery("y en Madrid", "clima en Buenos Aires"))
    }

    @Test
    fun `una pregunta corta sin anterior queda sola`() {
        assertEquals("y en Madrid", buildQuery("y en Madrid", null))
        assertEquals("y en Madrid", buildQuery("y en Madrid", "  "))
    }

    @Test
    fun `la consulta se recorta y normaliza espacios`() {
        val query = buildQuery("palabra ".repeat(100), null)
        assertTrue(query.length <= 200)
        assertFalse(query.contains("  "))
    }
}
