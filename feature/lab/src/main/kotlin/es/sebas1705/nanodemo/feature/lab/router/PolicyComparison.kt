package es.sebas1705.nanodemo.feature.lab.router

import es.sebas1705.nanodemo.domain.models.ChatMessage
import es.sebas1705.nanodemo.domain.models.GenerationConfig
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.StepResult
import es.sebas1705.nanodemo.feature.lab.trace.PipelineRunner
import es.sebas1705.nanodemo.feature.lab.trace.StepExecutor

/** A prompt of the built-in comparison set. `quick` ones form the short run. */
data class BenchPrompt(
    val id: String,
    val label: String,
    val text: String,
    val expectJson: Boolean = false,
    val quick: Boolean = false,
)

/** Ten prompts that stress different routing decisions: trivial, code, maths, multi-step, personal data, fresh facts, long, JSON. */
object RouterBenchSet {
    private const val LONG_TEXT =
        "Resume en tres puntos el siguiente texto: Las aplicaciones modernas de Android se construyen sobre Jetpack Compose, " +
            "un conjunto de herramientas declarativo que sustituye a las vistas XML. En lugar de modificar widgets, el " +
            "desarrollador describe cómo debe verse la interfaz para un estado dado y Compose se encarga de actualizarla cuando " +
            "ese estado cambia. Esto reduce el código repetitivo y hace más fácil probar y reutilizar los componentes. A la vez, " +
            "obliga a pensar con cuidado dónde vive el estado, cómo se eleva hasta un ViewModel y qué partes de la pantalla " +
            "deben recomponerse. Los equipos que migran suelen hacerlo de forma gradual, pantalla a pantalla, conviviendo " +
            "con vistas clásicas mediante interoperabilidad, y miden el rendimiento con perfiles de línea base para evitar " +
            "regresiones en el arranque y en el desplazamiento de las listas largas. Justifica cada punto."

    val prompts: List<BenchPrompt> = listOf(
        BenchPrompt("trivial", "Simple", "¿Cuál es la capital de Francia?", quick = true),
        BenchPrompt("translate", "Traducción", "Traduce al inglés: «buenos días, ¿cómo estás?»"),
        BenchPrompt("code", "Código", "Escribe una función en Kotlin que invierta una lista sin usar reversed() y añade un ejemplo de uso.", quick = true),
        BenchPrompt("math", "Matemáticas", "Resuelve paso a paso: 3x + 7 = 28. Después calcula el 15% de 240."),
        BenchPrompt("plan", "Varios pasos", "Compara las ventajas e inconvenientes de usar Room frente a SQLDelight en una app Android y recomiéndame una opción en tres pasos."),
        BenchPrompt(
            "pii-contact", "Datos de contacto",
            "Redacta un correo para ana.garcia@example.com confirmando la cita del jueves. Mi teléfono es +34 612 345 678 y mi DNI 12345678Z.",
            quick = true,
        ),
        BenchPrompt(
            "pii-bank", "Datos bancarios",
            "Mi IBAN es ES91 2100 0418 4502 0005 1332 y mi tarjeta 4111 1111 1111 1111. Explícame cómo comprobar que una transferencia SEPA ha llegado.",
        ),
        BenchPrompt("fresh", "Datos recientes", "¿Cuál es el precio actual del bitcoin y qué noticias hay hoy sobre él?", quick = true),
        BenchPrompt("long", "Texto largo", LONG_TEXT),
        BenchPrompt(
            "json", "JSON",
            "Extrae en JSON con las claves nombre y ciudad: «Me llamo Lucía y vivo en Valencia desde 2019.»",
            expectJson = true, quick = true,
        ),
    )

    val quick: List<BenchPrompt> get() = prompts.filter { it.quick }
}

/** What one prompt did under one policy: the raw numbers the aggregation works from. */
data class PolicyRun(
    val promptId: String,
    val policy: RoutingPolicy,
    val answeredBy: InferenceTier?,
    val blocked: Boolean,
    val failed: Boolean,
    val accepted: Boolean,
    val escalations: Int,
    val latencyMs: Long,
    val cloudCostUsd: Double,
    val cloudCalls: Int,
    /** Personal data detected in the prompt. */
    val piiInPrompt: Int,
    /** Personal data that reached a cloud engine in clear. */
    val piiLeaked: Int,
    /** What a cloud-only answer to this prompt would have cost (same pricing assumption). */
    val cloudOnlyCostUsd: Double,
) {
    val answered: Boolean get() = answeredBy != null
    val answeredLocally: Boolean get() = answeredBy != null && !answeredBy.isCloud
}

fun RouterOutcome.toPolicyRun(promptId: String, policy: RoutingPolicy, settings: RouterSettings = RouterSettings()): PolicyRun =
    PolicyRun(
        promptId = promptId,
        policy = policy,
        answeredBy = answeredBy,
        blocked = blockedReason != null,
        failed = blockedReason == null && answeredBy == null,
        accepted = accepted,
        escalations = escalations,
        latencyMs = latencyMs,
        cloudCostUsd = cloudCostUsd,
        cloudCalls = exposures.size,
        piiInPrompt = piiInPrompt,
        piiLeaked = piiLeakedToCloud,
        cloudOnlyCostUsd = classification?.let { RoutePlanner.estimatedCloudCostUsd(it, settings) } ?: 0.0,
    )

/** One row of the comparison table. */
data class PolicySummary(
    val policy: RoutingPolicy,
    val prompts: Int,
    val answered: Int,
    val blocked: Int,
    val failed: Int,
    /** Of the answered prompts, the fraction answered on the device (0..1). */
    val localShare: Double,
    /** Mean latency over the answered prompts, including every escalation and check on the way. */
    val avgLatencyMs: Double,
    val totalEscalations: Int,
    val avgEscalations: Double,
    val cloudCalls: Int,
    val cloudCostUsd: Double,
    /** Cost of sending every prompt straight to the cloud, for scale. */
    val cloudOnlyCostUsd: Double,
    /**
     * Personal data items in the prompts of this policy's runs that never reached a cloud engine in clear, measured
     * against a naive "everything raw to the cloud" baseline: kept on the device or masked by the gateway.
     */
    val piiLeaksPrevented: Int,
    /** Personal data items that did reach the cloud in clear (non-zero only with the gateway off or failing). */
    val piiLeaked: Int,
    /** Answers that passed the quality gate (0..1 of the answered prompts). */
    val acceptedShare: Double,
) {
    /** Fraction of the cloud-only cost this policy spent (0 = free, 1 = same as all-cloud). */
    val costVsCloudOnly: Double get() = if (cloudOnlyCostUsd <= 0.0) 0.0 else cloudCostUsd / cloudOnlyCostUsd
}

object PolicyAggregator {

    fun summarize(runs: List<PolicyRun>): List<PolicySummary> =
        RoutingPolicy.entries.mapNotNull { policy ->
            val own = runs.filter { it.policy == policy }
            if (own.isEmpty()) null else summarizeOne(policy, own)
        }

    fun summarizeOne(policy: RoutingPolicy, runs: List<PolicyRun>): PolicySummary {
        val answered = runs.filter { it.answered }
        val local = answered.count { it.answeredLocally }
        return PolicySummary(
            policy = policy,
            prompts = runs.size,
            answered = answered.size,
            blocked = runs.count { it.blocked },
            failed = runs.count { it.failed },
            localShare = if (answered.isEmpty()) 0.0 else local.toDouble() / answered.size,
            avgLatencyMs = if (answered.isEmpty()) 0.0 else answered.sumOf { it.latencyMs }.toDouble() / answered.size,
            totalEscalations = runs.sumOf { it.escalations },
            avgEscalations = if (runs.isEmpty()) 0.0 else runs.sumOf { it.escalations }.toDouble() / runs.size,
            cloudCalls = runs.sumOf { it.cloudCalls },
            cloudCostUsd = runs.sumOf { it.cloudCostUsd },
            cloudOnlyCostUsd = runs.sumOf { it.cloudOnlyCostUsd },
            piiLeaksPrevented = runs.filter { it.piiLeaked == 0 }.sumOf { it.piiInPrompt },
            piiLeaked = runs.sumOf { it.piiLeaked },
            acceptedShare = if (answered.isEmpty()) 0.0 else answered.count { it.accepted }.toDouble() / answered.size,
        )
    }
}

/**
 * Remembers every successful answer per (engine, text, config) so the policies of a comparison share one sample of
 * each engine's behaviour: the differences between policies then come only from routing, not from sampling noise,
 * and the 5 policies do not repeat the same slow on-device generation. The recorded metric (and so the latency)
 * is reused with the answer.
 */
class MemoizingExecutor(private val inner: StepExecutor) : StepExecutor {
    private data class Key(val tier: InferenceTier, val text: String, val json: Boolean, val web: Boolean, val maxTokens: Int?)

    private val cache = HashMap<Key, StepResult>()
    var hits = 0
        private set
    var misses = 0
        private set

    override suspend fun run(
        tier: InferenceTier,
        messages: List<ChatMessage>,
        config: GenerationConfig,
        onPartial: (String) -> Unit,
    ): StepResult {
        val key = Key(tier, messages.joinToString("\u0000") { it.role.name + it.text }, config.jsonMode, config.webSearch, config.maxOutputTokens)
        cache[key]?.let {
            hits++
            onPartial(it.text)
            return it
        }
        misses++
        val result = inner.run(tier, messages, config, onPartial)
        if (result.ok) cache[key] = result
        return result
    }
}

/** Runs the benchmark prompts under each policy through the real [RouterPipeline]. */
class PolicyComparison(
    private val executor: StepExecutor,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun run(
        prompts: List<BenchPrompt>,
        policies: List<RoutingPolicy>,
        usable: List<InferenceTier>,
        settings: RouterSettings = RouterSettings(),
        onProgress: (done: Int, total: Int, label: String) -> Unit = { _, _, _ -> },
    ): List<PolicyRun> {
        val memo = MemoizingExecutor(executor)
        val latencyBook = LatencyBook()
        val total = prompts.size * policies.size
        val runs = mutableListOf<PolicyRun>()
        var done = 0
        for (prompt in prompts) {
            for (policy in policies) {
                onProgress(done, total, "${policy.label} · ${prompt.label}")
                val outcome = RouterPipeline(PipelineRunner(memo), clock).run(
                    RouterRequest(
                        prompt = prompt.text,
                        policy = policy,
                        usable = usable,
                        expectJson = prompt.expectJson,
                        settings = settings,
                        observedLatencyMs = latencyBook.snapshot(),
                    ),
                )
                outcome.attempts.filter { it.error == null }.forEach { latencyBook.record(it.tier, it.latencyMs) }
                runs += outcome.toPolicyRun(prompt.id, policy, settings)
                done++
            }
        }
        onProgress(total, total, "")
        return runs
    }
}

/** The policies a comparison covers: everything except MANUAL, which needs a hand-picked engine. */
val COMPARED_POLICIES: List<RoutingPolicy> = RoutingPolicy.entries.filter { it != RoutingPolicy.MANUAL }
