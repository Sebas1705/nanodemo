package es.sebas1705.nanodemo.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPromptEmulationTest {
    private val english = appTextsFor("en")
    private val spanish = appTextsFor("es")
    private val bothLanguages = listOf(english, spanish)

    @Test
    fun `el preambulo de la app va siempre primero`() {
        bothLanguages.forEach { texts ->
            val prompt = buildLocalSystemPrompt("Sé breve", functionCalling = false, webSearch = false, jsonMode = false, texts)
            assertTrue(prompt.startsWith(appContextPreamble(texts)))
            assertTrue(prompt.contains("Sé breve"))
        }
    }

    @Test
    fun `el system prompt sale en el idioma de la UI`() {
        val en = buildLocalSystemPrompt(null, functionCalling = true, webSearch = false, jsonMode = true, english)
        assertTrue(en.startsWith("You are the chat assistant"))
        assertTrue(en.contains("Reply ONLY with valid JSON"))
        assertFalse(en.contains("Respondé"))

        val es = buildLocalSystemPrompt(null, functionCalling = true, webSearch = false, jsonMode = true, spanish)
        assertTrue(es.startsWith("Sos el asistente de chat"))
        assertTrue(es.contains("Respondé ÚNICAMENTE con JSON válido"))
        assertFalse(es.contains("You are"))
    }

    @Test
    fun `sin nada activo solo hay preambulo y prompt del usuario`() {
        bothLanguages.forEach { texts ->
            val prompt = buildLocalSystemPrompt(null, functionCalling = false, webSearch = false, jsonMode = false, texts)
            assertEquals(appContextPreamble(texts), prompt)
        }
    }

    @Test
    fun `herramientas agrega la instruccion de la etiqueta`() {
        bothLanguages.forEach { texts ->
            val prompt = buildLocalSystemPrompt(null, functionCalling = true, webSearch = false, jsonMode = false, texts)
            assertTrue(prompt.contains(DemoTools.reactInstruction(texts)))
        }
    }

    @Test
    fun `con busqueda activa no se agrega la instruccion de herramientas`() {
        bothLanguages.forEach { texts ->
            val prompt = buildLocalSystemPrompt(null, functionCalling = true, webSearch = true, jsonMode = false, texts)
            assertFalse(prompt.contains(DemoTools.reactInstruction(texts)))
        }
    }

    @Test
    fun `modo json es aditivo`() {
        bothLanguages.forEach { texts ->
            val prompt = buildLocalSystemPrompt(null, functionCalling = true, webSearch = false, jsonMode = true, texts)
            assertTrue(prompt.contains(texts.jsonModeInstruction))
            assertTrue(prompt.contains(DemoTools.reactInstruction(texts)))
        }
    }

    @Test
    fun `detecta la etiqueta de hora con zona`() {
        val es = resolveLocalToolCall("[[GET_CURRENT_TIME:Europe/Madrid]]", spanish)
        assertNotNull(es)
        assertEquals("🔧 usando \"get_current_time\"…\n\n", es!!.marker)
        assertTrue(es.resultMessage.startsWith("Resultado de get_current_time: "))

        val en = resolveLocalToolCall("[[GET_CURRENT_TIME:Europe/Madrid]]", english)
        assertNotNull(en)
        assertEquals("🔧 using \"get_current_time\"…\n\n", en!!.marker)
        assertTrue(en.resultMessage.startsWith("Result of get_current_time: "))
    }

    @Test
    fun `detecta la etiqueta aunque el modelo agregue texto alrededor`() {
        bothLanguages.forEach { texts ->
            assertNotNull(resolveLocalToolCall("Claro, voy a consultarlo: [[GET_CURRENT_TIME:Asia/Tokyo]] gracias", texts))
        }
    }

    @Test
    fun `la etiqueta sin zona cae a UTC en vez de fallar`() {
        bothLanguages.forEach { texts ->
            assertNotNull(resolveLocalToolCall("[[GET_CURRENT_TIME]]", texts))
            assertNotNull(resolveLocalToolCall("[[GET_CURRENT_TIME:]]", texts))
        }
    }

    @Test
    fun `una respuesta normal no es un pedido de herramienta`() {
        bothLanguages.forEach { texts ->
            assertNull(resolveLocalToolCall("Son las tres de la tarde.", texts))
            assertNull(resolveLocalToolCall("", texts))
        }
    }
}
