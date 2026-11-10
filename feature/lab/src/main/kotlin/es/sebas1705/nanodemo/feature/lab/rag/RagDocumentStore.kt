package es.sebas1705.nanodemo.feature.lab.rag

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Documents persisted as JSON in app storage, one file per document so saving one does not rewrite the rest
 * (a document carries all of its chunk vectors). A file that cannot be read back is skipped, not fatal: it is
 * reported through `lastSkipped` so the UI can say so.
 *
 * Uses `kotlinx.serialization` and `java.io.File` only, so it runs in JVM unit tests with a temp directory.
 */
class RagDocumentStore(private val directory: File) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Names of files that failed to load in the last [loadAll]. */
    var lastSkipped: List<String> = emptyList()
        private set

    private fun fileFor(id: String) = File(directory, id.replace(Regex("[^A-Za-z0-9_.-]"), "_") + ".json")

    suspend fun loadAll(): List<RagDocument> = withContext(Dispatchers.IO) {
        val skipped = mutableListOf<String>()
        val docs = directory.listFiles { f -> f.isFile && f.extension == "json" }.orEmpty().mapNotNull { file ->
            try {
                json.decodeFromString(RagDocument.serializer(), file.readText())
            } catch (e: Exception) {
                skipped += file.name
                null
            }
        }
        lastSkipped = skipped
        docs.sortedWith(compareBy({ it.origin != DocumentOrigin.SAMPLE }, { it.addedAtMs }, { it.title }))
    }

    /** Writes through a temp file and renames, so a crash mid-write never leaves a half-written document. */
    suspend fun save(document: RagDocument) = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val target = fileFor(document.id)
        val temp = File(directory, target.name + ".part")
        temp.writeText(json.encodeToString(RagDocument.serializer(), document))
        if (!temp.renameTo(target)) {
            target.delete()
            check(temp.renameTo(target)) { "Could not save document \"${document.title}\"" }
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) { fileFor(id).delete() }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        directory.listFiles().orEmpty().forEach { it.delete() }
    }
}
