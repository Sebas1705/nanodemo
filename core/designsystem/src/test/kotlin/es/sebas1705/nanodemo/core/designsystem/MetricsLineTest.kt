package es.sebas1705.nanodemo.core.designsystem

import es.sebas1705.nanodemo.domain.models.GenerationMetric
import es.sebas1705.nanodemo.domain.models.InferenceTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricsLineTest {
    private fun metric(
        first: Long? = 500,
        total: Long = 2_500,
        tokens: Int = 40,
        exact: Boolean = true,
        success: Boolean = true,
        load: Long? = null,
    ) = GenerationMetric(
        tier = InferenceTier.MEDIAPIPE_LOCAL,
        startedAtMs = 0,
        firstChunkLatencyMs = first,
        totalDurationMs = total,
        promptChars = 10,
        responseChars = tokens * 4,
        success = success,
        responseTokens = tokens,
        responseTokensExact = exact,
        memoryDeltaBytes = 0,
        loadTimeMs = load,
    )

    @Test
    fun `la velocidad cuenta desde el primer trozo, no desde el pedido`() {
        // 40 tokens en los 2.0 s que pasan entre el primer trozo (0.5 s) y el final (2.5 s) = 20 tok/s.
        assertEquals(20.0, metric().tokensPerSecond()!!, 0.001)
    }

    @Test
    fun `una respuesta de un saque no inventa una velocidad`() {
        assertNull(metric(first = 2_490, total = 2_500).tokensPerSecond())
    }

    @Test
    fun `sin primer trozo se mide sobre el total`() {
        assertEquals(16.0, metric(first = null, total = 2_500).tokensPerSecond()!!, 0.001)
    }

    @Test
    fun `una corrida fallida o sin tokens no tiene velocidad`() {
        assertNull(metric(success = false).tokensPerSecond())
        assertNull(metric(tokens = 0).tokensPerSecond())
    }

    @Test
    fun `las partes se muestran en orden con los tokens aproximados marcados`() {
        assertEquals(
            listOf("⚡ 0.5s", "⏱ 2.5s", "20 tok/s", "~40 tok"),
            metric(exact = false).displayParts(),
        )
    }

    @Test
    fun `los tokens reales no llevan tilde`() {
        assertTrue(metric(exact = true).displayParts().contains("40 tok"))
    }

    @Test
    fun `la carga del modelo aparece aparte al final solo si la hubo`() {
        assertEquals("carga 9.8s", metric(load = 9_800).displayParts().last())
        assertTrue(metric(load = null).displayParts().none { it.startsWith("carga") })
    }
}
