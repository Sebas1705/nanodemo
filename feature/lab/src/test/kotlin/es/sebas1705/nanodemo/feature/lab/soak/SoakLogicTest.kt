package es.sebas1705.nanodemo.feature.lab.soak

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

class SoakLogicTest {

    private fun point(tps: Double?, thermal: Int? = null, battery: Long? = null, at: Float = 0f) =
        SoakPoint(at, tps, ttftMs = 100, thermalStatus = thermal, batteryDrainUah = battery)

    @Test
    fun `a speed drop between the first and last third is reported as a negative change`() {
        val points = listOf(40.0, 40.0, 38.0, 30.0, 24.0, 20.0).map { point(it) }

        val summary = summarize(points)

        assertEquals(40.0, summary.startTokensPerSecond!!, 1e-9)
        assertEquals(22.0, summary.endTokensPerSecond!!, 1e-9)
        assertEquals(-45.0, summary.changePercent!!, 1e-9)
    }

    @Test
    fun `too few points leave the trend unknown`() {
        val summary = summarize(listOf(point(40.0), point(30.0)))
        assertNull(summary.changePercent)
        assertNull(summary.startTokensPerSecond)
        assertEquals(2, summary.iterations)
    }

    @Test
    fun `thermal and battery are aggregated`() {
        val summary = summarize(listOf(point(30.0, thermal = 0, battery = 400), point(30.0, thermal = 2, battery = 600), point(30.0, thermal = 1)))
        assertEquals(2, summary.maxThermalStatus)
        assertEquals(1_000L, summary.totalBatteryDrainUah)
    }

    @Test
    fun `no readings at all stay unknown instead of zero`() {
        val summary = summarize(listOf(point(null), point(null), point(null)))
        assertNull(summary.maxThermalStatus)
        assertNull(summary.totalBatteryDrainUah)
        assertNull(summary.changePercent)
    }

    private fun summarize(points: List<SoakPoint>) = summarizeSoak(points)

    /** Each call takes 1 s of fake time and the speed falls 2 tok/s per call. */
    private fun throttlingExecutor(clock: () -> Long, advance: (Long) -> Unit): StepExecutor {
        var call = 0
        return StepExecutor { tier, _, _, _ ->
            val tps = 40 - 2 * call++
            // first token at 100 ms, then 40 tokens: generation time = 40 / tps seconds.
            val generationMs = (40_000 / tps).toLong()
            advance(100 + generationMs)
            StepResult(
                "texto",
                GenerationMetric(tier, clock(), 100, 100 + generationMs, 10, 100, true, 40, true, 0, thermalStatus = call / 3),
            )
        }
    }

    @Test
    fun `the runner keeps generating until the time is up and records a falling curve`() = runBlocking {
        var now = 0L
        val runner = SoakRunner(throttlingExecutor({ now }, { now += it }), clock = { now })

        val points = runner.run(InferenceTier.LITERT_LM_LOCAL, durationMs = 8_000)

        assertTrue(points.size >= 4)
        assertTrue(points.first().tokensPerSecond!! > points.last().tokensPerSecond!!)
        assertTrue(points.zipWithNext().all { (a, b) -> b.elapsedSeconds >= a.elapsedSeconds })
        assertNotNull(points.last().thermalStatus)
    }

    @Test
    fun `an engine that keeps failing stops after a few attempts`() = runBlocking {
        var calls = 0
        val runner = SoakRunner(StepExecutor { _, _, _, _ -> calls++; StepResult("", null, "no model") }, clock = { 0L })

        val points = runner.run(InferenceTier.MEDIAPIPE_LOCAL, durationMs = 60_000)

        assertTrue(points.isEmpty())
        assertEquals(SoakRunner.MAX_CONSECUTIVE_FAILURES, calls)
    }

    @Test
    fun `a success resets the failure counter`() = runBlocking {
        var now = 0L
        var call = 0
        val executor = StepExecutor { tier, _, _, _ ->
            now += 1_000
            val i = call++
            if (i % 3 != 2) {
                StepResult("x", GenerationMetric(tier, now, 100, 900, 5, 5, true, 20, true, 0))
            } else {
                StepResult("", null, "flaky")
            }
        }
        val points = SoakRunner(executor, clock = { now }).run(InferenceTier.ML_KIT_NANO, durationMs = 10_000)

        assertTrue(points.size >= 5)
    }
}
