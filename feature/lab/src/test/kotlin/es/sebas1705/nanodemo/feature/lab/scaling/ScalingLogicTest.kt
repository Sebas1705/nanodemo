package es.sebas1705.nanodemo.feature.lab.scaling

import es.sebas1705.nanodemo.domain.models.GenerationMetric
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.StepResult
import es.sebas1705.nanodemo.feature.lab.trace.StepExecutor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScalingLogicTest {

    private fun point(chars: Int, ttft: Long?, ok: Boolean = true, tier: InferenceTier = InferenceTier.MEDIAPIPE_LOCAL) =
        ScalingPoint(tier, chars, ttft, totalMs = (ttft ?: 0) + 100, ok = ok)

    @Test
    fun `the padded prompt has the requested size before the question and is deterministic`() {
        val prompt = paddedPrompt(800)
        assertEquals(prompt, paddedPrompt(800))
        assertTrue(prompt.endsWith("Responde con una sola palabra."))
        val filler = prompt.substringBefore("\n\nPregunta:")
        assertTrue(filler.length in 780..800)
    }

    @Test
    fun `longer prompts are longer`() {
        assertTrue(paddedPrompt(3_200).length > paddedPrompt(800).length)
    }

    @Test
    fun `small contexts only get the sizes that leave room to answer`() {
        // 1024 tokens * 4 chars * 0.6 = 2457 chars.
        assertEquals(listOf(200, 800, 1_600), stepsFor(1_024))
        assertEquals(listOf(200, 800, 1_600, 3_200), stepsFor(2_048))
        assertEquals(SCALING_STEPS, stepsFor(null))
    }

    @Test
    fun `an engine with a tiny context gets no steps`() {
        assertTrue(stepsFor(64).isEmpty())
    }

    @Test
    fun `the slope is the marginal ms per thousand characters`() {
        // ttft = 100 + 0.5 ms per char -> 500 ms per 1000 chars.
        val points = listOf(200, 800, 1_600, 3_200).map { point(it, (100 + 0.5 * it).toLong()) }
        assertEquals(500.0, slopeMsPer1kChars(points)!!, 1.0)
    }

    @Test
    fun `failed points and points without a first token are ignored by the slope`() {
        val points = listOf(point(200, 200), point(800, null), point(1_600, 900, ok = false), point(3_200, 1_700))
        assertNotNull(slopeMsPer1kChars(points))
        assertNull(slopeMsPer1kChars(listOf(point(200, 200))))
        assertNull(slopeMsPer1kChars(listOf(point(800, 300), point(800, 500))))
    }

    @Test
    fun `repetitions are averaged per size for the chart`() {
        val mean = meanTtftBySize(listOf(point(200, 100), point(200, 300), point(800, 500)))
        assertEquals(listOf(200 to 200.0, 800 to 500.0), mean.getValue(InferenceTier.MEDIAPIPE_LOCAL))
    }

    @Test
    fun `the runner covers every fitting size with every repetition`() = runBlocking {
        val executor = StepExecutor { tier, _, _, _ ->
            StepResult(
                "París",
                GenerationMetric(tier, 0, 250, 400, 10, 5, true, 2, true, 0),
            )
        }
        val runner = ScalingRunner(executor, contextOf = { if (it == InferenceTier.MEDIAPIPE_LOCAL) 1_024 else null })

        val points = runner.run(listOf(InferenceTier.MEDIAPIPE_LOCAL, InferenceTier.CLOUD_GEMINI), repetitions = 2)

        assertEquals(3 * 2 + SCALING_STEPS.size * 2, points.size)
        assertTrue(points.all { it.ok && it.ttftMs == 250L })
    }

    @Test
    fun `a failed request is a not-ok point and the run continues`() = runBlocking {
        val executor = StepExecutor { _, _, _, _ -> StepResult("", null, "boom") }
        val runner = ScalingRunner(executor, contextOf = { 1_024 })

        val points = runner.run(listOf(InferenceTier.LITERT_LM_LOCAL), repetitions = 1)

        assertEquals(3, points.size)
        assertTrue(points.none { it.ok })
    }
}
