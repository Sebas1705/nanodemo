package es.sebas1705.nanodemo.feature.compare

import es.sebas1705.nanodemo.domain.models.GenerationMetric
import es.sebas1705.nanodemo.domain.models.InferenceTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareLogicTest {
    private fun metric(
        tier: InferenceTier,
        startedAt: Long = 1_000,
        first: Long? = 100,
        total: Long = 2_100,
        tokens: Int = 40,
        success: Boolean = true,
    ) = GenerationMetric(
        tier = tier, startedAtMs = startedAt, firstChunkLatencyMs = first, totalDurationMs = total,
        promptChars = 10, responseChars = tokens * 4, success = success, responseTokens = tokens,
        responseTokensExact = true, memoryDeltaBytes = 0,
    )

    @Test
    fun `solo cuentan las corridas posteriores al arranque de la ronda`() {
        val old = metric(InferenceTier.CLOUD_GEMINI, startedAt = 500)
        val current = metric(InferenceTier.CLOUD_GEMINI, startedAt = 1_500)
        val result = metricsForRun(listOf(old, current), runStartedAtMs = 1_000)
        assertEquals(current, result.getValue(InferenceTier.CLOUD_GEMINI))
    }

    @Test
    fun `una corrida posterior de Chat con el mismo motor no pisa la de la ronda`() {
        val round = metric(InferenceTier.CLOUD_GEMINI, startedAt = 1_100)
        val laterChat = metric(InferenceTier.CLOUD_GEMINI, startedAt = 9_000)
        assertEquals(round, metricsForRun(listOf(round, laterChat), 1_000).getValue(InferenceTier.CLOUD_GEMINI))
    }

    @Test
    fun `las corridas fallidas no cuentan`() {
        assertTrue(metricsForRun(listOf(metric(InferenceTier.ML_KIT_NANO, success = false)), 0).isEmpty())
    }

    @Test
    fun `con un solo motor no hay insignias`() {
        assertTrue(compareBadges(mapOf(InferenceTier.ML_KIT_NANO to metric(InferenceTier.ML_KIT_NANO))).isEmpty())
    }

    @Test
    fun `el mas rapido es el de menor tiempo total`() {
        val badges = compareBadges(
            mapOf(
                InferenceTier.ML_KIT_NANO to metric(InferenceTier.ML_KIT_NANO, total = 3_000),
                InferenceTier.CLOUD_GEMINI to metric(InferenceTier.CLOUD_GEMINI, total = 1_200),
            ),
        )
        assertTrue(CompareBadge.FASTEST in badges.getValue(InferenceTier.CLOUD_GEMINI))
        assertTrue(badges[InferenceTier.ML_KIT_NANO]?.contains(CompareBadge.FASTEST) != true)
    }

    @Test
    fun `la mayor velocidad es la de mas tokens por segundo y puede ser otro motor que el mas rapido`() {
        // Nano termina antes (poco texto), pero Cloud genera más tokens por segundo.
        val badges = compareBadges(
            mapOf(
                InferenceTier.ML_KIT_NANO to metric(InferenceTier.ML_KIT_NANO, first = 100, total = 1_100, tokens = 10),
                InferenceTier.CLOUD_GEMINI to metric(InferenceTier.CLOUD_GEMINI, first = 100, total = 2_100, tokens = 100),
            ),
        )
        assertEquals(setOf(CompareBadge.FASTEST), badges.getValue(InferenceTier.ML_KIT_NANO))
        assertEquals(setOf(CompareBadge.HIGHEST_SPEED), badges.getValue(InferenceTier.CLOUD_GEMINI))
    }

    @Test
    fun `un empate le da la insignia a los dos`() {
        val badges = compareBadges(
            mapOf(
                InferenceTier.ML_KIT_NANO to metric(InferenceTier.ML_KIT_NANO, total = 2_000),
                InferenceTier.CLOUD_GEMINI to metric(InferenceTier.CLOUD_GEMINI, total = 2_000),
            ),
        )
        assertTrue(CompareBadge.FASTEST in badges.getValue(InferenceTier.ML_KIT_NANO))
        assertTrue(CompareBadge.FASTEST in badges.getValue(InferenceTier.CLOUD_GEMINI))
    }
}
