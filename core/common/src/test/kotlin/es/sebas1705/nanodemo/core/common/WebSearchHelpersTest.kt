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

    private val results = listOf(WebResult("Título [1]", "descripción", "https://a.com/x)"))

    @Test
    fun `el turno con resultados sale en el idioma de la UI`() {
        val en = buildSearchPrompt("clima", results, listOf("extracto"), "¿clima?", appTextsFor("en"), compact = false)
        assertTrue(en, en.startsWith("Internet search results for \"clima\":"))
        assertTrue(en, en.contains("[1] Título [1] — descripción"))
        assertTrue(en, en.contains("Excerpt from [1]: extracto"))
        assertTrue(en, en.contains("User question: ¿clima?"))
        assertTrue(en, en.endsWith(appTextsFor("en").searchAnswerFormat))

        val es = buildSearchPrompt("clima", results, listOf("extracto"), "¿clima?", appTextsFor("es"), compact = false)
        assertTrue(es, es.startsWith("Resultados de búsqueda en internet para \"clima\":"))
        assertTrue(es, es.contains("Extracto de [1]: extracto"))
        assertTrue(es, es.contains("Pregunta del usuario: ¿clima?"))
        assertTrue(es, es.endsWith(appTextsFor("es").searchAnswerFormat))
    }

    @Test
    fun `un turno que ya trae etiqueta no se etiqueta dos veces`() {
        val texts = appTextsFor("en")
        val turn = texts.pageContentTurn("https://a.com", "page", "q")
        val prompt = buildSearchPrompt("q", results, emptyList(), turn, texts, compact = true)
        assertEquals(1, Regex("User question:").findAll(prompt).count())
    }

    @Test
    fun `el bloque de fuentes sale en el idioma de la UI`() {
        val sources = "1. [Título (1)](https://a.com/x%29)"
        assertEquals("**Sources**\n$sources", buildSourcesBlock(results, appTextsFor("en")))
        assertEquals("**Fuentes**\n$sources", buildSourcesBlock(results, appTextsFor("es")))
    }

    @Test
    fun `la consulta se recorta y normaliza espacios`() {
        val query = buildQuery("palabra ".repeat(100), null)
        assertTrue(query.length <= 200)
        assertFalse(query.contains("  "))
    }
}
