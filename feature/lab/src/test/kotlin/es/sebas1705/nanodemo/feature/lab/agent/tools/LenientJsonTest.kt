package es.sebas1705.nanodemo.feature.lab.agent.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LenientJsonTest {

    @Test
    fun `strict json`() {
        val map = LenientJson.parseObject("""{"a": "x", "b": 2, "c": 2.5, "d": true, "e": null, "f": [1, 2]}""")!!
        assertEquals("x", map["a"])
        assertEquals(2L, map["b"])
        assertEquals(2.5, map["c"])
        assertEquals(true, map["d"])
        assertTrue(map.containsKey("e"))
        assertNull(map["e"])
        assertEquals(listOf<Any?>(1L, 2L), map["f"])
    }

    @Test
    fun `single quotes trailing commas and unquoted keys`() {
        val map = LenientJson.parseObject("{expression: '2+2', unit: \"km\",}")!!
        assertEquals("2+2", map["expression"])
        assertEquals("km", map["unit"])
    }

    @Test
    fun `smart quotes`() {
        val map = LenientJson.parseObject("{“text”: “hola mundo”}")!!
        assertEquals("hola mundo", map["text"])
    }

    @Test
    fun `missing commas and equals instead of colon`() {
        val map = LenientJson.parseObject("{a = 1 b = 'x'}")!!
        assertEquals(1L, map["a"])
        assertEquals("x", map["b"])
    }

    @Test
    fun `a truncated object is closed at the end`() {
        val map = LenientJson.parseObject("{\"expression\": \"2+3")!!
        assertEquals("2+3", map["expression"])
    }

    @Test
    fun `escapes in strings`() {
        val map = LenientJson.parseObject("""{"t": "a\nb\"cA"}""")!!
        assertEquals("a\nb\"cA", map["t"])
    }

    @Test
    fun `a double quote inside a single quoted string`() {
        assertEquals("say \"hi\"", LenientJson.parseObject("{'t': 'say \"hi\"'}")!!["t"])
    }

    @Test
    fun `nested objects`() {
        val map = LenientJson.parseObject("""{"outer": {"inner": 1}}""")!!
        assertEquals(mapOf("inner" to 1L), map["outer"])
    }

    @Test
    fun `bare words as values`() {
        val map = LenientJson.parseObject("{action: read, flag: True, nothing: None}")!!
        assertEquals("read", map["action"])
        assertEquals(true, map["flag"])
        assertNull(map["nothing"])
    }

    @Test
    fun `negative and decimal numbers`() {
        val map = LenientJson.parseObject("{a: -5, b: .5, c: 1e3}")!!
        assertEquals(-5L, map["a"])
        assertEquals(0.5, map["b"])
        assertEquals(1000.0, map["c"])
    }

    @Test
    fun `non json yields null`() {
        assertNull(LenientJson.parse(""))
        assertNull(LenientJson.parse("2+2"))
        assertNull(LenientJson.parseObject("[1, 2]"))
    }

    @Test
    fun `absurd nesting is rejected instead of overflowing the stack`() {
        assertNull(LenientJson.parse("{\"a\":".repeat(500)))
    }

    @Test
    fun `garbage inside an object never loops forever`() {
        LenientJson.parseObject("{ ]]] ,,, ::: }")
    }

    @Test
    fun `ToolArgs reads named parameters case insensitively with synonyms`() {
        val args = ToolArgs("{\"Expression\": \"1+1\", \"value\": 72}")
        assertEquals("1+1", args.string("expr", "expression"))
        assertEquals(72.0, args.number("value")!!, 0.0)
        assertNull(args.string("missing"))
    }

    @Test
    fun `ToolArgs number accepts text and decimal commas`() {
        assertEquals(2.5, ToolArgs("{\"v\": \"2,5\"}").number("v")!!, 0.0)
    }

    @Test
    fun `ToolArgs text falls back to the only member then to the raw input`() {
        assertEquals("2+2", ToolArgs("{\"whatever\": \"2+2\"}").text("expression"))
        assertEquals("2+2", ToolArgs("2+2").text("expression"))
        assertEquals("Europe/Madrid", ToolArgs("\"Europe/Madrid\"").text("timezone"))
        assertNull(ToolArgs("").text("x"))
        assertNull(ToolArgs("{\"a\": 1, \"b\": 2}").text("x"))
    }

    @Test
    fun `ToolArgs knows whether the input was an object`() {
        assertTrue(ToolArgs("{}").isObject)
        assertFalse(ToolArgs("read").isObject)
    }

    @Test
    fun `formatNumber hides floating point noise`() {
        assertEquals("0.3", formatNumber(0.1 + 0.2))
        assertEquals("399.5", formatNumber(0.17 * 2350))
        assertEquals("5", formatNumber(5.0))
        assertEquals("0", formatNumber(-0.0))
        assertEquals("-2.5", formatNumber(-2.5))
        assertEquals("0.333333333333", formatNumber(1.0 / 3))
        assertEquals("1E+20", formatNumber(1e20))
    }
}
