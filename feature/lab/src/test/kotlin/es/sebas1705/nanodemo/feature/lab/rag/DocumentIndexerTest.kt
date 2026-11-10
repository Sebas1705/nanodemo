package es.sebas1705.nanodemo.feature.lab.rag

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DocumentIndexerTest {

    private fun doc(id: String, text: String) = RagDocument(id, "Título $id", text)
    private val sentences = (1..20).joinToString(" ") { "Esta es la frase número $it del documento." }

    @Test
    fun `every chunk is embedded with its heading and gets a vector and a timing`() = runBlocking {
        val embedder = BagOfWordsEmbedder()
        var tick = 0L
        val indexer = DocumentIndexer(embedder, XmlTexts.english, nanoClock = { tick += 3_000_000; tick }, wallClock = { 1234L })
        val result = indexer.indexAll(listOf(doc("a", "# Tema\n\n$sentences")), ChunkerConfig(200, 30))

        val indexed = result.single()
        assertTrue(indexed.isIndexed)
        assertEquals(1234L, indexed.indexedAtMs)
        assertEquals(200, indexed.chunkTargetChars)
        assertEquals(30, indexed.chunkOverlapChars)
        assertTrue(indexed.chunks.size > 2)
        indexed.chunks.forEach {
            assertEquals("Tema", it.heading)
            assertEquals(3.0, it.embedMs, 1e-9)
            assertTrue(it.embedding.any { v -> v != 0f })
        }
        assertEquals(indexed.chunks.size, embedder.calls)
        assertEquals(3.0, indexed.meanEmbedMs, 1e-9)
    }

    @Test
    fun `the embedded text includes the heading breadcrumb`() = runBlocking {
        val seen = mutableListOf<String>()
        val embedder = object : ChunkEmbedder by BagOfWordsEmbedder() {
            override suspend fun embed(text: String): FloatArray? {
                seen += text
                return floatArrayOf(1f)
            }
        }
        DocumentIndexer(embedder, XmlTexts.english).indexAll(listOf(doc("a", "# Manual\n## Gastos\nSe reclaman en Rendi.")), ChunkerConfig())
        assertEquals(listOf("Manual > Gastos\nSe reclaman en Rendi."), seen)
    }

    @Test
    fun `progress counts chunks across all documents and documents are delivered one by one`() = runBlocking {
        val progress = mutableListOf<IndexProgress>()
        val delivered = mutableListOf<String>()
        val result = DocumentIndexer(BagOfWordsEmbedder(), XmlTexts.english).indexAll(
            documents = listOf(doc("a", sentences), doc("b", "Un solo fragmento corto.")),
            config = ChunkerConfig(200, 30),
            onProgress = { progress += it },
            onDocument = { delivered += it.id },
        )
        val total = result.sumOf { it.chunks.size }
        assertEquals(total, progress.size)
        assertEquals((1..total).toList(), progress.map { it.chunksDone })
        assertTrue(progress.all { it.chunksTotal == total })
        assertEquals("Título b", progress.last().docTitle)
        assertEquals(listOf("a", "b"), delivered)
    }

    @Test
    fun `a chunk that cannot be embedded fails the document and names it`() = runBlocking {
        val embedder = BagOfWordsEmbedder(failOn = { "frase número 7" in it })
        val delivered = mutableListOf<String>()
        try {
            DocumentIndexer(embedder, XmlTexts.english).indexAll(
                listOf(doc("ok", "Corto."), doc("bad", sentences)), ChunkerConfig(200, 30), onDocument = { delivered += it.id },
            )
            fail("expected an error")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("Título bad"))
        }
        // The document before it was already delivered (and so persisted by the caller).
        assertEquals(listOf("ok"), delivered)
    }

    @Test
    fun `a blank document yields no chunks and is not reported as indexed`() = runBlocking {
        val result = DocumentIndexer(BagOfWordsEmbedder(), XmlTexts.english).indexAll(listOf(doc("empty", "# Solo título")), ChunkerConfig())
        assertTrue(result.single().chunks.isEmpty())
        assertFalse(result.single().isIndexed)
    }

    @Test
    fun `cancelling stops indexing between chunks`() = runBlocking {
        var embedded = 0
        val embedder = object : ChunkEmbedder by BagOfWordsEmbedder() {
            override suspend fun embed(text: String): FloatArray? {
                embedded++
                if (embedded == 3) throw CancellationException("stop")
                return floatArrayOf(1f)
            }
        }
        try {
            DocumentIndexer(embedder, XmlTexts.english).indexAll(listOf(doc("a", sentences)), ChunkerConfig(200, 30))
            fail("expected cancellation")
        } catch (e: CancellationException) {
            assertEquals(3, embedded)
        }
    }

    @Test
    fun `stats only count fully indexed documents`() = runBlocking {
        var tick = 0L
        val indexer = DocumentIndexer(BagOfWordsEmbedder(), XmlTexts.english, nanoClock = { tick += 2_000_000; tick })
        val indexed = indexer.indexAll(listOf(doc("a", sentences)), ChunkerConfig(200, 30))
        val stats = (indexed + doc("pending", "Sin indexar.")).indexStats()
        assertEquals(1, stats.documents)
        assertEquals(indexed.single().chunks.size, stats.chunks)
        assertEquals(2.0, stats.meanChunkMs, 1e-9)
        assertEquals(0.0, emptyList<RagDocument>().indexStats().meanChunkMs, 0.0)
    }

    @Test
    fun `the index flattens only indexed documents`() = runBlocking {
        val indexed = DocumentIndexer(BagOfWordsEmbedder(), XmlTexts.english).indexAll(listOf(doc("a", sentences)), ChunkerConfig(200, 30))
        val flat = (indexed + doc("pending", "Sin indexar.")).toIndex()
        assertEquals(indexed.single().chunks.size, flat.size)
        assertTrue(flat.all { it.docId == "a" && it.docTitle == "Título a" })
    }
}
