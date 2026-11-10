package es.sebas1705.nanodemo.feature.lab.agent.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class UnitConverterTest {

    private fun convert(value: Double, from: String, to: String) = UnitConverter.convert(value, from, to).output

    private fun assertFails(from: String, to: String, part: String) {
        try {
            UnitConverter.convert(1.0, from, to)
            fail("expected a failure")
        } catch (e: ConversionException) {
            assertTrue("message was: ${e.message}", e.message!!.contains(part))
        }
    }

    @Test
    fun `temperature`() {
        assertEquals(22.2222, convert(72.0, "F", "C"), 1e-4)
        assertEquals(212.0, convert(100.0, "C", "F"), 1e-9)
        assertEquals(-40.0, convert(-40.0, "C", "F"), 1e-9)
        assertEquals(273.15, convert(0.0, "celsius", "kelvin"), 1e-9)
        assertEquals(-459.67, convert(0.0, "K", "F"), 1e-9)
        assertEquals(37.0, convert(98.6, "fahrenheit", "celsius"), 1e-9)
    }

    @Test
    fun `below absolute zero is an error`() {
        try {
            UnitConverter.convert(-300.0, "C", "F")
            fail()
        } catch (e: ConversionException) {
            assertTrue(e.message!!.contains("absolute zero"))
        }
    }

    @Test
    fun `length`() {
        assertEquals(42.1648, convert(26.2, "miles", "km"), 1e-3)
        assertEquals(2.54, convert(1.0, "in", "cm"), 1e-9)
        assertEquals(1000.0, convert(1.0, "km", "m"), 1e-9)
        assertEquals(3.0, convert(1.0, "yd", "ft"), 1e-9)
        assertEquals(1.609344, convert(1.0, "mi", "km"), 1e-9)
    }

    @Test
    fun `mass`() {
        assertEquals(0.45359237, convert(1.0, "lb", "kg"), 1e-9)
        assertEquals(1000.0, convert(1.0, "kg", "g"), 1e-9)
        assertEquals(16.0, convert(1.0, "lb", "oz"), 1e-6)
    }

    @Test
    fun `volume`() {
        assertEquals(3.785411784, convert(1.0, "gal", "l"), 1e-9)
        assertEquals(236.5882365, convert(1.0, "cup", "ml"), 1e-6)
        assertEquals(1000.0, convert(1.0, "l", "ml"), 1e-9)
        assertEquals(29.5735, convert(1.0, "fl oz", "ml"), 1e-3)
    }

    @Test
    fun `spanish and english names, accents and symbols`() {
        assertEquals(1.609344, convert(1.0, "milla", "kilómetro"), 1e-9)
        assertEquals(1.609344, convert(1.0, "Millas", "KILOMETROS"), 1e-9)
        assertEquals(2.54, convert(1.0, "pulgada", "centímetros"), 1e-9)
        assertEquals(22.2222, convert(72.0, "°F", "°C"), 1e-4)
        assertEquals(22.2222, convert(72.0, "grados Fahrenheit", "grados Celsius"), 1e-4)
        assertEquals(3.785411784, convert(1.0, "galones", "litros"), 1e-9)
    }

    @Test
    fun `identity conversion`() {
        assertEquals(5.0, convert(5.0, "km", "km"), 0.0)
    }

    @Test
    fun `different categories are an error`() {
        assertFails("km", "kg", "cannot convert")
        assertFails("C", "m", "cannot convert")
    }

    @Test
    fun `unknown units are an error that lists the supported ones`() {
        assertFails("parsec", "km", "unknown unit 'parsec'")
        assertFails("km", "furlong", "supported")
        assertFails("", "km", "unknown unit")
    }

    @Test
    fun `parseQuery understands common phrasings`() {
        assertEquals(Triple(72.0, "F", "C"), UnitConverter.parseQuery("72 F to C"))
        assertEquals(Triple(5.0, "km", "millas"), UnitConverter.parseQuery("5 km a millas"))
        assertEquals(Triple(72.0, "°F", "°C"), UnitConverter.parseQuery("72°F en °C"))
        assertEquals(Triple(2.5, "kg", "lb"), UnitConverter.parseQuery("2,5 kg to lb"))
        assertEquals(Triple(10.0, "fl oz", "ml"), UnitConverter.parseQuery("10 fl oz to ml"))
        assertEquals(Triple(5.0, "in", "cm"), UnitConverter.parseQuery("5 in to cm"))
        assertEquals(Triple(-3.0, "C", "F"), UnitConverter.parseQuery("-3 C to F?"))
    }

    @Test
    fun `parseQuery rejects what is not a conversion`() {
        assertNull(UnitConverter.parseQuery("hello"))
        assertNull(UnitConverter.parseQuery("km to m"))
        assertNull(UnitConverter.parseQuery(""))
    }

    @Test
    fun `describe is readable`() {
        assertEquals("72 F = 22.222222 C", UnitConverter.convert(72.0, "F", "C").describe())
        assertNotNull(UnitConverter.findUnit("mi"))
    }
}
