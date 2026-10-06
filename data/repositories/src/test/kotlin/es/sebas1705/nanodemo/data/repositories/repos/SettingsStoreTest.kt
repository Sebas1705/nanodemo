package es.sebas1705.nanodemo.data.repositories.repos

import es.sebas1705.nanodemo.domain.models.GenerationConfig
import es.sebas1705.nanodemo.domain.models.InferenceTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsStoreTest {

    private fun roundTrip(config: GenerationConfig) =
        GenerationConfigCodec.fromMap(GenerationConfigCodec.toMap(config))

    @Test
    fun `la config por defecto sobrevive al guardado`() {
        assertEquals(GenerationConfig(), roundTrip(GenerationConfig()))
    }

    @Test
    fun `todos los campos modificados sobreviven`() {
        val config = GenerationConfig(
            temperature = 0.3f,
            topK = 12,
            topP = 0.5f,
            maxOutputTokens = 1024,
            systemPrompt = "Respondé en 2 oraciones.\nSin emojis.",
            jsonMode = true,
            functionCalling = true,
            webSearch = true,
        )
        assertEquals(config, roundTrip(config))
    }

    @Test
    fun `un campo null se queda null y no vuelve al valor por defecto`() {
        // topK = null significa "este tier no lo usa"; si volviera a 40 se le mandaría un valor que el
        // backend no soporta.
        val config = GenerationConfig(topK = null, topP = null, temperature = null, maxOutputTokens = null)
        val restored = roundTrip(config)
        assertNull(restored.topK)
        assertNull(restored.topP)
        assertNull(restored.temperature)
        assertNull(restored.maxOutputTokens)
    }

    @Test
    fun `los campos null no se escriben`() {
        val map = GenerationConfigCodec.toMap(GenerationConfig(systemPrompt = null, topK = null))
        assertFalse(map.containsKey("systemPrompt"))
        assertFalse(map.containsKey("topK"))
    }

    @Test
    fun `un valor con el tipo equivocado se trata como ausente en vez de romper`() {
        val restored = GenerationConfigCodec.fromMap(mapOf("topK" to "no soy un int", "jsonMode" to "tampoco"))
        assertNull(restored.topK)
        assertFalse(restored.jsonMode)
    }

    @Test
    fun `sin tier guardado se prueban todos en el orden de siempre`() {
        assertEquals(InferenceTier.entries, tierProbeOrder(null))
    }

    @Test
    fun `el tier guardado va primero y el resto conserva su orden`() {
        val order = tierProbeOrder(InferenceTier.LITERT_LM_LOCAL)
        assertEquals(InferenceTier.LITERT_LM_LOCAL, order.first())
        assertEquals(InferenceTier.entries.size, order.size)
        assertEquals(
            InferenceTier.entries.filter { it != InferenceTier.LITERT_LM_LOCAL },
            order.drop(1),
        )
    }
}
