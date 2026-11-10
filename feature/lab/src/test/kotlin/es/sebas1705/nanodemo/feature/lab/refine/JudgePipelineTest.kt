package es.sebas1705.nanodemo.feature.lab.refine

import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.StepKind
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class JudgePipelineTest {

    private val judgeTier = InferenceTier.CLOUD_GEMINI
    private val typed = JudgeSource.Typed(listOf("GOOD answer", "so-so answer", "bad answer"))

    /** A judge that rewards the quality words in the answers, whatever position they are shown in. */
    private fun quality(text: String) = when {
        "GOOD" in text -> 3
        "so-so" in text -> 2
        else -> 1
    }

    private fun fairJudge() = FakeExecutor { call, _ ->
        val prompt = call.prompt
        if ("Answer A:" in prompt) {
            val a = prompt.substringAfter("Answer A:").substringBefore("Answer B:")
            val b = prompt.substringAfter("Answer B:")
            ok(if (quality(a) >= quality(b)) "{\"winner\":\"A\"}" else "{\"winner\":\"B\"}")
        } else {
            ok("{\"correctness\":${quality(prompt.substringAfter("Answer:"))},\"completeness\":3,\"clarity\":3}")
        }
    }

    private fun pipeline(executor: FakeExecutor, seed: Int = 7) =
        JudgePipeline(executor.runner(), random = Random(seed))

    @Test
    fun `a fair judge ranks by quality, is consistent and runs every pair twice`() = runBlocking {
        val executor = fairJudge()
        val result = pipeline(executor).run("Q?", typed, judgeTier, JudgeMode.PAIRWISE)

        assertTrue(result.finished)
        assertEquals(3 * 2, executor.calls.size) // 3 pairs x 2 orders
        assertEquals(listOf("Answer 1", "Answer 2", "Answer 3"), result.leaderboard.map { it.candidate.label })
        assertEquals(listOf(1, 2, 3), result.leaderboard.map { it.rank })
        assertEquals(1.0, result.consistency.ratio!!, 0.0001)
        assertEquals(0.5, result.firstShare!!.ratio!!, 0.0001)
        assertEquals(6, result.judgeStats.calls)
        assertEquals(0, result.answerStats.calls)
    }

    @Test
    fun `the two runs of a pair show the answers in swapped positions`() = runBlocking {
        val executor = fairJudge()
        pipeline(executor).run("Q?", typed, judgeTier, JudgeMode.PAIRWISE)

        executor.calls.chunked(2).forEach { (one, two) ->
            fun answerA(p: String) = p.substringAfter("Answer A:").substringBefore("Answer B:").trim()
            fun answerB(p: String) = p.substringAfter("Answer B:").trim()
            assertEquals(answerA(one.prompt), answerB(two.prompt))
            assertEquals(answerB(one.prompt), answerA(two.prompt))
        }
    }

    @Test
    fun `the order of the pairs and of the first position depends on the seed`() = runBlocking {
        val orders = (1..6).map { seed ->
            val executor = fairJudge()
            pipeline(executor, seed).run("Q?", typed, judgeTier, JudgeMode.PAIRWISE)
            executor.calls.map { it.prompt }
        }
        assertTrue("expected different orders across seeds", orders.distinct().size > 1)
        // same seed, same order
        val again = fairJudge().also { pipeline(it, 3).run("Q?", typed, judgeTier, JudgeMode.PAIRWISE) }
        val once = fairJudge().also { pipeline(it, 3).run("Q?", typed, judgeTier, JudgeMode.PAIRWISE) }
        assertEquals(once.calls.map { it.prompt }, again.calls.map { it.prompt })
    }

    @Test
    fun `a judge that always says A has zero consistency and a full first position share`() = runBlocking {
        val executor = FakeExecutor { _, _ -> ok("{\"winner\":\"A\"}") }
        val result = pipeline(executor).run("Q?", typed, judgeTier, JudgeMode.PAIRWISE)

        assertEquals(0.0, result.consistency.ratio!!, 0.0001)
        assertEquals(3, result.consistency.of)
        assertEquals(1.0, result.firstShare!!.ratio!!, 0.0001)
        // every candidate beat and lost equally: all tied for first
        assertTrue(result.leaderboard.all { it.rank == 1 })
    }

    @Test
    fun `a judge that says B every time is biased the other way`() = runBlocking {
        val executor = FakeExecutor { _, _ -> ok("Winner: B") }
        val result = pipeline(executor).run("Q?", typed, judgeTier, JudgeMode.PAIRWISE)
        assertEquals(0.0, result.firstShare!!.ratio!!, 0.0001)
        assertEquals(0.0, result.consistency.ratio!!, 0.0001)
    }

    @Test
    fun `unreadable verdicts are counted and excluded from consistency`() = runBlocking {
        val executor = FakeExecutor { _, index -> ok(if (index % 2 == 0) "no idea" else "{\"winner\":\"A\"}") }
        val result = pipeline(executor).run("Q?", typed, judgeTier, JudgeMode.PAIRWISE)

        assertEquals(3, result.unreadable.count)
        assertEquals(6, result.unreadable.of)
        assertEquals(0, result.consistency.of)
        assertEquals(null, result.consistency.ratio)
    }

    @Test
    fun `a failing judge call is recorded as unreadable and the run goes on`() = runBlocking {
        val executor = FakeExecutor { _, index -> if (index == 0) failure("rate limited") else ok("{\"winner\":\"tie\"}") }
        val result = pipeline(executor).run("Q?", typed, judgeTier, JudgeMode.PAIRWISE)

        assertTrue(result.finished)
        assertEquals(1, result.unreadable.count)
        assertEquals(6, executor.calls.size)
    }

    @Test
    fun `rubric mode scores each answer on its own and ranks by the mean`() = runBlocking {
        val executor = fairJudge()
        val result = pipeline(executor).run("Q?", typed, judgeTier, JudgeMode.RUBRIC)

        assertEquals(3, executor.calls.size)
        assertEquals(listOf("Answer 1", "Answer 2", "Answer 3"), result.leaderboard.map { it.candidate.label })
        assertEquals(3, result.scores[1]!!.correctness)
        assertEquals(1, result.scores[3]!!.correctness)
        assertTrue(result.pairs.isEmpty())
        // each call carries one answer only
        assertTrue(executor.calls.all { "Answer B:" !in it.prompt })
    }

    @Test
    fun `an unreadable rubric leaves the answer unscored`() = runBlocking {
        val executor = FakeExecutor { _, index -> ok(if (index == 0) "meh" else "{\"correctness\":4,\"completeness\":4,\"clarity\":4}") }
        val result = pipeline(executor).run("Q?", typed, judgeTier, JudgeMode.RUBRIC)

        assertEquals(1, result.scores.values.count { it == null })
        assertEquals(0.0, result.leaderboard.last().score, 0.0001)
    }

    @Test
    fun `answers can come from several engines and failures are skipped with a reason`() = runBlocking {
        val executor = FakeExecutor { call, _ ->
            when {
                call.tier == InferenceTier.MEDIAPIPE_LOCAL -> failure("model missing")
                call.tier == judgeTier && "Answer A:" in call.prompt -> ok("{\"winner\":\"A\"}")
                else -> ok("answer from ${call.tier.name}")
            }
        }
        val tiers = listOf(InferenceTier.ML_KIT_NANO, InferenceTier.MEDIAPIPE_LOCAL, InferenceTier.LITERT_LM_LOCAL)
        val result = pipeline(executor).run("Q?", JudgeSource.Engines(tiers), judgeTier, JudgeMode.PAIRWISE)

        assertEquals(listOf(InferenceTier.ML_KIT_NANO, InferenceTier.LITERT_LM_LOCAL), result.candidates.map { it.tier })
        assertEquals(1, result.skipped.size)
        assertTrue(result.skipped.single().contains("model missing"))
        assertEquals(3 + 2, executor.calls.size) // 3 generations + one pair twice
        assertEquals(listOf(InferenceTier.ML_KIT_NANO, InferenceTier.MEDIAPIPE_LOCAL, InferenceTier.LITERT_LM_LOCAL), executor.calls.take(3).map { it.tier })
        assertTrue(executor.calls.drop(3).all { it.tier == judgeTier })
    }

    @Test
    fun `fewer than two answers is an error and nothing is judged`() = runBlocking {
        val executor = fairJudge()
        val result = pipeline(executor).run("Q?", JudgeSource.Typed(listOf("only one", "   ")), judgeTier, JudgeMode.PAIRWISE)

        assertNotNull(result.error)
        assertTrue(result.finished)
        assertEquals(0, executor.calls.size)
    }

    @Test
    fun `judge calls are JUDGE steps with a low temperature`() = runBlocking {
        val executor = fairJudge()
        val runner = executor.runner()
        JudgePipeline(runner, random = Random(1)).run("Q?", typed, judgeTier, JudgeMode.RUBRIC)

        assertTrue(runner.recorder.trace.value.steps.filter { it.kind == StepKind.JUDGE }.size == 3)
        assertTrue(executor.calls.all { it.config.temperature!! <= 0.2f })
    }

    @Test
    fun `long answers are cut to fit a small judge context`() = runBlocking {
        val executor = FakeExecutor { _, _ -> ok("{\"winner\":\"A\"}") }
        val small = EngineProfile(contextTokens = 1024)
        val huge = JudgeSource.Typed(listOf("x".repeat(20_000), "y".repeat(20_000)))
        JudgePipeline(executor.runner(), { small }, Random(1)).run("Q?", huge, InferenceTier.MEDIAPIPE_LOCAL, JudgeMode.PAIRWISE)

        executor.calls.forEach { call ->
            assertTrue(approxTokens(call.prompt) + call.config.maxOutputTokens!! <= 1024)
        }
    }

    @Test
    fun `cancelling stops before the next judge call`() {
        runBlocking {
            lateinit var job: Job
            val executor = FakeExecutor { _, index ->
                if (index == 1) job.cancel()
                ok("{\"winner\":\"A\"}")
            }
            job = launch(start = CoroutineStart.LAZY) { pipeline(executor).run("Q?", typed, judgeTier, JudgeMode.PAIRWISE) }
            job.start()
            job.join()
            assertTrue(job.isCancelled)
            assertEquals(2, executor.calls.size)
        }
    }

    @Test
    fun `progress ends at the total`() = runBlocking {
        val progress = mutableListOf<LabProgress>()
        pipeline(fairJudge()).run("Q?", typed, judgeTier, JudgeMode.PAIRWISE, onProgress = { progress += it })
        assertEquals(1f, progress.last().fraction, 0.0001f)
        assertTrue(progress.any { it.done > 0 && it.done < it.total })
    }
}
