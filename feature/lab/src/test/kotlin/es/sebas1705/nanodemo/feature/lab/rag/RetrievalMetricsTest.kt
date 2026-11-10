package es.sebas1705.nanodemo.feature.lab.rag

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RetrievalMetricsTest {

    private fun eval(rank: Int?, latency: Double = 10.0) = QuestionEval(
        question = EvalQuestion("q", "?", "d", "x"),
        rank = rank, latencyMs = latency, topScore = null, retrieved = emptyList(),
    )

    @Test
    fun `first relevant rank is one based and null when nothing matches`() {
        assertEquals(1, firstRelevantRank(listOf(true, false)))
        assertEquals(3, firstRelevantRank(listOf(false, false, true, true)))
        assertNull(firstRelevantRank(listOf(false, false)))
        assertNull(firstRelevantRank(emptyList()))
    }

    @Test
    fun `hit at k and reciprocal rank`() {
        assertTrue(hitAt(1, 1))
        assertTrue(hitAt(3, 3))
        assertFalse(hitAt(4, 3))
        assertFalse(hitAt(null, 10))
        assertEquals(1.0, reciprocalRank(1), 0.0)
        assertEquals(0.25, reciprocalRank(4), 0.0)
        assertEquals(0.0, reciprocalRank(null), 0.0)
    }

    @Test
    fun `summary computes hit rates, mrr and mean latency`() {
        val report = summarize(listOf(eval(1, 10.0), eval(2, 20.0), eval(4, 30.0), eval(null, 40.0)), k = 5, skipped = 2)
        assertEquals(4, report.questions)
        assertEquals(2, report.skipped)
        assertEquals(0.25, report.hitAt1, 1e-9)
        assertEquals(0.5, report.hitAt3, 1e-9)
        assertEquals(0.75, report.hitAtK, 1e-9)
        assertEquals((1.0 + 0.5 + 0.25 + 0.0) / 4, report.mrr, 1e-9)
        assertEquals(25.0, report.meanLatencyMs, 1e-9)
    }

    @Test
    fun `summary of nothing is all zeros, not a division by zero`() {
        val report = summarize(emptyList(), k = 3, skipped = 5)
        assertEquals(0, report.questions)
        assertEquals(0.0, report.mrr, 0.0)
        assertEquals(0.0, report.hitAt1, 0.0)
        assertEquals(0.0, report.meanLatencyMs, 0.0)
    }

    @Test
    fun `evidence matching ignores case, accents and spacing and requires the right document`() {
        val q = EvalQuestion("q", "?", "doc", "Aplicación  Rendi")
        assertTrue(q.isAnsweredBy(indexed("c", 1f, doc = "doc", text = "Se envía en la aplicacion rendi.")))
        assertFalse(q.isAnsweredBy(indexed("c", 1f, doc = "other", text = "Se envía en la aplicacion rendi.")))
        assertFalse(q.isAnsweredBy(indexed("c", 1f, doc = "doc", text = "Se envía por correo.")))
    }

    @Test
    fun `every sample question points at a real document and its evidence is in that document`() {
        for (q in SampleCorpus.questions) {
            val doc = SampleCorpus.document(q.docId)
            assertTrue("document ${q.docId} of ${q.id}", doc != null)
            assertTrue("evidence '${q.evidence}' of ${q.id}", normalizeForMatch(q.evidence) in normalizeForMatch(doc!!.text))
        }
        assertEquals(SampleCorpus.questions.size, SampleCorpus.questions.map { it.id }.toSet().size)
        assertTrue(SampleCorpus.questions.size in 6..10)
        assertEquals(5, SampleCorpus.documents.size)
    }

    @Test
    fun `the evaluator scores rankings, skips unindexed documents and measures latency`() = runBlocking {
        val index = listOf(
            indexed("a1", 1f, 0f, doc = "A", text = "respuesta alfa"),
            indexed("a2", 0.8f, 0.6f, doc = "A", text = "ruido"),
            indexed("b1", 0f, 1f, doc = "B", text = "respuesta beta"),
        )
        val vectors = mapOf("qa" to floatArrayOf(1f, 0f), "qb" to floatArrayOf(0.2f, 1f), "qc" to floatArrayOf(1f, 1f))
        var tick = 0L
        val evaluator = RetrievalEvaluator(embedQuery = { vectors[it] }, texts = XmlTexts.english, nanoClock = { tick += 2_000_000; tick })
        val questions = listOf(
            EvalQuestion("1", "qa", "A", "respuesta alfa"), // rank 1
            EvalQuestion("2", "qb", "B", "respuesta beta"), // b1 scores highest -> rank 1
            EvalQuestion("3", "qc", "B", "respuesta beta"), // a2 (.99), then a1 and b1 tie (.71, index order) -> rank 3
            EvalQuestion("4", "qa", "MISSING", "x"), // not indexed -> skipped
        )
        val progress = mutableListOf<Pair<Int, Int>>()
        val result = evaluator.evaluate(questions, index, RetrievalOptions(topK = 3)) { d, t -> progress += d to t }

        assertEquals(3, result.evals.size)
        assertEquals(1, result.report.skipped)
        assertEquals(listOf(1, 1, 3), result.evals.map { it.rank })
        assertEquals(listOf(1 to 3, 2 to 3, 3 to 3), progress)
        assertEquals(2.0, result.report.meanLatencyMs, 1e-9) // two ticks of 2 ms around each retrieval
        assertEquals(1.0, result.report.hitAt3, 1e-9)
        assertEquals((1.0 + 1.0 + 1.0 / 3) / 3, result.report.mrr, 1e-9)
    }

    @Test
    fun `a query that cannot be embedded fails the evaluation with the question in the message`() = runBlocking {
        val evaluator = RetrievalEvaluator(embedQuery = { null }, texts = XmlTexts.english)
        try {
            evaluator.evaluate(listOf(EvalQuestion("1", "pregunta", "A", "x")), listOf(indexed("a", 1f, doc = "A")), RetrievalOptions())
            fail("expected an error")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("pregunta"))
        }
    }

    @Test
    fun `retrieval depth is at least 3 so hit at 3 is defined even with k 1`() = runBlocking {
        val index = listOf(
            indexed("n1", 1f, 0f, doc = "A", text = "ruido uno"),
            indexed("n2", 0.9f, 0.1f, doc = "A", text = "ruido dos"),
            indexed("hit", 0.8f, 0.2f, doc = "A", text = "la clave"),
        )
        val evaluator = RetrievalEvaluator(embedQuery = { floatArrayOf(1f, 0f) }, texts = XmlTexts.english)
        val result = evaluator.evaluate(listOf(EvalQuestion("1", "q", "A", "la clave")), index, RetrievalOptions(topK = 1))
        assertEquals(3, result.evals.single().rank)
        assertEquals(0.0, result.report.hitAtK, 0.0)
        assertEquals(1.0, result.report.hitAt3, 0.0)
    }

    @Test
    fun `end to end on the sample corpus with a bag of words embedder finds most answers`() = runBlocking {
        val embedder = BagOfWordsEmbedder()
        for (size in CHUNK_SIZE_OPTIONS) {
            val docs = DocumentIndexer(embedder, XmlTexts.english).indexAll(
                SampleCorpus.documents.map { RagDocument(it.id, it.title, it.text, DocumentOrigin.SAMPLE) },
                ChunkerConfig.forTarget(size),
            )
            val result = RetrievalEvaluator(embedder::embed, XmlTexts.english).evaluate(SampleCorpus.questions, docs.toIndex(), RetrievalOptions(topK = 3))
            assertEquals(0, result.report.skipped)
            assertTrue("hit@3 ${result.report.hitAt3} at chunk size $size", result.report.hitAt3 >= 0.8)
        }
    }
}
