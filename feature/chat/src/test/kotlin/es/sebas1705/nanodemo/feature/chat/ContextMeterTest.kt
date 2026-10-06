package es.sebas1705.nanodemo.feature.chat

import es.sebas1705.nanodemo.core.common.SEARCH_CONTEXT_OVERHEAD_TOKENS
import es.sebas1705.nanodemo.core.common.SEARCH_CONTEXT_OVERHEAD_TOKENS_COMPACT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextMeterTest {

    @Test
    fun `sin nada solo cuenta el system prompt`() {
        assertEquals(100, estimateContextTokens(emptyList(), systemPromptChars = 400, userTurns = 0, searchOn = false, compactSearch = false))
    }

    @Test
    fun `el texto de la conversacion suma a razon de 4 caracteres por token`() {
        val texts = listOf("a".repeat(400), "b".repeat(800))
        assertEquals(300, estimateContextTokens(texts, systemPromptChars = 0, userTurns = 1, searchOn = false, compactSearch = false))
    }

    @Test
    fun `con busqueda cada turno del usuario suma el bloque de resultados`() {
        val base = estimateContextTokens(emptyList(), 0, userTurns = 3, searchOn = false, compactSearch = false)
        val withSearch = estimateContextTokens(emptyList(), 0, userTurns = 3, searchOn = true, compactSearch = false)
        assertEquals(3 * SEARCH_CONTEXT_OVERHEAD_TOKENS, withSearch - base)
    }

    @Test
    fun `la busqueda compacta suma menos que la normal`() {
        val compact = estimateContextTokens(emptyList(), 0, userTurns = 1, searchOn = true, compactSearch = true)
        assertEquals(SEARCH_CONTEXT_OVERHEAD_TOKENS_COMPACT, compact)
        assertTrue(SEARCH_CONTEXT_OVERHEAD_TOKENS_COMPACT < SEARCH_CONTEXT_OVERHEAD_TOKENS)
    }

    @Test
    fun `un turno con busqueda compacta entra en el presupuesto de MediaPipe pero uno normal casi no`() {
        // Presupuesto de MediaPipe: 1024 tokens en total. El system prompt (~preambulo) ronda los 200.
        val compact = estimateContextTokens(emptyList(), systemPromptChars = 800, userTurns = 1, searchOn = true, compactSearch = true)
        val normal = estimateContextTokens(emptyList(), systemPromptChars = 800, userTurns = 1, searchOn = true, compactSearch = false)
        assertTrue("compacto deja lugar para responder", compact < 1024 / 2)
        assertTrue("normal se come casi todo", normal > 1024 / 2)
    }

    @Test
    fun `la fraccion no pasa de uno`() {
        assertEquals(0.5f, contextFraction(512, 1024), 0.0001f)
        assertEquals(1f, contextFraction(5_000, 1024), 0.0001f)
        assertEquals(1f, contextFraction(0, 0), 0.0001f)
    }
}
