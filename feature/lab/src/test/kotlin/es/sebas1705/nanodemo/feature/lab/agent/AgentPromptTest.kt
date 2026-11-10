package es.sebas1705.nanodemo.feature.lab.agent

import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.feature.lab.agent.tools.CalculatorTool
import es.sebas1705.nanodemo.feature.lab.agent.tools.DeviceInfoTool
import es.sebas1705.nanodemo.feature.lab.agent.tools.DeviceSnapshot
import es.sebas1705.nanodemo.feature.lab.agent.tools.InMemoryNotesStore
import es.sebas1705.nanodemo.feature.lab.agent.tools.NotesTool
import es.sebas1705.nanodemo.feature.lab.agent.tools.UnitConverterTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentPromptTest {

    private val tools = listOf(CalculatorTool(), DeviceInfoTool { DeviceSnapshot() }, NotesTool(InMemoryNotesStore()), UnitConverterTool())

    // ---- budgets ----

    @Test
    fun `small engines get a compact prompt and a small reply allowance`() {
        val mediaPipe = AgentBudget.forTier(InferenceTier.MEDIAPIPE_LOCAL, 1024)
        val liteRt = AgentBudget.forTier(InferenceTier.LITERT_LM_LOCAL, 2048)
        assertEquals(PromptStyle.COMPACT, mediaPipe.style)
        assertEquals(PromptStyle.COMPACT, liteRt.style)
        assertTrue(mediaPipe.maxOutputTokens < liteRt.maxOutputTokens)
        assertTrue(mediaPipe.promptChars < liteRt.promptChars)
        // The whole window is 4096 characters; the prompt plus the reply plus the backend preamble must fit.
        val total = mediaPipe.promptChars + mediaPipe.maxOutputTokens * AgentBudget.CHARS_PER_TOKEN + AgentBudget.BACKEND_OVERHEAD_CHARS
        assertTrue("total $total", total <= 1024 * AgentBudget.CHARS_PER_TOKEN)
    }

    @Test
    fun `unmeasured engines depend on the tier`() {
        val nano = AgentBudget.forTier(InferenceTier.ML_KIT_NANO, null)
        val cloud = AgentBudget.forTier(InferenceTier.CLOUD_GEMINI, null)
        assertEquals(PromptStyle.FULL, cloud.style)
        assertTrue(nano.promptChars < cloud.promptChars)
        assertTrue(nano.promptChars > 3_000)
        assertEquals(cloud, AgentBudget.forTier(InferenceTier.FIREBASE_AI, null))
    }

    @Test
    fun `tool output cap scales with the budget`() {
        assertTrue(AgentBudget.forTier(InferenceTier.MEDIAPIPE_LOCAL, 1024).toolOutputChars < AgentBudget.forTier(InferenceTier.CLOUD_GEMINI, null).toolOutputChars)
    }

    // ---- fixed part ----

    @Test
    fun `the fixed part lists every enabled tool and nothing else`() {
        val text = AgentPrompt.fixedPart(listOf(CalculatorTool()), PromptStyle.FULL)
        assertTrue(text.contains("- calculator"))
        assertFalse(text.contains("device_info"))
        assertTrue(text.contains("Final Answer"))
        assertTrue(text.contains("Action Input"))
    }

    @Test
    fun `compact style drops descriptions and the example`() {
        val full = AgentPrompt.fixedPart(tools, PromptStyle.FULL)
        val compact = AgentPrompt.fixedPart(tools, PromptStyle.COMPACT)
        assertTrue(compact.length < full.length)
        assertTrue(full.contains("Example:"))
        assertFalse(compact.contains("Example:"))
        assertFalse(compact.contains(CalculatorTool().description))
    }

    @Test
    fun `no example without a calculator and a hint when there are no tools`() {
        assertFalse(AgentPrompt.fixedPart(listOf(UnitConverterTool()), PromptStyle.FULL).contains("Example:"))
        assertTrue(AgentPrompt.fixedPart(emptyList(), PromptStyle.FULL).contains("no tools"))
    }

    @Test
    fun `the compact fixed part for all tools fits a one thousand token window`() {
        val budget = AgentBudget.forTier(InferenceTier.MEDIAPIPE_LOCAL, 1024)
        val fixed = AgentPrompt.fixedPart(tools, budget.style).length
        assertTrue("fixed $fixed of ${budget.promptChars}", fixed < budget.promptChars * 0.6)
    }

    // ---- build ----

    private val cloud = AgentBudget.forTier(InferenceTier.CLOUD_GEMINI, null)
    private val tiny = AgentBudget.forTier(InferenceTier.MEDIAPIPE_LOCAL, 1024)

    @Test
    fun `the first prompt has the task and no scratchpad`() {
        val prompt = AgentPrompt.build("What is 2+2?", tools, emptyList(), cloud, lastStep = false)
        assertTrue(prompt.contains("Task: What is 2+2?"))
        assertFalse(prompt.contains(AgentPrompt.LAST_STEP_NUDGE))
        assertTrue(prompt.trimEnd().endsWith("Task: What is 2+2?"))
    }

    @Test
    fun `later prompts carry the scratchpad and the last step is nudged`() {
        val entries = listOf(ScratchEntry("calc", "calculator", "{\"expression\":\"2+2\"}", "4"))
        val prompt = AgentPrompt.build("What is 2+2?", tools, entries, cloud, lastStep = true)
        assertTrue(prompt.contains("Action: calculator\nAction Input: {\"expression\":\"2+2\"}\nObservation: 4"))
        assertTrue(prompt.contains("Thought: calc"))
        assertTrue(prompt.endsWith(AgentPrompt.LAST_STEP_NUDGE))
    }

    @Test
    fun `a long run stays inside a tiny window and keeps the latest observation`() {
        val entries = (1..12).map { ScratchEntry("thought number $it ".repeat(8), "calculator", "{\"expression\":\"$it+$it\"}", "result-$it ".repeat(30)) }
        val prompt = AgentPrompt.build("Do a long task", tools, entries, tiny, lastStep = true)
        assertTrue("length ${prompt.length} > ${tiny.promptChars}", prompt.length <= tiny.promptChars)
        assertTrue(prompt.contains("result-12"))
        assertTrue(prompt.contains("earlier step(s) omitted"))
        assertTrue(prompt.endsWith(AgentPrompt.LAST_STEP_NUDGE))
    }

    @Test
    fun `build is deterministic`() {
        val entries = listOf(ScratchEntry(null, "calculator", "1", "1"))
        assertEquals(
            AgentPrompt.build("t", tools, entries, tiny, false),
            AgentPrompt.build("t", tools, entries, tiny, false),
        )
    }

    // ---- scratchpad ----

    private fun entry(i: Int, observation: String = "obs$i", thought: String? = "thought $i") =
        ScratchEntry(thought, "calculator", "{\"expression\":\"$i\"}", observation)

    @Test
    fun `an empty scratchpad is empty`() {
        assertEquals("", AgentPrompt.renderScratchpad(emptyList(), 100))
    }

    @Test
    fun `a scratchpad that fits is rendered in full and in order`() {
        val text = AgentPrompt.renderScratchpad(listOf(entry(1), entry(2)), 1_000)
        assertTrue(text.indexOf("Thought: thought 1") < text.indexOf("Observation: obs1"))
        assertTrue(text.indexOf("obs1") < text.indexOf("thought 2"))
        assertTrue(text.contains("Observation: obs2"))
    }

    @Test
    fun `old thoughts are dropped first`() {
        val entries = listOf(entry(1, "a".repeat(10)), entry(2, "b".repeat(10)))
        val full = AgentPrompt.renderScratchpad(entries, 10_000)
        val tight = AgentPrompt.renderScratchpad(entries, full.length - 5)
        assertFalse(tight.contains("thought 1"))
        assertTrue(tight.contains("thought 2"))
        assertTrue(tight.contains("a".repeat(10)))
    }

    @Test
    fun `old observations are shortened before whole steps are dropped`() {
        val entries = listOf(entry(1, "a".repeat(400)), entry(2, "b".repeat(50)))
        val text = AgentPrompt.renderScratchpad(entries, 420)
        assertTrue(text.length <= 420)
        assertFalse(text.contains("earlier step"))
        assertTrue(text.contains("[truncated]"))
        assertTrue(text.contains("b".repeat(50)))
    }

    @Test
    fun `the oldest steps are dropped with a note and the newest is always kept`() {
        val entries = (1..10).map { entry(it, "x".repeat(60)) }
        val text = AgentPrompt.renderScratchpad(entries, 400)
        assertTrue(text.length <= 400)
        assertTrue(text.startsWith("[") && text.contains("earlier step(s) omitted"))
        assertTrue(text.contains("{\"expression\":\"10\"}"))
        assertFalse(text.contains("{\"expression\":\"1\"}"))
    }

    @Test
    fun `an enormous last observation is shortened but never removed`() {
        val text = AgentPrompt.renderScratchpad(listOf(entry(1, "z".repeat(5_000))), 300)
        assertTrue(text.length <= 300 + 20)
        assertTrue(text.contains("Observation: zzzz"))
        assertTrue(text.contains("[truncated]"))
    }

    @Test
    fun `a hopelessly small budget still returns the start of the last observation`() {
        val text = AgentPrompt.renderScratchpad(listOf(entry(1, "important result ".repeat(20))), 10)
        assertTrue(text.contains("important result"))
    }

    @Test
    fun `entries without an action render as a bare observation`() {
        val text = AgentPrompt.renderScratchpad(listOf(ScratchEntry(null, null, null, "Format error")), 500)
        assertEquals("Observation: Format error", text)
    }

    @Test
    fun `newlines in thoughts and inputs do not break the format`() {
        val text = AgentPrompt.renderScratchpad(listOf(ScratchEntry("a\nb", "notes", "{\n\"x\": 1\n}", "ok")), 500)
        assertEquals(1, Regex("(?m)^Action:").findAll(text).count())
        assertTrue(text.contains("Thought: a b"))
    }
}
