package es.sebas1705.nanodemo.data.analytics

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.hilt.android.qualifiers.ApplicationContext
import es.sebas1705.nanodemo.core.common.AnalyticsTracker
import es.sebas1705.nanodemo.data.firebasecore.FirebaseBootstrap
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "NanoDemo.Analytics"

// Límites de Firebase Analytics: nombres de evento/parámetro ≤ 40 caracteres (letras, dígitos y
// `_`, empezando por letra), valores de texto ≤ 100, hasta 25 parámetros por evento, propiedades
// de usuario con nombre ≤ 24 y valor ≤ 36. Los prefijos `firebase_`, `google_` y `ga_` están
// reservados. Un evento que se pasa de estos límites se descarta sin avisar, por eso se normaliza.
private const val MAX_NAME_LENGTH = 40
private const val MAX_PARAMS = 25
private const val MAX_VALUE_LENGTH = 100
private const val MAX_USER_PROPERTY_NAME = 24
private const val MAX_USER_PROPERTY_VALUE = 36
private val RESERVED_PREFIXES = listOf("firebase_", "google_", "ga_")

/**
 * `AnalyticsTracker` sobre Firebase Analytics.
 *
 * - Analytics **no** lee `FirebaseOptions`: toma el app id de la resource `google_app_id`
 *   (verificado en el bytecode de `play-services-measurement`). `:app` la genera en el build desde
 *   `FIREBASE_APP_ID`; sin ella Analytics queda desactivado.
 * - Sin ID de publicidad ni SSAID: el manifest de `:app` los desactiva y quita el permiso.
 */
@Singleton
class FirebaseAnalyticsTracker @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val firebase: FirebaseBootstrap,
) : AnalyticsTracker {

    @Volatile
    private var analytics: FirebaseAnalytics? = null

    override fun initialize() {
        if (analytics != null) return
        if (!firebase.ensureInitialized()) {
            Log.w(TAG, "initialize: Firebase sin configurar, Analytics desactivado")
            return
        }
        analytics = try {
            FirebaseAnalytics.getInstance(context).also {
                it.setAnalyticsCollectionEnabled(BuildConfig.ANALYTICS_ENABLED)
            }
        } catch (e: IllegalStateException) {
            Log.e(TAG, "initialize: Analytics no está disponible", e)
            null
        }
        Log.i(TAG, "initialize: listo=${analytics != null} recolección=${BuildConfig.ANALYTICS_ENABLED}")
    }

    override fun logEvent(name: String, params: Map<String, Any>) {
        analytics?.logEvent(sanitizeName(name), params.toBundle())
    }

    override fun logScreen(screenName: String) {
        analytics?.logEvent(
            FirebaseAnalytics.Event.SCREEN_VIEW,
            Bundle().apply { putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName.take(MAX_VALUE_LENGTH)) },
        )
    }

    override fun setUserProperty(name: String, value: String?) {
        analytics?.setUserProperty(
            sanitizeName(name).take(MAX_USER_PROPERTY_NAME),
            value?.take(MAX_USER_PROPERTY_VALUE),
        )
    }

    override fun setUserId(id: String?) {
        analytics?.setUserId(id)
    }

    private fun Map<String, Any>.toBundle(): Bundle = Bundle().also { bundle ->
        entries.take(MAX_PARAMS).forEach { (rawKey, value) ->
            val key = sanitizeName(rawKey)
            when (value) {
                is Int -> bundle.putLong(key, value.toLong())
                is Long -> bundle.putLong(key, value)
                is Float -> bundle.putDouble(key, value.toDouble())
                is Double -> bundle.putDouble(key, value)
                else -> bundle.putString(key, value.toString().take(MAX_VALUE_LENGTH))
            }
        }
    }

    private fun sanitizeName(raw: String): String {
        var name = raw.lowercase().replace(Regex("[^a-z0-9_]"), "_")
        if (name.isEmpty() || !name[0].isLetter()) name = "p_$name"
        if (RESERVED_PREFIXES.any { name.startsWith(it) }) name = "app_$name"
        return name.take(MAX_NAME_LENGTH)
    }
}
