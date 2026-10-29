package es.sebas1705.nanodemo.domain.models

/**
 * Catálogo fijo de modelos `.task` que el Tier B (MediaPipe) puede cargar.
 * Cada uno se descarga y guarda por separado (nombre de archivo derivado de
 * [id]), así el usuario puede tener varios bajados a la vez y cambiar entre
 * ellos sin volver a descargar. La URL/token de cada uno viven en
 * `local.properties` -> `BuildConfig` de `data:mediapipe` (ver
 * `MediaPipeBackend.kt`) — subir el archivo real a cada URL sigue siendo un
 * paso manual del usuario (mismo mecanismo que el modelo original).
 */
enum class MediaPipeModelOption(val id: String, val displayName: String) {
    GEMMA_3_1B("gemma3-1b", "Gemma 3 1B"),
    GEMMA_2_2B("gemma2-2b", "Gemma 2 2B"),
}
