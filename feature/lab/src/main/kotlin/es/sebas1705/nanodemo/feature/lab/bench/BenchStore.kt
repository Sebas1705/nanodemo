package es.sebas1705.nanodemo.feature.lab.bench

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Saved sessions kept on disk; old ones are dropped so the file stays small. */
const val MAX_SAVED_SESSIONS = 10

private val json = Json { ignoreUnknownKeys = true }

internal fun encodeSessions(sessions: List<BenchSession>): String =
    json.encodeToString(ListSerializer(BenchSession.serializer()), sessions)

/** A missing, empty or corrupt file is "no sessions", never a crash. */
internal fun decodeSessions(text: String?): List<BenchSession> {
    if (text.isNullOrBlank()) return emptyList()
    return runCatching { json.decodeFromString(ListSerializer(BenchSession.serializer()), text) }.getOrDefault(emptyList())
}

/** Newest first, capped at [MAX_SAVED_SESSIONS]. */
internal fun addSession(existing: List<BenchSession>, session: BenchSession): List<BenchSession> =
    (listOf(session) + existing.filterNot { it.id == session.id }).take(MAX_SAVED_SESSIONS)

/** Benchmark sessions persisted as one JSON file in the app's private storage. */
@Singleton
class BenchStore @Inject constructor(@ApplicationContext private val context: Context) {

    private fun file() = File(context.filesDir, "bench-sessions.json")

    fun load(): List<BenchSession> = decodeSessions(runCatching { file().takeIf { it.exists() }?.readText() }.getOrNull())

    fun save(session: BenchSession): List<BenchSession> {
        val updated = addSession(load(), session)
        runCatching { file().writeText(encodeSessions(updated)) }
        return updated
    }

    fun delete(id: Long): List<BenchSession> {
        val updated = load().filterNot { it.id == id }
        runCatching { file().writeText(encodeSessions(updated)) }
        return updated
    }
}
