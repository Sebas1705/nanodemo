package es.sebas1705.nanodemo.feature.lab.rag

import es.sebas1705.nanodemo.data.embeddings.LocalEmbeddingService
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow

/**
 * The embedding model as the RAG experiment needs it. Production uses [LocalEmbeddingService] (MediaPipe
 * text embedder, on-device) through [asChunkEmbedder]; tests pass a fake with hand-made vectors.
 */
interface ChunkEmbedder {
    /** True when the embedding model file is on the device. */
    fun isAvailable(): Boolean

    /** Downloads the model, emitting the percentage done. */
    fun download(): Flow<Int>

    /** The vector for `text`, or null when the model is missing or failed. */
    suspend fun embed(text: String): FloatArray?
}

fun LocalEmbeddingService.asChunkEmbedder(): ChunkEmbedder {
    val service = this
    return object : ChunkEmbedder {
        override fun isAvailable() = service.isAvailable()
        override fun download() = service.download()
        override suspend fun embed(text: String) = service.embed(text)
    }
}

/** Progress of an indexing run, across all documents being indexed. */
data class IndexProgress(
    val docTitle: String,
    val chunksDone: Int,
    val chunksTotal: Int,
    val lastChunkMs: Double,
)

/** Cost summary of the indexed chunks of a set of documents. */
data class IndexStats(val documents: Int, val chunks: Int, val totalEmbedMs: Double) {
    val meanChunkMs: Double get() = if (chunks == 0) 0.0 else totalEmbedMs / chunks
}

fun List<RagDocument>.indexStats(): IndexStats {
    val indexed = filter { it.isIndexed }
    return IndexStats(indexed.size, indexed.sumOf { it.chunks.size }, indexed.sumOf { it.totalEmbedMs })
}

/**
 * Chunks documents and embeds every chunk, timing each embedding. Documents are returned one at a time through
 * `onDocument` so the caller can persist them as they complete (a cancelled run keeps what was already done).
 * A chunk that cannot be embedded fails the whole document: a half-indexed document would silently never be
 * retrieved from.
 */
class DocumentIndexer(
    private val embedder: ChunkEmbedder,
    private val nanoClock: () -> Long = System::nanoTime,
    private val wallClock: () -> Long = System::currentTimeMillis,
) {
    suspend fun indexAll(
        documents: List<RagDocument>,
        config: ChunkerConfig,
        onProgress: (IndexProgress) -> Unit = {},
        onDocument: suspend (RagDocument) -> Unit = {},
    ): List<RagDocument> {
        val planned = documents.map { doc -> doc to Chunker.chunk(doc.id, doc.text, config) }
        val total = planned.sumOf { it.second.size }
        var done = 0
        val result = mutableListOf<RagDocument>()
        for ((doc, textChunks) in planned) {
            val embedded = textChunks.map { chunk ->
                currentCoroutineContext().ensureActive()
                val start = nanoClock()
                val tmp = RagChunk(chunk.id, chunk.index, chunk.heading, chunk.text)
                val vector = embedder.embed(tmp.retrievalText)
                    ?: error("No se pudo calcular el embedding del fragmento ${chunk.index + 1} de «${doc.title}»")
                val ms = (nanoClock() - start) / 1_000_000.0
                done++
                onProgress(IndexProgress(doc.title, done, total, ms))
                tmp.copy(embedding = vector, embedMs = ms)
            }
            val indexed = doc.copy(
                chunkTargetChars = config.target,
                chunkOverlapChars = config.overlap,
                chunks = embedded,
                indexedAtMs = wallClock(),
            )
            onDocument(indexed)
            result += indexed
        }
        return result
    }
}
