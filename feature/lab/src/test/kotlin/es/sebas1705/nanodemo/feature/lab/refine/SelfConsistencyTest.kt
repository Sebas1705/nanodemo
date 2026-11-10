package es.sebas1705.nanodemo.feature.lab.refine

import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.StepKind
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfConsistencyTest {

    private fun num(n: String) = Extracted(AnswerKind.NUMBER, n, n)
    private val tier = InferenceTier.MEDIAPIPE_LOCAL

    // ---- voting ----

    @Test
    fun `the most common answer wins and agreement is its share`() {
        val vote = majorityVote(listOf(num("12"), num("12"), num("13"), num("12"), num("14")))
        assertEquals("12", vote.winner!!.key)
        assertEquals(3, vote.winner!!.count)
        assertEquals(0.6, vote.agreement!!, 0.0001)
        assertFalse(vote.tie)
        assertEquals(listOf("12", "13", "14"), vote.buckets.map { it.key })
    }

    @Test
    fun `unreadable answers take no part in the vote`() {
        val vote = majorityVote(listOf(num("12"), null, num("12"), null, num("9")))
        assertEquals(3, vote.valid)
        assertEquals(5, vote.total)
        assertEquals(2.0 / 3.0, vote.agreement!!, 0.0001)
    }

    @Test
    fun `a tie is reported and broken by first appearance`() {
        val vote = majorityVote(listOf(num("7"), num("8"), num("8"), num("7")))
        assertTrue(vote.tie)
        assertEquals(listOf("7", "8"), vote.tiedKeys)
        assertEquals("7", vote.winner!!.key)
        assertEquals(0.5, vote.agreement!!, 0.0001)
    }

    @Test
    fun `a tie is broken in favour of the preferred answer when it is among the tied ones`() {
        val vote = majorityVote(listOf(num("7"), num("8"), num("8"), num("7")), preferKey = "8")
        assertTrue(vote.tie)
        assertEquals("8", vote.winner!!.key)
        // a preferred answer that is NOT tied does not override the majority
        val clear = majorityVote(listOf(num("7"), num("7"), num("8")), preferKey = "8")
        assertEquals("7", clear.winner!!.key)
    }

    @Test
    fun `no readable answers means no winner`() {
        val vote = majorityVote(listOf(null, null))
        assertNull(vote.winner)
        assertNull(vote.agreement)
        assertEquals(0, vote.valid)
        assertEquals(VoteVerdict.NO_VOTE, judgeVote(vote, null, "5"))
        assertTrue(majorityVote(emptyList()).buckets.isEmpty())
    }

    @Test
    fun `a single answer is unanimous`() {
        val vote = majorityVote(listOf(num("3")))
        assertEquals(1.0, vote.agreement!!, 0.0001)
        assertFalse(vote.tie)
    }

    // ---- voting vs greedy ----

    @Test
    fun `with a reference answer the verdict says who was right`() {
        val vote = majorityVote(listOf(num("17"), num("17"), num("16")))
        assertEquals(VoteVerdict.VOTING_BEAT_GREEDY, judgeVote(vote, num("16"), "17"))
        assertEquals(VoteVerdict.BOTH_CORRECT, judgeVote(vote, num("17"), "17"))
        assertEquals(VoteVerdict.GREEDY_BEAT_VOTING, judgeVote(vote, num("16"), "16"))
        assertEquals(VoteVerdict.BOTH_WRONG, judgeVote(vote, num("15"), "18"))
        assertEquals(VoteVerdict.VOTING_BEAT_GREEDY, judgeVote(vote, null, "17")) // greedy failed outright
    }

    @Test
    fun `without a reference the verdict only says whether voting changed the answer`() {
        val vote = majorityVote(listOf(num("17"), num("17"), num("16")))
        assertEquals(VoteVerdict.SAME_ANSWER, judgeVote(vote, num("17"), null))
        assertEquals(VoteVerdict.CHANGED_ANSWER, judgeVote(vote, num("16"), "  "))
        assertEquals(VoteVerdict.CHANGED_ANSWER, judgeVote(vote, null, null))
    }

    // ---- pipeline ----

    /** Greedy (call 0) answers 16; the five samples answer 17, 17, 16, 17, 18. */
    private fun scriptedRun(vararg replies: String) = FakeExecutor { _, i -> ok(replies[minOf(i, replies.lastIndex)]) }

    @Test
    fun `voting beats a wrong greedy answer and the run reports it`() = runBlocking {
        val executor = scriptedRun(
            "Reasoning...\nFinal answer: 16",
            "Final answer: 17", "Final answer: 17", "Final answer: 16", "Final answer: 17", "Final answer: 18",
        )
        val result = SelfConsistencyPipeline(executor.runner()).run("Q", tier, samples = 5, temperature = 0.8f, expected = "17")

        assertTrue(result.finished)
        assertEquals(6, executor.calls.size)
        assertEquals("17", result.vote!!.winner!!.key)
        assertEquals(0.6, result.vote!!.agreement!!, 0.0001)
        assertEquals("16", result.greedy!!.extracted!!.key)
        assertEquals(VoteVerdict.VOTING_BEAT_GREEDY, result.verdict)
        assertEquals(true, result.votedCorrect)
        assertEquals(false, result.greedyCorrect)
        assertEquals(AnswerKind.NUMBER, result.kind)
        assertEquals(5, result.samples.size)
    }

    @Test
    fun `greedy runs near zero temperature and samples at the requested one`() = runBlocking {
        val executor = scriptedRun("Final answer: 1")
        SelfConsistencyPipeline(executor.runner()).run("Q", tier, samples = 3, temperature = 0.9f)

        assertEquals(GREEDY_TEMPERATURE, executor.calls.first().config.temperature)
        assertTrue(executor.calls.drop(1).all { it.config.temperature == 0.9f })
        assertTrue(executor.calls.all { it.tier == tier })
        assertTrue(executor.calls.all { it.prompt.contains("Final answer:") })
    }

    @Test
    fun `a failed sample does not stop the vote`() = runBlocking {
        val executor = FakeExecutor { _, i ->
            when (i) {
                0 -> ok("Final answer: 4")
                2 -> failure("timeout")
                else -> ok("Final answer: 4")
            }
        }
        val result = SelfConsistencyPipeline(executor.runner()).run("Q", tier, samples = 3, temperature = 0.7f)

        assertEquals(3, result.samples.size)
        assertEquals("timeout", result.samples[1].error)
        assertNull(result.samples[1].extracted)
        assertEquals(2, result.vote!!.valid)
        assertEquals(VoteVerdict.SAME_ANSWER, result.verdict)
        assertNull(result.error)
    }

    @Test
    fun `when every call fails the run reports the error`() = runBlocking {
        val executor = FakeExecutor { _, _ -> failure("model missing") }
        val result = SelfConsistencyPipeline(executor.runner()).run("Q", tier, samples = 2, temperature = 0.7f)

        assertEquals("model missing", result.error)
        assertEquals(VoteVerdict.NO_VOTE, result.verdict)
        assertNull(result.vote!!.winner)
    }

    @Test
    fun `a tie between samples prefers the greedy answer`() = runBlocking {
        val executor = scriptedRun("Final answer: 8", "Final answer: 7", "Final answer: 8", "Final answer: 7", "Final answer: 8")
        // samples: 7, 8, 7, 8 -> tied; greedy said 8
        val result = SelfConsistencyPipeline(executor.runner()).run("Q", tier, samples = 4, temperature = 0.7f)

        assertTrue(result.vote!!.tie)
        assertEquals("8", result.vote!!.winner!!.key)
        assertEquals(VoteVerdict.SAME_ANSWER, result.verdict)
    }

    @Test
    fun `an explicit answer shape is used even if the samples look like something else`() = runBlocking {
        val executor = scriptedRun("Final answer: Yes", "Final answer: yes", "Final answer: No", "Final answer: Sí")
        val result = SelfConsistencyPipeline(executor.runner()).run("Q", tier, samples = 3, temperature = 0.7f, kind = AnswerKind.YES_NO)

        assertEquals(AnswerKind.YES_NO, result.kind)
        assertEquals("yes", result.vote!!.winner!!.key)
        assertEquals(2, result.vote!!.winner!!.count)
    }

    @Test
    fun `the trace has one generation per call plus the extraction and the vote`() = runBlocking {
        val executor = scriptedRun("Final answer: 2")
        val runner = executor.runner()
        SelfConsistencyPipeline(runner).run("Q", tier, samples = 2, temperature = 0.7f)

        val kinds = runner.recorder.trace.value.steps.map { it.kind }
        assertEquals(listOf(StepKind.GENERATE, StepKind.GENERATE, StepKind.GENERATE, StepKind.VALIDATE, StepKind.VALIDATE), kinds)
    }

    @Test
    fun `a long question is cut to fit a 1024-token engine`() = runBlocking {
        val executor = scriptedRun("Final answer: 2")
        val small = EngineProfile(contextTokens = 1024)
        SelfConsistencyPipeline(executor.runner()) { small }.run("word ".repeat(10_000), tier, samples = 2, temperature = 0.7f)

        executor.calls.forEach {
            assertTrue(approxTokens(it.prompt) + it.config.maxOutputTokens!! <= 1024)
        }
    }

    @Test
    fun `cancelling stops sampling`() {
        runBlocking {
            lateinit var job: Job
            val executor = FakeExecutor { _, i ->
                if (i == 2) job.cancel()
                ok("Final answer: 1")
            }
            job = launch(start = CoroutineStart.LAZY) {
                SelfConsistencyPipeline(executor.runner()).run("Q", tier, samples = 9, temperature = 0.7f)
            }
            job.start()
            job.join()
            assertTrue(job.isCancelled)
            assertEquals(3, executor.calls.size)
        }
    }

    @Test
    fun `progress counts the greedy run plus the samples`() = runBlocking {
        val progress = mutableListOf<LabProgress>()
        SelfConsistencyPipeline(scriptedRun("Final answer: 1").runner())
            .run("Q", tier, samples = 4, temperature = 0.7f, onProgress = { progress += it })
        assertEquals(5, progress.first().total)
        assertEquals(1f, progress.last().fraction, 0.0001f)
    }
}
