package es.sebas1705.nanodemo.feature.history

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DateGroupTest {
    private val today = LocalDate.of(2026, 9, 24)

    private fun groupOf(daysAgo: Long): String {
        val ms = today.minusDays(daysAgo).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return dateGroup(ms, today)
    }

    @Test
    fun `hoy`() = assertEquals("Hoy", groupOf(0))

    @Test
    fun `ayer`() = assertEquals("Ayer", groupOf(1))

    @Test
    fun `esta semana es de 2 a 6 dias`() {
        assertEquals("Esta semana", groupOf(2))
        assertEquals("Esta semana", groupOf(6))
    }

    @Test
    fun `este mes es de 7 a 29 dias`() {
        assertEquals("Este mes", groupOf(7))
        assertEquals("Este mes", groupOf(29))
    }

    @Test
    fun `antes desde 30 dias`() = assertEquals("Antes", groupOf(30))

    @Test
    fun `una fecha futura por reloj desfasado cuenta como hoy`() = assertEquals("Hoy", groupOf(-3))
}
