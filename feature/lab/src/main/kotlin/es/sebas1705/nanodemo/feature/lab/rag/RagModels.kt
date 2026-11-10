package es.sebas1705.nanodemo.feature.lab.rag

import kotlinx.serialization.Serializable

/** Where a document came from; the sample corpus is shown first and can be reloaded without duplicating. */
@Serializable
enum class DocumentOrigin { SAMPLE, PASTED, FILE }

/**
 * One indexed piece of a document. `embedding` is empty until the chunk has been embedded. `embedMs` is how
 * long embedding this chunk took on this device (recorded so indexing cost can be compared across chunk sizes).
 */
@Suppress("ArrayInDataClass")
@Serializable
data class RagChunk(
    val id: String,
    val index: Int,
    val heading: String? = null,
    val text: String,
    val embedding: FloatArray = FloatArray(0),
    val embedMs: Double = 0.0,
) {
    /** What gets embedded: the section heading gives a chunk its topic even when the body never repeats it. */
    val retrievalText: String get() = if (heading.isNullOrBlank()) text else "$heading\n$text"
}

/** A document in the app's storage with its chunks and their vectors (chunks are empty until it is indexed). */
@Serializable
data class RagDocument(
    val id: String,
    val title: String,
    val text: String,
    val origin: DocumentOrigin = DocumentOrigin.PASTED,
    val addedAtMs: Long = 0L,
    /** Chunker settings the current `chunks` were built with (0 = never indexed). */
    val chunkTargetChars: Int = 0,
    val chunkOverlapChars: Int = 0,
    val chunks: List<RagChunk> = emptyList(),
    val indexedAtMs: Long = 0L,
) {
    val isIndexed: Boolean get() = chunks.isNotEmpty() && chunks.all { it.embedding.isNotEmpty() }
    val totalEmbedMs: Double get() = chunks.sumOf { it.embedMs }
    val meanEmbedMs: Double get() = if (chunks.isEmpty()) 0.0 else totalEmbedMs / chunks.size
}

/** A chunk flattened with the data a search needs; the retriever works on these and never touches storage. */
@Suppress("ArrayInDataClass")
data class IndexedChunk(
    val docId: String,
    val docTitle: String,
    val chunkId: String,
    val heading: String?,
    val text: String,
    val embedding: FloatArray,
) {
    /** Short label for citations and result lists. */
    val label: String get() = heading?.takeIf { it.isNotBlank() } ?: docTitle
}

/** Flattens the indexed documents into the list a search runs over. Documents not fully indexed are skipped. */
fun List<RagDocument>.toIndex(): List<IndexedChunk> = filter { it.isIndexed }.flatMap { doc ->
    doc.chunks.map { IndexedChunk(doc.id, doc.title, it.id, it.heading, it.text, it.embedding) }
}

/** A search hit: the chunk and its cosine similarity with the query. */
data class ScoredChunk(val chunk: IndexedChunk, val score: Double)
