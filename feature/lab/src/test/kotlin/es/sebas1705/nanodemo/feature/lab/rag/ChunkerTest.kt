package es.sebas1705.nanodemo.feature.lab.rag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChunkerTest {

    private fun config(target: Int, overlap: Int) = ChunkerConfig(target, overlap)

    @Test
    fun `blank text gives no chunks`() {
        assertTrue(Chunker.chunk("d", "").isEmpty())
        assertTrue(Chunker.chunk("d", "  \n\n  \r\n ").isEmpty())
    }

    @Test
    fun `a heading with nothing under it gives no chunks`() {
        assertTrue(Chunker.chunk("d", "# Only a title").isEmpty())
    }

    @Test
    fun `short text is a single chunk with a stable id`() {
        val chunks = Chunker.chunk("doc-1", "Hola mundo. Esto es corto.")
        assertEquals(1, chunks.size)
        assertEquals("doc-1#0", chunks[0].id)
        assertEquals(0, chunks[0].index)
        assertEquals("Hola mundo. Esto es corto.", chunks[0].text)
        assertNull(chunks[0].heading)
    }

    @Test
    fun `chunking is deterministic and ids are sequential`() {
        val text = (1..40).joinToString(" ") { "Esta es la frase número $it del documento." }
        val a = Chunker.chunk("d", text, config(200, 40))
        val b = Chunker.chunk("d", text, config(200, 40))
        assertEquals(a, b)
        assertEquals(a.indices.map { "d#$it" }, a.map { it.id })
        assertEquals(a.indices.toList(), a.map { it.index })
    }

    @Test
    fun `no chunk is empty or longer than the target on any input`() {
        val inputs = listOf(
            SampleCorpus.documents.joinToString("\n\n") { it.text },
            "a".repeat(2_000),
            "palabra ".repeat(500),
            (1..200).joinToString(" ") { "Frase corta $it." },
            "- uno\n- dos\n- tres\n\n\n\n" + "x".repeat(300),
        )
        for (target in listOf(80, 120, 250, 400, 900)) {
            for (text in inputs) {
                val chunks = Chunker.chunk("d", text, config(target, target / 6))
                assertTrue(chunks.isNotEmpty())
                for (chunk in chunks) {
                    assertTrue("blank chunk at target $target", chunk.text.isNotBlank())
                    assertTrue("chunk of ${chunk.text.length} > $target", chunk.text.length <= target)
                }
            }
        }
    }

    @Test
    fun `a tiny target is clamped so every sentence is not its own chunk`() {
        assertEquals(ChunkerConfig.MIN_TARGET_CHARS, ChunkerConfig(5, 0).target)
        // Overlap is capped at half a chunk and never negative.
        assertEquals(50, ChunkerConfig(100, 500).overlap)
        assertEquals(0, ChunkerConfig(200, -10).overlap)
    }

    @Test
    fun `chunks break between sentences, not inside them`() {
        val text = (1..30).joinToString(" ") { "La frase $it termina aquí." }
        val chunks = Chunker.chunk("d", text, config(150, 0))
        assertTrue(chunks.size > 1)
        chunks.forEach {
            assertTrue("chunk should start at a sentence: ${it.text}", it.text.startsWith("La frase"))
            assertTrue("chunk should end at a sentence: ${it.text}", it.text.endsWith("aquí."))
        }
    }

    @Test
    fun `without overlap every sentence appears in exactly one chunk`() {
        val sentences = (1..30).map { "La frase $it termina aquí." }
        val chunks = Chunker.chunk("d", sentences.joinToString(" "), config(150, 0))
        sentences.forEach { s -> assertEquals("'$s'", 1, chunks.count { s in it.text }) }
    }

    @Test
    fun `overlap repeats whole trailing sentences at the start of the next chunk`() {
        val sentences = (10..49).map { "Frase número $it del texto." }
        val chunks = Chunker.chunk("d", sentences.joinToString(" "), config(160, 60))
        assertTrue(chunks.size > 2)
        for ((previous, next) in chunks.zipWithNext()) {
            // The overlap is the leading sentence(s) of `next` that `previous` ends with, and it fits the overlap size.
            val firstSentence = next.text.substringBefore(". ") + "."
            assertTrue("'${next.text}' should start with a sentence the previous chunk ends with", previous.text.contains(firstSentence))
            val overlap = (previous.text.length downTo 1).map { previous.text.takeLast(it) }.first { next.text.startsWith(it) }
            assertTrue("overlap '$overlap' exceeds 60 chars", overlap.length <= 60)
            assertTrue(overlap.startsWith("Frase") && overlap.endsWith("."))
        }
        // Every sentence is still present somewhere.
        sentences.forEach { s -> assertTrue(chunks.any { s in it.text }) }
    }

    @Test
    fun `overlap never makes a chunk repeat the previous one entirely`() {
        val text = (1..60).joinToString(" ") { "Oración $it." }
        val chunks = Chunker.chunk("d", text, config(100, 50))
        chunks.zipWithNext().forEach { (a, b) -> assertTrue(a.text != b.text) }
    }

    @Test
    fun `a sentence longer than the target is cut at word boundaries and loses nothing`() {
        val words = (1..120).map { "palabra$it" }
        val chunks = Chunker.chunk("d", words.joinToString(" "), config(100, 0))
        assertTrue(chunks.size > 5)
        chunks.forEach { assertTrue(it.text.length <= 100) }
        assertEquals(words, chunks.flatMap { it.text.split(" ") })
    }

    @Test
    fun `a very long sentence prefers cutting at a comma`() {
        val clauses = (1..8).map { "esta es la cláusula número $it de una frase interminable" }
        val chunks = Chunker.chunk("d", clauses.joinToString(", ") + ".", config(160, 0))
        assertTrue(chunks.size > 1)
        // All but the last piece end on the comma that separated clauses.
        chunks.dropLast(1).forEach { assertTrue("'${it.text}'", it.text.endsWith(",")) }
    }

    @Test
    fun `a word longer than the target is hard-cut`() {
        val chunks = Chunker.chunk("d", "x".repeat(1_000), config(100, 0))
        assertEquals(10, chunks.size)
        chunks.forEach { assertEquals(100, it.text.length) }
    }

    @Test
    fun `markdown headings become a breadcrumb and are not part of the body`() {
        val text = """
            # Manual
            Intro del manual.

            ## Vacaciones
            Hay 23 días.

            ### Detalle
            Se piden con antelación.

            ## Gastos
            Se reclaman en Rendi.
        """.trimIndent()
        val chunks = Chunker.chunk("d", text, config(500, 0))
        assertEquals(
            listOf("Manual", "Manual > Vacaciones", "Manual > Vacaciones > Detalle", "Manual > Gastos"),
            chunks.map { it.heading },
        )
        assertEquals(
            listOf("Intro del manual.", "Hay 23 días.", "Se piden con antelación.", "Se reclaman en Rendi."),
            chunks.map { it.text },
        )
    }

    @Test
    fun `chunks never cross a heading even when they would fit`() {
        val text = "## A\nUna.\n\n## B\nDos."
        val chunks = Chunker.chunk("d", text, config(500, 0))
        assertEquals(2, chunks.size)
        assertEquals(listOf("A", "B"), chunks.map { it.heading })
    }

    @Test
    fun `a hash that is not a heading stays in the text`() {
        val chunks = Chunker.chunk("d", "#hashtag y #otro más")
        assertEquals("#hashtag y #otro más", chunks.single().text)
        assertNull(chunks.single().heading)
    }

    @Test
    fun `paragraph breaks are kept inside a chunk and soft line wraps are joined`() {
        val text = "Primera línea\nde la misma frase.\n\nSegundo párrafo."
        val chunks = Chunker.chunk("d", text, config(500, 0))
        assertEquals("Primera línea de la misma frase.\n\nSegundo párrafo.", chunks.single().text)
    }

    @Test
    fun `list items are separate units`() {
        val text = "- Primero\n- Segundo\n- Tercero"
        val chunks = Chunker.chunk("d", text, config(ChunkerConfig.MIN_TARGET_CHARS, 0))
        assertEquals("- Primero\n- Segundo\n- Tercero", chunks.single().text)
        // With a target that only fits two items the third goes to the next chunk, never splitting an item.
        val a = "- " + "a".repeat(40)
        val b = "- " + "b".repeat(40)
        val c = "- " + "c".repeat(40)
        val tiny = Chunker.chunk("d", "$a\n$b\n$c", config(90, 0))
        assertEquals(listOf("$a\n$b", c), tiny.map { it.text })
    }

    @Test
    fun `Windows and old Mac line endings are handled`() {
        val chunks = Chunker.chunk("d", "# T\r\nUno.\r\n\r\nDos.\rTres.", config(500, 0))
        assertEquals("T", chunks.single().heading)
        assertEquals("Uno.\n\nDos. Tres.", chunks.single().text)
    }

    @Test
    fun `Spanish inverted question marks start a new sentence`() {
        val text = "Esto es una afirmación. ¿Esto es una pregunta? ¡Y esto una exclamación!"
        val chunks = Chunker.chunk("d", text, config(ChunkerConfig.MIN_TARGET_CHARS, 0))
        // Fits in one chunk; the point is that splitting on the boundaries did not drop or alter anything.
        assertEquals(text, chunks.single().text)
        // Forced to split, each chunk starts at one of the three sentences.
        val split = Chunker.chunk("d", (1..3).joinToString(" ") { text }, config(80, 0))
        assertTrue(split.size > 1)
        split.forEach { assertTrue(it.text, it.text.first() in "E¿¡") }
        assertFalse(split.any { it.text.isBlank() })
    }

    @Test
    fun `every sample evidence phrase survives chunking at all offered sizes`() {
        for (size in CHUNK_SIZE_OPTIONS) {
            val chunksByDoc = SampleCorpus.documents.associate { it.id to Chunker.chunk(it.id, it.text, ChunkerConfig.forTarget(size)) }
            for (q in SampleCorpus.questions) {
                val evidence = normalizeForMatch(q.evidence)
                val found = chunksByDoc.getValue(q.docId).any { evidence in normalizeForMatch(it.text) }
                assertTrue("evidence '${q.evidence}' lost at chunk size $size", found)
            }
        }
    }
}
