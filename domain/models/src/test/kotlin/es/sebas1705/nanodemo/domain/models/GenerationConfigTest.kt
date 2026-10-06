package es.sebas1705.nanodemo.domain.models

import org.junit.Assert.assertEquals
import org.junit.Test

class GenerationConfigTest {
    private val everything = TierCapabilities(
        temperature = true, topK = true, topP = true, maxOutputTokens = true,
        systemPrompt = true, multiTurn = true, images = true,
        structuredOutput = true, functionCalling = true, webSearch = true,
    )

    private val source = GenerationConfig(
        temperature = 0.2f, topK = 10, topP = 0.5f, maxOutputTokens = 256,
        systemPrompt = "Sé breve", jsonMode = true, functionCalling = true, webSearch = true,
    )

    @Test
    fun `un tier que soporta todo recibe toda la configuracion`() {
        assertEquals(source, GenerationConfig().withSupportedSettingsFrom(source, everything))
    }

    @Test
    fun `lo que el tier no soporta se deja como estaba`() {
        val nanoLike = everything.copy(topP = false, webSearch = false)
        val target = GenerationConfig(topP = null, webSearch = false)
        val result = target.withSupportedSettingsFrom(source, nanoLike)
        assertEquals(null, result.topP)
        assertEquals(false, result.webSearch)
        assertEquals(0.2f, result.temperature)
        assertEquals("Sé breve", result.systemPrompt)
    }

    @Test
    fun `un tier que no soporta nada no cambia`() {
        val nothing = TierCapabilities(
            temperature = false, topK = false, topP = false, maxOutputTokens = false,
            systemPrompt = false, multiTurn = false, images = false,
        )
        val target = GenerationConfig(temperature = 1.5f, systemPrompt = "otro")
        assertEquals(target, target.withSupportedSettingsFrom(source, nothing))
    }
}
