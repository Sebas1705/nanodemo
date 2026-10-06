package es.sebas1705.nanodemo.data.repositories.repos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetricTimingTest {

    @Test
    fun `sin carga los tiempos quedan como estaban`() {
        assertEquals(RunTiming(500, 2_500), excludeLoadTime(500, 2_500, loadMs = null))
    }

    @Test
    fun `la carga se descuenta del primer trozo y del total`() {
        // 9.8 s de cargar el modelo + 0.5 s hasta el primer trozo + 2 s más de generación.
        assertEquals(RunTiming(500, 2_500), excludeLoadTime(10_300, 12_300, loadMs = 9_800))
    }

    @Test
    fun `sin primer trozo solo se corrige el total`() {
        val timing = excludeLoadTime(null, 12_300, loadMs = 9_800)
        assertNull(timing.firstChunkLatencyMs)
        assertEquals(2_500, timing.totalDurationMs)
    }

    @Test
    fun `una carga mayor al tiempo medido nunca da negativos`() {
        // Los relojes difieren unos ms: la carga no puede dejar el tiempo por debajo de cero.
        assertEquals(RunTiming(0, 0), excludeLoadTime(100, 200, loadMs = 250))
    }
}
