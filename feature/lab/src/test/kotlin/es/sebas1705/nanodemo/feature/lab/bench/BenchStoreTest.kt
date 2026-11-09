package es.sebas1705.nanodemo.feature.lab.bench

import es.sebas1705.nanodemo.domain.models.InferenceTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchStoreTest {

    private fun session(id: Long, label: String = "s$id") = BenchSession(
        id = id, label = label, startedAtMs = id, temperature = 0f, repetitions = 1,
        samples = listOf(
            BenchSample(
                tier = InferenceTier.LITERT_LM_LOCAL, taskId = "capital", category = BenchCategory.KNOWLEDGE,
                rep = 1, score = 1.0, latencyMs = 900, ttftMs = 120, tokens = 8, tokensPerSecond = 11.5, answer = "Canberra",
            ),
        ),
    )

    @Test
    fun `sessions survive an encode and decode round trip`() {
        val original = listOf(session(2), session(1))
        assertEquals(original, decodeSessions(encodeSessions(original)))
    }

    @Test
    fun `a missing or corrupt file means no sessions`() {
        assertTrue(decodeSessions(null).isEmpty())
        assertTrue(decodeSessions("").isEmpty())
        assertTrue(decodeSessions("{ this is not json").isEmpty())
    }

    @Test
    fun `unknown fields from a newer version are ignored`() {
        val text = encodeSessions(listOf(session(1))).replace("\"label\"", "\"extra\":42,\"label\"")
        assertEquals(1, decodeSessions(text).size)
    }

    @Test
    fun `the newest session goes first and the list is capped`() {
        var sessions = emptyList<BenchSession>()
        for (id in 1L..(MAX_SAVED_SESSIONS + 3L)) sessions = addSession(sessions, session(id))

        assertEquals(MAX_SAVED_SESSIONS, sessions.size)
        assertEquals(MAX_SAVED_SESSIONS + 3L, sessions.first().id)
        assertTrue(sessions.none { it.id == 1L })
    }

    @Test
    fun `saving a session with an existing id replaces it`() {
        val sessions = addSession(listOf(session(1, "old")), session(1, "new"))
        assertEquals(listOf("new"), sessions.map { it.label })
    }
}
