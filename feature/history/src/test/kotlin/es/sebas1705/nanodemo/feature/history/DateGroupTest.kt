package es.sebas1705.nanodemo.feature.history

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class DateGroupTest {
    private val today = LocalDate.of(2026, 9, 24)

    private fun groupOf(daysAgo: Long): DateGroup {
        val ms = today.minusDays(daysAgo).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return dateGroup(ms, today)
    }

    @Test
    fun `hoy`() = assertEquals(DateGroup.TODAY, groupOf(0))

    @Test
    fun `ayer`() = assertEquals(DateGroup.YESTERDAY, groupOf(1))

    @Test
    fun `esta semana es de 2 a 6 dias`() {
        assertEquals(DateGroup.THIS_WEEK, groupOf(2))
        assertEquals(DateGroup.THIS_WEEK, groupOf(6))
    }

    @Test
    fun `este mes es de 7 a 29 dias`() {
        assertEquals(DateGroup.THIS_MONTH, groupOf(7))
        assertEquals(DateGroup.THIS_MONTH, groupOf(29))
    }

    @Test
    fun `antes desde 30 dias`() = assertEquals(DateGroup.EARLIER, groupOf(30))

    @Test
    fun `una fecha futura por reloj desfasado cuenta como hoy`() = assertEquals(DateGroup.TODAY, groupOf(-3))
}
