package es.sebas1705.nanodemo.feature.lab.bench

import es.sebas1705.nanodemo.domain.models.GenerationMetric
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.StepResult
import es.sebas1705.nanodemo.feature.lab.trace.StepExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchRunnerTest {

    private fun metric(tier: InferenceTier, total: Long = 1_000, first: Long = 200, tokens: Int = 20) = GenerationMetric(
        tier = tier, startedAtMs = 0, firstChunkLatencyMs = first, totalDurationMs = total,
        promptChars = 10, responseChars = tokens * 4, success = true, responseTokens = tokens,
        responseTokensExact = true, memoryDeltaBytes = 0, loadTimeMs = null, thermalStatus = 1,
    )

    /** Answers every task correctly by looking up the expected answer from the task prompt. */
    private val oracle = StepExecutor { tier, messages, _, _ ->
        val prompt = messages.last().text
        val answer = when {
            "9:15" in prompt -> "150"
            "capital de Australia" in prompt -> "Canberra"
            else -> "no sé"
        }
        StepResult(answer, metric(tier))
    }

    private fun config(vararg tiers: InferenceTier, categories: Set<BenchCategory> = BenchCategory.entries.toSet(), reps: Int = 1) =
        BenchConfig(tiers.toList(), categories, reps)

    @Test
    fun `every engine answers every task of the selected categories`() = runBlocking {
        val runner = BenchRunner(oracle, supportsJson = { true })
        val samples = runner.run(config(InferenceTier.ML_KIT_NANO, InferenceTier.CLOUD_GEMINI, categories = setOf(BenchCategory.KNOWLEDGE)))

        val knowledgeTasks = BenchTasks.byCategories(setOf(BenchCategory.KNOWLEDGE)).size
        assertEquals(2 * knowledgeTasks, samples.size)
        assertTrue(samples.all { it.category == BenchCategory.KNOWLEDGE })
        // The oracle only knows the capital of Australia.
        val capital = samples.filter { it.taskId == "capital" }
        assertTrue(capital.all { it.passed })
        assertTrue(samples.filter { it.taskId == "gold" }.none { it.passed })
    }

    @Test
    fun `repetitions multiply the runs and number them`() = runBlocking {
        val runner = BenchRunner(oracle, supportsJson = { true })
        val samples = runner.run(config(InferenceTier.ML_KIT_NANO, categories = setOf(BenchCategory.REASONING), reps = 3))

        val reasoning = BenchTasks.byCategories(setOf(BenchCategory.REASONING)).size
        assertEquals(reasoning * 3, samples.size)
        assertEquals(setOf(1, 2, 3), samples.map { it.rep }.toSet())
    }

    @Test
    fun `a failing engine produces error samples and the session continues`() = runBlocking {
        val executor = StepExecutor { tier, _, _, _ ->
            if (tier == InferenceTier.MEDIAPIPE_LOCAL) StepResult("", null, "model not loaded") else StepResult("150", metric(tier))
        }
        val runner = BenchRunner(executor, supportsJson = { true })
        val samples = runner.run(config(InferenceTier.MEDIAPIPE_LOCAL, InferenceTier.ML_KIT_NANO, categories = setOf(BenchCategory.REASONING)))

        val failed = samples.filter { it.tier == InferenceTier.MEDIAPIPE_LOCAL }
        assertTrue(failed.all { it.error == "model not loaded" && !it.passed && it.score == 0.0 })
        assertTrue(samples.filter { it.tier == InferenceTier.ML_KIT_NANO }.any { it.passed })
    }

    @Test
    fun `a task that never answers is recorded as a timeout`() = runBlocking {
        val executor = StepExecutor { _, _, _, _ ->
            delay(10_000)
            StepResult("late", null)
        }
        val runner = BenchRunner(executor, supportsJson = { true }, timeoutMs = 20)
        val samples = runner.run(config(InferenceTier.CLOUD_GEMINI, categories = setOf(BenchCategory.SUMMARY)))

        assertTrue(samples.isNotEmpty())
        assertTrue(samples.all { it.error?.startsWith("timeout") == true })
    }

    @Test
    fun `json mode is only requested from engines that support it`() = runBlocking {
        val seen = mutableMapOf<InferenceTier, Boolean>()
        val executor = StepExecutor { tier, _, config, _ ->
            if (config.jsonMode) seen[tier] = true else seen.putIfAbsent(tier, false)
            StepResult("{}", metric(tier))
        }
        val runner = BenchRunner(executor, supportsJson = { it == InferenceTier.CLOUD_GEMINI })
        runner.run(config(InferenceTier.CLOUD_GEMINI, InferenceTier.ML_KIT_NANO, categories = setOf(BenchCategory.EXTRACTION)))

        assertEquals(true, seen[InferenceTier.CLOUD_GEMINI])
        assertEquals(false, seen[InferenceTier.ML_KIT_NANO])
    }

    @Test
    fun `progress is reported up to the total and metrics are copied to the sample`() = runBlocking {
        val progress = mutableListOf<BenchProgress>()
        val runner = BenchRunner(oracle, supportsJson = { true })
        val samples = runner.run(
            config(InferenceTier.ML_KIT_NANO, categories = setOf(BenchCategory.KNOWLEDGE)),
            onProgress = { progress += it },
        )

        assertEquals(samples.size, progress.last().total)
        assertEquals(samples.size, progress.last().done)
        assertNotNull(progress.first().taskTitle)
        val sample = samples.first { it.passed }
        assertEquals(1_000L, sample.latencyMs)
        assertEquals(200L, sample.ttftMs)
        assertEquals(1, sample.thermalStatus)
        assertFalse(sample.tokensPerSecond == null)
    }

    @Test
    fun `an empty selection produces no samples`() = runBlocking {
        val runner = BenchRunner(oracle, supportsJson = { true })
        assertTrue(runner.run(config(InferenceTier.ML_KIT_NANO, categories = emptySet())).isEmpty())
    }
}
