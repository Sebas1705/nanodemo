package es.sebas1705.nanodemo.feature.lab.refine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A right answer for every built-in task, as a model would write it. */
internal val RIGHT_ANSWERS = mapOf(
    "arith-change" to "29",
    "arith-trip" to "270",
    "arith-candies-es" to "17",
    "logic-syllogism" to "Yes",
    "logic-order" to "Lee",
    "logic-weekday-es" to "Sábado",
    "extract-date" to "14/03/2027",
    "extract-email" to "maria.lopez@example.com",
    "classify-sentiment" to "negative",
    "classify-topic-es" to "Deportes",
    "es-capital" to "Canberra",
    "es-hexagon" to "6",
)

class StrategyTasksTest {

    @Test
    fun `the built-in set has about a dozen tasks across all five categories with unique ids`() {
        val tasks = StrategyTasks.all
        assertTrue(tasks.size in 10..14)
        assertEquals(tasks.size, tasks.map { it.id }.distinct().size)
        assertEquals(TaskCategory.entries.toSet(), tasks.map { it.category }.toSet())
        assertTrue(tasks.any { it.question.contains("¿") })
        assertEquals(RIGHT_ANSWERS.keys, tasks.map { it.id }.toSet())
    }

    @Test
    fun `every task accepts its right answer in any common reply shape`() {
        StrategyTasks.all.forEach { task ->
            val right = RIGHT_ANSWERS.getValue(task.id)
            listOf(
                "Final answer: $right",
                "Let me think.\nFinal answer: $right.",
                "**Final answer: $right**",
                right,
            ).forEach { reply ->
                assertTrue("${task.id} should accept <$reply>", scoreTask(task, reply).correct)
            }
        }
    }

    @Test
    fun `every task rejects an empty, a wrong and an unrelated answer`() {
        StrategyTasks.all.forEach { task ->
            listOf("", "Final answer: 0", "Final answer: banana", "Final answer: nobody knows").forEach { reply ->
                assertFalse("${task.id} should reject <$reply>", scoreTask(task, reply).correct)
            }
        }
    }

    @Test
    fun `numbers accept equivalent writings and reject near misses`() {
        val change = StrategyTasks.byId("arith-change")!!
        assertTrue(scoreTask(change, "Final answer: 29.00 euros").correct)
        assertTrue(scoreTask(change, "Final answer: 50 - 21 = 29").correct)
        assertFalse(scoreTask(change, "Final answer: 21").correct)
        assertFalse(scoreTask(change, "Final answer: 290").correct)
        val trip = StrategyTasks.byId("arith-trip")!!
        assertTrue(scoreTask(trip, "Final answer: 270 km").correct)
        assertTrue(scoreTask(trip, "Respuesta final: 270,0").correct)
    }

    @Test
    fun `a number task may also be answered with a word`() {
        val hexagon = StrategyTasks.byId("es-hexagon")!!
        assertTrue(scoreTask(hexagon, "Final answer: seis").correct)
        assertTrue(scoreTask(hexagon, "Respuesta: Seis lados").correct)
        assertFalse(scoreTask(hexagon, "Final answer: siete").correct)
    }

    @Test
    fun `yes-no accepts Spanish and rejects the opposite`() {
        val task = StrategyTasks.byId("logic-syllogism")!!
        assertTrue(scoreTask(task, "Final answer: Sí").correct)
        assertTrue(scoreTask(task, "Final answer: true").correct)
        assertFalse(scoreTask(task, "Final answer: No").correct)
    }

    @Test
    fun `classification names exactly one label`() {
        val task = StrategyTasks.byId("classify-sentiment")!!
        assertTrue(scoreTask(task, "Final answer: Negative.").correct)
        assertTrue(scoreTask(task, "Final answer: the sentiment is negative").correct)
        assertFalse(scoreTask(task, "Final answer: positive").correct)
        assertFalse(scoreTask(task, "Final answer: negative or neutral").correct)
        val es = StrategyTasks.byId("classify-topic-es")!!
        assertTrue(scoreTask(es, "Respuesta final: deportes").correct)
        assertFalse(scoreTask(es, "Respuesta final: política").correct)
    }

    @Test
    fun `extraction ignores formatting around the value`() {
        val date = StrategyTasks.byId("extract-date")!!
        assertTrue(scoreTask(date, "Final answer: 14/03/2027").correct)
        assertTrue(scoreTask(date, "Final answer: `14-03-2027`").correct)
        assertFalse(scoreTask(date, "Final answer: 03/14/2027").correct)
        val email = StrategyTasks.byId("extract-email")!!
        assertTrue(scoreTask(email, "Final answer: Maria.Lopez@example.com").correct)
        assertFalse(scoreTask(email, "Final answer: maria.lopez@example.org").correct)
    }

    @Test
    fun `the score reports the short answer that was read`() {
        val score = scoreTask(StrategyTasks.byId("es-capital")!!, "Es una pregunta fácil.\nFinal answer: Sídney")
        assertFalse(score.correct)
        assertEquals("Sídney", score.answer)
        assertNull(StrategyTasks.byId("nope"))
    }

    // ---- prompts ----

    private val profile = EngineProfile()
    private val task = StrategyTasks.byId("arith-change")!!

    @Test
    fun `every strategy produces a prompt that contains the question`() {
        PromptStrategy.entries.forEach { strategy ->
            val step = StrategyPrompts.firstStep(strategy, task, profile)
            assertTrue("$strategy", step.prompt.contains(task.question))
            assertTrue(step.wantedTokens > 0)
        }
    }

    @Test
    fun `the strategies differ in the way the tests expect`() {
        fun prompt(s: PromptStrategy) = StrategyPrompts.firstStep(s, task, profile).prompt
        assertFalse(prompt(PromptStrategy.ZERO_SHOT).contains("step by step"))
        assertTrue(prompt(PromptStrategy.CHAIN_OF_THOUGHT).contains("Think step by step"))
        assertTrue(prompt(PromptStrategy.FEW_SHOT).contains("What is 12 + 30?"))
        assertTrue(prompt(PromptStrategy.FEW_SHOT).endsWith("Final answer:"))
        assertTrue(prompt(PromptStrategy.ROLE).startsWith("You are a careful math teacher"))
        assertTrue(prompt(PromptStrategy.STEP_BACK).contains("general principle"))
        assertTrue(StrategyPrompts.firstStep(PromptStrategy.CHAIN_OF_THOUGHT, task, profile).wantedTokens >
            StrategyPrompts.firstStep(PromptStrategy.ZERO_SHOT, task, profile).wantedTokens)
    }

    @Test
    fun `step-back needs a second call that carries the principle`() {
        assertEquals(2, PromptStrategy.STEP_BACK.callsPerTask)
        assertEquals(1, PromptStrategy.ROLE.callsPerTask)
        val second = StrategyPrompts.stepBackAnswer(task, "Change = paid - price.", profile)
        assertTrue(second.prompt.contains("Change = paid - price."))
        assertTrue(second.prompt.contains(task.question))
        assertTrue(second.prompt.contains("Final answer:"))
    }

    @Test
    fun `few-shot examples do not leak the built-in tasks`() {
        val prompt = StrategyPrompts.firstStep(PromptStrategy.FEW_SHOT, task, profile).prompt
        val examples = prompt.substringBefore("Question: ${task.question}")
        StrategyTasks.all.forEach { assertFalse(examples.contains(it.question)) }
    }

    @Test
    fun `prompts for a 1024-token engine fit even with an absurd question`() {
        val small = EngineProfile(contextTokens = 1024)
        val long = task.copy(question = "word ".repeat(10_000))
        PromptStrategy.entries.forEach { strategy ->
            val step = StrategyPrompts.firstStep(strategy, long, small)
            assertTrue("$strategy", approxTokens(step.prompt) + small.outputTokens(step.wantedTokens) <= 1024)
        }
        val second = StrategyPrompts.stepBackAnswer(long, "principle ".repeat(5_000), small)
        assertTrue(approxTokens(second.prompt) + small.outputTokens(second.wantedTokens) <= 1024)
    }
}
