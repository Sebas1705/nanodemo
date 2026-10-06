package es.sebas1705.nanodemo.feature.chat

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
}
