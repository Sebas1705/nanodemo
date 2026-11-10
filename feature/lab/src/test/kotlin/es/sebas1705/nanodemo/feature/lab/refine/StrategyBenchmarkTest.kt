package es.sebas1705.nanodemo.feature.lab.refine

import es.sebas1705.nanodemo.domain.models.InferenceTier
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrategyBenchmarkTest {

    private val nano = InferenceTier.ML_KIT_NANO
    private val cloud = InferenceTier.CLOUD_GEMINI

    private fun taskOf(prompt: String) = StrategyTasks.all.first { prompt.contains(it.question) }

    private fun strategyOf(prompt: String) = when {
        prompt.contains("general principle") -> PromptStrategy.STEP_BACK
        prompt.contains("Background:") -> PromptStrategy.STEP_BACK
        prompt.contains("Think step by step") -> PromptStrategy.CHAIN_OF_THOUGHT
        prompt.contains("What is 12 + 30?") -> PromptStrategy.FEW_SHOT
        prompt.startsWith("You are ") -> PromptStrategy.ROLE
        else -> PromptStrategy.ZERO_SHOT
    }

    /** An engine that answers right only for the strategies in `good`, and wrongly otherwise. */
    private fun replyFor(call: Call, good: Set<PromptStrategy>, tokens: Int = 10, ms: Long = 100) = when {
        call.prompt.contains("general principle") -> ok("Some principle.", ms, tokens, call.tier)
        strategyOf(call.prompt) in good -> ok("Final answer: ${RIGHT_ANSWERS.getValue(taskOf(call.prompt).id)}", ms, tokens, call.tier)
        else -> ok("Final answer: 12345", ms, tokens, call.tier)
    }

    private fun engine(good: Set<PromptStrategy>, tokens: Int = 10, ms: Long = 100) =
        FakeExecutor { call, _ -> replyFor(call, good, tokens, ms) }

    private fun outcome(
        strategy: PromptStrategy, correct: Boolean, tier: InferenceTier = nano, rep: Int = 1,
        failed: Boolean = false, tokens: Int = 10, ms: Long = 100,
    ) = TaskOutcome(tier, strategy, "t", rep, correct, failed, tokens, ms, "")

    // ---- aggregation ----

    @Test
    fun `accuracy averages tokens and latency over the completed runs`() {
        val stats = cellStats(
            listOf(
                outcome(PromptStrategy.ZERO_SHOT, true, tokens = 10, ms = 100),
                outcome(PromptStrategy.ZERO_SHOT, false, tokens = 30, ms = 300),
                outcome(PromptStrategy.ZERO_SHOT, true, tokens = 20, ms = 200),
            ),
        )
        assertEquals(3, stats.runs)
        assertEquals(2.0 / 3.0, stats.accuracy!!, 0.0001)
        assertEquals(20.0, stats.avgTokens, 0.0001)
        assertEquals(200.0, stats.avgLatencyMs, 0.0001)
        assertEquals(0, stats.errors)
        assertNull(stats.spread)
    }

    @Test
    fun `failed runs are counted as errors and left out of accuracy and averages`() {
        val stats = cellStats(
            listOf(
                outcome(PromptStrategy.ROLE, true, tokens = 10),
                outcome(PromptStrategy.ROLE, false, failed = true, tokens = 999),
            ),
        )
        assertEquals(2, stats.runs)
        assertEquals(1, stats.completed)
        assertEquals(1, stats.errors)
        assertEquals(1.0, stats.accuracy!!, 0.0001)
        assertEquals(10.0, stats.avgTokens, 0.0001)
    }

    @Test
    fun `a cell where everything failed has no accuracy`() {
        val stats = cellStats(listOf(outcome(PromptStrategy.ROLE, false, failed = true)))
        assertNull(stats.accuracy)
        assertEquals(0.0, stats.avgTokens, 0.0001)
        assertNull(cellStats(emptyList()).accuracy)
    }

    @Test
    fun `repetitions give a spread`() {
        val stats = cellStats(
            listOf(
                outcome(PromptStrategy.FEW_SHOT, true, rep = 1), outcome(PromptStrategy.FEW_SHOT, true, rep = 1),
                outcome(PromptStrategy.FEW_SHOT, true, rep = 2), outcome(PromptStrategy.FEW_SHOT, false, rep = 2),
                outcome(PromptStrategy.FEW_SHOT, false, rep = 3), outcome(PromptStrategy.FEW_SHOT, false, rep = 3),
            ),
        )
        assertEquals(listOf(1.0, 0.5, 0.0), stats.repetitionAccuracies)
        assertEquals(0.0, stats.spread!!.start, 0.0001)
        assertEquals(1.0, stats.spread!!.endInclusive, 0.0001)
        assertEquals(0.5, stats.accuracy!!, 0.0001)
    }

    @Test
    fun `grouping by strategy, engine and both`() {
        val all = listOf(
            outcome(PromptStrategy.ZERO_SHOT, true, nano), outcome(PromptStrategy.ZERO_SHOT, false, nano),
            outcome(PromptStrategy.ZERO_SHOT, true, cloud), outcome(PromptStrategy.CHAIN_OF_THOUGHT, true, cloud),
        )
        val cells = statsByStrategyAndTier(all)
        assertEquals(0.5, cells.getValue(PromptStrategy.ZERO_SHOT to nano).accuracy!!, 0.0001)
        assertEquals(1.0, cells.getValue(PromptStrategy.ZERO_SHOT to cloud).accuracy!!, 0.0001)
        assertNull(cells[PromptStrategy.CHAIN_OF_THOUGHT to nano])
        assertEquals(2.0 / 3.0, statsByStrategy(all).getValue(PromptStrategy.ZERO_SHOT).accuracy!!, 0.0001)
        assertEquals(0.5, statsByTier(all).getValue(nano).accuracy!!, 0.0001)
        assertEquals(1.0, statsByTier(all).getValue(cloud).accuracy!!, 0.0001)
    }

    @Test
    fun `the best strategies are all those sharing the top accuracy`() {
        val stats = statsByStrategy(
            listOf(
                outcome(PromptStrategy.ZERO_SHOT, false), outcome(PromptStrategy.FEW_SHOT, true),
                outcome(PromptStrategy.CHAIN_OF_THOUGHT, true), outcome(PromptStrategy.ROLE, false, failed = true),
            ),
        )
        assertEquals(setOf(PromptStrategy.FEW_SHOT, PromptStrategy.CHAIN_OF_THOUGHT), bestStrategies(stats))
        assertTrue(bestStrategies(emptyMap()).isEmpty())
    }

    // ---- the run ----

    @Test
    fun `every strategy is tried on every task on every engine`() = runBlocking {
        val executor = engine(good = PromptStrategy.entries.toSet())
        val outcomes = StrategyBenchmark(executor.runner()).run(
            tiers = listOf(nano, cloud), strategies = PromptStrategy.entries, tasks = StrategyTasks.all,
            repetitions = 1, temperature = GREEDY_TEMPERATURE,
        )

        assertEquals(2 * 5 * StrategyTasks.all.size, outcomes.size)
        assertTrue(outcomes.all { it.correct && !it.failed })
        // step-back costs two calls per task, the rest one
        assertEquals(2 * StrategyTasks.all.size * (4 + 2), executor.calls.size)
        assertEquals(setOf(nano, cloud), executor.calls.map { it.tier }.toSet())
    }

    @Test
    fun `scores differ by strategy and the table shows it`() = runBlocking {
        val executor = engine(good = setOf(PromptStrategy.CHAIN_OF_THOUGHT, PromptStrategy.STEP_BACK))
        val outcomes = StrategyBenchmark(executor.runner()).run(
            listOf(nano), PromptStrategy.entries, StrategyTasks.all, repetitions = 1, temperature = GREEDY_TEMPERATURE,
        )
        val stats = statsByStrategy(outcomes)

        assertEquals(1.0, stats.getValue(PromptStrategy.CHAIN_OF_THOUGHT).accuracy!!, 0.0001)
        assertEquals(1.0, stats.getValue(PromptStrategy.STEP_BACK).accuracy!!, 0.0001)
        assertEquals(0.0, stats.getValue(PromptStrategy.ZERO_SHOT).accuracy!!, 0.0001)
        assertEquals(setOf(PromptStrategy.CHAIN_OF_THOUGHT, PromptStrategy.STEP_BACK), bestStrategies(stats))
    }

    @Test
    fun `tokens and latency of step-back add up both calls`() = runBlocking {
        val executor = engine(good = PromptStrategy.entries.toSet(), tokens = 10, ms = 100)
        val bench = StrategyBenchmark(executor.runner())
        val task = StrategyTasks.byId("arith-change")!!

        val single = bench.runOne(nano, PromptStrategy.ZERO_SHOT, task, 1, GREEDY_TEMPERATURE)
        val double = bench.runOne(nano, PromptStrategy.STEP_BACK, task, 1, GREEDY_TEMPERATURE)

        assertEquals(10, single.tokens)
        assertEquals(100L, single.latencyMs)
        assertEquals(20, double.tokens)
        assertEquals(200L, double.latencyMs)
        assertTrue(double.correct)
    }

    @Test
    fun `an engine error marks the run failed instead of wrong`() = runBlocking {
        val executor = FakeExecutor { _, _ -> failure("context overflow") }
        val outcome = StrategyBenchmark(executor.runner())
            .runOne(nano, PromptStrategy.CHAIN_OF_THOUGHT, StrategyTasks.all.first(), 1, GREEDY_TEMPERATURE)

        assertTrue(outcome.failed)
        assertEquals(false, outcome.correct)
        assertEquals("context overflow", outcome.error)
    }

    @Test
    fun `step-back stops after a failed first call`() = runBlocking {
        val executor = FakeExecutor { _, _ -> failure("boom") }
        val outcome = StrategyBenchmark(executor.runner())
            .runOne(nano, PromptStrategy.STEP_BACK, StrategyTasks.all.first(), 1, GREEDY_TEMPERATURE)
        assertTrue(outcome.failed)
        assertEquals(1, executor.calls.size)
    }

    @Test
    fun `repetitions multiply the runs and are numbered`() = runBlocking {
        val executor = engine(good = setOf(PromptStrategy.ZERO_SHOT))
        val outcomes = StrategyBenchmark(executor.runner()).run(
            listOf(nano), listOf(PromptStrategy.ZERO_SHOT), StrategyTasks.all.take(3), repetitions = 3, temperature = 0.6f,
        )
        assertEquals(9, outcomes.size)
        assertEquals(setOf(1, 2, 3), outcomes.map { it.repetition }.toSet())
        assertTrue(executor.calls.all { it.config.temperature == 0.6f })
    }

    @Test
    fun `an interrupted run leaves every strategy with the same tasks answered`() {
        runBlocking {
            lateinit var job: Job
            var partial = emptyList<TaskOutcome>()
            val executor = FakeExecutor { call, i ->
                if (i == 12) job.cancel()
                replyFor(call, PromptStrategy.entries.toSet())
            }
            job = launch(start = CoroutineStart.LAZY) {
                StrategyBenchmark(executor.runner()).run(
                    listOf(nano), PromptStrategy.entries, StrategyTasks.all, 1, GREEDY_TEMPERATURE, onUpdate = { partial = it },
                )
            }
            job.start()
            job.join()

            assertTrue(job.isCancelled)
            assertTrue(partial.isNotEmpty())
            // task-major order: the first task got all five strategies before the second one started
            assertEquals(PromptStrategy.entries, partial.take(5).map { it.strategy })
            assertEquals(1, partial.take(5).map { it.taskId }.toSet().size)
        }
    }

    @Test
    fun `progress reports every run`() = runBlocking {
        val progress = mutableListOf<LabProgress>()
        StrategyBenchmark(engine(good = emptySet()).runner()).run(
            listOf(nano), listOf(PromptStrategy.ZERO_SHOT, PromptStrategy.ROLE), StrategyTasks.all.take(2), 1, GREEDY_TEMPERATURE,
            onProgress = { progress += it },
        )
        assertEquals(4, progress.first().total)
        assertEquals(1f, progress.last().fraction, 0.0001f)
        assertEquals(5, progress.size) // one per run plus the final "done"
    }

    @Test
    fun `small engines get prompts and answer budgets that fit their context`() = runBlocking {
        val executor = engine(good = emptySet())
        val small = EngineProfile(contextTokens = 1024)
        StrategyBenchmark(executor.runner()) { small }.run(
            listOf(InferenceTier.MEDIAPIPE_LOCAL), PromptStrategy.entries, StrategyTasks.all, 1, GREEDY_TEMPERATURE,
        )
        executor.calls.forEach {
            assertTrue(approxTokens(it.prompt) + it.config.maxOutputTokens!! <= 1024)
        }
    }
}
