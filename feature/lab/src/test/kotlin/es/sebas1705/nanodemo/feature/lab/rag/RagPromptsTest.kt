package es.sebas1705.nanodemo.feature.lab.rag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RagPromptsTest {

    private fun hit(id: String, text: String, score: Double = 0.5, heading: String? = "Sección $id") =
        ScoredChunk(indexed(id, 1f, text = text, heading = heading), score)

    @Test
    fun `an engine without a known window gets the default budget`() {
        assertEquals(RagPrompts.DEFAULT_CONTEXT_CHARS, RagPrompts.contextBudgetChars(null, 512, 100))
    }

    @Test
    fun `a small window leaves room for the answer, the question and the instructions`() {
        // MediaPipe: 1024 tokens total ~ 4096 chars, 256 tokens (1024 chars) for the answer, 500 for instructions.
        val budget = RagPrompts.contextBudgetChars(1024, RagPrompts.maxAnswerTokens(1024), questionChars = 100)
        assertEquals(4096 - 1024 - 500 - 100, budget)
        // A bigger window gives more room.
        assertTrue(RagPrompts.contextBudgetChars(2048, RagPrompts.maxAnswerTokens(2048), 100) > budget)
    }

    @Test
    fun `the budget never drops below a usable minimum`() {
        assertEquals(300, RagPrompts.contextBudgetChars(256, 256, 5_000))
    }

    @Test
    fun `answer length is capped for small windows only`() {
        assertEquals(512, RagPrompts.maxAnswerTokens(null))
        assertEquals(256, RagPrompts.maxAnswerTokens(1024))
        assertEquals(256, RagPrompts.maxAnswerTokens(2048))
        assertEquals(64, RagPrompts.maxAnswerTokens(100))
    }

    @Test
    fun `context entries are numbered in rank order with their label`() {
        val ctx = RagPrompts.buildContext(listOf(hit("a", "Texto A."), hit("b", "Texto B.")), maxChars = 1_000)
        assertEquals("[1] Sección a\nTexto A.\n\n[2] Sección b\nTexto B.", ctx.text)
        assertEquals(listOf("a", "b"), ctx.used.map { it.chunk.chunkId })
        assertEquals(0, ctx.dropped)
        assertFalse(ctx.firstTruncated)
    }

    @Test
    fun `a chunk without heading is labelled with its document title`() {
        val ctx = RagPrompts.buildContext(listOf(hit("a", "Texto.", heading = null)), 1_000)
        assertTrue(ctx.text.startsWith("[1] Doc doc\n"))
    }

    @Test
    fun `chunks that do not fit are dropped from the end and the context stays within budget`() {
        val chunks = listOf(hit("a", "a".repeat(100)), hit("b", "b".repeat(100)), hit("c", "c".repeat(100)))
        val ctx = RagPrompts.buildContext(chunks, maxChars = 280)
        assertEquals(2, ctx.used.size)
        assertEquals(1, ctx.dropped)
        assertTrue(ctx.text.length <= 280)
    }

    @Test
    fun `a lower ranked chunk is not squeezed in ahead of one that did not fit`() {
        val chunks = listOf(hit("a", "a".repeat(100)), hit("big", "b".repeat(500)), hit("small", "c"))
        val ctx = RagPrompts.buildContext(chunks, maxChars = 400)
        assertEquals(listOf("a"), ctx.used.map { it.chunk.chunkId })
    }

    @Test
    fun `when not even the first fits it is cut so the model still gets something`() {
        val ctx = RagPrompts.buildContext(listOf(hit("a", "x".repeat(1_000))), maxChars = 200)
        assertEquals(1, ctx.used.size)
        assertTrue(ctx.firstTruncated)
        assertTrue(ctx.text.length <= 200)
        assertTrue(ctx.text.endsWith("…"))
    }

    @Test
    fun `no chunks give an empty context`() {
        val ctx = RagPrompts.buildContext(emptyList(), 1_000)
        assertEquals("", ctx.text)
        assertTrue(ctx.used.isEmpty())
    }

    @Test
    fun `the rag prompt demands citations, context-only answers and the exact not found sentence`() {
        val ctx = RagPrompts.buildContext(listOf(hit("a", "Hay 23 días.")), 1_000)
        val prompt = RagPrompts.ragPrompt("  ¿Cuántos días?  ", ctx)
        assertTrue(prompt.contains("SOLO el contexto"))
        assertTrue(prompt.contains("[1]"))
        assertTrue(prompt.contains("\"${RagPrompts.NOT_FOUND}\""))
        assertTrue(prompt.contains("Hay 23 días."))
        assertTrue(prompt.contains("Pregunta: ¿Cuántos días?\n"))
        assertTrue(prompt.endsWith("Respuesta:"))
    }

    @Test
    fun `the plain prompt has the question and no context`() {
        val prompt = RagPrompts.plainPrompt("¿Cuántos días?")
        assertTrue(prompt.contains("Pregunta: ¿Cuántos días?"))
        assertFalse(prompt.contains("Contexto"))
    }

    @Test
    fun `the judge prompt carries context, question and answer and asks for the two labelled lines`() {
        val prompt = RagPrompts.judgePrompt("¿Cuántos?", "[1] Hay 23.", "Son 23 [1].")
        assertTrue(prompt.contains("VEREDICTO:"))
        assertTrue(prompt.contains("PUNTUACION:"))
        assertTrue(prompt.contains("[1] Hay 23."))
        assertTrue(prompt.contains("RESPUESTA: Son 23 [1]."))
    }

    @Test
    fun `answer config is deterministic-leaning and scales with the window`() {
        assertEquals(0.2f, RagPrompts.answerConfig(1024).temperature)
        assertEquals(256, RagPrompts.answerConfig(1024).maxOutputTokens)
        assertEquals(512, RagPrompts.answerConfig(null).maxOutputTokens)
    }
}
