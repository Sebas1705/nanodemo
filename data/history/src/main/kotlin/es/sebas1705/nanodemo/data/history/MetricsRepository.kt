package es.sebas1705.nanodemo.data.history

import es.sebas1705.nanodemo.domain.models.GenerationMetric
import es.sebas1705.nanodemo.domain.models.InferenceTier
import javax.inject.Inject
import javax.inject.Singleton

/** Tope de corridas guardadas — de sobra para comparar tiers en un demo, sin crecer sin límite. */
private const val MAX_METRICS_ROWS = 2_000

@Singleton
class MetricsRepository @Inject constructor(
    private val dao: MetricDao,
) : IMetricsRepository {

    override suspend fun loadAll(): List<GenerationMetric> = dao.getAll().map { it.toDomain() }

    override suspend fun record(metric: GenerationMetric) {
        dao.insert(metric.toEntity())
        dao.pruneOlderThan(MAX_METRICS_ROWS)
    }
}

private fun MetricEntity.toDomain() = GenerationMetric(
    tier = InferenceTier.valueOf(tier),
    startedAtMs = startedAtMs,
    firstChunkLatencyMs = firstChunkLatencyMs,
    totalDurationMs = totalDurationMs,
    promptChars = promptChars,
    responseChars = responseChars,
    success = success,
    responseTokens = responseTokens,
    responseTokensExact = responseTokensExact,
    memoryDeltaBytes = memoryDeltaBytes,
)

private fun GenerationMetric.toEntity() = MetricEntity(
    tier = tier.name,
    startedAtMs = startedAtMs,
    firstChunkLatencyMs = firstChunkLatencyMs,
    totalDurationMs = totalDurationMs,
    promptChars = promptChars,
    responseChars = responseChars,
    success = success,
    responseTokens = responseTokens,
    responseTokensExact = responseTokensExact,
    memoryDeltaBytes = memoryDeltaBytes,
)
