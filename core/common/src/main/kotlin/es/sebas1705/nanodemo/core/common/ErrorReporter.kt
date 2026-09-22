package es.sebas1705.nanodemo.core.common

/**
 * Contrato para reportar errores y contexto de diagnóstico, sin que quien lo usa conozca el
 * proveedor (hoy Firebase Crashlytics, en `data:crashlytics`). Vive en `core:common` para que
 * cualquier capa pueda inyectarlo sin depender de Firebase. En la práctica se usa a través de
 * [Telemetry], que además lo combina con [AnalyticsTracker].
 *
 * Todas las operaciones son seguras de llamar en cualquier momento: si el proveedor no está
 * configurado o no ha arrancado, no hacen nada en vez de lanzar.
 *
 * Privacidad: no mandes contenido del usuario (prompts, respuestas, URIs de imágenes). Los
 * mensajes y claves son para estado técnico (tier, modelo, fase), no para datos personales.
 */
interface ErrorReporter {

    /** Arranca el proveedor. Idempotente; se llama una vez desde `Application.onCreate()`. */
    fun initialize()

    /** Añade una línea al registro que acompaña al siguiente crash o error no fatal (miga de pan). */
    fun log(message: String)

    /** Fija un par clave/valor que se adjunta a todos los reportes posteriores (p. ej. el tier activo). */
    fun setKey(key: String, value: String)

    /** Igual que [setKey] para varios pares a la vez. */
    fun setKeys(keys: Map<String, String>) = keys.forEach { (key, value) -> setKey(key, value) }

    /**
     * Identificador **anónimo** de la instalación (no es un dato personal), para agrupar los
     * reportes de un mismo dispositivo y cruzarlos con los eventos de analytics.
     */
    fun setUserId(id: String)

    /**
     * Reporta un error **manejado** (la app sigue viva): aparece en la consola como no fatal.
     * `context` describe qué se estaba haciendo (p. ej. `"generate"`) y `attributes` añade detalle
     * técnico que solo se adjunta a este reporte (como líneas del registro, no como claves).
     */
    fun recordNonFatal(throwable: Throwable, context: String? = null, attributes: Map<String, String> = emptyMap())
}
