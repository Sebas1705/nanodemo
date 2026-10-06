package es.sebas1705.nanodemo.domain.models

/**
 * Una corrida de `generate()` en un tier, para el panel de estadísticas. Se persiste en SQLite vía
 * Room (`data:history`, tabla `metrics`) además de mantenerse en memoria — sobrevive a que el
 * sistema mate el proceso, no solo a la sesión actual.
 */
data class GenerationMetric(
    val tier: InferenceTier,
    val startedAtMs: Long,
    val firstChunkLatencyMs: Long?,
    val totalDurationMs: Long,
    val promptChars: Int,
    val responseChars: Int,
    val success: Boolean,
    /** Tokens reales si el backend los expone (MediaPipe/Cloud); aproximado (chars/4) si no. */
    val responseTokens: Int,
    val responseTokensExact: Boolean,
    /**
     * `Runtime.getRuntime()` heap usado, después menos antes de generar.
     * Aproximado — no es un profiler real, solo una señal relativa entre
     * tiers, no una medición absoluta de consumo.
     */
    val memoryDeltaBytes: Long,
    /**
     * Tiempo de cargar el motor/sesión del modelo en ESTE turno (solo MediaPipe y LiteRT-LM, y solo el
     * primero tras abrir la app o cambiar de tier); `null` si no hubo carga. Ya está RESTADO de
     * [firstChunkLatencyMs] y [totalDurationMs]: así comparar velocidades no mezcla "cargar el modelo" con
     * "generar". Las corridas guardadas antes de existir este campo quedan en `null`.
     */
    val loadTimeMs: Long? = null,
)
