package es.sebas1705.nanodemo.core.common

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Claves del contexto que **toda** app del template adjunta a sus reportes de errores y (las
 * marcadas) a sus eventos de analytics como propiedades de usuario. Mantener los mismos nombres
 * en todas las apps permite reutilizar los mismos filtros y consultas de BigQuery/Crashlytics.
 */
object TelemetryContextKeys {
    const val APP_ID = "app_id"
    const val APP_VERSION = "app_version"
    const val VERSION_CODE = "version_code"
    const val BUILD_TYPE = "build_type"
    const val INSTALLER = "installer"
    const val DEVICE = "device"
    const val ANDROID_SDK = "android_sdk"
    const val ABI = "abi"
    const val RAM_BUCKET = "ram_bucket"
    const val LOCALE = "locale"

    /**
     * Subconjunto que también se manda como propiedad de usuario de analytics. Firebase limita
     * a 25 propiedades (nombre ≤ 24 caracteres, valor ≤ 36) y ya recoge por su cuenta la
     * versión de la app, el modelo y el sistema, así que solo va lo que no trae de serie.
     */
    val USER_PROPERTIES = setOf(BUILD_TYPE, INSTALLER, RAM_BUCKET, ABI)
}

/** Foto del entorno actual: app, build, dispositivo. Sin datos personales. */
@Suppress("DEPRECATION") // getPackageInfo(String, int): la variante con flags tipados exige API 33 y minSdk es 31.
fun collectTelemetryContext(context: Context): Map<String, String> {
    val packageManager = context.packageManager
    val packageInfo = runCatching { packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val installer = runCatching {
        packageManager.getInstallSourceInfo(context.packageName).installingPackageName
    }.getOrNull()

    return mapOf(
        TelemetryContextKeys.APP_ID to context.packageName,
        TelemetryContextKeys.APP_VERSION to (packageInfo?.versionName ?: "unknown"),
        TelemetryContextKeys.VERSION_CODE to (packageInfo?.longVersionCode?.toString() ?: "unknown"),
        TelemetryContextKeys.BUILD_TYPE to if (debuggable) "debug" else "release",
        // null = instalada por adb / Android Studio (sin tienda ni distribuidor).
        TelemetryContextKeys.INSTALLER to (installer ?: "sideload"),
        TelemetryContextKeys.DEVICE to "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
        TelemetryContextKeys.ANDROID_SDK to Build.VERSION.SDK_INT.toString(),
        TelemetryContextKeys.ABI to (Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"),
        TelemetryContextKeys.RAM_BUCKET to ramBucket(context),
        TelemetryContextKeys.LOCALE to Locale.getDefault().toLanguageTag(),
    )
}

/**
 * RAM en un cubo de baja cardinalidad (4/6/8/12/16 GB…): `totalMem` informa algo menos que la
 * nominal, así que se redondea al entero más cercano y se agrupa. Sirve para cruzar fallos de
 * los modelos locales con la memoria del dispositivo.
 */
private fun ramBucket(context: Context): String {
    val info = ActivityManager.MemoryInfo()
    (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
    val gb = (info.totalMem / (1024.0 * 1024 * 1024)).roundToInt()
    return when {
        gb <= 3 -> "<=3gb"
        gb <= 4 -> "4gb"
        gb <= 6 -> "6gb"
        gb <= 8 -> "8gb"
        gb <= 12 -> "12gb"
        else -> ">=16gb"
    }
}

private const val PREFS_NAME = "telemetry"
private const val KEY_INSTALL_ID = "install_id"

/**
 * UUID aleatorio generado en la primera ejecución y guardado en las preferencias de la app. No
 * es un identificador del dispositivo ni de la persona (se borra al desinstalar): solo permite
 * seguir una misma instalación entre analytics y crashes.
 */
fun installId(context: Context): String {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getString(KEY_INSTALL_ID, null) ?: UUID.randomUUID().toString().also {
        prefs.edit().putString(KEY_INSTALL_ID, it).apply()
    }
}
