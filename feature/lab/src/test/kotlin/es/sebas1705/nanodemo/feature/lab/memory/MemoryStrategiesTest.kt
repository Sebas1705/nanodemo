package es.sebas1705.nanodemo.feature.lab.memory

import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.Role
import es.sebas1705.nanodemo.domain.models.StepResult
import es.sebas1705.nanodemo.feature.lab.trace.PipelineRunner
import es.sebas1705.nanodemo.feature.lab.trace.StepExecutor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryStrategiesTest {

    private val trip = MemoryScenarios.trip
    private val smallBudget = 600 // tokens; the whole trip conversation is well above this

    @Test
    fun `the full conversation is bigger than a small budget`() {
        assertTrue(estimateTokens(buildFull(trip.turns, trip.question)) > smallBudget)
    }

    @Test
    fun `fitting recent turns keeps the newest ones in order and never splits a turn`() {
        val turns = (1..6).map { Turn("user $it ".repeat(10), "assistant $it ".repeat(10)) }
        val kept = fitRecentTurns(turns, budgetTokens = 120)

        assertTrue(kept.isNotEmpty() && kept.size < turns.size)
        assertEquals(turns.takeLast(kept.size), kept)
        assertTrue(estimateTokens(turnsToMessages(kept)) <= 120)
    }

    @Test
    fun `a budget too small for any turn keeps none`() {
        assertTrue(fitRecentTurns(trip.turns, budgetTokens = 5).isEmpty())
    }

    @Test
    fun `the window strategy forgets the early facts and fits the budget`() {
        val messages = buildWindow(trip.turns, trip.question, smallBudget)

        assertTrue(estimateTokens(messages) <= smallBudget)
        assertEquals(trip.question, messages.last().text)
        assertEquals(Role.USER, messages.last().role)
        assertFalse(messages.any { "Luna" in it.text }) // said in turn 1, long gone
    }

    @Test
    fun `the summary plan splits recent from older without losing turns`() {
        val plan = planSummary(trip.turns, trip.question, smallBudget)

        assertTrue(plan.toSummarize.isNotEmpty())
        assertEquals(trip.turns, plan.toSummarize + plan.recent)
    }

    @Test
    fun `the summarized prompt starts with the summary and ends with the question`() {
        val plan = planSummary(trip.turns, trip.question, smallBudget)
        val messages = buildSummarized(plan, " Marta, Lisboa, Luna. ", trip.question)

        assertTrue(messages.first().text.startsWith("Summary of the earlier conversation: Marta"))
        assertEquals(Role.MODEL, messages[1].role)
        assertEquals(trip.question, messages.last().text)
    }

    @Test
    fun `facts are read from bullets, numbers and json-ish replies`() {
        assertEquals(listOf("Her dog is Luna", "She travels to Lisbon"), parseFacts("- Her dog is Luna\n- She travels to Lisbon"))
        assertEquals(listOf("Dog: Luna", "City: Lisbon"), parseFacts("1. Dog: Luna\n2) City: Lisbon"))
        assertEquals(listOf("Dog is Luna", "Trip to Lisbon"), parseFacts("""["Dog is Luna", "Trip to Lisbon"]"""))
        assertEquals(listOf("The dog is called Luna"), parseFacts("```\nThe dog is called Luna\n```"))
    }

    @Test
    fun `duplicate and tiny facts are dropped and the list is capped`() {
        val reply = "- Dog is Luna\n- dog is luna!\n- ok\n" + (1..40).joinToString("\n") { "- distinct fact number $it" }
        val facts = parseFacts(reply)

        assertEquals(24, facts.size)
        assertEquals(1, facts.count { it.contains("luna", ignoreCase = true) })
    }

    @Test
    fun `fact selection returns everything when few and ranks by overlap when many`() {
        assertEquals(listOf("a fact", "b fact"), selectFacts(listOf("a fact", "b fact"), "anything", max = 12))

        val many = (1..20).map { "unrelated detail $it" } + "the dog is called Luna"
        val selected = selectFacts(many, "How is my dog called?", max = 3)
        assertEquals(3, selected.size)
        assertEquals("the dog is called Luna", selected.first())
    }

    @Test
    fun `chunking groups consecutive turns within the limit`() {
        val groups = chunkTurns(trip.turns, maxTokens = 200)

        assertTrue(groups.size > 1)
        assertEquals(trip.turns, groups.flatten())
        assertTrue(groups.all { estimateTokens(turnsToMessages(it)) <= 200 + 40 })
    }

    @Test
    fun `recall counts every expected fact that appears in any accepted spelling`() {
        assertEquals(1.0, recallScore("Luna viaja a Lisboa con 1.200 euros", trip.expected), 0.0)
        assertEquals(2.0 / 3, recallScore("Luna va a Lisboa", trip.expected), 1e-9)
        assertEquals(0.0, recallScore("No lo sé", trip.expected), 0.0)
        assertEquals(1.0, recallScore("anything", emptyList()), 0.0)
    }

    // --- the runner, with a fake model that only answers well when the fact is in the prompt it receives ---

    private val factsReply = "- Marta travels to Lisboa with her dog Luna\n- Budget is 1200 euros for three people"

    private fun fakeModel(calls: MutableList<String>) = StepExecutor { _, messages, _, _ ->
        val prompt = messages.joinToString("\n") { it.text }
        calls += prompt.take(30)
        when {
            prompt.startsWith("Summarize the conversation") -> StepResult("Marta is going to Lisboa with her dog Luna, budget 1200 euros.", null)
            prompt.startsWith("List every fact") -> StepResult(factsReply, null)
            else -> {
                val remembered = listOf("Luna", "Lisboa", "1200").filter { it in prompt }
                StepResult(if (remembered.isEmpty()) "I do not know." else remembered.joinToString(", "), null)
            }
        }
    }

    private fun runner(calls: MutableList<String> = mutableListOf()) = MemoryRunner(PipelineRunner(fakeModel(calls)))

    @Test
    fun `full history recalls everything but overflows a small budget`() = runBlocking {
        val result = runner().run(MemoryStrategy.FULL, trip, InferenceTier.MEDIAPIPE_LOCAL, smallBudget)

        assertEquals(1.0, result.recall, 0.0)
        assertTrue(result.overflow)
        assertEquals(1, result.modelCalls)
    }

    @Test
    fun `a window forgets what was said early`() = runBlocking {
        val result = runner().run(MemoryStrategy.WINDOW, trip, InferenceTier.MEDIAPIPE_LOCAL, smallBudget)

        assertFalse(result.overflow)
        assertTrue(result.recall < 1.0)
    }

    @Test
    fun `the summary strategy recalls the early facts within budget`() = runBlocking {
        val calls = mutableListOf<String>()
        val result = runner(calls).run(MemoryStrategy.SUMMARY, trip, InferenceTier.MEDIAPIPE_LOCAL, smallBudget)

        assertEquals(1.0, result.recall, 0.0)
        assertFalse(result.overflow)
        assertTrue(result.modelCalls >= 2) // at least one summary + the answer
        assertTrue(calls.first().startsWith("Summarize"))
    }

    @Test
    fun `the facts strategy recalls the early facts within budget`() = runBlocking {
        val result = runner().run(MemoryStrategy.FACTS, trip, InferenceTier.MEDIAPIPE_LOCAL, smallBudget)

        assertEquals(1.0, result.recall, 0.0)
        assertFalse(result.overflow)
        assertTrue(result.modelCalls >= 2)
    }

    @Test
    fun `a failing helper call stops the strategy with the error`() = runBlocking {
        val failing = MemoryRunner(PipelineRunner(StepExecutor { _, _, _, _ -> StepResult("", null, "engine down") }))

        val result = failing.run(MemoryStrategy.SUMMARY, trip, InferenceTier.MEDIAPIPE_LOCAL, smallBudget)

        assertEquals("engine down", result.error)
        assertEquals(1, result.modelCalls)
    }

    @Test
    fun `every strategy is traced and the recall check is the last step`() = runBlocking {
        val pipeline = PipelineRunner(fakeModel(mutableListOf()))
        MemoryRunner(pipeline).run(MemoryStrategy.FACTS, MemoryScenarios.project, InferenceTier.LITERT_LM_LOCAL, 1_000)

        val steps = pipeline.recorder.trace.value.steps
        assertTrue(steps.size >= 3)
        assertEquals("Check recall", steps.last().title)
    }

    @Test
    fun `the scenarios have early facts and an answerable question`() {
        for (scenario in MemoryScenarios.all) {
            assertTrue(scenario.turns.size >= 8)
            assertTrue(scenario.expected.size >= 3)
            // The facts must be stated before the last third of the conversation, or a window would not lose them.
            val early = scenario.turns.take(scenario.turns.size / 2).joinToString(" ") { it.user }
            assertTrue(scenario.expected.any { options -> options.any { it in early } })
        }
    }
}
