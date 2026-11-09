package es.sebas1705.nanodemo.feature.lab.trace

import es.sebas1705.nanodemo.domain.models.ChatMessage
import es.sebas1705.nanodemo.domain.models.GenerationConfig
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.Role
import es.sebas1705.nanodemo.domain.models.StepKind
import es.sebas1705.nanodemo.domain.models.StepResult
import es.sebas1705.nanodemo.domain.models.StepStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PipelineRunnerTest {

    private fun runner(executor: StepExecutor) = PipelineRunner(executor)
    private val question = listOf(ChatMessage(Role.USER, "What is 2+2?"))

    @Test
    fun `a successful generation is recorded as a done step with its output`() = runBlocking {
        val runner = runner { _, _, _, onPartial ->
            onPartial("4")
            StepResult("The answer is 4", metric = null)
        }

        val result = runner.generate(StepKind.GENERATE, "Answer", InferenceTier.ML_KIT_NANO, question)

        assertTrue(result.ok)
        val step = runner.recorder.trace.value.steps.single()
        assertEquals(StepStatus.DONE, step.status)
        assertEquals(InferenceTier.ML_KIT_NANO, step.tier)
        assertEquals("What is 2+2?", step.input)
        assertEquals("The answer is 4", step.output)
    }

    @Test
    fun `a failed generation is recorded as a failed step with the error`() = runBlocking {
        val runner = runner { _, _, _, _ -> StepResult("", null, "engine unavailable") }

        val result = runner.generate(StepKind.GENERATE, "Answer", InferenceTier.CLOUD_GEMINI, question)

        assertFalse(result.ok)
        val step = runner.recorder.trace.value.steps.single()
        assertEquals(StepStatus.FAILED, step.status)
        assertEquals("engine unavailable", step.detail)
    }

    @Test
    fun `the config and tier reach the executor unchanged`() = runBlocking {
        var seenTier: InferenceTier? = null
        var seenConfig: GenerationConfig? = null
        val runner = runner { tier, _, config, _ ->
            seenTier = tier
            seenConfig = config
            StepResult("ok", null)
        }
        val config = GenerationConfig(temperature = 0.1f, jsonMode = true)

        runner.generate(StepKind.JUDGE, "Judge", InferenceTier.LITERT_LM_LOCAL, question, config)

        assertEquals(InferenceTier.LITERT_LM_LOCAL, seenTier)
        assertEquals(config, seenConfig)
    }

    @Test
    fun `a local step returns its value and records the outcome`() = runBlocking {
        val runner = runner { _, _, _, _ -> StepResult("", null) }

        val value = runner.local(StepKind.RETRIEVE, "Search", input = "query") {
            LocalOutcome(value = 3, output = "3 chunks", detail = "top score 0.82")
        }

        assertEquals(3, value)
        val step = runner.recorder.trace.value.steps.single()
        assertEquals(StepStatus.DONE, step.status)
        assertEquals("3 chunks", step.output)
        assertEquals("top score 0.82", step.detail)
    }

    @Test
    fun `a local step that throws is marked failed and the exception continues up`() = runBlocking {
        val runner = runner { _, _, _, _ -> StepResult("", null) }

        try {
            runner.local<Int>(StepKind.TOOL, "Calc") { throw IllegalStateException("division by zero") }
            fail("expected the exception")
        } catch (e: IllegalStateException) {
            assertEquals("division by zero", e.message)
        }

        val step = runner.recorder.trace.value.steps.single()
        assertEquals(StepStatus.FAILED, step.status)
        assertEquals("division by zero", step.detail)
    }
}
