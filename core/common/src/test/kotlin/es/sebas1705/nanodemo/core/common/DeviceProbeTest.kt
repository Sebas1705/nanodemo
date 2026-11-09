package es.sebas1705.nanodemo.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceProbeTest {

    @Test
    fun `battery drain is positive when the charge went down`() {
        val delta = deviceDelta(
            DeviceReading(batteryChargeUah = 3_000_000, thermalStatus = 0, pssKb = 500_000),
            DeviceReading(batteryChargeUah = 2_999_200, thermalStatus = 1, pssKb = 1_700_000),
        )
        assertEquals(800L, delta.batteryDrainUah)
        assertEquals(1, delta.thermalStatus)
        assertEquals(1_200_000L, delta.pssDeltaKb)
    }

    @Test
    fun `a missing reading on either side leaves the delta unknown`() {
        val delta = deviceDelta(
            DeviceReading(batteryChargeUah = null, thermalStatus = null, pssKb = 100),
            DeviceReading(batteryChargeUah = 10, thermalStatus = null, pssKb = null),
        )
        assertNull(delta.batteryDrainUah)
        assertNull(delta.pssDeltaKb)
        assertNull(delta.thermalStatus)
    }

    @Test
    fun `memory can shrink between readings`() {
        val delta = deviceDelta(
            DeviceReading(null, null, pssKb = 2_000),
            DeviceReading(null, null, pssKb = 1_500),
        )
        assertEquals(-500L, delta.pssDeltaKb)
    }

    @Test
    fun `thermal labels cover the platform range and unknown values`() {
        assertEquals("normal", thermalStatusLabel(0))
        assertEquals("severo", thermalStatusLabel(3))
        assertEquals("apagado", thermalStatusLabel(6))
        assertEquals("?", thermalStatusLabel(null))
        assertEquals("?", thermalStatusLabel(42))
    }
}
