package es.sebas1705.nanodemo.core.common

/**
 * Contrato para reportar errores y contexto de diagnóstico, sin que quien lo usa conozca el
 * proveedor (hoy Firebase Crashlytics, en `data:crashlytics`). Vive en `core:common` para que
 * cualquier capa (repositorio, ViewModel) pueda inyectarlo sin depender de Firebase.
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

    /** Añade una línea al registro que acompaña al siguiente crash o error no fatal. */
    fun log(message: String)

    /** Fija un par clave/valor que se adjunta a todos los reportes posteriores (p. ej. el tier activo). */
    fun setKey(key: String, value: String)

    /**
     * Reporta un error **manejado** (la app sigue viva): aparece en la consola como no fatal.
     * `context` describe qué se estaba haciendo (p. ej. `"generate(FIREBASE_AI)"`).
     */
    fun recordNonFatal(throwable: Throwable, context: String? = null)
}
