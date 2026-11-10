package es.sebas1705.nanodemo.feature.lab.rag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetrieverTest {

    private val q = floatArrayOf(1f, 0f)

    @Test
    fun `cosine of identical, orthogonal and opposite vectors`() {
        assertEquals(1.0, VectorMath.cosine(floatArrayOf(1f, 2f), floatArrayOf(2f, 4f)), 1e-9)
        assertEquals(0.0, VectorMath.cosine(floatArrayOf(1f, 0f), floatArrayOf(0f, 3f)), 1e-9)
        assertEquals(-1.0, VectorMath.cosine(floatArrayOf(1f, 1f), floatArrayOf(-1f, -1f)), 1e-9)
    }

    @Test
    fun `cosine is zero for empty, zero or mismatched vectors instead of failing`() {
        assertEquals(0.0, VectorMath.cosine(FloatArray(0), FloatArray(0)), 0.0)
        assertEquals(0.0, VectorMath.cosine(floatArrayOf(0f, 0f), floatArrayOf(1f, 1f)), 0.0)
        assertEquals(0.0, VectorMath.cosine(floatArrayOf(1f, 0f), floatArrayOf(1f, 0f, 0f)), 0.0)
    }

    @Test
    fun `top k returns the best chunks by score, highest first`() {
        val index = listOf(
            indexed("far", 0f, 1f),
            indexed("near", 1f, 0.1f),
            indexed("mid", 1f, 1f),
        )
        val hits = Retriever.search(q, index, RetrievalOptions(topK = 2))
        assertEquals(listOf("near", "mid"), hits.map { it.chunk.chunkId })
        assertTrue(hits[0].score > hits[1].score)
    }

    @Test
    fun `top k larger than the index returns everything and an empty index returns nothing`() {
        val index = listOf(indexed("a", 1f, 0f), indexed("b", 0f, 1f))
        assertEquals(2, Retriever.search(q, index, RetrievalOptions(topK = 10)).size)
        assertTrue(Retriever.search(q, emptyList(), RetrievalOptions()).isEmpty())
        assertTrue(Retriever.search(q, index, RetrievalOptions(topK = 0)).isEmpty())
    }

    @Test
    fun `the score threshold drops weak matches`() {
        val index = listOf(indexed("good", 1f, 0.1f), indexed("weak", 0.2f, 1f))
        val hits = Retriever.search(q, index, RetrievalOptions(topK = 5, minScore = 0.5))
        assertEquals(listOf("good"), hits.map { it.chunk.chunkId })
        assertTrue(Retriever.search(q, index, RetrievalOptions(topK = 5, minScore = 0.999)).isEmpty())
    }

    @Test
    fun `equal scores keep index order so results are deterministic`() {
        val index = listOf(indexed("first", 1f, 0f), indexed("second", 2f, 0f), indexed("third", 3f, 0f))
        val hits = Retriever.search(q, index, RetrievalOptions(topK = 3))
        assertEquals(listOf("first", "second", "third"), hits.map { it.chunk.chunkId })
    }

    @Test
    fun `mmr swaps a near duplicate for a different chunk`() {
        val index = listOf(
            indexed("best", 1f, 0.05f, 0f),
            indexed("duplicate", 1f, 0.06f, 0f),
            indexed("different", 0.7f, 0f, 0.7f),
        )
        val q3 = floatArrayOf(1f, 0f, 0f)
        val plain = Retriever.search(q3, index, RetrievalOptions(topK = 2))
        val diverse = Retriever.search(q3, index, RetrievalOptions(topK = 2, mmr = true, mmrLambda = 0.5))
        assertEquals(listOf("best", "duplicate"), plain.map { it.chunk.chunkId })
        assertEquals(listOf("best", "different"), diverse.map { it.chunk.chunkId })
    }

    @Test
    fun `mmr scores are still the similarity with the query`() {
        val index = listOf(indexed("a", 1f, 0f), indexed("b", 1f, 1f))
        val hits = Retriever.search(q, index, RetrievalOptions(topK = 2, mmr = true, mmrLambda = 0.1))
        val b = hits.first { it.chunk.chunkId == "b" }
        assertEquals(VectorMath.cosine(q, floatArrayOf(1f, 1f)), b.score, 1e-9)
    }

    @Test
    fun `mmr with lambda 1 is plain top k`() {
        val index = (1..10).map { indexed("c$it", 1f, it / 10f) }
        val plain = Retriever.search(q, index, RetrievalOptions(topK = 4))
        val mmr = Retriever.search(q, index, RetrievalOptions(topK = 4, mmr = true, mmrLambda = 1.0))
        assertEquals(plain.map { it.chunk.chunkId }, mmr.map { it.chunk.chunkId })
    }

    @Test
    fun `mmr respects the threshold and k`() {
        val index = listOf(indexed("a", 1f, 0f), indexed("b", 0f, 1f), indexed("c", 1f, 0.2f))
        val hits = Retriever.search(q, index, RetrievalOptions(topK = 3, minScore = 0.5, mmr = true))
        assertEquals(setOf("a", "c"), hits.map { it.chunk.chunkId }.toSet())
        assertEquals(1, Retriever.search(q, index, RetrievalOptions(topK = 1, mmr = true)).size)
    }
}
