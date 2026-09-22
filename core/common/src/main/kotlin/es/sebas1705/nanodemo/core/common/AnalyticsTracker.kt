package es.sebas1705.nanodemo.core.common

/**
 * Contrato de analytics, independiente del proveedor (hoy Firebase Analytics, en
 * `data:analytics`). Igual que [ErrorReporter], todas las operaciones son seguras aunque el
 * proveedor no esté configurado. En la práctica se usa a través de [Telemetry].
 *
 * Los valores de `params` pueden ser `String`, `Int`, `Long`, `Float`, `Double` o `Boolean`;
 * cualquier otro tipo se convierte con `toString()`. El proveedor aplica sus propios límites
 * de nombres y longitudes (ver `docs/TELEMETRY.md`).
 */
interface AnalyticsTracker {

    /** Arranca el proveedor. Idempotente; se llama una vez desde `Application.onCreate()`. */
    fun initialize()

    fun logEvent(name: String, params: Map<String, Any> = emptyMap())

    /** Vista de pantalla (evento estándar `screen_view`). */
    fun logScreen(screenName: String)

    /** Propiedad que se adjunta a **todos** los eventos de este usuario/instalación. `null` la borra. */
    fun setUserProperty(name: String, value: String?)

    /** Identificador anónimo de la instalación; el mismo que recibe [ErrorReporter.setUserId]. */
    fun setUserId(id: String?)
}
