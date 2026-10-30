package es.sebas1705.nanodemo.domain.models

/** Título por defecto que se guardaba antes en la base; ahora el título vacío es "sin título". */
private const val LEGACY_DEFAULT_TITLE = "Conversación"

/**
 * Resumen de una conversación guardada en el historial local (pantalla
 * Historial) — no incluye los mensajes, solo lo necesario para listarla.
 */
data class Conversation(
    val id: Long,
    val tier: InferenceTier,
    val title: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val messageCount: Int,
) {
    /** Sin título propio: la UI muestra el título por defecto en su idioma, también en las filas ya guardadas. */
    val isUntitled: Boolean
        get() = title.isBlank() || title == LEGACY_DEFAULT_TITLE
}
