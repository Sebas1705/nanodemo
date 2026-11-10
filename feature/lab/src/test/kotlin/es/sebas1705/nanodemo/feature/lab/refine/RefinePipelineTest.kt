package es.sebas1705.nanodemo.feature.lab.refine

import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.StepKind
import es.sebas1705.nanodemo.domain.models.StepStatus
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val CRITIC_ACCEPT = """{"verdict":"accept","issues":[]}"""
private const val CRITIC_REVISE = """{"verdict":"revise","issues":["Missing the units"]}"""

class RefinePipelineTest {

    private val nano = InferenceTier.ML_KIT_NANO
    private val cloud = InferenceTier.CLOUD_GEMINI
    private val setup = RefineSetup(planner = nano, worker = nano, critic = cloud, maxRevisions = 2)

    @Test
    fun `accepted on round two keeps both drafts and the change between them`() = runBlocking {
        val executor = scripted("1. do it", "Draft one", CRITIC_REVISE, "Draft two with units", CRITIC_ACCEPT)
        val pipeline = RefinePipeline(executor.runner())

        val result = pipeline.run("Explain X", setup)

        assertEquals(RefineStop.ACCEPTED, result.stop)
        assertNull(result.error)
        assertEquals("1. do it", result.plan)
        assertEquals(listOf("Draft one", "Draft two with units"), result.drafts.map { it.text })
        assertEquals(Verdict.REVISE, result.drafts[0].critique?.verdict)
        assertEquals(Verdict.ACCEPT, result.drafts[1].critique?.verdict)
        assertNull(result.drafts[0].change)
        assertNotNull(result.drafts[1].change)
        assertEquals("Draft two with units", result.finalAnswer)
        assertEquals(5, executor.calls.size)
    }

    @Test
    fun `each role runs on its own engine`() = runBlocking {
        val executor = scripted("plan", "v1", CRITIC_REVISE, "v2", CRITIC_ACCEPT)
        RefinePipeline(executor.runner()).run(
            "Task",
            RefineSetup(planner = InferenceTier.MEDIAPIPE_LOCAL, worker = InferenceTier.LITERT_LM_LOCAL, critic = cloud),
        )
        assertEquals(
            listOf(InferenceTier.MEDIAPIPE_LOCAL, InferenceTier.LITERT_LM_LOCAL, cloud, InferenceTier.LITERT_LM_LOCAL, cloud),
            executor.calls.map { it.tier },
        )
    }

    @Test
    fun `the revision prompt carries the critic's issues and the previous draft`() = runBlocking {
        val executor = scripted("plan", "First attempt", CRITIC_REVISE, "Second", CRITIC_ACCEPT)
        RefinePipeline(executor.runner()).run("Task", setup)

        val revision = executor.calls[3].prompt
        assertTrue(revision.contains("Missing the units"))
        assertTrue(revision.contains("First attempt"))
    }

    @Test
    fun `stops after the revision budget when the critic never accepts`() = runBlocking {
        val executor = scripted("plan", "v1", CRITIC_REVISE, "v2", CRITIC_REVISE, "v3", CRITIC_REVISE)
        val result = RefinePipeline(executor.runner()).run("Task", setup.copy(maxRevisions = 2))

        assertEquals(RefineStop.MAX_REVISIONS, result.stop)
        assertEquals(3, result.drafts.size)
        // plan + v1 + (critique + rewrite) x 2 + final critique
        assertEquals(7, executor.calls.size)
        assertEquals("v3", result.finalAnswer)
        assertEquals(Verdict.REVISE, result.drafts.last().critique?.verdict)
    }

    @Test
    fun `zero revisions critiques once and never rewrites`() = runBlocking {
        val executor = scripted("plan", "v1", CRITIC_REVISE)
        val result = RefinePipeline(executor.runner()).run("Task", setup.copy(maxRevisions = 0))

        assertEquals(RefineStop.MAX_REVISIONS, result.stop)
        assertEquals(1, result.drafts.size)
        assertEquals(3, executor.calls.size)
    }

    @Test
    fun `accepted straight away costs one plan, one draft and one critique`() = runBlocking {
        val executor = scripted("plan", "good answer", CRITIC_ACCEPT)
        val result = RefinePipeline(executor.runner()).run("Task", setup)

        assertEquals(RefineStop.ACCEPTED, result.stop)
        assertEquals(1, result.drafts.size)
        assertEquals(3, executor.calls.size)
    }

    @Test
    fun `a critic that returns garbage stops the loop and keeps the draft`() = runBlocking {
        val executor = scripted("plan", "my draft", "hmm, hard to say, the weather is nice")
        val result = RefinePipeline(executor.runner()).run("Task", setup)

        assertEquals(RefineStop.CRITIC_UNREADABLE, result.stop)
        assertEquals("my draft", result.finalAnswer)
        assertEquals(Verdict.UNKNOWN, result.drafts.single().critique?.verdict)
        assertEquals(3, executor.calls.size)
    }

    @Test
    fun `a failing worker ends the run as failed with the engine error`() = runBlocking {
        val executor = FakeExecutor { _, index -> if (index == 0) ok("plan") else failure("engine unavailable") }
        val result = RefinePipeline(executor.runner()).run("Task", setup)

        assertEquals(RefineStop.FAILED, result.stop)
        assertTrue(result.error!!.contains("engine unavailable"))
        assertTrue(result.drafts.isEmpty())
    }

    @Test
    fun `a failing planner and a blank reply both end the run`() = runBlocking {
        val planner = FakeExecutor { _, _ -> failure("no network") }
        assertEquals(RefineStop.FAILED, RefinePipeline(planner.runner()).run("Task", setup).stop)

        val blank = FakeExecutor { _, _ -> ok("   ") }
        val result = RefinePipeline(blank.runner()).run("Task", setup)
        assertEquals(RefineStop.FAILED, result.stop)
        assertEquals(1, blank.calls.size)
        assertTrue(result.error!!.contains(EMPTY_REPLY_MESSAGE))
    }

    @Test
    fun `a failing critic ends the run but the draft survives`() = runBlocking {
        val executor = FakeExecutor { _, index -> if (index < 2) ok("text $index") else failure("rate limited") }
        val result = RefinePipeline(executor.runner()).run("Task", setup)

        assertEquals(RefineStop.FAILED, result.stop)
        assertEquals("text 1", result.finalAnswer)
    }

    @Test
    fun `latency and tokens are measured per role from the engine metrics`() = runBlocking {
        val executor = FakeExecutor { _, index ->
            when (index) {
                0 -> ok("plan", ms = 100, tokens = 20)
                1 -> ok("v1", ms = 300, tokens = 50)
                2 -> ok(CRITIC_REVISE, ms = 200, tokens = 30)
                3 -> ok("v2", ms = 400, tokens = 60)
                else -> ok(CRITIC_ACCEPT, ms = 150, tokens = 25)
            }
        }
        val result = RefinePipeline(executor.runner()).run("Task", setup)

        assertEquals(CallStats(1, 100, 20), result.stats[RefineRole.PLANNER])
        assertEquals(CallStats(2, 700, 110), result.stats[RefineRole.WORKER])
        assertEquals(CallStats(2, 350, 55), result.stats[RefineRole.CRITIC])
        assertEquals(CallStats(5, 1150, 185), result.total)
    }

    @Test
    fun `the trace shows every role, the parse and the comparison`() = runBlocking {
        val runner = scripted("plan", "v1", CRITIC_REVISE, "v2", CRITIC_ACCEPT).runner()
        RefinePipeline(runner).run("Task", setup)
        val kinds = runner.recorder.trace.value.steps.map { it.kind }
        assertEquals(
            listOf(
                StepKind.PLAN, StepKind.GENERATE, StepKind.CRITIQUE, StepKind.VALIDATE,
                StepKind.GENERATE, StepKind.SUMMARIZE, StepKind.CRITIQUE, StepKind.VALIDATE,
            ),
            kinds,
        )
        assertTrue(runner.recorder.trace.value.steps.all { it.status == StepStatus.DONE })
    }

    @Test
    fun `prompts for a 1024-token engine stay inside its context even with a huge task and draft`() = runBlocking {
        val huge = "word ".repeat(5_000)
        val executor = FakeExecutor { _, index ->
            when (index) {
                0 -> ok("1. step ".repeat(500))
                1 -> ok(huge)
                2 -> ok(CRITIC_REVISE)
                3 -> ok(huge)
                else -> ok(CRITIC_ACCEPT)
            }
        }
        val small = EngineProfile(contextTokens = 1024)
        val pipeline = RefinePipeline(executor.runner()) { small }
        pipeline.run(huge, RefineSetup(InferenceTier.MEDIAPIPE_LOCAL, InferenceTier.MEDIAPIPE_LOCAL, InferenceTier.MEDIAPIPE_LOCAL))

        executor.calls.forEach { call ->
            val promptTokens = approxTokens(call.prompt)
            val answer = call.config.maxOutputTokens!!
            assertTrue("prompt $promptTokens + answer $answer exceeds 1024", promptTokens + answer <= 1024)
        }
    }

    @Test
    fun `cancelling stops the run before the next call`() {
        runBlocking {
            lateinit var job: Job
            val executor = FakeExecutor { _, index ->
                if (index == 1) job.cancel() // the user hits cancel while the first draft is being written
                ok("text $index")
            }
            job = launch(start = CoroutineStart.LAZY) { RefinePipeline(executor.runner()).run("Task", setup) }
            job.start()
            job.join()

            assertTrue(job.isCancelled)
            assertEquals(2, executor.calls.size)
        }
    }

    @Test
    fun `progress ends at the total and partial results are published as the run goes`() = runBlocking {
        val executor = scripted("plan", "v1", CRITIC_ACCEPT)
        val updates = mutableListOf<RefineResult>()
        val progress = mutableListOf<LabProgress>()
        RefinePipeline(executor.runner()).run("Task", setup, onUpdate = { updates += it }, onProgress = { progress += it })

        assertTrue(updates.first().stop == null)
        assertEquals(RefineStop.ACCEPTED, updates.last().stop)
        assertTrue(updates.any { it.plan.isNotEmpty() && it.drafts.isEmpty() })
        assertEquals(progress.last().total, progress.last().done)
        assertEquals(1f, progress.last().fraction, 0.0001f)
    }

    @Test
    fun `default roles prefer a local worker and a cloud critic`() {
        val all = InferenceTier.entries
        val setup = RefineSetup.defaultFor(all)
        assertEquals(InferenceTier.ML_KIT_NANO, setup.worker)
        assertEquals(InferenceTier.CLOUD_GEMINI, setup.critic)

        val onlyCloud = RefineSetup.defaultFor(listOf(InferenceTier.FIREBASE_AI))
        assertEquals(InferenceTier.FIREBASE_AI, onlyCloud.worker)
        assertEquals(InferenceTier.FIREBASE_AI, onlyCloud.critic)

        val onlyLocal = RefineSetup.defaultFor(listOf(InferenceTier.MEDIAPIPE_LOCAL))
        assertEquals(InferenceTier.MEDIAPIPE_LOCAL, onlyLocal.critic)

        val none = RefineSetup.defaultFor(emptyList())
        assertEquals(InferenceTier.ML_KIT_NANO, none.worker)
    }
}
