package es.sebas1705.nanodemo.core.common

import android.content.Context
import android.os.BatteryManager
import android.os.Debug
import android.os.PowerManager

/**
 * A point-in-time reading of the device resources a generation consumes. Every field is nullable: not every
 * device exposes each counter, and a missing value must stay "unknown" instead of turning into a fake zero.
 *
 * - `batteryChargeUah`: remaining battery charge in microampere-hours (`CHARGE_COUNTER`). Coarse, it only
 *   changes every so often, so a single short run usually shows 0; it becomes meaningful summed over many runs.
 * - `thermalStatus`: `PowerManager.THERMAL_STATUS_*` (0 none ... 6 shutdown).
 * - `pssKb`: proportional set size of this process, which includes the native memory a loaded model uses
 *   (the JVM heap alone barely moves for the on-device engines).
 */
data class DeviceReading(
    val batteryChargeUah: Long?,
    val thermalStatus: Int?,
    val pssKb: Long?,
)

/** What changed between two readings. `batteryDrainUah` is positive when the battery lost charge. */
data class DeviceDelta(
    val batteryDrainUah: Long?,
    val thermalStatus: Int?,
    val pssDeltaKb: Long?,
)

fun deviceDelta(before: DeviceReading, after: DeviceReading): DeviceDelta = DeviceDelta(
    batteryDrainUah = if (before.batteryChargeUah != null && after.batteryChargeUah != null) {
        before.batteryChargeUah - after.batteryChargeUah
    } else {
        null
    },
    thermalStatus = after.thermalStatus,
    pssDeltaKb = if (before.pssKb != null && after.pssKb != null) after.pssKb - before.pssKb else null,
)

/** Short Spanish name of a `PowerManager.THERMAL_STATUS_*` value, for the UI. */
fun thermalStatusLabel(status: Int?): String = when (status) {
    null -> "?"
    0 -> "normal"
    1 -> "leve"
    2 -> "moderado"
    3 -> "severo"
    4 -> "crítico"
    5 -> "emergencia"
    6 -> "apagado"
    else -> "?"
}

/** Reads the counters from the system. Cheap enough to call before and after every generation. */
class DeviceProbe(private val context: Context) {

    fun read(): DeviceReading = DeviceReading(
        batteryChargeUah = batteryCharge(),
        thermalStatus = thermal(),
        pssKb = pss(),
    )

    private fun batteryCharge(): Long? = runCatching {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return null
        val value = manager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        // Unsupported devices report Long.MIN_VALUE; some report 0 for "not available".
        value.takeIf { it != Long.MIN_VALUE && it > 0L }
    }.getOrNull()

    private fun thermal(): Int? = runCatching {
        (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.currentThermalStatus
    }.getOrNull()

    private fun pss(): Long? = runCatching {
        val info = Debug.MemoryInfo()
        Debug.getMemoryInfo(info)
        info.totalPss.toLong()
    }.getOrNull()
}
