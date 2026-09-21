package es.sebas1705.nanodemo.data.crashlytics

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import es.sebas1705.nanodemo.core.common.ErrorReporter
import es.sebas1705.nanodemo.data.firebasecore.FirebaseBootstrap
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "NanoDemo.Crashlytics"

/**
 * `ErrorReporter` sobre Firebase Crashlytics.
 *
 * Decisiones (verificadas leyendo el bytecode de `firebase-crashlytics:20.1.1`):
 * - **Sin plugin de Gradle de Crashlytics ni google-services**: el SDK toma el app id de
 *   `FirebaseOptions.getApplicationId()`, así que la inicialización manual de
 *   `FirebaseBootstrap` le basta. El plugin solo aporta el *build id* que enlaza cada reporte con
 *   el mapping de R8 y los símbolos nativos; como este proyecto no ofusca (no hay `minify`), se
 *   desactiva la exigencia con la resource `RequireBuildId=false` (en `:app`). Sin esa resource
 *   el SDK lanza `IllegalStateException("The Crashlytics build ID is missing")` y **no arranca**.
 *   Si algún día se activa R8, hay que aplicar el plugin `com.google.firebase.crashlytics`.
 * - El SDK se registra con `eagerInDefaultApp`: arranca solo al existir la `FirebaseApp` por
 *   defecto, por eso `initialize()` se llama desde `Application.onCreate()`.
 * - Sin NDK: los crashes nativos (MediaPipe / LiteRT-LM son librerías C++) no se capturan;
 *   añadir `firebase-crashlytics-ndk` sería el siguiente paso.
 */
@Singleton
class CrashlyticsErrorReporter @Inject constructor(
    private val firebase: FirebaseBootstrap,
) : ErrorReporter {

    @Volatile
    private var crashlytics: FirebaseCrashlytics? = null

    override fun initialize() {
        if (crashlytics != null) return
        if (!firebase.ensureInitialized()) {
            Log.w(TAG, "initialize: Firebase sin configurar, Crashlytics desactivado")
            return
        }
        crashlytics = try {
            FirebaseCrashlytics.getInstance().also {
                it.setCrashlyticsCollectionEnabled(BuildConfig.CRASHLYTICS_ENABLED)
            }
        } catch (e: IllegalStateException) {
            Log.e(TAG, "initialize: el componente de Crashlytics no está disponible", e)
            null
        }
        Log.i(TAG, "initialize: listo=${crashlytics != null} recolección=${BuildConfig.CRASHLYTICS_ENABLED}")
    }

    override fun log(message: String) {
        crashlytics?.log(message)
    }

    override fun setKey(key: String, value: String) {
        crashlytics?.setCustomKey(key, value)
    }

    override fun recordNonFatal(throwable: Throwable, context: String?) {
        val reporter = crashlytics ?: return
        if (context != null) reporter.log("error en $context")
        reporter.recordException(throwable)
    }
}
