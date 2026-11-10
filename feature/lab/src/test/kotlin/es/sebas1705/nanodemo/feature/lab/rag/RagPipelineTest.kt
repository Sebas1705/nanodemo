package es.sebas1705.nanodemo.feature.lab.rag

import es.sebas1705.nanodemo.domain.models.ChatMessage
import es.sebas1705.nanodemo.domain.models.GenerationConfig
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.StepKind
import es.sebas1705.nanodemo.domain.models.StepResult
import es.sebas1705.nanodemo.domain.models.StepStatus
import es.sebas1705.nanodemo.feature.lab.trace.PipelineRunner
import es.sebas1705.nanodemo.feature.lab.trace.StepExecutor
import es.sebas1705.nanodemo.feature.lab.R
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RagPipelineTest {

    private class Call(val tier: InferenceTier, val prompt: String, val config: GenerationConfig)

    /** Fake executor: records every call and answers with the first script entry whose key is in the prompt. */
    private class Script(private val replies: List<Pair<String, StepResult>>) : StepExecutor {
        val calls = mutableListOf<Call>()
        override suspend fun run(tier: InferenceTier, messages: List<ChatMessage>, config: GenerationConfig, onPartial: (String) -> Unit): StepResult {
            val prompt = messages.last().text
            calls += Call(tier, prompt, config)
            return replies.firstOrNull { (key, _) -> key in prompt }?.second ?: StepResult("?", null)
        }
    }

    private val embedder = BagOfWordsEmbedder()
    private val index: List<IndexedChunk> = runBlocking {
        DocumentIndexer(embedder, XmlTexts.english).indexAll(
            SampleCorpus.documents.map { RagDocument(it.id, it.title, it.text, DocumentOrigin.SAMPLE) },
            ChunkerConfig.forTarget(400),
        ).toIndex()
    }

    private fun pipeline(script: Script, embed: suspend (String) -> FloatArray? = embedder::embed): Pair<RagPipeline, PipelineRunner> {
        val runner = PipelineRunner(script)
        return RagPipeline(runner, embed, XmlTexts.english) to runner
    }

    private val question = "¿Cuántos días de vacaciones tengo al año?"

    @Test
    fun `with retrieval the trace is retrieve, build prompt, generate`() = runBlocking {
        val script = Script(listOf("Pregunta:" to StepResult("Tienes 23 días laborables [1].", null)))
        val (pipeline, runner) = pipeline(script)

        val run = pipeline.answerWithRag(question, InferenceTier.CLOUD_GEMINI, null, index, RetrievalOptions(topK = 3))

        assertTrue(run.ok)
        assertEquals("Tienes 23 días laborables [1].", run.answer)
        assertEquals(listOf(StepKind.RETRIEVE, StepKind.TOOL, StepKind.GENERATE), runner.recorder.trace.value.steps.map { it.kind })
        assertTrue(runner.recorder.trace.value.steps.all { it.status == StepStatus.DONE })
        assertEquals(3, run.retrieved.size)
        assertEquals(3, run.citedChunks)
        assertTrue(run.retrievalMs >= 0.0)
    }

    @Test
    fun `the model gets the retrieved text with numbered citations and the answer-only-from-context instruction`() = runBlocking {
        val script = Script(emptyList())
        val (pipeline, _) = pipeline(script)

        val run = pipeline.answerWithRag(question, InferenceTier.LITERT_LM_LOCAL, 2048, index, RetrievalOptions(topK = 2))

        val prompt = script.calls.single().prompt
        assertTrue(prompt.contains("SOLO el contexto"))
        assertTrue(prompt.contains(RagPrompts.NOT_FOUND))
        assertTrue(prompt.contains("[1] "))
        assertTrue(prompt.contains("[2] "))
        assertTrue("the best chunk (vacation days) is in the prompt", prompt.contains("23 días laborables"))
        assertTrue(prompt.contains("Pregunta: $question"))
        assertEquals(InferenceTier.LITERT_LM_LOCAL, script.calls.single().tier)
        assertEquals(RagPrompts.maxAnswerTokens(2048), script.calls.single().config.maxOutputTokens)
        assertEquals(2, run.citedChunks)
    }

    @Test
    fun `a small window shrinks the context so the prompt fits`() = runBlocking {
        val script = Script(emptyList())
        val (pipeline, _) = pipeline(script)

        val run = pipeline.answerWithRag(question, InferenceTier.MEDIAPIPE_LOCAL, 1024, index, RetrievalOptions(topK = 5))

        val budget = RagPrompts.contextBudgetChars(1024, RagPrompts.maxAnswerTokens(1024), question.length)
        assertEquals(budget, run.contextBudgetChars)
        assertTrue(run.contextChars <= budget)
        // The whole prompt plus the answer reserve stays within the 1024-token window (4 chars per token).
        assertTrue(script.calls.single().prompt.length + RagPrompts.maxAnswerTokens(1024) * 4 <= 1024 * 4)
    }

    @Test
    fun `retrieval above the threshold only, and nothing found skips the model entirely`() = runBlocking {
        val script = Script(emptyList())
        val (pipeline, runner) = pipeline(script)

        val run = pipeline.answerWithRag("¿?",InferenceTier.ML_KIT_NANO, null, index, RetrievalOptions(topK = 3, minScore = 0.2))

        assertTrue(run.modelSkipped)
        assertEquals(XmlTexts.english.string(R.string.rag_answer_not_found), run.answer)
        assertTrue(script.calls.isEmpty())
        val steps = runner.recorder.trace.value.steps
        assertEquals(listOf(StepKind.RETRIEVE, StepKind.GENERATE), steps.map { it.kind })
        assertEquals(StepStatus.SKIPPED, steps.last().status)
    }

    @Test
    fun `a question that cannot be embedded is a failed retrieve step and an error, not a crash`() = runBlocking {
        val script = Script(emptyList())
        val (pipeline, runner) = pipeline(script, embed = { null })

        val run = pipeline.answerWithRag(question, InferenceTier.ML_KIT_NANO, null, index, RetrievalOptions())

        assertFalse(run.ok)
        assertNotNull(run.error)
        assertTrue(script.calls.isEmpty())
        assertEquals(StepStatus.FAILED, runner.recorder.trace.value.steps.single().status)
    }

    @Test
    fun `a generation error is reported with the retrieved chunks kept`() = runBlocking {
        val script = Script(listOf("Pregunta:" to StepResult("", null, "engine unavailable")))
        val (pipeline, runner) = pipeline(script)

        val run = pipeline.answerWithRag(question, InferenceTier.CLOUD_GEMINI, null, index, RetrievalOptions(), judgeTier = InferenceTier.CLOUD_GEMINI)

        assertEquals("engine unavailable", run.error)
        assertEquals(3, run.retrieved.size)
        assertNull("no judge call after a failed answer", run.grounding)
        assertEquals(1, script.calls.size)
        assertEquals(StepStatus.FAILED, runner.recorder.trace.value.steps.last().status)
    }

    @Test
    fun `the groundedness check runs on its own tier and adds judge and validate steps`() = runBlocking {
        val script = Script(
            listOf(
                "Eres un verificador" to StepResult("VEREDICTO: PARCIAL\nPUNTUACION: 6", null),
                "Pregunta:" to StepResult("Tienes 23 días [1].", null),
            ),
        )
        val (pipeline, runner) = pipeline(script)

        val run = pipeline.answerWithRag(
            question, InferenceTier.ML_KIT_NANO, null, index, RetrievalOptions(topK = 2), judgeTier = InferenceTier.CLOUD_GEMINI,
        )

        assertEquals(GroundingVerdict.PARTIAL, run.grounding!!.verdict)
        assertEquals(0.6, run.grounding!!.score!!, 1e-9)
        assertEquals(
            listOf(StepKind.RETRIEVE, StepKind.TOOL, StepKind.GENERATE, StepKind.JUDGE, StepKind.VALIDATE),
            runner.recorder.trace.value.steps.map { it.kind },
        )
        val judgeCall = script.calls.last()
        assertEquals(InferenceTier.CLOUD_GEMINI, judgeCall.tier)
        assertEquals(RagPrompts.JUDGE_MAX_TOKENS, judgeCall.config.maxOutputTokens)
        assertTrue(judgeCall.prompt.contains("RESPUESTA: Tienes 23 días [1]."))
        assertTrue(judgeCall.prompt.contains("23 días laborables"))
    }

    @Test
    fun `without a judge tier there is no judge step`() = runBlocking {
        val (pipeline, runner) = pipeline(Script(emptyList()))
        val run = pipeline.answerWithRag(question, InferenceTier.ML_KIT_NANO, null, index, RetrievalOptions())
        assertNull(run.grounding)
        assertTrue(runner.recorder.trace.value.steps.none { it.kind == StepKind.JUDGE })
    }

    @Test
    fun `a failing judge does not lose the answer`() = runBlocking {
        val script = Script(listOf("Eres un verificador" to StepResult("", null, "judge down"), "Pregunta:" to StepResult("Tienes 23.", null)))
        val (pipeline, _) = pipeline(script)

        val run = pipeline.answerWithRag(question, InferenceTier.ML_KIT_NANO, null, index, RetrievalOptions(), judgeTier = InferenceTier.CLOUD_GEMINI)

        assertEquals("Tienes 23.", run.answer)
        assertEquals("judge down", run.groundingError)
        assertNull(run.grounding)
    }

    @Test
    fun `the baseline sends the bare question with no context`() = runBlocking {
        val script = Script(listOf("Pregunta:" to StepResult("  No lo sé.  ", null)))
        val (pipeline, runner) = pipeline(script)

        val run = pipeline.answerPlain(question, InferenceTier.ML_KIT_NANO, null)

        assertEquals("No lo sé.", run.answer)
        assertTrue(run.retrieved.isEmpty())
        assertFalse(script.calls.single().prompt.contains("Contexto"))
        assertEquals(listOf(StepKind.GENERATE), runner.recorder.trace.value.steps.map { it.kind })
    }

    @Test
    fun `both answers can share one trace`() = runBlocking {
        val (pipeline, runner) = pipeline(Script(emptyList()))
        pipeline.answerPlain(question, InferenceTier.ML_KIT_NANO, null)
        pipeline.answerWithRag(question, InferenceTier.ML_KIT_NANO, null, index, RetrievalOptions())
        val titles = runner.recorder.trace.value.steps.map { it.title }
        assertTrue(titles.first().startsWith("Without retrieval"))
        assertTrue(titles.last().startsWith("With retrieval"))
        assertEquals(4, titles.size)
    }

    @Test
    fun `describing hits and verdicts for the trace`() {
        val hits = listOf(ScoredChunk(indexed("a", 1f, heading = "Gastos"), 0.8312))
        // The decimal separator follows the device locale, so compare ignoring it.
        assertEquals("[1] 0.83 · Gastos", describeHits(hits).replace(',', '.'))
        assertTrue(describe(Groundedness(GroundingVerdict.SUPPORTED, 0.9, ""), XmlTexts.english).startsWith("supported"))
        assertTrue(describe(Groundedness(GroundingVerdict.UNSUPPORTED, null, ""), XmlTexts.english).startsWith("not supported"))
        assertTrue(describe(Groundedness(GroundingVerdict.UNKNOWN, null, ""), XmlTexts.english).contains("could not be read"))
    }
}
