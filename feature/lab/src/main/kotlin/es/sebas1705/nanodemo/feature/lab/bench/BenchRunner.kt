package es.sebas1705.nanodemo.feature.lab.bench

import es.sebas1705.nanodemo.core.designsystem.tokensPerSecond
import es.sebas1705.nanodemo.domain.models.ChatMessage
import es.sebas1705.nanodemo.domain.models.GenerationConfig
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.Role
import es.sebas1705.nanodemo.domain.models.StepResult
import es.sebas1705.nanodemo.feature.lab.trace.StepExecutor
import kotlinx.coroutines.withTimeoutOrNull

private const val ANSWER_KEEP_CHARS = 300

/**
 * Runs a benchmark session: every selected engine answers every task of the chosen categories, `repetitions`
 * times. Runs are sequential (one engine at a time, one request at a time) so timings are not disturbed by
 * parallel load. A failure or timeout on one task never stops the session; it is recorded as an error sample.
 */
class BenchRunner(
    private val executor: StepExecutor,
    private val supportsJson: (InferenceTier) -> Boolean,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    suspend fun run(
        config: BenchConfig,
        onProgress: (BenchProgress) -> Unit = {},
        onSample: (BenchSample) -> Unit = {},
    ): List<BenchSample> {
        val tasks = BenchTasks.byCategories(config.categories)
        val total = config.tiers.size * tasks.size * config.repetitions
        val samples = mutableListOf<BenchSample>()
        var done = 0
        for (tier in config.tiers) {
            for (task in tasks) {
                for (rep in 1..config.repetitions) {
                    onProgress(BenchProgress(done, total, tier, task.title))
                    val sample = runOne(tier, task, rep, config)
                    samples += sample
                    onSample(sample)
                    done++
                }
            }
        }
        onProgress(BenchProgress(done, total, null, null))
        return samples
    }

    private suspend fun runOne(tier: InferenceTier, task: BenchTask, rep: Int, config: BenchConfig): BenchSample {
        val generation = GenerationConfig(
            temperature = config.temperature,
            maxOutputTokens = task.maxTokens,
            jsonMode = task.jsonMode && supportsJson(tier),
        )
        val result = withTimeoutOrNull(timeoutMs) {
            executor.run(tier, listOf(ChatMessage(Role.USER, task.prompt)), generation) {}
        }
        return when {
            result == null -> errorSample(tier, task, rep, "timeout after ${timeoutMs / 1000}s")
            !result.ok -> errorSample(tier, task, rep, result.error ?: "error")
            else -> sampleOf(tier, task, rep, result)
        }
    }

    private fun sampleOf(tier: InferenceTier, task: BenchTask, rep: Int, result: StepResult): BenchSample {
        val metric = result.metric
        return BenchSample(
            tier = tier,
            taskId = task.id,
            category = task.category,
            rep = rep,
            score = task.checker.score(result.text),
            latencyMs = metric?.totalDurationMs ?: 0,
            ttftMs = metric?.firstChunkLatencyMs,
            tokens = metric?.responseTokens ?: 0,
            tokensPerSecond = metric?.tokensPerSecond(),
            loadMs = metric?.loadTimeMs,
            thermalStatus = metric?.thermalStatus,
            batteryDrainUah = metric?.batteryDrainUah,
            pssDeltaKb = metric?.pssDeltaKb,
            answer = result.text.take(ANSWER_KEEP_CHARS),
        )
    }

    private fun errorSample(tier: InferenceTier, task: BenchTask, rep: Int, message: String) =
        BenchSample(tier = tier, taskId = task.id, category = task.category, rep = rep, score = 0.0, error = message)

    companion object {
        const val DEFAULT_TIMEOUT_MS = 120_000L
    }
}
