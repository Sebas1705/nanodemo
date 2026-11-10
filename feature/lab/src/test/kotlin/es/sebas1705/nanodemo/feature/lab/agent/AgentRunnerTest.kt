package es.sebas1705.nanodemo.feature.lab.agent

import es.sebas1705.nanodemo.domain.models.ChatMessage
import es.sebas1705.nanodemo.domain.models.GenerationConfig
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.StepKind
import es.sebas1705.nanodemo.domain.models.StepResult
import es.sebas1705.nanodemo.domain.models.StepStatus
import es.sebas1705.nanodemo.feature.lab.agent.tools.CalculatorTool
import es.sebas1705.nanodemo.feature.lab.agent.tools.InMemoryNotesStore
import es.sebas1705.nanodemo.feature.lab.agent.tools.NotesTool
import es.sebas1705.nanodemo.feature.lab.agent.tools.TRUNCATION_MARK
import es.sebas1705.nanodemo.feature.lab.agent.tools.Tool
import es.sebas1705.nanodemo.feature.lab.agent.tools.ToolArgs
import es.sebas1705.nanodemo.feature.lab.agent.tools.ToolRegistry
import es.sebas1705.nanodemo.feature.lab.agent.tools.ToolResult
import es.sebas1705.nanodemo.feature.lab.trace.PipelineRunner
import es.sebas1705.nanodemo.feature.lab.trace.StepExecutor
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentRunnerTest {

    private val tier = InferenceTier.CLOUD_GEMINI

    private fun registry() = ToolRegistry(listOf(CalculatorTool(), NotesTool(InMemoryNotesStore())))

    private fun harness(
        executor: StepExecutor,
        clock: () -> Long = System::currentTimeMillis,
    ): Pair<AgentRunner, PipelineRunner> {
        val pipeline = PipelineRunner(executor)
        return AgentRunner(pipeline, clock) to pipeline
    }

    private fun AgentRunner.runTask(
        task: String = "task",
        registry: ToolRegistry = registry(),
        capabilities: es.sebas1705.nanodemo.domain.models.TierCapabilities = caps(),
        settings: AgentSettings = AgentSettings(),
        tier: InferenceTier = InferenceTier.CLOUD_GEMINI,
    ) = runBlocking { run(task, tier, registry, capabilities, settings) }

    // ---- success paths ----

    @Test
    fun `one tool call then the final answer`() {
        val executor = ScriptedExecutor(listOf(calc("0.17*2350"), finalAnswer("399.5")))
        val (agent, pipeline) = harness(executor)

        val result = agent.runTask("17% of 2350")

        assertEquals(AgentOutcome.ANSWERED, result.outcome)
        assertTrue(result.succeeded)
        assertEquals("399.5", result.answer)
        assertEquals(2, result.steps)
        assertEquals(1, result.toolCalls.size)
        assertEquals("calculator", result.toolCalls[0].tool)
        assertEquals("399.5", result.toolCalls[0].output)
        assertTrue(result.toolCalls[0].ok)
        assertEquals(0, result.parseFailures)
        assertEquals(0, result.toolErrors)
        assertEquals(0, result.loopsDetected)

        // The second prompt shows the model its own action and the observation.
        assertTrue(executor.prompts[1].contains("Task: 17% of 2350"))
        assertTrue(executor.prompts[1].contains("Observation: 399.5"))
        assertFalse(executor.prompts[0].contains("Observation: 399.5"))

        // The trace: generate, tool, generate, final.
        val steps = pipeline.recorder.trace.value.steps
        assertEquals(listOf(StepKind.GENERATE, StepKind.TOOL, StepKind.GENERATE, StepKind.VALIDATE), steps.map { it.kind })
        assertTrue(steps.all { it.status == StepStatus.DONE })
        assertEquals("399.5", steps[3].output)
    }

    @Test
    fun `an answer with no tool is a single step`() {
        val executor = ScriptedExecutor(listOf(finalAnswer("París")))
        val (agent, pipeline) = harness(executor)
        val result = agent.runTask("capital of France")
        assertEquals("París", result.answer)
        assertEquals(1, result.steps)
        assertTrue(result.toolCalls.isEmpty())
        assertEquals(2, pipeline.recorder.trace.value.steps.size)
    }

    @Test
    fun `an unlabelled answer is accepted and counted`() {
        val (agent, _) = harness(ScriptedExecutor(listOf("It is 4.")))
        val result = agent.runTask()
        assertEquals(AgentOutcome.ANSWERED, result.outcome)
        assertEquals("It is 4.", result.answer)
        assertEquals(1, result.implicitAnswers)
        assertEquals(0, result.parseFailures)
    }

    @Test
    fun `the prompt is sent as one user message with the engine config`() {
        var seenMessages: List<ChatMessage>? = null
        var seenTier: InferenceTier? = null
        var seenConfig: GenerationConfig? = null
        val (agent, _) = harness(
            StepExecutor { t, messages, config, _ ->
                seenTier = t
                seenMessages = messages
                seenConfig = config
                StepResult(finalAnswer("x"), null)
            },
        )
        agent.runTask(tier = InferenceTier.LITERT_LM_LOCAL, capabilities = caps(2048))
        assertEquals(InferenceTier.LITERT_LM_LOCAL, seenTier)
        assertEquals(1, seenMessages!!.size)
        assertEquals(224, seenConfig!!.maxOutputTokens)
        assertEquals(0.2f, seenConfig!!.temperature)
        assertFalse(seenConfig!!.functionCalling)
        assertFalse(seenConfig!!.jsonMode)
        assertEquals(null, seenConfig!!.systemPrompt)
    }

    @Test
    fun `tokens and duration are accumulated`() {
        var now = 0L
        val (agent, _) = harness({ _, _, _, _ ->
            now += 250
            StepResult(if (now < 500) calc("1+1") else finalAnswer("2"), metric = null)
        }, clock = { now })
        val result = agent.runTask()
        assertEquals(2, result.steps)
        assertEquals(500L, result.durationMs)
        assertTrue(result.tokens > 0)
        assertTrue(result.peakPromptChars > 0)
    }

    @Test
    fun `an action and a final answer in one reply runs the action first`() {
        val executor = ScriptedExecutor(
            listOf(
                action("calculator", "{\"expression\": \"6*7\"}") + "\nObservation: 41\nFinal Answer: 41",
                finalAnswer("42"),
            ),
        )
        val (agent, _) = harness(executor)
        val result = agent.runTask()
        assertEquals("42", result.answer)
        assertEquals("42", result.toolCalls.single().output)
    }

    @Test
    fun `only the first of several actions runs`() {
        val executor = ScriptedExecutor(
            listOf(calc("1+1") + "\nAction: calculator\nAction Input: {\"expression\": \"9+9\"}", finalAnswer("2")),
        )
        val (agent, _) = harness(executor)
        val result = agent.runTask()
        assertEquals(1, result.toolCalls.size)
        assertEquals("2", result.toolCalls[0].output)
    }

    // ---- parse failures ----

    @Test
    fun `a malformed reply is corrected and the run recovers`() {
        val executor = ScriptedExecutor(listOf("Thought: let me think", calc("2*3"), finalAnswer("6")))
        val (agent, pipeline) = harness(executor)

        val result = agent.runTask()

        assertEquals(AgentOutcome.ANSWERED, result.outcome)
        assertEquals(1, result.parseFailures)
        assertTrue(executor.prompts[1].contains("Format error"))
        val failedFormat = pipeline.recorder.trace.value.steps.single { it.title == "Invalid format" }
        assertEquals(StepStatus.FAILED, failedFormat.status)
        assertEquals("Thought: let me think", failedFormat.input)
    }

    @Test
    fun `the format error counter resets after a good reply`() {
        val executor = ScriptedExecutor(
            listOf("Thought: a", "Thought: b", calc("1+1"), "Thought: c", "Thought: d", finalAnswer("2")),
        )
        val (agent, _) = harness(executor)
        val result = agent.runTask(settings = AgentSettings(maxSteps = 10))
        assertEquals(AgentOutcome.ANSWERED, result.outcome)
        assertEquals(4, result.parseFailures)
    }

    @Test
    fun `too many format errors in a row end the run`() {
        val executor = ScriptedExecutor(listOf("Thought: a", "Thought: b", "Thought: c"))
        val (agent, pipeline) = harness(executor)
        val result = agent.runTask(settings = AgentSettings(maxSteps = 10, maxFormatErrors = 2))
        assertEquals(AgentOutcome.FORMAT_FAILURE, result.outcome)
        assertFalse(result.succeeded)
        assertEquals(3, result.parseFailures)
        assertEquals(3, result.steps)
        assertEquals("c", result.answer)
        assertEquals(StepStatus.FAILED, pipeline.recorder.trace.value.steps.last().status)
    }

    // ---- tool errors ----

    @Test
    fun `a tool error is shown to the model and the run continues`() {
        val executor = ScriptedExecutor(listOf(calc("1/0"), finalAnswer("cannot divide by zero")))
        val (agent, pipeline) = harness(executor)
        val result = agent.runTask()
        assertEquals(AgentOutcome.ANSWERED, result.outcome)
        assertEquals(1, result.toolErrors)
        assertFalse(result.toolCalls.single().ok)
        assertTrue(executor.prompts[1].contains("Observation: Error: division by zero"))
        val toolStep = pipeline.recorder.trace.value.steps.single { it.kind == StepKind.TOOL }
        assertEquals(StepStatus.FAILED, toolStep.status)
        assertTrue(toolStep.detail!!.contains("division by zero"))
    }

    @Test
    fun `an unknown tool is an error that names the real ones`() {
        val executor = ScriptedExecutor(listOf(action("teleport", "{}"), finalAnswer("ok")))
        val (agent, _) = harness(executor)
        val result = agent.runTask()
        assertEquals(1, result.toolErrors)
        assertEquals("teleport", result.toolCalls.single().tool)
        assertTrue(executor.prompts[1].contains("Available tools: calculator, notes"))
    }

    @Test
    fun `a disabled tool cannot be called`() {
        val executor = ScriptedExecutor(listOf(action("notes", "{\"action\": \"read\"}"), finalAnswer("ok")))
        val (agent, _) = harness(executor)
        val onlyCalc = ToolRegistry(listOf(CalculatorTool()))
        val result = agent.runTask(registry = onlyCalc)
        assertEquals(1, result.toolErrors)
        assertFalse(executor.prompts[0].contains("- notes"))
    }

    @Test
    fun `a failing tool does not stop the loop`() {
        val boom = object : Tool {
            override val name = "boom"
            override val description = "always fails"
            override val parameterHint = "{}"
            override suspend fun execute(args: ToolArgs): ToolResult = error("kaput")
        }
        val executor = ScriptedExecutor(listOf(action("boom", "{}"), finalAnswer("sorry")))
        val (agent, _) = harness(executor)
        val result = agent.runTask(registry = ToolRegistry(listOf(boom)))
        assertEquals(AgentOutcome.ANSWERED, result.outcome)
        assertEquals(1, result.toolErrors)
        assertTrue(executor.prompts[1].contains("kaput"))
    }

    // ---- loop detection ----

    @Test
    fun `a repeated action is not executed again and the model is warned`() {
        val executor = ScriptedExecutor(listOf(calc("1+1"), calc("1+1"), finalAnswer("2")))
        val (agent, pipeline) = harness(executor)
        val result = agent.runTask()
        assertEquals(AgentOutcome.ANSWERED, result.outcome)
        assertEquals(1, result.loopsDetected)
        assertEquals(1, result.toolCalls.size)
        assertTrue(executor.prompts[2].contains("already ran this exact action and got: 2"))
        val skipped = pipeline.recorder.trace.value.steps.filter { it.status == StepStatus.SKIPPED }
        assertEquals(1, skipped.size)
        assertEquals(StepKind.TOOL, skipped.single().kind)
    }

    @Test
    fun `repeating the same action again ends the run as a loop`() {
        val executor = ScriptedExecutor(listOf(calc("1+1")))
        val (agent, pipeline) = harness(executor)
        val result = agent.runTask(settings = AgentSettings(maxSteps = 10))
        assertEquals(AgentOutcome.LOOP, result.outcome)
        assertEquals(2, result.loopsDetected)
        assertEquals(1, result.toolCalls.size)
        assertEquals(3, result.steps)
        assertEquals("2", result.answer)
        assertEquals(StepStatus.FAILED, pipeline.recorder.trace.value.steps.last().status)
    }

    @Test
    fun `spacing quoting and key order do not hide a repeat`() {
        val executor = ScriptedExecutor(
            listOf(
                action("calculator", "{\"expression\": \"1+1\"}"),
                action("calculator", "{'expression':'1+1',}"),
                action("calculator", "{ \"EXPRESSION\" : \"1+1\" }"),
            ),
        )
        val (agent, _) = harness(executor)
        val result = agent.runTask(settings = AgentSettings(maxSteps = 10))
        assertEquals(AgentOutcome.LOOP, result.outcome)
        assertEquals(1, result.toolCalls.size)
    }

    @Test
    fun `different inputs are not a loop`() {
        var n = 0
        val (agent, _) = harness({ _, _, _, _ ->
            n++
            StepResult(if (n <= 3) calc("$n+1") else finalAnswer("done"), null)
        })
        val result = agent.runTask(settings = AgentSettings(maxSteps = 10))
        assertEquals(AgentOutcome.ANSWERED, result.outcome)
        assertEquals(0, result.loopsDetected)
        assertEquals(3, result.toolCalls.size)
    }

    // ---- step limit ----

    @Test
    fun `the step limit ends the run and the last step is nudged to answer`() {
        var n = 0
        val executor = ScriptedExecutor(emptyList())
        val (agent, pipeline) = harness({ t, m, c, p ->
            n++
            executor.prompts += m.last().text
            StepResult(calc("$n*$n"), null)
        })
        val result = agent.runTask(settings = AgentSettings(maxSteps = 3))
        assertEquals(AgentOutcome.MAX_STEPS, result.outcome)
        assertEquals(3, result.steps)
        assertEquals(3, result.toolCalls.size)
        assertFalse(executor.prompts[0].contains(AgentPrompt.LAST_STEP_NUDGE))
        assertFalse(executor.prompts[1].contains(AgentPrompt.LAST_STEP_NUDGE))
        assertTrue(executor.prompts[2].contains(AgentPrompt.LAST_STEP_NUDGE))
        val last = pipeline.recorder.trace.value.steps.last()
        assertEquals(StepStatus.FAILED, last.status)
        assertTrue(last.detail!!.contains("3 steps"))
    }

    @Test
    fun `a nudged last step can still answer`() {
        var n = 0
        val (agent, _) = harness({ _, _, _, _ ->
            n++
            StepResult(if (n < 3) calc("$n+$n") else finalAnswer("done"), null)
        })
        val result = agent.runTask(settings = AgentSettings(maxSteps = 3))
        assertEquals(AgentOutcome.ANSWERED, result.outcome)
        assertEquals(3, result.steps)
    }

    // ---- time budget ----

    @Test
    fun `the time budget ends the run between steps`() {
        var now = 0L
        var n = 0
        val (agent, pipeline) = harness({ _, _, _, _ ->
            now += 100
            n++
            StepResult(calc("$n+0"), null)
        }, clock = { now })
        val result = agent.runTask(settings = AgentSettings(maxSteps = 10, timeBudgetMs = 150))
        assertEquals(AgentOutcome.TIMEOUT, result.outcome)
        assertEquals(2, result.steps)
        assertEquals(StepStatus.FAILED, pipeline.recorder.trace.value.steps.last().status)
    }

    @Test
    fun `a model call that outlasts the budget is cut off and its step is closed`() {
        val (agent, pipeline) = harness({ _, _, _, _ ->
            delay(10_000)
            StepResult(finalAnswer("late"), null)
        })
        val result = agent.runTask(settings = AgentSettings(timeBudgetMs = 80))
        assertEquals(AgentOutcome.TIMEOUT, result.outcome)
        assertEquals(1, result.steps)
        assertTrue(pipeline.recorder.trace.value.steps.none { it.status == StepStatus.RUNNING })
    }

    // ---- model errors and cancellation ----

    @Test
    fun `a model error ends the run with the error`() {
        val (agent, pipeline) = harness({ _, _, _, _ -> StepResult("", null, "engine unavailable") })
        val result = agent.runTask()
        assertEquals(AgentOutcome.MODEL_ERROR, result.outcome)
        assertEquals("engine unavailable", result.error)
        assertEquals(1, result.steps)
        assertTrue(pipeline.recorder.trace.value.failed)
    }

    @Test
    fun `cancelling leaves no running step in the trace`() = runBlocking {
        val (agent, pipeline) = harness({ _, _, _, _ ->
            delay(60_000)
            StepResult(finalAnswer("never"), null)
        })
        val job = launch { agent.run("task", tier, registry(), caps(), AgentSettings()) }
        delay(100)
        job.cancelAndJoin()
        val steps = pipeline.recorder.trace.value.steps
        assertEquals(1, steps.size)
        assertEquals(StepStatus.FAILED, steps.single().status)
    }

    // ---- context budget ----

    @Test
    fun `on a tiny window every prompt fits and the reply allowance is small`() {
        val big = object : Tool {
            override val name = "dump"
            override val description = "returns a lot"
            override val parameterHint = "{}"
            override val maxOutputChars = 50_000
            override suspend fun execute(args: ToolArgs): ToolResult = ToolResult.Ok("data ".repeat(2_000))
        }
        var n = 0
        val prompts = mutableListOf<String>()
        val configs = mutableListOf<GenerationConfig>()
        val (agent, _) = harness({ _, m, c, _ ->
            n++
            prompts += m.last().text
            configs += c
            StepResult(if (n < 5) action("dump", "{\"n\": $n}") else finalAnswer("ok"), null)
        })
        val budget = AgentBudget.forTier(InferenceTier.MEDIAPIPE_LOCAL, 1024)
        val result = agent.runTask(
            registry = ToolRegistry(listOf(big, CalculatorTool())),
            capabilities = caps(1024),
            tier = InferenceTier.MEDIAPIPE_LOCAL,
            settings = AgentSettings(maxSteps = 8),
        )
        assertEquals(AgentOutcome.ANSWERED, result.outcome)
        assertTrue(prompts.all { it.length <= budget.promptChars })
        assertTrue(prompts[1].contains(TRUNCATION_MARK.trim()))
        assertTrue(configs.all { it.maxOutputTokens == budget.maxOutputTokens })
        assertTrue(result.peakPromptChars <= budget.promptChars)
    }

    @Test
    fun `the scratchpad of a long run keeps growing only within the budget on a cloud engine too`() {
        var n = 0
        val prompts = mutableListOf<String>()
        val (agent, _) = harness({ _, m, _, _ ->
            n++
            prompts += m.last().text
            StepResult(if (n < 30) calc("$n+1") else finalAnswer("done"), null)
        })
        val budget = AgentBudget.forTier(tier, null)
        val result = agent.runTask(settings = AgentSettings(maxSteps = 40))
        assertEquals(AgentOutcome.ANSWERED, result.outcome)
        assertTrue(prompts.all { it.length <= budget.promptChars })
    }
}
