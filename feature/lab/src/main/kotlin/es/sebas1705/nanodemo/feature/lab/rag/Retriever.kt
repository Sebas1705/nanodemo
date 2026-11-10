package es.sebas1705.nanodemo.feature.lab.rag

import kotlin.math.sqrt

/** Vector helpers. Written here (not taken from the embedding SDK) so retrieval is pure and unit-testable. */
object VectorMath {
    /** Cosine similarity in [-1, 1]; 0 when the vectors differ in size, are empty or have zero length. */
    fun cosine(a: FloatArray, b: FloatArray): Double {
        if (a.isEmpty() || a.size != b.size) return 0.0
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            val x = a[i].toDouble()
            val y = b[i].toDouble()
            dot += x * y
            normA += x * x
            normB += y * y
        }
        if (normA == 0.0 || normB == 0.0) return 0.0
        return dot / (sqrt(normA) * sqrt(normB))
    }
}

/**
 * How to pick chunks. `minScore` drops weak matches (so an off-topic question retrieves nothing instead of the
 * least-bad chunks). With `mmr`, selection trades relevance against redundancy: `mmrLambda` 1.0 is plain top-k,
 * lower values favour chunks that differ from the ones already picked.
 */
data class RetrievalOptions(
    val topK: Int = 3,
    val minScore: Double = 0.0,
    val mmr: Boolean = false,
    val mmrLambda: Double = 0.7,
)

object Retriever {

    /** MMR re-ranks only the best candidates; looking at the whole index would cost O(n*k) similarities for no gain. */
    private const val MMR_POOL_FACTOR = 4
    private const val MMR_MIN_POOL = 12

    /**
     * The best `topK` chunks for `query`, highest score first (or in MMR selection order). `score` is always the
     * cosine similarity with the query, whichever way the chunks were picked.
     */
    fun search(query: FloatArray, index: List<IndexedChunk>, options: RetrievalOptions = RetrievalOptions()): List<ScoredChunk> {
        if (options.topK <= 0 || index.isEmpty()) return emptyList()
        val candidates = index
            .map { ScoredChunk(it, VectorMath.cosine(query, it.embedding)) }
            .filter { it.score >= options.minScore }
            .sortedByDescending { it.score } // stable: equal scores keep index order, so results are deterministic
        if (!options.mmr || candidates.size <= 1) return candidates.take(options.topK)
        return mmrSelect(candidates.take(maxOf(options.topK * MMR_POOL_FACTOR, MMR_MIN_POOL)), options)
    }

    private fun mmrSelect(pool: List<ScoredChunk>, options: RetrievalOptions): List<ScoredChunk> {
        val lambda = options.mmrLambda.coerceIn(0.0, 1.0)
        val selected = mutableListOf(pool.first())
        val remaining = pool.drop(1).toMutableList()
        while (selected.size < options.topK && remaining.isNotEmpty()) {
            val best = remaining.maxByOrNull { candidate ->
                val redundancy = selected.maxOf { VectorMath.cosine(candidate.chunk.embedding, it.chunk.embedding) }
                lambda * candidate.score - (1 - lambda) * redundancy
            } ?: break
            selected += best
            remaining -= best
        }
        return selected
    }
}
