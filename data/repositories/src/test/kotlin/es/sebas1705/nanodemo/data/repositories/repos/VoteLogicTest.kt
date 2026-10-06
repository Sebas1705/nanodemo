package es.sebas1705.nanodemo.data.repositories.repos

import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.VoteTally
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoteLogicTest {
    private val nano = InferenceTier.ML_KIT_NANO
    private val cloud = InferenceTier.CLOUD_GEMINI
    private val lite = InferenceTier.LITERT_LM_LOCAL

    @Test
    fun `el primer voto suma una aparicion a cada participante y una victoria al ganador`() {
        val result = applyVote(emptyMap(), setOf(nano, cloud, lite), winner = cloud, previous = null)
        assertEquals(VoteTally(wins = 1, appearances = 1), result.getValue(cloud))
        assertEquals(VoteTally(wins = 0, appearances = 1), result.getValue(nano))
        assertEquals(VoteTally(wins = 0, appearances = 1), result.getValue(lite))
    }

    @Test
    fun `cambiar de voto mueve la victoria sin contar la ronda otra vez`() {
        val first = applyVote(emptyMap(), setOf(nano, cloud), winner = nano, previous = null)
        val changed = applyVote(first, setOf(nano, cloud), winner = cloud, previous = nano)
        assertEquals(VoteTally(wins = 0, appearances = 1), changed.getValue(nano))
        assertEquals(VoteTally(wins = 1, appearances = 1), changed.getValue(cloud))
    }

    @Test
    fun `votar lo mismo dos veces no cambia nada`() {
        val first = applyVote(emptyMap(), setOf(nano, cloud), winner = nano, previous = null)
        assertEquals(first, applyVote(first, setOf(nano, cloud), winner = nano, previous = nano))
    }

    @Test
    fun `las victorias se acumulan entre rondas`() {
        var votes = applyVote(emptyMap(), setOf(nano, cloud), winner = cloud, previous = null)
        votes = applyVote(votes, setOf(nano, cloud), winner = cloud, previous = null)
        votes = applyVote(votes, setOf(nano, cloud), winner = nano, previous = null)
        assertEquals(VoteTally(wins = 2, appearances = 3), votes.getValue(cloud))
        assertEquals(VoteTally(wins = 1, appearances = 3), votes.getValue(nano))
    }

    @Test
    fun `las victorias nunca bajan de cero`() {
        val result = applyVote(emptyMap(), setOf(nano, cloud), winner = cloud, previous = nano)
        assertEquals(0, result.getValue(nano).wins)
    }

    @Test
    fun `el porcentaje de victorias`() {
        assertNull(VoteTally().winRate)
        assertEquals(0.5f, VoteTally(wins = 1, appearances = 2).winRate!!, 0.0001f)
    }
}
