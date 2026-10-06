package es.sebas1705.nanodemo.data.repositories.repos

internal data class RunTiming(val firstChunkLatencyMs: Long?, val totalDurationMs: Long)

/**
 * Descuenta el tiempo de cargar el modelo (si lo hubo) de los tiempos de una corrida, para que la latencia
 * y la velocidad sean de generación pura. Nunca da negativos: el reloj de la carga y el de la corrida son
 * distintos y pueden diferir unos milisegundos.
 */
internal fun excludeLoadTime(firstChunkLatencyMs: Long?, totalDurationMs: Long, loadMs: Long?): RunTiming {
    if (loadMs == null) return RunTiming(firstChunkLatencyMs, totalDurationMs)
    return RunTiming(
        firstChunkLatencyMs = firstChunkLatencyMs?.minus(loadMs)?.coerceAtLeast(0),
        totalDurationMs = (totalDurationMs - loadMs).coerceAtLeast(0),
    )
}
