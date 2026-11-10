package es.sebas1705.nanodemo.feature.lab.rag

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RagDocumentStoreTest {

    private lateinit var dir: File
    private lateinit var store: RagDocumentStore

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("rag-store-test").toFile()
        store = RagDocumentStore(File(dir, "rag"))
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun doc(id: String, origin: DocumentOrigin = DocumentOrigin.PASTED, added: Long = 0L) = RagDocument(
        id = id, title = "Título $id", text = "Texto con acentos y ñ: ¿qué tal? \"comillas\"\n\nSegundo párrafo.",
        origin = origin, addedAtMs = added, chunkTargetChars = 400, chunkOverlapChars = 66, indexedAtMs = 99L,
        chunks = listOf(
            RagChunk("$id#0", 0, "Cabecera > Sub", "Primer fragmento.", floatArrayOf(0.25f, -1.5f, 3e-8f), 1.75),
            RagChunk("$id#1", 1, null, "Segundo fragmento.", floatArrayOf(1f, 2f, 3f), 0.5),
        ),
    )

    @Test
    fun `an empty or missing directory loads as no documents`() = runBlocking {
        assertTrue(store.loadAll().isEmpty())
        assertTrue(store.lastSkipped.isEmpty())
    }

    @Test
    fun `a document round trips with its chunks, vectors and timings intact`() = runBlocking {
        val original = doc("a")
        store.save(original)
        val loaded = store.loadAll().single()

        assertEquals(original.id, loaded.id)
        assertEquals(original.title, loaded.title)
        assertEquals(original.text, loaded.text)
        assertEquals(original.origin, loaded.origin)
        assertEquals(400, loaded.chunkTargetChars)
        assertEquals(66, loaded.chunkOverlapChars)
        assertEquals(99L, loaded.indexedAtMs)
        assertEquals(2, loaded.chunks.size)
        assertEquals("Cabecera > Sub", loaded.chunks[0].heading)
        assertEquals(null, loaded.chunks[1].heading)
        assertEquals(1.75, loaded.chunks[0].embedMs, 0.0)
        assertArrayEquals(original.chunks[0].embedding, loaded.chunks[0].embedding, 0f)
        assertTrue(loaded.isIndexed)
    }

    @Test
    fun `saving the same id again replaces the document`() = runBlocking {
        store.save(doc("a"))
        store.save(doc("a").copy(title = "Nuevo título", chunks = emptyList()))
        val loaded = store.loadAll()
        assertEquals(1, loaded.size)
        assertEquals("Nuevo título", loaded.single().title)
        assertTrue(loaded.single().chunks.isEmpty())
    }

    @Test
    fun `sample documents come first, then by date added`() = runBlocking {
        store.save(doc("late", added = 30))
        store.save(doc("early", added = 10))
        store.save(doc("sample", DocumentOrigin.SAMPLE, added = 50))
        assertEquals(listOf("sample", "early", "late"), store.loadAll().map { it.id })
    }

    @Test
    fun `a corrupt file is skipped and reported, the rest still load`() = runBlocking {
        store.save(doc("good"))
        File(dir, "rag/broken.json").writeText("{ this is not json")
        File(dir, "rag/other.txt").writeText("ignored: not a json file")
        val loaded = store.loadAll()
        assertEquals(listOf("good"), loaded.map { it.id })
        assertEquals(listOf("broken.json"), store.lastSkipped)
    }

    @Test
    fun `unknown fields from a newer version are ignored`() = runBlocking {
        File(dir, "rag").mkdirs()
        File(dir, "rag/x.json").writeText("""{"id":"x","title":"T","text":"hola","future":{"a":1}}""")
        assertEquals("x", store.loadAll().single().id)
    }

    @Test
    fun `delete removes one document and delete all removes everything`() = runBlocking {
        store.save(doc("a"))
        store.save(doc("b"))
        store.delete("a")
        assertEquals(listOf("b"), store.loadAll().map { it.id })
        store.deleteAll()
        assertTrue(store.loadAll().isEmpty())
    }

    @Test
    fun `ids with unsafe characters cannot escape the directory`() = runBlocking {
        store.save(doc("../../evil id"))
        assertTrue(File(dir, "rag").listFiles()!!.all { it.parentFile == File(dir, "rag") })
        assertEquals("../../evil id", store.loadAll().single().id)
        store.delete("../../evil id")
        assertTrue(store.loadAll().isEmpty())
    }

    @Test
    fun `no temp files are left behind`() = runBlocking {
        store.save(doc("a"))
        assertTrue(File(dir, "rag").listFiles()!!.none { it.name.endsWith(".part") })
    }
}
