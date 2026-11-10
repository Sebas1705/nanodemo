package es.sebas1705.nanodemo.feature.lab.refine

import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.VoteTally
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JudgeScoringTest {

    private fun cand(id: Int, tier: InferenceTier? = null) = Candidate(id, "C$id", tier, "text $id")
    private val c1 = cand(1)
    private val c2 = cand(2)
    private val c3 = cand(3)

    private fun pair(a: Int, b: Int, first: Pick?, second: Pick?) = PairResult(
        a, b, listOf(PairRun(a, b, first), PairRun(b, a, second)),
    )

    @Test
    fun `a pair that agrees in both orders gives the winner two points`() {
        // run 1: A=1 is shown first and FIRST wins -> 1. run 2: A=2 shown first, SECOND wins -> 1.
        val r = pair(1, 2, Pick.FIRST, Pick.SECOND)
        assertEquals(mapOf(1 to 2.0, 2 to 0.0), r.points())
        assertEquals(true, r.consistent)
    }

    @Test
    fun `picking the same position both times is position bias and splits the points`() {
        val r = pair(1, 2, Pick.FIRST, Pick.FIRST) // always "A": candidate 1 then candidate 2
        assertEquals(mapOf(1 to 1.0, 2 to 1.0), r.points())
        assertEquals(false, r.consistent)
    }

    @Test
    fun `ties and unreadable runs`() {
        val tie = pair(1, 2, Pick.TIE, Pick.TIE)
        assertEquals(mapOf(1 to 1.0, 2 to 1.0), tie.points())
        assertEquals(true, tie.consistent)

        val mixed = pair(1, 2, Pick.TIE, Pick.FIRST)
        assertEquals(false, mixed.consistent)

        val unreadable = pair(1, 2, null, Pick.SECOND)
        assertNull(unreadable.consistent)
        assertEquals(mapOf(1 to 1.0, 2 to 0.0), unreadable.points())
    }

    @Test
    fun `consistency rate counts only comparable pairs`() {
        val results = listOf(
            pair(1, 2, Pick.FIRST, Pick.SECOND),
            pair(1, 3, Pick.FIRST, Pick.FIRST),
            pair(2, 3, null, Pick.FIRST),
        )
        val rate = consistencyRate(results)
        assertEquals(1, rate.count)
        assertEquals(2, rate.of)
        assertEquals(0.5, rate.ratio!!, 0.0001)
        assertNull(consistencyRate(emptyList()).ratio)
    }

    @Test
    fun `first position share reveals the bias`() {
        val biased = listOf(pair(1, 2, Pick.FIRST, Pick.FIRST), pair(1, 3, Pick.FIRST, Pick.FIRST))
        assertEquals(1.0, firstPositionShare(biased)!!.ratio!!, 0.0001)
        val fair = listOf(pair(1, 2, Pick.FIRST, Pick.SECOND))
        assertEquals(0.5, firstPositionShare(fair)!!.ratio!!, 0.0001)
        assertNull(firstPositionShare(listOf(pair(1, 2, Pick.TIE, null))))
    }

    @Test
    fun `unreadable runs are counted`() {
        val rate = unreadableRuns(listOf(pair(1, 2, null, Pick.FIRST), pair(1, 3, null, null)))
        assertEquals(3, rate.count)
        assertEquals(4, rate.of)
    }

    @Test
    fun `pairwise leaderboard ranks by total points and shares ranks on ties`() {
        val results = listOf(
            pair(1, 2, Pick.FIRST, Pick.SECOND), // 1 beats 2 twice
            pair(1, 3, Pick.FIRST, Pick.SECOND), // 1 beats 3 twice
            pair(2, 3, Pick.TIE, Pick.TIE),      // 2 and 3 tie
        )
        val board = pairwiseLeaderboard(listOf(c1, c2, c3), results)
        assertEquals(listOf(1, 2, 3), board.map { it.candidate.id })
        assertEquals(listOf(4.0, 1.0, 1.0), board.map { it.score })
        assertEquals(listOf(1, 2, 2), board.map { it.rank })
    }

    @Test
    fun `rubric leaderboard uses the mean and puts unscored answers last`() {
        val board = rubricLeaderboard(
            listOf(c1, c2, c3),
            mapOf(1 to RubricScores(3, 3, 3), 2 to RubricScores(5, 4, 5), 3 to null),
        )
        assertEquals(listOf(2, 1, 3), board.map { it.candidate.id })
        assertEquals(listOf(1, 2, 3), board.map { it.rank })
        assertEquals(0.0, board.last().score, 0.0001)
    }

    @Test
    fun `human agreement is the share of engine pairs ordered the same way`() {
        val a = cand(1, InferenceTier.ML_KIT_NANO)
        val b = cand(2, InferenceTier.CLOUD_GEMINI)
        val c = cand(3, InferenceTier.MEDIAPIPE_LOCAL)
        val entries = listOf(
            LeaderEntry(b, 5.0, 1),
            LeaderEntry(a, 3.0, 2),
            LeaderEntry(c, 1.0, 3),
        )
        val votes = mapOf(
            InferenceTier.CLOUD_GEMINI to VoteTally(wins = 8, appearances = 10),
            InferenceTier.ML_KIT_NANO to VoteTally(wins = 1, appearances = 10),
            InferenceTier.MEDIAPIPE_LOCAL to VoteTally(wins = 3, appearances = 10),
        )
        val agreement = humanAgreement(entries, votes)!!
        // judge: cloud > nano > mediapipe; humans: cloud > mediapipe > nano -> 2 of 3 pairs agree
        assertEquals(2, agreement.concordant)
        assertEquals(3, agreement.comparable)
        assertEquals(2.0 / 3.0, agreement.ratio, 0.0001)
    }

    @Test
    fun `human agreement needs engines with votes and differing scores`() {
        val typed = listOf(LeaderEntry(cand(1), 5.0, 1), LeaderEntry(cand(2), 3.0, 2))
        assertNull(humanAgreement(typed, emptyMap()))

        val a = cand(1, InferenceTier.ML_KIT_NANO)
        val b = cand(2, InferenceTier.CLOUD_GEMINI)
        val unvoted = mapOf(InferenceTier.ML_KIT_NANO to VoteTally(1, 2))
        assertNull(humanAgreement(listOf(LeaderEntry(a, 2.0, 1), LeaderEntry(b, 1.0, 2)), unvoted))

        val tiedJudge = listOf(LeaderEntry(a, 2.0, 1), LeaderEntry(b, 2.0, 1))
        val both = mapOf(InferenceTier.ML_KIT_NANO to VoteTally(1, 2), InferenceTier.CLOUD_GEMINI to VoteTally(2, 2))
        assertNull(humanAgreement(tiedJudge, both))
        assertNotNull(humanAgreement(listOf(LeaderEntry(a, 2.0, 1), LeaderEntry(b, 1.0, 2)), both))
        assertFalse(humanAgreement(listOf(LeaderEntry(a, 2.0, 1), LeaderEntry(b, 1.0, 2)), both)!!.ratio > 0.5)
        assertTrue(humanAgreement(listOf(LeaderEntry(b, 2.0, 1), LeaderEntry(a, 1.0, 2)), both)!!.ratio > 0.5)
    }
}
