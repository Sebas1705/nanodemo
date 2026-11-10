package es.sebas1705.nanodemo.feature.lab.rag

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * A deterministic stand-in for the on-device embedder: hashes the words of a text into a fixed number of buckets
 * (a bag of words). Texts that share rare words get similar vectors, which is enough to exercise chunking,
 * retrieval and evaluation end to end without a model. It says nothing about how good the real embedder is.
 */
class BagOfWordsEmbedder(
    private val dimensions: Int = 256,
    private val available: Boolean = true,
    private val failOn: (String) -> Boolean = { false },
) : ChunkEmbedder {
    var calls = 0
        private set

    override fun isAvailable() = available
    override fun download(): Flow<Int> = flowOf(100)

    override suspend fun embed(text: String): FloatArray? {
        calls++
        if (failOn(text)) return null
        return vectorOf(text)
    }

    fun vectorOf(text: String): FloatArray {
        val v = FloatArray(dimensions)
        normalizeForMatch(text).split(Regex("[^a-z0-9]+"))
            .filter { it.length > 2 && it !in STOPWORDS }
            .forEach { word -> v[Math.floorMod(word.hashCode(), dimensions)] += 1f }
        return v
    }

    private companion object {
        val STOPWORDS = setOf(
            "los", "las", "una", "uno", "del", "que", "con", "por", "para", "como", "cual", "cuanto", "cuantos",
            "cuantas", "cada", "mas", "sin", "son", "hay", "tiene", "tengo", "puedo", "hago", "debe", "esta",
            "este", "sus", "pero", "tus", "nos", "les", "han", "ser", "mis", "usa", "usamos", "pida", "pedir",
        )
    }
}

fun indexed(id: String, vararg values: Float, doc: String = "doc", text: String = id, heading: String? = null) =
    IndexedChunk(doc, "Doc $doc", id, heading, text, values)
