package es.sebas1705.nanodemo.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class AppTextsTest {
    private val english = appTextsFor("en")
    private val spanish = appTextsFor("es")
    private val date = ZonedDateTime.of(2026, 10, 30, 9, 5, 0, 0, ZoneId.of("UTC"))

    /** Todo lo que puede salir de [AppTexts], para los chequeos que valen para cualquier texto. */
    private fun allTexts(texts: AppTexts): List<String> = listOf(
        texts.appContextPreamble,
        texts.jsonModeInstruction,
        texts.searchAnswerFormat,
        texts.currentTimeDescription,
        texts.deviceTimeZoneNote,
        texts.timeZoneParamDescription,
        texts.instructionsLabel,
        texts.questionLabel,
        texts.reactInstruction("[[TAG:x]]", "[[TAG:y]]"),
        texts.pageContentTurn("https://a.com", "page", "q"),
        texts.toolResult("tool", "when", "iso"),
        texts.searchResultsHeader("q"),
        texts.pageExcerpt(1, "x"),
        texts.formatDateTime(date),
        texts.sourcesHeading,
        texts.usingToolNotice("tool"),
        texts.searchDoneNotice("q", 5),
        texts.searchDoneNotice("q", 1),
        texts.searchUnavailableNotice(WebSearchFailure.NotConfigured),
        texts.searchUnavailableNotice(WebSearchFailure.QuotaReached),
        texts.searchUnavailableNotice(WebSearchFailure.NoResults),
        texts.searchUnavailableNotice(WebSearchFailure.NetworkError),
        texts.searchUnavailableNotice(WebSearchFailure.HttpError(503)),
        texts.groundingNotice("\"q\"", 3),
        texts.groundingNotice("\"q\"", 1),
        texts.errorPreparingModel,
        texts.errorGenerating,
        texts.errorNetwork,
        texts.errorMovingModel,
        texts.errorNoUserTurn,
        texts.errorMissingFirebaseConfig,
        texts.errorDownloadingLink("x"),
        texts.errorDownloadingModel(500),
        texts.errorDownloadingModelPart(500, 2),
        texts.errorDownloadingEmbeddingModel(500),
        texts.errorMissingModelUrl("model"),
    ) + QuotaService.entries.map { texts.quotaBlocked(it, 20, 20) }

    @Test
    fun `el idioma se elige como en values y values-es`() {
        assertSame(appTextsFor("es"), appTextsFor("es-419"))
        assertSame(appTextsFor("es"), appTextsFor("ES"))
        assertSame(appTextsFor("en"), appTextsFor("en-GB"))
        assertSame(appTextsFor("en"), appTextsFor("fr"))
        assertSame(appTextsFor("en"), appTextsFor(""))
    }

    @Test
    fun `ningun texto queda vacio`() {
        (allTexts(english) + allTexts(spanish)).forEach { assertTrue(it, it.isNotBlank()) }
    }

    @Test
    fun `cada texto cambia con el idioma`() {
        allTexts(english).zip(allTexts(spanish)).forEach { (en, es) -> assertNotEquals(en, es) }
    }

    @Test
    fun `en ingles no queda nada en espanol`() {
        val spanishWords = Regex("""(?i)\b(el|la|los|las|de|del|que|para|con|sin|una|no se|respondé|búsqueda|fuentes?)\b""")
        allTexts(english).forEach { text ->
            assertFalse(text, spanishWords.containsMatchIn(text.replace("America/Argentina/Buenos_Aires", "")))
        }
    }

    @Test
    fun `la fecha legible sale en el idioma pedido`() {
        assertEquals("Friday, October 30, 2026, 09:05", english.formatDateTime(date))
        assertEquals("viernes 30 de octubre de 2026, 09:05", spanish.formatDateTime(date))
    }

    @Test
    fun `el formato de la busqueda pide responder en el idioma de la UI`() {
        assertTrue(english.searchAnswerFormat.contains("answer in English"))
        assertTrue(spanish.searchAnswerFormat.contains("respondé en español"))
    }

    @Test
    fun `el singular y el plural salen bien`() {
        assertTrue(english.searchDoneNotice("q", 1).endsWith("— 1 result"))
        assertTrue(english.searchDoneNotice("q", 5).endsWith("— 5 results"))
        assertTrue(spanish.searchDoneNotice("q", 1).endsWith("— 1 resultado"))
        assertTrue(spanish.searchDoneNotice("q", 5).endsWith("— 5 resultados"))
        assertTrue(english.groundingNotice("\"q\"", 1).endsWith("— 1 source"))
        assertTrue(spanish.groundingNotice("\"q\"", 3).endsWith("— 3 fuentes"))
    }

    @Test
    fun `el aviso de cuota nombra el servicio, el uso y donde cambiarlo`() {
        val en = english.quotaBlocked(QuotaService.BRAVE_SEARCH, 1000, 1000)
        assertTrue(en, en.contains("1000/1000 per month") && en.contains("next month"))
        assertTrue(en, en.contains("Settings → Usage and limits"))
        val es = spanish.quotaBlocked(QuotaService.GEMINI_CLOUD, 20, 20)
        assertTrue(es, es.contains("20/20 por día") && es.contains("medianoche del Pacífico"))
        assertTrue(es, es.contains("Ajustes → Uso y límites"))
    }

    @Test
    fun `los marcadores no dependen del idioma`() {
        listOf(english, spanish).forEach { texts ->
            val tool = toolMarker(texts.usingToolNotice("t"))
            assertTrue(tool.startsWith(TOOL_MARKER_PREFIX) && tool.endsWith("\n\n"))
            assertTrue(groundingMarker(texts.groundingNotice("q", 2)).startsWith("\n\n$GROUNDING_MARKER_PREFIX"))
        }
    }

    @Test
    fun `la pregunta se recupera del turno con link en los dos idiomas`() {
        listOf(english, spanish).forEach { texts ->
            val turn = texts.pageContentTurn("https://a.com", "Una página\ncon texto", "¿qué dice?")
            assertEquals("¿qué dice?", extractQuestion(turn))
            assertTrue(hasQuestionLabel(turn))
        }
    }

    @Test
    fun `la pregunta se recupera aunque el idioma haya cambiado entre armar y leer el turno`() {
        // El turno lo arma el repositorio y lo lee el backend: cada uno pide los textos por su cuenta.
        assertEquals("q", extractQuestion(spanish.pageContentTurn("https://a.com", english.questionLabel + " page", "q")))
        assertEquals("q", extractQuestion(english.pageContentTurn("https://a.com", spanish.questionLabel + " page", "q")))
    }

    @Test
    fun `sin etiqueta la pregunta es el texto entero`() {
        assertEquals("hola", extractQuestion("  hola "))
        assertFalse(hasQuestionLabel("hola"))
    }
}
