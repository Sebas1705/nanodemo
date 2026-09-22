package es.sebas1705.nanodemo.core.common

import android.app.ActivityManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Eventos **genéricos** que cualquier app del template usa con el mismo nombre y los mismos
 * parámetros, para que un mismo dashboard sirva a todas. Los eventos propios de una app
 * (p. ej. `tier_selected` en nanodemo) se definen en su propio módulo, pero reutilizando los
 * parámetros de [TelemetryParams] siempre que encajen. Esquema completo: `docs/TELEMETRY.md`.
 */
object TelemetryEvents {
    /** Se usó una función concreta. Parámetro: [TelemetryParams.FEATURE]. */
    const val FEATURE_USED = "feature_used"

    /** Terminó una operación (éxito, fallo o cancelación). Ver [Telemetry.operation]. */
    const val OPERATION_RESULT = "operation_result"

    /** El sistema pidió liberar memoria. Parámetro: [TelemetryParams.LEVEL]. */
    const val MEMORY_TRIM = "memory_trim"
}

object TelemetryParams {
    const val FEATURE = "feature"
    const val OPERATION = "operation"
    const val RESULT = "result"
    const val DURATION_MS = "duration_ms"
    const val ERROR_TYPE = "error_type"
    const val ERROR_MESSAGE = "error_message"
    const val LEVEL = "level"
}

object TelemetryResult {
    const val SUCCESS = "success"
    const val FAILURE = "failure"
    const val CANCELLED = "cancelled"
}

private const val MAX_BREADCRUMB_CHARS = 300
private const val MAX_ERROR_MESSAGE_CHARS = 100

/**
 * Punto único de observabilidad de la app: une [AnalyticsTracker] (qué hacen los usuarios) y
 * [ErrorReporter] (qué falla). Todo evento de analytics deja además una **miga de pan** en el
 * registro de Crashlytics, así que un crash o error no fatal muestra las últimas acciones antes
 * de ocurrir. Los proveedores concretos se enlazan por Hilt (`data:analytics`, `data:crashlytics`);
 * si no están configurados, todo esto no hace nada.
 *
 * Privacidad: nunca pases contenido del usuario (prompts, respuestas, rutas de archivos) como
 * parámetro; solo estado técnico y contadores.
 */
@Singleton
class Telemetry @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val errors: ErrorReporter,
    private val analytics: AnalyticsTracker,
) {

    /** Arranca ambos proveedores y les adjunta el contexto común. Se llama al inicio de la app. */
    fun initialize() {
        errors.initialize()
        analytics.initialize()

        val installId = installId(context)
        errors.setUserId(installId)
        analytics.setUserId(installId)

        val appContext = collectTelemetryContext(context)
        errors.setKeys(appContext)
        appContext
            .filterKeys { it in TelemetryContextKeys.USER_PROPERTIES }
            .forEach { (name, value) -> analytics.setUserProperty(name, value) }

        errors.log("app: iniciada ${appContext.formatForLog()}")
    }

    /** Evento de analytics + miga de pan. Los parámetros nulos se omiten. */
    fun event(name: String, vararg params: Pair<String, Any?>) {
        val clean = params.filter { it.second != null }.associate { it.first to it.second!! }
        analytics.logEvent(name, clean)
        errors.log("evento $name ${clean.formatForLog()}".trim().take(MAX_BREADCRUMB_CHARS))
    }

    /** El usuario usó una función (botón, modo, ajuste). */
    fun feature(feature: String, vararg params: Pair<String, Any?>) {
        event(TelemetryEvents.FEATURE_USED, TelemetryParams.FEATURE to feature, *params)
    }

    /** Vista de pantalla; además queda como clave `screen` en los reportes de errores. */
    fun screen(name: String) {
        analytics.logScreen(name)
        errors.setKey("screen", name)
        errors.log("pantalla $name")
    }

    /** Solo miga de pan para diagnóstico (no genera evento de analytics). */
    fun breadcrumb(message: String) {
        errors.log(message.take(MAX_BREADCRUMB_CHARS))
    }

    /** Estado que acompañará a todos los reportes de errores posteriores (no a analytics). */
    fun setContext(key: String, value: String) {
        errors.setKey(key, value)
    }

    /** Resultado de una operación con nombre: éxito, fallo o cancelación, con su duración. */
    fun operation(
        operation: String,
        result: String,
        durationMs: Long? = null,
        vararg params: Pair<String, Any?>,
    ) {
        event(
            TelemetryEvents.OPERATION_RESULT,
            TelemetryParams.OPERATION to operation,
            TelemetryParams.RESULT to result,
            TelemetryParams.DURATION_MS to durationMs,
            *params,
        )
    }

    /**
     * Una operación falló pero la app sigue viva: reporta un error **no fatal** a Crashlytics
     * (con el detalle técnico como atributos) y el resultado `failure` a analytics.
     */
    fun failure(
        operation: String,
        throwable: Throwable,
        durationMs: Long? = null,
        vararg params: Pair<String, Any?>,
    ) {
        val attributes = params.filter { it.second != null }.associate { it.first to it.second.toString() }
        errors.recordNonFatal(throwable, operation, attributes)
        this.operation(
            operation,
            TelemetryResult.FAILURE,
            durationMs,
            TelemetryParams.ERROR_TYPE to throwable.javaClass.simpleName,
            TelemetryParams.ERROR_MESSAGE to throwable.message?.take(MAX_ERROR_MESSAGE_CHARS),
            *params,
        )
    }

    /** Guarda el estado de memoria como claves de Crashlytics: clave para entender cierres por falta de RAM. */
    fun snapshotMemory() {
        val info = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
        val runtime = Runtime.getRuntime()
        errors.setKeys(
            mapOf(
                "avail_mem_mb" to (info.availMem / BYTES_PER_MB).toString(),
                "low_memory" to info.lowMemory.toString(),
                "java_heap_mb" to ((runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MB).toString(),
            )
        )
    }

    private fun Map<String, Any>.formatForLog(): String = entries.joinToString(" ") { "${it.key}=${it.value}" }

    private companion object {
        const val BYTES_PER_MB = 1024L * 1024
    }
}
