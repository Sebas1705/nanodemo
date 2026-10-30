package es.sebas1705.nanodemo.feature.chat

import es.sebas1705.nanodemo.core.common.appTextsFor
import es.sebas1705.nanodemo.core.common.groundingMarker
import es.sebas1705.nanodemo.core.common.toolMarker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SplitAssistantTextTest {

    @Test
    fun `texto normal queda intacto`() {
        val parts = splitAssistantText("Hola mundo")
        assertNull(parts.tool)
        assertNull(parts.footer)
        assertEquals("Hola mundo", parts.body)
    }

    @Test
    fun `el marcador de herramienta se separa del cuerpo`() {
        val parts = splitAssistantText("🔧 usando \"get_current_time\"…\n\nSon las 10:00.")
        assertEquals("usando \"get_current_time\"…", parts.tool)
        assertEquals("Son las 10:00.", parts.body)
    }

    @Test
    fun `el pie de grounding se separa del cuerpo`() {
        val parts = splitAssistantText("Respuesta.\n\n🔎 Grounded con Google Search (\"x\") — 3 fuente(s)")
        assertEquals("Respuesta.", parts.body)
        assertEquals("Grounded con Google Search (\"x\") — 3 fuente(s)", parts.footer)
    }

    @Test
    fun `marcador de busqueda mas cuerpo mas fuentes`() {
        val parts = splitAssistantText("🔧 busqué en internet \"clima\" — 5 resultados\n\nHace sol [1].\n\n**Fuentes**\n1. [A](https://a.com)")
        assertEquals("busqué en internet \"clima\" — 5 resultados", parts.tool)
        assertEquals("Hace sol [1].\n\n**Fuentes**\n1. [A](https://a.com)", parts.body)
    }

    @Test
    fun `solo el marcador mientras todavia no llego texto`() {
        val parts = splitAssistantText("🔧 usando \"get_current_time\"…\n\n")
        assertEquals("usando \"get_current_time\"…", parts.tool)
        assertEquals("", parts.body)
    }

    @Test
    fun `los avisos reales se separan en los dos idiomas`() {
        listOf(appTextsFor("en"), appTextsFor("es")).forEach { texts ->
            val tool = texts.searchDoneNotice("clima", 5)
            val footer = texts.groundingNotice("\"clima\"", 3)
            val parts = splitAssistantText(toolMarker(tool) + "Cuerpo [1]." + groundingMarker(footer))
            assertEquals(tool, parts.tool)
            assertEquals("Cuerpo [1].", parts.body)
            assertEquals(footer, parts.footer)
        }
    }

    @Test
    fun `un aviso sin cuerpo todavia se separa en los dos idiomas`() {
        listOf(appTextsFor("en"), appTextsFor("es")).forEach { texts ->
            val notice = texts.usingToolNotice("get_current_time")
            assertEquals(notice, splitAssistantText(toolMarker(notice)).tool)
            assertEquals("", splitAssistantText(toolMarker(notice)).body)
        }
    }

    @Test
    fun `una conversacion guardada con avisos en espanol se sigue separando`() {
        val parts = splitAssistantText("🔧 busqué en internet \"x\" — 1 resultados\n\nHola.\n\n🔎 Grounded con Google Search (\"x\") — 3 fuente(s)")
        assertEquals("busqué en internet \"x\" — 1 resultados", parts.tool)
        assertEquals("Hola.", parts.body)
        assertEquals("Grounded con Google Search (\"x\") — 3 fuente(s)", parts.footer)
    }
}
