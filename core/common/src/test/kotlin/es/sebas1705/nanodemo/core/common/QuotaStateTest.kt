package es.sebas1705.nanodemo.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuotaStateTest {
    private fun state(used: Int, limit: Int) = QuotaState(QuotaService.GEMINI_CLOUD, used, limit, enforced = true)

    @Test
    fun `la fraccion es uso sobre limite`() {
        assertEquals(0.5f, state(10, 20).fraction, 0.0001f)
    }

    @Test
    fun `la fraccion no pasa de uno aunque se haya excedido`() {
        assertEquals(1f, state(30, 20).fraction, 0.0001f)
    }

    @Test
    fun `un limite cero cuenta como agotado`() {
        assertEquals(1f, state(0, 0).fraction, 0.0001f)
        assertTrue(state(0, 0).reached)
    }

    @Test
    fun `alcanzado es uso mayor o igual al limite`() {
        assertFalse(state(19, 20).reached)
        assertTrue(state(20, 20).reached)
        assertTrue(state(21, 20).reached)
    }

    @Test
    fun `cerca del limite desde el 80 por ciento`() {
        assertFalse(state(15, 20).nearLimit)
        assertTrue(state(16, 20).nearLimit)
    }

    @Test
    fun `todos los servicios tienen un limite por defecto positivo`() {
        QuotaService.entries.forEach { assertTrue(it.name, it.defaultLimit > 0) }
    }
}
