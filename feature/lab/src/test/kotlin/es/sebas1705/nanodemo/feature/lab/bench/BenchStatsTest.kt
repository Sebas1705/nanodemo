package es.sebas1705.nanodemo.feature.lab.bench

import es.sebas1705.nanodemo.domain.models.InferenceTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchStatsTest {

    private fun sample(
        tier: InferenceTier = InferenceTier.ML_KIT_NANO,
        task: String = "t1",
        category: BenchCategory = BenchCategory.REASONING,
        rep: Int = 1,
        score: Double = 1.0,
        error: String? = null,
        latency: Long = 1_000,
        ttft: Long? = 200,
        answer: String = "ok",
        tps: Double? = 20.0,
    ) = BenchSample(tier, task, category, rep, score, error, latency, ttft, tokens = 20, tokensPerSecond = tps, answer = answer)

    @Test
    fun `percentiles interpolate and handle tiny lists`() {
        assertNull(percentile(emptyList(), 50.0))
        assertEquals(7L, percentile(listOf(7L), 95.0))
        assertEquals(30L, percentile(listOf(50L, 10L, 30L, 20L, 40L), 50.0))
        assertEquals(15L, percentile(listOf(10L, 20L), 50.0))
        assertEquals(48L, percentile(listOf(10L, 20L, 30L, 40L, 50L), 95.0))
    }

    @Test
    fun `a sample passes only with a perfect score and no error`() {
        assertTrue(sample(score = 1.0).passed)
        assertTrue(!sample(score = 0.5).passed)
        assertTrue(!sample(score = 1.0, error = "boom").passed)
    }

    @Test
    fun `summaries rank by pass rate and exclude errors from speed`() {
        val samples = listOf(
            sample(InferenceTier.CLOUD_GEMINI, "a", latency = 800),
            sample(InferenceTier.CLOUD_GEMINI, "b", latency = 1_200),
            sample(InferenceTier.ML_KIT_NANO, "a", score = 0.0, latency = 300),
            sample(InferenceTier.ML_KIT_NANO, "b", error = "timeout", latency = 0),
        )

        val summaries = summarize(samples)

        assertEquals(listOf(InferenceTier.CLOUD_GEMINI, InferenceTier.ML_KIT_NANO), summaries.map { it.tier })
        val cloud = summaries[0]
        assertEquals(1.0, cloud.passRate, 0.0)
        assertEquals(1_000L, cloud.latencyP50Ms)
        val nano = summaries[1]
        assertEquals(0.0, nano.passRate, 0.0)
        assertEquals(1, nano.errors)
        assertEquals(300L, nano.latencyP50Ms) // the errored run is not a 0 ms run
    }

    @Test
    fun `heatmap gives the pass rate per engine and category`() {
        val samples = listOf(
            sample(category = BenchCategory.CODE, score = 1.0),
            sample(category = BenchCategory.CODE, score = 0.0),
            sample(category = BenchCategory.KNOWLEDGE, score = 1.0),
        )

        val map = heatmap(samples)

        assertEquals(0.5, map.getValue(InferenceTier.ML_KIT_NANO to BenchCategory.CODE).passRate, 0.0)
        assertEquals(2, map.getValue(InferenceTier.ML_KIT_NANO to BenchCategory.CODE).runs)
        assertEquals(1.0, map.getValue(InferenceTier.ML_KIT_NANO to BenchCategory.KNOWLEDGE).passRate, 0.0)
    }

    @Test
    fun `determinism is the share of tasks with identical repeated answers`() {
        val samples = listOf(
            sample(task = "a", rep = 1, answer = "Canberra."),
            sample(task = "a", rep = 2, answer = "canberra"), // same after normalisation
            sample(task = "b", rep = 1, answer = "uno"),
            sample(task = "b", rep = 2, answer = "dos"),
            sample(task = "c", rep = 1, answer = "single run, ignored"),
        )

        assertEquals(0.5, determinism(samples).getValue(InferenceTier.ML_KIT_NANO)!!, 0.0)
    }

    @Test
    fun `determinism is unknown when nothing was repeated`() {
        assertNull(determinism(listOf(sample())).getValue(InferenceTier.ML_KIT_NANO))
    }

    @Test
    fun `comparing sessions reports the change per engine present in both`() {
        val baseline = listOf(
            sample(task = "a", score = 0.0, latency = 2_000),
            sample(task = "b", score = 1.0, latency = 2_000),
            sample(InferenceTier.CLOUD_GEMINI, "a", latency = 500),
        )
        val current = listOf(
            sample(task = "a", score = 1.0, latency = 1_000),
            sample(task = "b", score = 1.0, latency = 1_000),
        )

        val deltas = compareSessions(current, baseline)

        assertEquals(1, deltas.size) // the cloud engine is not in the current session
        assertEquals(0.5, deltas[0].passRateDelta, 0.0)
        assertEquals(-1_000L, deltas[0].latencyP50DeltaMs)
    }

    @Test
    fun `csv has a header and one row per sample with commas in errors neutralised`() {
        val csv = samplesToCsv(listOf(sample(), sample(error = "a, b\nc", score = 0.0)))
        val lines = csv.trim().lines()

        assertEquals(3, lines.size)
        assertTrue(lines[0].startsWith("tier,task,category"))
        assertEquals(lines[0].split(',').size, lines[2].split(',').size)
    }
}
