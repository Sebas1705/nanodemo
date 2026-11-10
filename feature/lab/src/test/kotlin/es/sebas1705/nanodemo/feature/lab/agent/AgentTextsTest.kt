package es.sebas1705.nanodemo.feature.lab.agent

import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.feature.lab.trace.PipelineRunner
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The agent loop and the task checks write their user-visible text through an [AgentTexts]. */
class AgentTextsTest {

    private val spanish = object : AgentTexts {
        override val noAnswer = "Sin respuesta"
        override fun outcome(outcome: AgentOutcome) = "resultado ${outcome.name}"
        override fun missingValue(value: String) = "faltó $value"
    }

    @Test
    fun `a stopped run closes the trace with the supplied title`() = runBlocking {
        val pipeline = PipelineRunner(ScriptedExecutor(listOf(calc("1+1"))))
        val result = AgentRunner(pipeline, texts = spanish)
            .run("t", InferenceTier.CLOUD_GEMINI, BenchmarkEnvironment.newRegistry(), caps(), AgentSettings(maxSteps = 1))
        assertEquals(AgentOutcome.MAX_STEPS, result.outcome)
        assertTrue(pipeline.recorder.trace.value.steps.any { it.title == "Sin respuesta" })
    }

    @Test
    fun `check failures and outcomes use the supplied texts`() {
        assertEquals("faltó 5", Checks.containsNumber(spanish, "no", 5.0).reason)
        val case = BenchmarkSuite.cases.first()
        val run = AgentRunResult(task = "t", tier = InferenceTier.CLOUD_GEMINI, outcome = AgentOutcome.LOOP, answer = "", steps = 1, maxSteps = 6)
        assertEquals("resultado LOOP", BenchmarkAggregator.check(case, run, spanish).failureReason)
    }

    @Test
    fun `the default final detail is singular for one step`() {
        assertEquals("1 step · 1 tool", AgentTexts.finalDetail(1, 1, implicit = false))
        assertEquals("2 steps · 0 tools · no «Final Answer» label", AgentTexts.finalDetail(2, 0, implicit = true))
    }
}
