package es.sebas1705.nanodemo.feature.lab.rag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextImportAndStateTest {

    @Test
    fun `clean normalises line endings, drops the BOM and trims`() {
        val cleaned = TextImport.clean("﻿  Hola\r\nmundo\r\n\r\n  ")!!
        assertEquals("Hola\nmundo", cleaned.text)
        assertFalse(cleaned.truncated)
    }

    @Test
    fun `empty, blank and binary content are rejected`() {
        assertNull(TextImport.clean(""))
        assertNull(TextImport.clean(" \n\t "))
        assertNull(TextImport.clean("%PDF-1.4\u0000\u0001binary"))
    }

    @Test
    fun `text over the limit is cut and flagged`() {
        val cleaned = TextImport.clean("a".repeat(TextImport.MAX_CHARS + 500))!!
        assertEquals(TextImport.MAX_CHARS, cleaned.text.length)
        assertTrue(cleaned.truncated)
        assertFalse(TextImport.clean("a".repeat(TextImport.MAX_CHARS))!!.truncated)
    }

    @Test
    fun `titles come from the file name without its extension`() {
        assertEquals("notas", TextImport.titleFromFileName("notas.md"))
        assertEquals("notas.v2", TextImport.titleFromFileName("notas.v2.txt"))
        assertEquals("README", TextImport.titleFromFileName("README"))
        assertEquals("Documento", TextImport.titleFromFileName(null))
        assertEquals("Documento", TextImport.titleFromFileName("   "))
        assertEquals("Documento", TextImport.titleFromFileName(".md"))
    }

    @Test
    fun `new documents get distinct ids and a fallback title`() {
        val a = TextImport.newDocument("  ", "texto", DocumentOrigin.PASTED, 5L)
        val b = TextImport.newDocument("Mi nota", "texto", DocumentOrigin.FILE, 6L)
        assertNotEquals(a.id, b.id)
        assertEquals("Documento", a.title)
        assertEquals("Mi nota", b.title)
        assertFalse(a.isIndexed)
    }

    @Test
    fun `settings label lists only the options that are on`() {
        assertEquals("frag. 400 · k=3", settingsLabel(400, RetrievalOptions(topK = 3)))
        assertEquals("frag. 250 · k=5 · MMR", settingsLabel(250, RetrievalOptions(topK = 5, mmr = true)))
        assertEquals("frag. mixto · k=1 · ≥0.20",settingsLabel(null, RetrievalOptions(topK = 1, minScore = 0.2)).replace(',', '.'))
    }

    @Test
    fun `common chunk target is the shared size or null when mixed or nothing is indexed`() {
        fun indexedDoc(id: String, size: Int) =
            RagDocument(id, id, "t", chunkTargetChars = size, chunks = listOf(RagChunk("$id#0", 0, null, "t", floatArrayOf(1f))))
        assertEquals(400, listOf(indexedDoc("a", 400), indexedDoc("b", 400)).commonChunkTarget())
        assertNull(listOf(indexedDoc("a", 400), indexedDoc("b", 250)).commonChunkTarget())
        assertNull(listOf(RagDocument("p", "p", "t")).commonChunkTarget())
        assertNull(emptyList<RagDocument>().commonChunkTarget())
    }

    @Test
    fun `settings derive the chunker and retrieval options`() {
        val s = RagSettings(chunkTargetChars = 600, topK = 4, mmr = true, minScore = 0.25f)
        assertEquals(600, s.chunker.target)
        assertEquals(100, s.chunker.overlap)
        assertEquals(4, s.retrieval.topK)
        assertTrue(s.retrieval.mmr)
        assertEquals(0.25, s.retrieval.minScore, 1e-6)
    }
}
