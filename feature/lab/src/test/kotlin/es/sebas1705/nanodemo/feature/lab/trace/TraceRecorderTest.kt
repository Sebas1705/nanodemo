package es.sebas1705.nanodemo.feature.lab.trace

import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.StepKind
import es.sebas1705.nanodemo.domain.models.StepStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TraceRecorderTest {

    private var now = 1_000L
    private val recorder = TraceRecorder { now }

    @Test
    fun `a finished step records its duration, output and tokens`() {
        val id = recorder.begin(StepKind.GENERATE, "Answer", InferenceTier.ML_KIT_NANO, "question")
        now += 250
        recorder.finish(id, output = "answer", tokens = 12, detail = "fast")

        val step = recorder.trace.value.steps.single()
        assertEquals(StepStatus.DONE, step.status)
        assertEquals(250L, step.durationMs)
        assertEquals("answer", step.output)
        assertEquals(12, step.tokens)
        assertEquals("fast", step.detail)
    }

    @Test
    fun `a failed step keeps the message as detail`() {
        val id = recorder.begin(StepKind.TOOL, "Calc")
        now += 10
        recorder.fail(id, "boom")

        val step = recorder.trace.value.steps.single()
        assertEquals(StepStatus.FAILED, step.status)
        assertEquals("boom", step.detail)
        assertTrue(recorder.trace.value.failed)
    }

    @Test
    fun `preview updates the output of the given step only`() {
        val a = recorder.begin(StepKind.GENERATE, "A")
        val b = recorder.begin(StepKind.GENERATE, "B")
        recorder.preview(a, "par")

        val steps = recorder.trace.value.steps
        assertEquals("par", steps.first { it.id == a }.output)
        assertNull(steps.first { it.id == b }.output)
        assertTrue(recorder.trace.value.running)
    }

    @Test
    fun `skip adds a zero-length step with the reason`() {
        recorder.skip(StepKind.ROUTE, "Cloud", "local answer was enough", InferenceTier.CLOUD_GEMINI)

        val step = recorder.trace.value.steps.single()
        assertEquals(StepStatus.SKIPPED, step.status)
        assertEquals(0L, step.durationMs)
        assertEquals("local answer was enough", step.detail)
        assertFalse(recorder.trace.value.running)
    }

    @Test
    fun `totals and tiers are aggregated over the steps`() {
        val a = recorder.begin(StepKind.GENERATE, "A", InferenceTier.ML_KIT_NANO)
        now += 100
        recorder.finish(a, tokens = 5)
        val b = recorder.begin(StepKind.JUDGE, "B", InferenceTier.CLOUD_GEMINI)
        now += 300
        recorder.finish(b, tokens = 7)
        recorder.begin(StepKind.RETRIEVE, "no model")

        val trace = recorder.trace.value
        assertEquals(400L, trace.totalDurationMs)
        assertEquals(12, trace.totalTokens)
        assertEquals(setOf(InferenceTier.ML_KIT_NANO, InferenceTier.CLOUD_GEMINI), trace.tiersUsed)
    }

    @Test
    fun `reset clears the trace and restarts ids`() {
        recorder.begin(StepKind.GENERATE, "A")
        recorder.reset()
        assertTrue(recorder.trace.value.steps.isEmpty())
        assertEquals(1, recorder.begin(StepKind.GENERATE, "B"))
    }

    @Test
    fun `long previews are truncated`() {
        val id = recorder.begin(StepKind.GENERATE, "A", input = "x".repeat(2_000))
        recorder.finish(id, output = "y".repeat(2_000))

        val step = recorder.trace.value.steps.single()
        assertTrue(step.input!!.length <= TRACE_PREVIEW_CHARS + 1)
        assertTrue(step.output!!.endsWith("…"))
    }
}
