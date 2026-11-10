package es.sebas1705.nanodemo.feature.lab.agent.tools

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Reads the live device state from system services. Every reading is guarded: a missing one is `null`, never a crash. */
class AndroidDeviceInfoProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : DeviceInfoProvider {

    override fun snapshot(): DeviceSnapshot {
        val battery = runCatching { readBattery() }.getOrNull()
        val memory = runCatching { readMemory() }.getOrNull()
        val storage = runCatching { readStorage() }.getOrNull()
        return DeviceSnapshot(
            batteryPercent = battery?.first,
            charging = battery?.second,
            network = runCatching { readNetwork() }.getOrDefault(NetworkKind.UNKNOWN),
            freeRamMb = memory?.first,
            totalRamMb = memory?.second,
            freeStorageMb = storage?.first,
            totalStorageMb = storage?.second,
        )
    }

    private fun readBattery(): Pair<Int, Boolean>? {
        // ACTION_BATTERY_CHANGED is sticky: registering a null receiver returns the last broadcast.
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL ||
            plugged != 0
        return (level * 100 / scale) to charging
    }

    private fun readNetwork(): NetworkKind {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return NetworkKind.UNKNOWN
        val network = manager.activeNetwork ?: return NetworkKind.NONE
        val caps = manager.getNetworkCapabilities(network) ?: return NetworkKind.NONE
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkKind.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkKind.CELLULAR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkKind.ETHERNET
            else -> NetworkKind.OTHER
        }
    }

    private fun readMemory(): Pair<Long, Long>? {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return null
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        return (info.availMem / BYTES_PER_MB) to (info.totalMem / BYTES_PER_MB)
    }

    private fun readStorage(): Pair<Long, Long> {
        val stat = StatFs(context.filesDir.path)
        return (stat.availableBytes / BYTES_PER_MB) to (stat.totalBytes / BYTES_PER_MB)
    }

    private companion object {
        const val BYTES_PER_MB = 1024L * 1024L
    }
}

/** Notes in a private SharedPreferences file, so they survive the process (that is the point of the notes tool). */
class PrefsNotesStore @Inject constructor(
    @ApplicationContext context: Context,
) : NotesStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized override fun all(): List<String> =
        prefs.getString(KEY_NOTES, null)?.split(SEPARATOR)?.filter { it.isNotEmpty() }.orEmpty()

    @Synchronized override fun add(text: String): Int {
        val notes = (all() + text.replace(SEPARATOR, " ")).takeLast(NotesStore.MAX_NOTES)
        prefs.edit().putString(KEY_NOTES, notes.joinToString(SEPARATOR)).apply()
        return notes.size
    }

    @Synchronized override fun clear() {
        prefs.edit().remove(KEY_NOTES).apply()
    }

    private companion object {
        const val PREFS_NAME = "lab_agent_notes"
        const val KEY_NOTES = "notes"
        const val SEPARATOR = "\u001F" // unit separator: cannot appear in a note typed by a model or a user
    }
}
