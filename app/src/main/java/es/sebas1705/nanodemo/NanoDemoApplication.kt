package es.sebas1705.nanodemo

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import es.sebas1705.nanodemo.core.common.ErrorReporter
import javax.inject.Inject

/** Punto de entrada de Hilt para toda la app (necesario para que `@AndroidEntryPoint`/`@HiltViewModel` funcionen). */
@HiltAndroidApp
class NanoDemoApplication : Application() {

    @Inject
    lateinit var errorReporter: ErrorReporter

    override fun onCreate() {
        super.onCreate() // Hilt inyecta los campos aquí, antes de que se ejecute el resto.
        // Cuanto antes: Crashlytics solo captura lo que pasa después de este punto.
        errorReporter.initialize()
    }
}
