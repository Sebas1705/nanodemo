package es.sebas1705.nanodemo.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPromptEmulationTest {

    @Test
    fun `el preambulo de la app va siempre primero`() {
        val prompt = buildLocalSystemPrompt("Sé breve", functionCalling = false, webSearch = false, jsonMode = false)
        assertTrue(prompt.startsWith(appContextPreamble()))
        assertTrue(prompt.contains("Sé breve"))
    }

    @Test
    fun `sin nada activo solo hay preambulo y prompt del usuario`() {
        val prompt = buildLocalSystemPrompt(null, functionCalling = false, webSearch = false, jsonMode = false)
        assertEquals(appContextPreamble(), prompt)
    }

    @Test
    fun `herramientas agrega la instruccion de la etiqueta`() {
        val prompt = buildLocalSystemPrompt(null, functionCalling = true, webSearch = false, jsonMode = false)
        assertTrue(prompt.contains(DemoTools.reactInstruction()))
    }

    @Test
    fun `con busqueda activa no se agrega la instruccion de herramientas`() {
        val prompt = buildLocalSystemPrompt(null, functionCalling = true, webSearch = true, jsonMode = false)
        assertFalse(prompt.contains(DemoTools.reactInstruction()))
    }

    @Test
    fun `modo json es aditivo`() {
        val prompt = buildLocalSystemPrompt(null, functionCalling = true, webSearch = false, jsonMode = true)
        assertTrue(prompt.contains(JSON_MODE_INSTRUCTION))
        assertTrue(prompt.contains(DemoTools.reactInstruction()))
    }

    @Test
    fun `detecta la etiqueta de hora con zona`() {
        val call = resolveLocalToolCall("[[GET_CURRENT_TIME:Europe/Madrid]]")
        assertNotNull(call)
        assertTrue(call!!.marker.startsWith("🔧"))
        assertTrue(call.resultMessage.startsWith("Resultado de get_current_time"))
    }

    @Test
    fun `detecta la etiqueta aunque el modelo agregue texto alrededor`() {
        assertNotNull(resolveLocalToolCall("Claro, voy a consultarlo: [[GET_CURRENT_TIME:Asia/Tokyo]] gracias"))
    }

    @Test
    fun `la etiqueta sin zona cae a UTC en vez de fallar`() {
        assertNotNull(resolveLocalToolCall("[[GET_CURRENT_TIME]]"))
        assertNotNull(resolveLocalToolCall("[[GET_CURRENT_TIME:]]"))
    }

    @Test
    fun `una respuesta normal no es un pedido de herramienta`() {
        assertNull(resolveLocalToolCall("Son las tres de la tarde."))
        assertNull(resolveLocalToolCall(""))
    }
}
