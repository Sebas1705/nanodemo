package es.sebas1705.nanodemo.feature.lab.router

import es.sebas1705.nanodemo.domain.models.GenerationConfig
import es.sebas1705.nanodemo.domain.models.GenerationMetric
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.InferenceTier.CLOUD_GEMINI
import es.sebas1705.nanodemo.domain.models.InferenceTier.FIREBASE_AI
import es.sebas1705.nanodemo.domain.models.InferenceTier.LITERT_LM_LOCAL
import es.sebas1705.nanodemo.domain.models.InferenceTier.MEDIAPIPE_LOCAL
import es.sebas1705.nanodemo.domain.models.InferenceTier.ML_KIT_NANO
import es.sebas1705.nanodemo.domain.models.StepKind
import es.sebas1705.nanodemo.domain.models.StepResult
import es.sebas1705.nanodemo.domain.models.StepStatus
import es.sebas1705.nanodemo.feature.lab.trace.PipelineRunner
import es.sebas1705.nanodemo.feature.lab.trace.StepExecutor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouterPipelineTest {

    private data class Call(val tier: InferenceTier, val text: String, val config: GenerationConfig)

    /** Scripted executor: `reply` decides every answer from the engine and the text it was sent. */
    private class Fake(val reply: (InferenceTier, String) -> StepResult) : StepExecutor {
        val calls = mutableListOf<Call>()
        override suspend fun run(
            tier: InferenceTier,
            messages: List<es.sebas1705.nanodemo.domain.models.ChatMessage>,
            config: GenerationConfig,
            onPartial: (String) -> Unit,
        ): StepResult {
            val text = messages.last().text
            calls += Call(tier, text, config)
            return reply(tier, text)
        }

        fun tiers() = calls.map { it.tier }
    }

    private fun ok(text: String) = StepResult(text, null)
    private fun err(message: String) = StepResult("", null, message)

    private val goodAnswer = "París es la capital de Francia y está a orillas del Sena."
    private val locals = listOf(ML_KIT_NANO, LITERT_LM_LOCAL, MEDIAPIPE_LOCAL)
    private val everything = InferenceTier.entries

    private fun run(
        fake: Fake,
        prompt: String = "¿Cuál es la capital de Francia?",
        policy: RoutingPolicy = RoutingPolicy.LOCAL_FIRST,
        usable: List<InferenceTier> = everything,
        configure: (RouterRequest) -> RouterRequest = { it },
    ): Pair<RouterOutcome, PipelineRunner> = runBlocking {
        val runner = PipelineRunner(fake)
        val outcome = RouterPipeline(runner, testTexts).run(configure(RouterRequest(prompt, policy, usable)))
        outcome to runner
    }

    private fun PipelineRunner.steps() = recorder.trace.value.steps

    // ---- happy path and escalation ------------------------------------------------------------------------

    @Test
    fun `a good local answer is accepted and the cloud steps are skipped`() {
        val fake = Fake { _, _ -> ok(goodAnswer) }
        val (outcome, runner) = run(fake)

        assertTrue(outcome.accepted)
        assertEquals(ML_KIT_NANO, outcome.answeredBy)
        assertTrue(outcome.answeredLocally)
        assertEquals(0, outcome.escalations)
        assertEquals(listOf(ML_KIT_NANO), fake.tiers())
        assertTrue(outcome.exposures.isEmpty())
        assertEquals(0.0, outcome.cloudCostUsd, 0.0)
        assertEquals(goodAnswer, outcome.answer)

        val skipped = runner.steps().filter { it.status == StepStatus.SKIPPED }
        assertEquals(listOf(LITERT_LM_LOCAL, CLOUD_GEMINI), skipped.map { it.tier })
        assertTrue(skipped.all { it.detail!!.contains("not needed") })
    }

    @Test
    fun `an empty local answer escalates to the next engine`() {
        val fake = Fake { tier, _ -> if (tier == ML_KIT_NANO) ok("") else ok(goodAnswer) }
        val (outcome, _) = run(fake)

        assertEquals(LITERT_LM_LOCAL, outcome.answeredBy)
        assertEquals(1, outcome.escalations)
        assertFalse(outcome.attempts.first().accepted)
        assertEquals("not_empty", outcome.attempts.first().gate!!.failures.single().id)
    }

    @Test
    fun `a refusal escalates and the chain can reach the cloud`() {
        val refusal = "Lo siento, pero no puedo ayudarte con esa consulta, de verdad que no."
        val fake = Fake { tier, _ -> if (tier.isCloud) ok(goodAnswer) else ok(refusal) }
        val (outcome, _) = run(fake)

        assertEquals(listOf(ML_KIT_NANO, LITERT_LM_LOCAL, CLOUD_GEMINI), fake.tiers())
        assertEquals(CLOUD_GEMINI, outcome.answeredBy)
        assertEquals(2, outcome.escalations)
        assertFalse(outcome.answeredLocally)
    }

    @Test
    fun `an engine error escalates and is traced as a failed step`() {
        val fake = Fake { tier, _ -> if (tier == ML_KIT_NANO) err("model not loaded") else ok(goodAnswer) }
        val (outcome, runner) = run(fake)

        assertEquals(LITERT_LM_LOCAL, outcome.answeredBy)
        assertEquals("model not loaded", outcome.attempts.first().error)
        assertTrue(runner.steps().any { it.status == StepStatus.FAILED && it.tier == ML_KIT_NANO })
    }

    @Test
    fun `the escalation limit stops the cascade and the rest is skipped`() {
        val fake = Fake { _, _ -> ok("corta") }
        val (outcome, runner) = run(fake, prompt = "Analiza paso a paso y compara las ventajas e inconvenientes de Room frente a SQLDelight, y justifica la elección.") {
            it.copy(settings = RouterSettings(maxEscalations = 0))
        }

        assertEquals(1, fake.calls.size)
        assertFalse(outcome.accepted)
        assertEquals("corta", outcome.answer) // the rejected answer is still returned, flagged as not accepted
        assertTrue(runner.steps().any { it.status == StepStatus.SKIPPED && it.detail!!.contains("limit") })
    }

    @Test
    fun `when every engine is rejected the last usable answer is returned unaccepted`() {
        val fake = Fake { tier, _ -> if (tier == CLOUD_GEMINI) ok("") else ok("x") }
        val (outcome, _) = run(fake, prompt = "Explica con detalle cómo funciona la recolección de basura en la JVM y sus fases.")

        assertFalse(outcome.accepted)
        assertEquals("x", outcome.answer)
        assertEquals(LITERT_LM_LOCAL, outcome.answeredBy) // the last non-empty one
        assertEquals(2, outcome.escalations)
    }

    @Test
    fun `when nothing answers there is an error`() {
        val fake = Fake { _, _ -> err("offline") }
        val (outcome, _) = run(fake)

        assertEquals("", outcome.answer)
        assertNull(outcome.answeredBy)
        assertEquals("offline", outcome.error)
    }

    // ---- no engines / blocked -----------------------------------------------------------------------------

    @Test
    fun `no usable engine runs nothing`() {
        val fake = Fake { _, _ -> ok(goodAnswer) }
        val (outcome, runner) = run(fake, usable = emptyList())

        assertTrue(fake.calls.isEmpty())
        assertNotNull(outcome.blockedReason)
        assertTrue(outcome.error!!.startsWith("Nothing was run"))
        assertTrue(runner.steps().any { it.status == StepStatus.SKIPPED })
    }

    @Test
    fun `no local engine usable still answers through the cloud with local first`() {
        val fake = Fake { _, _ -> ok(goodAnswer) }
        val (outcome, _) = run(fake, usable = listOf(CLOUD_GEMINI, FIREBASE_AI))
        assertEquals(CLOUD_GEMINI, outcome.answeredBy)
        assertEquals(1, outcome.exposures.size)
    }

    @Test
    fun `no cloud engine usable cloud first falls back to local`() {
        val fake = Fake { _, _ -> ok(goodAnswer) }
        val (outcome, _) = run(fake, policy = RoutingPolicy.CLOUD_FIRST, usable = locals)
        assertEquals(ML_KIT_NANO, outcome.answeredBy)
        assertTrue(outcome.exposures.isEmpty())
    }

    // ---- privacy ------------------------------------------------------------------------------------------

    private val piiPrompt = "Escribe un correo a ana@example.com avisando de que llegaré tarde, mi tel es +34 612 345 678"

    @Test
    fun `privacy first never calls a cloud engine for a prompt with pii even if every local answer is rejected`() {
        val fake = Fake { _, _ -> ok("") }
        val (outcome, _) = run(fake, piiPrompt, RoutingPolicy.PRIVACY_FIRST)

        assertTrue(fake.tiers().none { it.isCloud })
        assertTrue(outcome.exposures.isEmpty())
        assertEquals(0, outcome.piiLeakedToCloud)
    }

    @Test
    fun `privacy first with only cloud engines is blocked and sends nothing`() {
        val fake = Fake { _, _ -> ok(goodAnswer) }
        val (outcome, _) = run(fake, piiPrompt, RoutingPolicy.PRIVACY_FIRST, usable = listOf(CLOUD_GEMINI, FIREBASE_AI))

        assertTrue(fake.calls.isEmpty())
        assertTrue(outcome.plan!!.blocked)
        assertTrue(outcome.exposures.isEmpty())
    }

    @Test
    fun `the cloud sees placeholders never the original values and the answer is restored`() {
        val fake = Fake { tier, _ ->
            if (tier.isCloud) ok("Hecho: aviso a <EMAIL_1> y te llamo al <PHONE_1> para confirmar el retraso.") else ok("")
        }
        val (outcome, runner) = run(fake, piiPrompt)

        val cloudCall = fake.calls.single { it.tier.isCloud }
        assertFalse(cloudCall.text.contains("ana@example.com"))
        assertFalse(cloudCall.text.contains("612 345 678"))
        assertTrue(cloudCall.text.contains("<EMAIL_1>"))
        assertTrue(cloudCall.text.contains("<PHONE_1>"))

        assertEquals("Hecho: aviso a ana@example.com y te llamo al +34 612 345 678 para confirmar el retraso.", outcome.answer)
        assertEquals(0, outcome.piiLeakedToCloud)
        assertEquals(2, outcome.piiInPrompt)

        val exposure = outcome.exposures.single()
        assertEquals(cloudCall.text, exposure.sent)
        assertEquals(piiPrompt, exposure.original)
        assertEquals(2, exposure.redaction!!.distinctCount)
        assertTrue(exposure.gatewayApplied)

        val kinds = runner.steps().map { it.kind }
        // redact -> engine -> restore around the cloud call
        val redactIndex = runner.steps().indexOfFirst { it.kind == StepKind.REDACT && it.title.startsWith("Filter") }
        val restoreIndex = runner.steps().indexOfFirst { it.kind == StepKind.REDACT && it.title.startsWith("Restore") }
        val cloudIndex = runner.steps().indexOfFirst { it.tier == CLOUD_GEMINI && it.kind == StepKind.GENERATE }
        assertTrue(kinds.isNotEmpty() && redactIndex in 0 until cloudIndex && cloudIndex < restoreIndex)
    }

    @Test
    fun `a model that rewrites a placeholder slightly still gets restored`() {
        val fake = Fake { tier, _ -> if (tier.isCloud) ok("Listo, escribo a [email_1] y llamo al < PHONE 1 > sin falta ninguna.") else ok("") }
        val (outcome, _) = run(fake, piiPrompt)
        assertTrue(outcome.answer.contains("ana@example.com"))
        assertTrue(outcome.answer.contains("+34 612 345 678"))
    }

    @Test
    fun `an invented placeholder is left alone and reported`() {
        val fake = Fake { tier, _ -> if (tier.isCloud) ok("Aviso a <EMAIL_1> y también a <EMAIL_9> como pedías, aunque nadie lo pidió.") else ok("") }
        val (outcome, runner) = run(fake, piiPrompt)
        assertTrue(outcome.answer.contains("<EMAIL_9>"))
        val restore = runner.steps().first { it.title.startsWith("Restore") }
        assertTrue(restore.detail!!.contains("<EMAIL_9>"))
    }

    @Test
    fun `with the gateway off the raw data reaches the cloud and is counted as leaked`() {
        val fake = Fake { tier, _ -> if (tier.isCloud) ok(goodAnswer) else ok("") }
        val (outcome, _) = run(fake, piiPrompt) { it.copy(settings = RouterSettings(gatewayEnabled = false)) }

        assertTrue(fake.calls.single { it.tier.isCloud }.text.contains("ana@example.com"))
        assertEquals(2, outcome.piiLeakedToCloud)
        assertFalse(outcome.exposures.single().gatewayApplied)
    }

    @Test
    fun `a local answer never produces an exposure`() {
        val fake = Fake { _, _ -> ok(goodAnswer) }
        val (outcome, _) = run(fake, piiPrompt)
        assertTrue(outcome.exposures.isEmpty())
        assertEquals(0, outcome.piiLeakedToCloud)
    }

    @Test
    fun `secrets in the prompt are masked before the cloud call`() {
        val fake = Fake { tier, _ -> if (tier.isCloud) ok(goodAnswer) else ok("") }
        run(fake, "Mi clave es AIzaSyD-9tSrke72PouQMnMX-a7eZSW0jkFMBWY, ¿está bien guardada en el repositorio?")
        assertFalse(fake.calls.single { it.tier.isCloud }.text.contains("AIzaSy"))
    }

    // ---- json, fresh facts, config ------------------------------------------------------------------------

    @Test
    fun `non json from a local engine escalates to a cloud engine in json mode`() {
        val fake = Fake { tier, _ -> if (tier.isCloud) ok("""{"nombre":"Lucía","ciudad":"Valencia"}""") else ok("Lucía vive en Valencia") }
        val (outcome, _) = run(fake, "Extrae nombre y ciudad: Me llamo Lucía y vivo en Valencia.") { it.copy(expectJson = true) }

        assertEquals(CLOUD_GEMINI, outcome.answeredBy)
        val localCall = fake.calls.first()
        assertTrue(localCall.text.contains("valid JSON"))
        assertFalse(localCall.config.jsonMode)
        assertTrue(fake.calls.last().config.jsonMode)
        assertFalse(fake.calls.last().config.webSearch)
    }

    @Test
    fun `fresh facts send the cloud call with web search on`() {
        val fake = Fake { _, _ -> ok("El bitcoin cotiza a un precio que cambia a cada minuto, según las fuentes.") }
        val (outcome, _) = run(fake, "¿Cuál es el precio actual del bitcoin?")
        assertEquals(CLOUD_GEMINI, outcome.answeredBy)
        assertTrue(fake.calls.first().config.webSearch)
    }

    @Test
    fun `output limits follow the engine kind`() {
        val fake = Fake { _, _ -> ok("") }
        run(fake)
        assertEquals(256, fake.calls.first { !it.tier.isCloud }.config.maxOutputTokens)
        assertEquals(512, fake.calls.first { it.tier.isCloud }.config.maxOutputTokens)
    }

    // ---- confidence ---------------------------------------------------------------------------------------

    @Test
    fun `a low self assessment escalates and a high one accepts`() {
        var score = "2"
        val fake = Fake { tier, text ->
            when {
                text.startsWith("Question:") -> ok(score)
                tier.isCloud -> ok("Respuesta de la nube, más elaborada.")
                else -> ok(goodAnswer)
            }
        }
        val (low, _) = run(fake, usable = listOf(ML_KIT_NANO, CLOUD_GEMINI)) { it.copy(confidenceMode = ConfidenceMode.SELF_ASSESS) }
        assertEquals(CLOUD_GEMINI, low.answeredBy)
        assertEquals(0.4, low.attempts.first().confidence!!, 1e-9)
        assertEquals(1, low.escalations)

        score = "5"
        val (high, _) = run(Fake(fake.reply), usable = listOf(ML_KIT_NANO, CLOUD_GEMINI)) { it.copy(confidenceMode = ConfidenceMode.SELF_ASSESS) }
        assertEquals(ML_KIT_NANO, high.answeredBy)
        assertEquals(0, high.escalations)
    }

    @Test
    fun `an unreadable self assessment accepts the answer`() {
        val fake = Fake { _, text -> if (text.startsWith("Question:")) ok("no sabría decirlo") else ok(goodAnswer) }
        val (outcome, _) = run(fake, usable = listOf(ML_KIT_NANO, CLOUD_GEMINI)) { it.copy(confidenceMode = ConfidenceMode.SELF_ASSESS) }
        assertEquals(ML_KIT_NANO, outcome.answeredBy)
        assertNull(outcome.attempts.single().confidence)
    }

    @Test
    fun `the last engine of the route is not judged`() {
        val fake = Fake { _, text -> if (text.startsWith("You are a strict grader")) ok("1") else ok(goodAnswer) }
        val (outcome, runner) = run(fake, usable = listOf(ML_KIT_NANO)) { it.copy(confidenceMode = ConfidenceMode.JUDGE) }
        assertEquals(1, fake.calls.size)
        assertTrue(outcome.accepted)
        assertTrue(runner.steps().any { it.status == StepStatus.SKIPPED && it.detail!!.contains("last engine") })
    }

    @Test
    fun `the judge is a different local engine when there is one`() {
        val fake = Fake { _, text -> if (text.startsWith("You are a strict grader")) ok("5") else ok(goodAnswer) }
        val (outcome, runner) = run(fake) { it.copy(confidenceMode = ConfidenceMode.JUDGE) }

        assertEquals(listOf(ML_KIT_NANO, LITERT_LM_LOCAL), fake.tiers())
        assertEquals(ML_KIT_NANO, outcome.answeredBy)
        assertEquals(1.0, outcome.attempts.single().confidence!!, 1e-9)
        assertTrue(runner.steps().any { it.kind == StepKind.JUDGE && it.tier == LITERT_LM_LOCAL })
    }

    @Test
    fun `a cloud judge only ever sees masked text`() {
        val fake = Fake { tier, text ->
            when {
                text.startsWith("You are a strict grader") -> ok("5")
                else -> ok("Correo para ana@example.com listo: llegaré tarde, perdona las molestias ocasionadas.")
            }
        }
        val (outcome, _) = run(fake, piiPrompt) { it.copy(confidenceMode = ConfidenceMode.JUDGE, judgeTier = CLOUD_GEMINI) }

        val judgeCall = fake.calls.single { it.text.startsWith("You are a strict grader") || it.text.contains("strict grader") }
        assertEquals(CLOUD_GEMINI, judgeCall.tier)
        assertFalse(judgeCall.text.contains("ana@example.com"))
        assertFalse(judgeCall.text.contains("612 345 678"))
        assertTrue(outcome.exposures.any { it.purpose == ExposurePurpose.JUDGE })
        assertEquals(0, outcome.piiLeakedToCloud)
    }

    @Test
    fun `privacy first never uses a cloud judge for sensitive prompts`() {
        val fake = Fake { _, text -> if (text.contains("strict grader")) ok("5") else ok(goodAnswer) }
        run(fake, piiPrompt, RoutingPolicy.PRIVACY_FIRST) { it.copy(confidenceMode = ConfidenceMode.JUDGE, judgeTier = CLOUD_GEMINI) }
        assertTrue(fake.tiers().none { it.isCloud })
    }

    // ---- LLM classifier -----------------------------------------------------------------------------------

    @Test
    fun `the llm classifier runs on the chosen local engine and its reading is used`() {
        val fake = Fake { _, text ->
            if (text.startsWith("Classify the user prompt")) ok("""{"complexity":"complex","sensitive":false,"fresh":false}""") else ok(goodAnswer)
        }
        val (outcome, runner) = run(fake) { it.copy(classifierTier = MEDIAPIPE_LOCAL) }

        assertEquals(MEDIAPIPE_LOCAL, fake.calls.first().tier)
        assertEquals(Complexity.COMPLEX, outcome.classification!!.complexity)
        assertEquals(ClassificationSource.LLM, outcome.classification!!.source)
        assertTrue(runner.steps().any { it.kind == StepKind.ROUTE && it.tier == MEDIAPIPE_LOCAL })
    }

    @Test
    fun `an unreadable llm classification falls back to the heuristic`() {
        val fake = Fake { _, text -> if (text.startsWith("Classify the user prompt")) ok("¿perdón?") else ok(goodAnswer) }
        val (outcome, runner) = run(fake) { it.copy(classifierTier = ML_KIT_NANO) }

        assertEquals(ClassificationSource.HEURISTIC, outcome.classification!!.source)
        assertTrue(runner.steps().any { it.output?.contains("heuristic is kept") == true })
        assertEquals(goodAnswer, outcome.answer)
    }

    @Test
    fun `a failed llm classification falls back to the heuristic`() {
        val fake = Fake { _, text -> if (text.startsWith("Classify the user prompt")) err("boom") else ok(goodAnswer) }
        val (outcome, _) = run(fake) { it.copy(classifierTier = ML_KIT_NANO) }
        assertEquals(ClassificationSource.HEURISTIC, outcome.classification!!.source)
    }

    @Test
    fun `a cloud classifier choice is refused and traced as skipped`() {
        val fake = Fake { _, _ -> ok(goodAnswer) }
        val (_, runner) = run(fake) { it.copy(classifierTier = CLOUD_GEMINI) }
        assertEquals(listOf(ML_KIT_NANO), fake.tiers())
        assertTrue(runner.steps().any { it.status == StepStatus.SKIPPED && it.title.contains("LLM") })
    }

    @Test
    fun `the llm cannot remove sensitivity found by the detector so privacy still holds`() {
        val fake = Fake { _, text ->
            if (text.startsWith("Classify the user prompt")) ok("""{"complexity":"trivial","sensitive":false}""") else ok("")
        }
        run(fake, piiPrompt, RoutingPolicy.PRIVACY_FIRST) { it.copy(classifierTier = ML_KIT_NANO) }
        assertTrue(fake.tiers().none { it.isCloud })
    }

    // ---- measurement --------------------------------------------------------------------------------------

    private fun metric(tier: InferenceTier, totalMs: Long, promptChars: Int, tokens: Int) = GenerationMetric(
        tier = tier, startedAtMs = 0, firstChunkLatencyMs = 100, totalDurationMs = totalMs, promptChars = promptChars,
        responseChars = tokens * 4, success = true, responseTokens = tokens, responseTokensExact = true, memoryDeltaBytes = 0,
    )

    @Test
    fun `latency and cloud cost come from the step metrics`() {
        val fake = Fake { tier, _ ->
            if (tier.isCloud) StepResult(goodAnswer, metric(tier, 1_500, promptChars = 4_000, tokens = 200))
            else StepResult("", metric(tier, 700, promptChars = 100, tokens = 0))
        }
        val (outcome, _) = run(fake, policy = RoutingPolicy.LOCAL_FIRST, usable = listOf(ML_KIT_NANO, CLOUD_GEMINI))

        assertEquals(2_200L, outcome.latencyMs)
        val expected = CloudPricing.usd(1_000, 200)
        assertEquals(expected, outcome.cloudCostUsd, 1e-12)
        assertTrue(outcome.attempts.first().cloudCostUsd == 0.0)
    }

    @Test
    fun `cost falls back to character counts when there is no metric`() {
        val fake = Fake { _, _ -> ok(goodAnswer) }
        val (outcome, _) = run(fake, policy = RoutingPolicy.CLOUD_FIRST)
        assertTrue(outcome.cloudCostUsd > 0.0)
    }

    @Test
    fun `the trace records classify route and every attempt in order`() {
        val fake = Fake { tier, _ -> if (tier == ML_KIT_NANO) ok("") else ok(goodAnswer) }
        val (_, runner) = run(fake)
        val titles = runner.steps().map { it.title }
        assertEquals("Classify the prompt", titles[0])
        assertTrue(titles[1].startsWith("Choose route"))
        assertTrue(titles.indexOf("Answer · Nano") < titles.indexOf("Answer · LiteRT-LM"))
        assertEquals(StepStatus.DONE, runner.steps().first { it.title == "Answer · Nano" }.status)
        assertTrue(runner.steps().none { it.status == StepStatus.RUNNING })
    }
}
