package es.sebas1705.nanodemo.feature.lab.router

import es.sebas1705.nanodemo.core.designsystem.label
import es.sebas1705.nanodemo.domain.models.ChatMessage
import es.sebas1705.nanodemo.domain.models.GenerationConfig
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.Role
import es.sebas1705.nanodemo.domain.models.StepKind
import es.sebas1705.nanodemo.domain.models.StepResult
import es.sebas1705.nanodemo.feature.lab.trace.LocalOutcome
import es.sebas1705.nanodemo.feature.lab.trace.PipelineRunner

/** Everything one router run needs; the UI builds it from its form, tests build it directly. */
data class RouterRequest(
    val prompt: String,
    val policy: RoutingPolicy,
    /** Engines usable right now (see `usableTiers()`). */
    val usable: List<InferenceTier>,
    val manualTier: InferenceTier? = null,
    val markedSensitive: Boolean = false,
    val expectJson: Boolean = false,
    val settings: RouterSettings = RouterSettings(),
    val confidenceMode: ConfidenceMode = ConfidenceMode.NONE,
    /** Minimum normalised score (0..1) a self-assessment or judge must give for the answer to be kept. */
    val confidenceThreshold: Double = 0.6,
    /** Engine for the JUDGE step; null = choose automatically (a different local engine when there is one). */
    val judgeTier: InferenceTier? = null,
    /** Local engine that classifies the prompt with an LLM; null = heuristic only. */
    val classifierTier: InferenceTier? = null,
    val observedLatencyMs: Map<InferenceTier, Long> = emptyMap(),
)

/** One thing that was sent to a cloud engine, kept so the UI can show exactly what left the device. */
data class Exposure(
    val tier: InferenceTier,
    /** What the call was for: the answer, a self-assessment or a judge verdict. */
    val purpose: String,
    /** The text before the gateway (never leaves the device). */
    val original: String,
    /** The exact text that was sent. */
    val sent: String,
    val gatewayApplied: Boolean,
    val redaction: RedactionResult?,
    /** Personal data still detectable in `sent`: empty when the gateway did its job. */
    val leaked: List<PiiMatch>,
)

data class Attempt(
    val tier: InferenceTier,
    /** The answer as the user sees it (placeholders already restored). */
    val answer: String,
    val error: String?,
    val gate: GateResult?,
    /** Self-assessment or judge score normalised to 0..1, when one was obtained. */
    val confidence: Double?,
    val accepted: Boolean,
    val latencyMs: Long,
    val cloudCostUsd: Double,
    val rehydration: RehydrationResult?,
)

data class RouterOutcome(
    val classification: Classification?,
    val plan: RoutePlan?,
    val attempts: List<Attempt>,
    val answer: String,
    val answeredBy: InferenceTier?,
    /** True when the final answer passed the gate (false: the cascade ran out of engines and returned the last answer). */
    val accepted: Boolean,
    val escalations: Int,
    val blockedReason: String?,
    val error: String?,
    val exposures: List<Exposure>,
    val latencyMs: Long,
    val cloudCostUsd: Double,
) {
    val answeredLocally: Boolean get() = answeredBy != null && !answeredBy.isCloud

    /** Distinct personal data detected in the prompt (occurrences, as the classifier counts them). */
    val piiInPrompt: Int get() = classification?.features?.piiCount ?: 0

    /** Personal data that did reach a cloud engine in clear (zero unless the gateway was off or failed). */
    val piiLeakedToCloud: Int get() = exposures.sumOf { it.leaked.size }
}

/**
 * The hybrid edge/cloud router: classify -> plan a route -> cascade through the engines with a quality gate,
 * masking personal data before anything reaches a cloud engine and restoring it in the answer. Every decision is a
 * step in the runner's trace. All model access goes through [PipelineRunner], so a fake executor drives it in tests.
 */
class RouterPipeline(
    private val runner: PipelineRunner,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val recorder get() = runner.recorder

    suspend fun run(request: RouterRequest): RouterOutcome {
        recorder.reset()
        var latency = 0L
        var cost = 0.0
        val exposures = mutableListOf<Exposure>()

        // 1. Classify (heuristic first: it is the fallback and it is where PII is counted).
        val heuristic = runner.local(StepKind.ROUTE, "Clasificar el prompt", input = request.prompt) {
            val c = PromptClassifier.classify(request.prompt, request.markedSensitive)
            LocalOutcome(c, output = c.summary(), detail = c.reasons.joinToString("\n"))
        }
        var classification = heuristic
        val classifierTier = request.classifierTier?.takeIf { !it.isCloud && it in request.usable }
        if (classifierTier != null) {
            val call = guarded(
                kind = StepKind.ROUTE,
                title = "Clasificar con ${classifierTier.label()}",
                tier = classifierTier,
                text = LlmClassifier.prompt(request.prompt),
                config = GenerationConfig(temperature = 0.0f, maxOutputTokens = 60),
                purpose = "clasificación",
                gatewayEnabled = false,
                exposures = exposures,
            )
            latency += call.latencyMs
            val parsed = if (call.result.ok) LlmClassifier.parse(call.result.text) else null
            classification = runner.local(StepKind.ROUTE, "Leer la clasificación del LLM") {
                if (parsed == null) {
                    LocalOutcome(heuristic, output = "no se pudo leer: se mantiene la heurística")
                } else {
                    val merged = PromptClassifier.merge(heuristic, parsed)
                    LocalOutcome(merged, output = merged.summary())
                }
            }
        } else if (request.classifierTier != null) {
            recorder.skip(StepKind.ROUTE, "Clasificar con LLM", "el motor elegido no está disponible o no es local", request.classifierTier)
        }

        // 2. Plan the route.
        val plan = runner.local(StepKind.ROUTE, "Elegir ruta · ${request.policy.label}") {
            val p = RoutePlanner.plan(
                request.policy, classification, request.usable, request.settings, request.manualTier, request.observedLatencyMs,
            )
            val notes = p.notes.toMutableList()
            if (classification.sensitive && classification.features.piiCount == 0 && p.engines.any { it.tier.isCloud }) {
                notes += "marcado como sensible: solo se enmascaran datos reconocibles"
            }
            val shown = p.copy(notes = notes)
            LocalOutcome(shown, output = shown.describe(), detail = notes.joinToString("\n").ifEmpty { null })
        }
        if (plan.blocked) {
            recorder.skip(StepKind.GENERATE, "Respuesta", "ruta bloqueada: ${plan.blockedReason}")
            return RouterOutcome(
                classification, plan, emptyList(), "", null, false, 0, plan.blockedReason, "No se ejecutó nada: ${plan.blockedReason}.",
                exposures, latency, cost,
            )
        }

        // 3. Cascade.
        val maxAttempts = (request.settings.maxEscalations.coerceAtLeast(0)) + 1
        val engines = plan.engines.take(maxAttempts)
        plan.engines.drop(maxAttempts).forEach {
            recorder.skip(StepKind.GENERATE, "Respuesta · ${it.tier.label()}", "límite de escalados alcanzado", it.tier)
        }
        val attempts = mutableListOf<Attempt>()
        for ((index, engine) in engines.withIndex()) {
            val isLast = index == engines.lastIndex
            val next = engines.getOrNull(index + 1)
            val answerCall = guarded(
                kind = StepKind.GENERATE,
                title = "Respuesta · ${engine.tier.label()}",
                tier = engine.tier,
                text = request.prompt,
                config = answerConfig(engine.tier, request, classification),
                purpose = "respuesta",
                gatewayEnabled = engine.viaGateway,
                exposures = exposures,
                decorate = { body, masked -> decorateAnswer(body, masked, request.expectJson, engine.tier) },
            )
            var attemptLatency = answerCall.latencyMs
            var attemptCost = answerCall.costUsd

            var gate: GateResult? = null
            var confidence: Double? = null
            var accepted: Boolean
            var error: String? = null

            if (!answerCall.result.ok) {
                error = answerCall.result.error ?: "Error"
                accepted = false
                val outcome = if (next != null) "falló el motor: se escala a ${next.tier.label()}" else "falló el motor y no quedan más"
                runner.local(StepKind.VALIDATE, "Control de calidad", input = null) { LocalOutcome(Unit, output = outcome, detail = error) }
            } else {
                val g = QualityGate.evaluate(answerCall.answer, classification.complexity, request.expectJson)
                gate = g
                accepted = g.accepted
                val needsConfidence = g.accepted && request.confidenceMode != ConfidenceMode.NONE && !isLast
                val verdict = when {
                    needsConfidence -> "aceptada por heurísticas; falta la comprobación de confianza"
                    g.accepted -> "aceptada"
                    next != null -> "${g.summary()} → se escala a ${next.tier.label()}"
                    else -> "${g.summary()}; es la última opción y se devuelve igualmente"
                }
                runner.local(StepKind.VALIDATE, "Control de calidad", input = answerCall.answer) {
                    LocalOutcome(Unit, output = verdict, detail = g.checks.joinToString("\n") { (if (it.passed) "ok  " else "FALLA ") + it.label })
                }

                if (needsConfidence) {
                    val check = confidenceCheck(request, engine.tier, answerCall.answer, exposures)
                    attemptLatency += check.latencyMs
                    attemptCost += check.costUsd
                    confidence = check.score
                    accepted = check.score == null || check.score >= request.confidenceThreshold
                    val decision = when {
                        check.score == null -> "sin puntuación legible: se acepta"
                        accepted -> "confianza ${percent(check.score)} ≥ ${percent(request.confidenceThreshold)}: se acepta"
                        next != null -> "confianza ${percent(check.score)} < ${percent(request.confidenceThreshold)} → se escala a ${next.tier.label()}"
                        else -> "confianza baja pero no quedan más motores"
                    }
                    runner.local(StepKind.VALIDATE, "Decisión", input = null) { LocalOutcome(Unit, output = decision) }
                } else if (g.accepted && request.confidenceMode != ConfidenceMode.NONE) {
                    recorder.skip(
                        if (request.confidenceMode == ConfidenceMode.JUDGE) StepKind.JUDGE else StepKind.VALIDATE,
                        "Comprobación de confianza", "último motor de la ruta: no hay a dónde escalar",
                    )
                }
            }

            latency += attemptLatency
            cost += attemptCost
            attempts += Attempt(
                tier = engine.tier,
                answer = answerCall.answer,
                error = error,
                gate = gate,
                confidence = confidence,
                accepted = accepted,
                latencyMs = attemptLatency,
                cloudCostUsd = attemptCost,
                rehydration = answerCall.rehydration,
            )
            if (accepted) {
                engines.drop(index + 1).forEach {
                    recorder.skip(StepKind.GENERATE, "Respuesta · ${it.tier.label()}", "no hizo falta: la respuesta anterior pasó el control", it.tier)
                }
                break
            }
        }

        val winner = attempts.lastOrNull { it.accepted }
        val fallback = attempts.lastOrNull { it.error == null && it.answer.isNotBlank() }
        val chosen = winner ?: fallback
        return RouterOutcome(
            classification = classification,
            plan = plan,
            attempts = attempts,
            answer = chosen?.answer.orEmpty(),
            answeredBy = chosen?.tier,
            accepted = winner != null,
            escalations = (attempts.size - 1).coerceAtLeast(0),
            blockedReason = null,
            error = if (chosen == null) attempts.lastOrNull()?.error ?: "Ningún motor pudo responder." else null,
            exposures = exposures,
            latencyMs = latency,
            cloudCostUsd = cost,
        )
    }

    // ---- helpers ------------------------------------------------------------------------------------------

    private fun answerConfig(tier: InferenceTier, request: RouterRequest, c: Classification) = GenerationConfig(
        temperature = 0.3f,
        maxOutputTokens = if (tier.isCloud) request.settings.cloudMaxOutputTokens else request.settings.localMaxOutputTokens,
        jsonMode = request.expectJson && tier.isCloud,
        webSearch = c.needsFreshFacts && tier.isCloud && !request.expectJson,
    )

    private fun decorateAnswer(body: String, masked: Boolean, expectJson: Boolean, tier: InferenceTier): String = buildString {
        if (masked) {
            append("Tokens such as <EMAIL_1> or <PHONE_1> stand in for private data. Copy them exactly as written if you need to mention them.\n\n")
        }
        append(body)
        // Cloud engines get a real JSON mode; the local ones only get the instruction.
        if (expectJson && !tier.isCloud) append("\n\nReply ONLY with valid JSON, no explanation.")
    }

    private class Guarded(
        val result: StepResult,
        /** The answer with the original values restored. */
        val answer: String,
        val latencyMs: Long,
        val costUsd: Double,
        val rehydration: RehydrationResult?,
    )

    /**
     * One model call with the privacy gateway in front when the engine is a cloud one: REDACT -> engine -> restore.
     * A cloud call is always recorded as an [Exposure] so the UI can show what left the device.
     */
    private suspend fun guarded(
        kind: StepKind,
        title: String,
        tier: InferenceTier,
        text: String,
        config: GenerationConfig,
        purpose: String,
        gatewayEnabled: Boolean,
        exposures: MutableList<Exposure>,
        decorate: (body: String, masked: Boolean) -> String = { body, _ -> body },
    ): Guarded {
        val cloud = tier.isCloud
        var redaction: RedactionResult? = null
        var outgoing = decorate(text, false)

        if (cloud && gatewayEnabled) {
            redaction = runner.local(StepKind.REDACT, "Filtrar datos personales · ${tier.label()}", input = text) {
                val r = PiiGateway.redact(text)
                val detail = if (r.isEmpty) "nada que filtrar" else
                    r.replacements.joinToString("\n") { "${it.placeholder} ← ${it.kind.label} (${it.occurrences}×)" }
                LocalOutcome(r, output = decorate(r.redacted, !r.isEmpty), detail = detail)
            }
            outgoing = decorate(redaction.redacted, !redaction.isEmpty)
        }

        val leaked = if (cloud) PiiGateway.residual(outgoing) else emptyList()
        if (cloud) {
            exposures += Exposure(tier, purpose, text, outgoing, redaction != null, redaction, leaked)
        }

        // Safety net: if the gateway is on and something recognisable is still in the outgoing text, do not send it.
        if (cloud && gatewayEnabled && leaked.isNotEmpty()) {
            val message = "envío cancelado: quedan ${leaked.size} datos sin filtrar"
            recorder.fail(recorder.begin(kind, title, tier, outgoing), message)
            return Guarded(StepResult("", null, message), "", 0L, 0.0, null)
        }

        val started = clock()
        val result = runner.generate(kind, title, tier, listOf(ChatMessage(Role.USER, outgoing)), config)
        val wall = clock() - started
        val latency = result.metric?.totalDurationMs ?: wall

        var answer = result.text
        var rehydration: RehydrationResult? = null
        if (redaction != null && result.ok) {
            val r = redaction
            rehydration = runner.local(StepKind.REDACT, "Restaurar datos · ${tier.label()}", input = result.text) {
                val back = PiiGateway.rehydrate(result.text, r)
                val out = if (r.isEmpty) "sin datos que restaurar" else "${back.restored.size} de ${r.distinctCount} restaurados"
                val detail = buildList {
                    if (back.missing.isNotEmpty()) add("el modelo no usó: " + back.missing.joinToString { it.placeholder })
                    if (back.unknown.isNotEmpty()) add("marcadores desconocidos, sin tocar: " + back.unknown.joinToString())
                }.joinToString("\n").ifEmpty { null }
                LocalOutcome(back, output = out, detail = detail)
            }
            answer = rehydration.text
        }

        val costUsd = if (!cloud) 0.0 else result.metric?.let {
            CloudPricing.usd(CloudPricing.tokensFromChars(it.promptChars), it.responseTokens)
        } ?: if (result.ok) CloudPricing.usdForChars(outgoing.length, result.text.length) else 0.0
        return Guarded(result, answer, latency, costUsd, rehydration)
    }

    private class Confidence(val score: Double?, val latencyMs: Long, val costUsd: Double)

    /** Self-assessment (same engine) or JUDGE (another engine) on an answer that passed the heuristics. */
    private suspend fun confidenceCheck(
        request: RouterRequest,
        answered: InferenceTier,
        answer: String,
        exposures: MutableList<Exposure>,
    ): Confidence {
        val judge = if (request.confidenceMode == ConfidenceMode.JUDGE) chooseJudge(request, answered) else answered
        val judging = request.confidenceMode == ConfidenceMode.JUDGE
        val prompt = if (judging) ConfidenceParser.judgePrompt(request.prompt, answer) else ConfidenceParser.selfAssessPrompt(request.prompt, answer)
        // A cloud judge only ever sees masked text, even when the answering engine was local.
        val call = guarded(
            kind = if (judging) StepKind.JUDGE else StepKind.VALIDATE,
            title = if (judging) "Juez · ${judge.label()}" else "Autoevaluación · ${judge.label()}",
            tier = judge,
            text = prompt,
            config = GenerationConfig(temperature = 0.0f, maxOutputTokens = 8),
            purpose = if (judging) "juez" else "autoevaluación",
            gatewayEnabled = request.settings.gatewayEnabled,
            exposures = exposures,
        )
        val score = if (call.result.ok) ConfidenceParser.parse(call.result.text) else null
        return Confidence(score, call.latencyMs, call.costUsd)
    }

    /** Never a cloud judge for a sensitive prompt under the privacy policy; otherwise prefer a different local engine. */
    private fun chooseJudge(request: RouterRequest, answered: InferenceTier): InferenceTier {
        val privacyLocked = request.policy == RoutingPolicy.PRIVACY_FIRST &&
            (request.markedSensitive || PiiDetector.detect(request.prompt).isNotEmpty())
        request.judgeTier?.takeIf { it in request.usable && !(privacyLocked && it.isCloud) }?.let { return it }
        val locals = request.usable.filter { !it.isCloud }
        locals.filter { it != answered }.maxByOrNull { EngineProfiles.of(it).quality }?.let { return it }
        return answered
    }

    private fun percent(v: Double) = "${(v * 100).toInt()}%"
}
