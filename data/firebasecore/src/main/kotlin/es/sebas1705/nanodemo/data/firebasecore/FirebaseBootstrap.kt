package es.sebas1705.nanodemo.data.firebasecore

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "NanoDemo.Firebase"

/**
 * Único punto que inicializa la `FirebaseApp` por defecto. Antes lo hacía el backend de IA de
 * forma perezosa, pero Crashlytics necesita que exista al arrancar la app (instala su manejador
 * de excepciones no capturadas al crearse), así que la inicialización pasó aquí y la llaman
 * tanto `Application.onCreate()` como el backend de Firebase AI. Es idempotente.
 */
@Singleton
class FirebaseBootstrap @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    /** ¿Están los tres valores de configuración? Sin ellos no se inicializa nada. */
    val isConfigured: Boolean
        get() = BuildConfig.FIREBASE_API_KEY.isNotBlank() &&
            BuildConfig.FIREBASE_APP_ID.isNotBlank() &&
            BuildConfig.FIREBASE_PROJECT_ID.isNotBlank()

    /**
     * Inicializa `FirebaseApp` si todavía no existe. Devuelve `true` si la app default está
     * disponible al terminar, `false` si falta configuración o la inicialización falló.
     */
    @Synchronized
    fun ensureInitialized(): Boolean {
        if (!isConfigured) {
            Log.w(TAG, "ensureInitialized: falta FIREBASE_API_KEY / FIREBASE_APP_ID / FIREBASE_PROJECT_ID")
            return false
        }
        if (FirebaseApp.getApps(context).isNotEmpty()) return true
        return try {
            Log.d(TAG, "ensureInitialized: inicializando FirebaseApp a mano")
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .build(),
            ) != null
        } catch (e: IllegalStateException) {
            Log.e(TAG, "ensureInitialized: falló la inicialización de FirebaseApp", e)
            false
        }
    }
}
