package es.sebas1705.nanodemo.feature.compare

import es.sebas1705.nanodemo.core.designsystem.tokensPerSecond
import es.sebas1705.nanodemo.domain.models.GenerationMetric
import es.sebas1705.nanodemo.domain.models.InferenceTier

internal enum class CompareBadge { FASTEST, HIGHEST_SPEED }

/**
 * Las métricas de ESTA ronda de Comparar: la primera corrida exitosa de cada motor desde que arrancó.
 * `metrics` es global (mezcla Chat y rondas anteriores), así que no sirve "la última de cada tier": si después
 * de comparar se usa Chat con ese motor, la última ya sería otra. La primera posterior al arranque es la de la
 * ronda, porque Comparar lanza un solo pedido por motor.
 */
internal fun metricsForRun(metrics: List<GenerationMetric>, runStartedAtMs: Long): Map<InferenceTier, GenerationMetric> =
    metrics.filter { it.success && it.startedAtMs >= runStartedAtMs }
        .groupBy { it.tier }
        .mapValues { (_, runs) -> runs.first() }

/**
 * Quién fue más rápido (menor tiempo total) y quién generó más tokens por segundo. Solo con 2 o más motores con
 * dato (con uno solo no hay con qué comparar); un empate da la insignia a todos los empatados.
 */
internal fun compareBadges(metrics: Map<InferenceTier, GenerationMetric>): Map<InferenceTier, Set<CompareBadge>> {
    if (metrics.size < 2) return emptyMap()
    val result = mutableMapOf<InferenceTier, MutableSet<CompareBadge>>()

    val fastest = metrics.minOf { it.value.totalDurationMs }
    metrics.filterValues { it.totalDurationMs == fastest }.keys
        .forEach { result.getOrPut(it) { mutableSetOf() }.add(CompareBadge.FASTEST) }

    val speeds = metrics.mapNotNull { (tier, metric) -> metric.tokensPerSecond()?.let { tier to it } }
    if (speeds.size >= 2) {
        val best = speeds.maxOf { it.second }
        speeds.filter { it.second == best }
            .forEach { (tier, _) -> result.getOrPut(tier) { mutableSetOf() }.add(CompareBadge.HIGHEST_SPEED) }
    }
    return result
}
