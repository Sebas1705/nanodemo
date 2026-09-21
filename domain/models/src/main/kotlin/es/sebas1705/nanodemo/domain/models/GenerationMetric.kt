package es.sebas1705.nanodemo.domain.models

/**
 * Una corrida de `generate()` en un tier, para el panel de estadísticas.
 * Vive solo en memoria (se pierde al cerrar la app) — no hace falta
 * persistencia para comparar tiers durante una sesión de demo.
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
)
