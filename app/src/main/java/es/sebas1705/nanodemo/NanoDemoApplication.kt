package es.sebas1705.nanodemo

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks2
import android.os.Bundle
import dagger.hilt.android.HiltAndroidApp
import es.sebas1705.nanodemo.core.common.Telemetry
import es.sebas1705.nanodemo.core.common.TelemetryEvents
import es.sebas1705.nanodemo.core.common.TelemetryParams
import javax.inject.Inject

/** Punto de entrada de Hilt para toda la app (necesario para que `@AndroidEntryPoint`/`@HiltViewModel` funcionen). */
@HiltAndroidApp
class NanoDemoApplication : Application() {

    @Inject
    lateinit var telemetry: Telemetry

    override fun onCreate() {
        super.onCreate() // Hilt inyecta los campos aquí, antes de que se ejecute el resto.
        // Cuanto antes: lo que ocurra antes de este punto no llega a Crashlytics ni a Analytics.
        telemetry.initialize()
        registerActivityLifecycleCallbacks(LifecycleBreadcrumbs())
    }

    /**
     * Android avisa cuando va justo de memoria. Es la mejor pista de por qué el sistema puede matar la app
     * mientras hay un modelo cargado (esas muertes no generan reporte de crash).
     */
    @Suppress("DEPRECATION") // Los niveles RUNNING_* están obsoletos desde API 34 pero siguen llegando.
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        val name = trimLevelName(level)
        telemetry.setContext("last_trim_level", name)
        telemetry.snapshotMemory()
        telemetry.breadcrumb("memoria: onTrimMemory($name)")
        // UI_HIDDEN salta cada vez que la app pasa a segundo plano: es ruido, no presión de memoria.
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW && level != ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            telemetry.event(TelemetryEvents.MEMORY_TRIM, TelemetryParams.LEVEL to name)
        }
    }

    @Suppress("DEPRECATION")
    private fun trimLevelName(level: Int): String = when (level) {
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> "running_moderate"
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> "running_low"
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> "running_critical"
        ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> "ui_hidden"
        ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> "background"
        ComponentCallbacks2.TRIM_MEMORY_MODERATE -> "moderate"
        ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> "complete"
        else -> "level_$level"
    }

    /** Migas de pan de primer/segundo plano: un crash muestra si ocurrió con la app visible o no. */
    private inner class LifecycleBreadcrumbs : ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) = telemetry.breadcrumb("ciclo: ${activity.localClassName} visible")
        override fun onActivityStopped(activity: Activity) = telemetry.breadcrumb("ciclo: ${activity.localClassName} oculta")
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}
