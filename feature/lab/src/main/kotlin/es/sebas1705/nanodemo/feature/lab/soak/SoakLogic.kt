package es.sebas1705.nanodemo.feature.lab.soak

import es.sebas1705.nanodemo.core.designsystem.tokensPerSecond
import es.sebas1705.nanodemo.domain.models.ChatMessage
import es.sebas1705.nanodemo.domain.models.GenerationConfig
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.Role
import es.sebas1705.nanodemo.feature.lab.trace.StepExecutor

private const val SOAK_PROMPT = "Escribe un párrafo de unas 80 palabras sobre cómo cambia el mar a lo largo del día."
private const val SOAK_MAX_TOKENS = 140

/** One generation of the sustained-load run: when it finished, how fast it was and what the device reported. */
data class SoakPoint(
    val elapsedSeconds: Float,
    val tokensPerSecond: Double?,
    val ttftMs: Long?,
    val thermalStatus: Int?,
    val batteryDrainUah: Long?,
)

/** Sustained-load verdict: speed at the start against the end, and how hot and how drained the device got. */
data class SoakSummary(
    val iterations: Int,
    val startTokensPerSecond: Double?,
    val endTokensPerSecond: Double?,
    /** Negative = slower at the end. `null` when there is not enough data to compare. */
    val changePercent: Double?,
    val maxThermalStatus: Int?,
    val totalBatteryDrainUah: Long?,
)

/** Needs at least this many timed generations to compare the first and last thirds. */
private const val MIN_POINTS_FOR_TREND = 3

fun summarizeSoak(points: List<SoakPoint>): SoakSummary {
    val speeds = points.mapNotNull { it.tokensPerSecond }
    val third = (speeds.size / 3).coerceAtLeast(1)
    val start = speeds.take(third).takeIf { speeds.size >= MIN_POINTS_FOR_TREND }?.average()
    val end = speeds.takeLast(third).takeIf { speeds.size >= MIN_POINTS_FOR_TREND }?.average()
    return SoakSummary(
        iterations = points.size,
        startTokensPerSecond = start,
        endTokensPerSecond = end,
        changePercent = if (start != null && end != null && start > 0) (end - start) / start * 100.0 else null,
        maxThermalStatus = points.mapNotNull { it.thermalStatus }.maxOrNull(),
        totalBatteryDrainUah = points.mapNotNull { it.batteryDrainUah }.takeIf { it.isNotEmpty() }?.sum(),
    )
}

/**
 * Generates back to back on one engine for `durationMs`, recording speed and device state after each one.
 * A phone that throttles when it heats up shows as a falling speed curve with a rising thermal status.
 * `clock` is injectable so tests control time.
 */
class SoakRunner(
    private val executor: StepExecutor,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun run(
        tier: InferenceTier,
        durationMs: Long,
        onPoint: (SoakPoint) -> Unit = {},
    ): List<SoakPoint> {
        val startedAt = clock()
        val points = mutableListOf<SoakPoint>()
        var consecutiveFailures = 0
        while (clock() - startedAt < durationMs && consecutiveFailures < MAX_CONSECUTIVE_FAILURES) {
            val result = executor.run(
                tier,
                listOf(ChatMessage(Role.USER, SOAK_PROMPT)),
                GenerationConfig(temperature = 0.7f, maxOutputTokens = SOAK_MAX_TOKENS),
            ) {}
            if (!result.ok) {
                consecutiveFailures++
                continue
            }
            consecutiveFailures = 0
            val metric = result.metric
            val point = SoakPoint(
                elapsedSeconds = (clock() - startedAt) / 1000f,
                tokensPerSecond = metric?.tokensPerSecond(),
                ttftMs = metric?.firstChunkLatencyMs,
                thermalStatus = metric?.thermalStatus,
                batteryDrainUah = metric?.batteryDrainUah,
            )
            points += point
            onPoint(point)
        }
        return points
    }

    companion object {
        /** An engine that keeps failing must not spin until the time is up. */
        const val MAX_CONSECUTIVE_FAILURES = 3
    }
}
