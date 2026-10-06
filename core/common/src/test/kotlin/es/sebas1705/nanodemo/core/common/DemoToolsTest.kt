package es.sebas1705.nanodemo.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoToolsTest {

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
    fun `executeCurrentTime respeta la zona pedida`() {
        val result = DemoTools.executeCurrentTime("Asia/Tokyo")
        assertTrue(result.getValue("iso8601").endsWith("[Asia/Tokyo]"))
    }

    @Test
    fun `executeCurrentTime nunca falla con una zona invalida o ausente`() {
        assertTrue(DemoTools.executeCurrentTime("No/Existe").getValue("iso8601").endsWith("[UTC]"))
        assertTrue(DemoTools.executeCurrentTime(null).getValue("iso8601").endsWith("[UTC]"))
    }

    @Test
    fun `executeCurrentTime devuelve las dos claves`() {
        assertEquals(setOf("iso8601", "legible"), DemoTools.executeCurrentTime("UTC").keys)
    }
}
