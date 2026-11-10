package es.sebas1705.nanodemo.feature.lab.refine

import es.sebas1705.nanodemo.domain.models.InferenceTier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pipelines write every user-visible text through a [RefineTexts], so a translation is honoured. */
class RefineTextsTest {

    private val spanish = object : RefineTexts {
        override val done = "Listo"
        override val yes = "sí"
        override val emptyReply = "Respuesta vacía"
        override fun typedAnswerLabel(number: Int) = "Respuesta $number"
        override fun draftTitle(version: Int) = "Borrador v$version"
    }

    @Test
    fun `yes and no come from the texts but the key stays language independent`() {
        val answer = extractAnswer("Respuesta: sí", AnswerKind.YES_NO, spanish)!!
        assertEquals("yes", answer.key)
        assertEquals("sí", answer.display)
        assertEquals("yes", extractAnswer("Respuesta: sí", AnswerKind.YES_NO)!!.display)
    }

    @Test
    fun `the refine trace and progress use the supplied texts`() = runBlocking {
        val executor = scripted("1. plan", "Draft", """{"verdict":"accept","issues":[]}""")
        val labels = mutableListOf<String>()
        val runner = executor.runner()
        RefinePipeline(runner, texts = spanish).run(
            "Task", RefineSetup(InferenceTier.ML_KIT_NANO, InferenceTier.ML_KIT_NANO, InferenceTier.ML_KIT_NANO),
            onProgress = { labels += it.label },
        )
        assertEquals("Listo", labels.last())
        assertTrue(runner.recorder.trace.value.steps.any { it.title == "Borrador v1" })
    }

    @Test
    fun `the judge labels typed answers with the supplied texts`() = runBlocking {
        val executor = scripted("""{"winner":"A"}""")
        val result = JudgePipeline(executor.runner(), texts = spanish).run(
            "Q", JudgeSource.Typed(listOf("one", "two")), InferenceTier.CLOUD_GEMINI, JudgeMode.PAIRWISE,
        )
        assertEquals(listOf("Respuesta 1", "Respuesta 2"), result.candidates.map { it.label })
    }

    @Test
    fun `an empty engine reply is reported with the supplied texts`() = runBlocking {
        val executor = scripted("")
        val result = RefinePipeline(executor.runner(), texts = spanish).run(
            "Task", RefineSetup(InferenceTier.ML_KIT_NANO, InferenceTier.ML_KIT_NANO, InferenceTier.ML_KIT_NANO),
        )
        assertEquals(RefineStop.FAILED, result.stop)
        assertTrue(result.error!!.contains("Respuesta vacía"))
    }
}
