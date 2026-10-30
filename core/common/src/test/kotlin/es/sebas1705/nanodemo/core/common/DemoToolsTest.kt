package es.sebas1705.nanodemo.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoToolsTest {
    private val english = appTextsFor("en")
    private val spanish = appTextsFor("es")

    @Test
    fun `matchReactCall devuelve la zona pedida`() {
        assertEquals("Europe/Madrid", DemoTools.matchReactCall("[[GET_CURRENT_TIME:Europe/Madrid]]"))
    }

    @Test
    fun `matchReactCall devuelve vacio si la etiqueta no trae zona`() {
        assertEquals("", DemoTools.matchReactCall("[[GET_CURRENT_TIME]]"))
    }

    @Test
    fun `matchReactCall es null si no hay etiqueta`() {
        assertNull(DemoTools.matchReactCall("hola"))
        assertNull(DemoTools.matchReactCall("[[OTRA_COSA:x]]"))
    }

    @Test
    fun `la etiqueta del prompt es la que se parsea, en los dos idiomas`() {
        assertTrue(DemoTools.reactInstruction(english).contains("[[GET_CURRENT_TIME:IANA_time_zone]]"))
        assertTrue(DemoTools.reactInstruction(spanish).contains("[[GET_CURRENT_TIME:zona_horaria_IANA]]"))
        listOf(english, spanish).forEach { texts ->
            val example = Regex("""\[\[[^\]]*Europe/Madrid]]""").find(DemoTools.reactInstruction(texts))!!.value
            assertEquals("Europe/Madrid", DemoTools.matchReactCall(example))
        }
    }

    @Test
    fun `executeCurrentTime respeta la zona pedida`() {
        val result = DemoTools.executeCurrentTime("Asia/Tokyo", english)
        assertTrue(result.getValue("iso8601").endsWith("[Asia/Tokyo]"))
    }

    @Test
    fun `executeCurrentTime nunca falla con una zona invalida o ausente`() {
        assertTrue(DemoTools.executeCurrentTime("No/Existe", spanish).getValue("iso8601").endsWith("[UTC]"))
        assertTrue(DemoTools.executeCurrentTime(null, spanish).getValue("iso8601").endsWith("[UTC]"))
    }

    @Test
    fun `las claves del resultado no dependen del idioma`() {
        assertEquals(setOf("iso8601", "readable"), DemoTools.executeCurrentTime("UTC", english).keys)
        assertEquals(setOf("iso8601", "readable"), DemoTools.executeCurrentTime("UTC", spanish).keys)
    }

    @Test
    fun `la fecha legible sale en el idioma pedido`() {
        assertTrue(DemoTools.executeCurrentTime("UTC", spanish).getValue("readable").contains(" de "))
        assertTrue(DemoTools.executeCurrentTime("UTC", english).getValue("readable").contains(", "))
    }
}
