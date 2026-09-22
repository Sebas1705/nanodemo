package es.sebas1705.nanodemo.data.history

import es.sebas1705.nanodemo.domain.models.GenerationMetric

/** Historial local de corridas de `generate()` (SQLite vía Room) — ver [MetricEntity]. */
interface IMetricsRepository {

    /** Todas las corridas guardadas, en orden cronológico. Se usa una vez al arrancar `InferenceRepository`. */
    suspend fun loadAll(): List<GenerationMetric>

    suspend fun record(metric: GenerationMetric)
}
