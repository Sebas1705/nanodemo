package es.sebas1705.nanodemo.feature.lab.router

import es.sebas1705.nanodemo.domain.models.InferenceTier

enum class RoutingPolicy(val label: String, val description: String) {
    LOCAL_FIRST("Local primero", "Intenta los motores del dispositivo y solo sube a la nube si no bastan."),
    CLOUD_FIRST("Nube primero", "Empieza por la nube; los motores locales quedan de respaldo."),
    PRIVACY_FIRST("Privacidad primero", "Lo que tenga datos personales o esté marcado como sensible nunca sale del dispositivo."),
    COST_AWARE("Coste", "Local primero y la nube solo si la consulta cabe en el presupuesto y no es trivial."),
    LATENCY_AWARE("Latencia", "Ordena los motores por la latencia esperada (o la observada en esta sesión)."),
    MANUAL("Manual", "Un único motor elegido a mano, sin escalado."),
}

/** Knobs of the router that are not the policy itself. */
data class RouterSettings(
    val gatewayEnabled: Boolean = true,
    /** Most on-device engines tried before the cloud. */
    val maxLocalAttempts: Int = 2,
    /** How many times the cascade may move to the next engine after a rejected answer. */
    val maxEscalations: Int = 2,
    /** Cost-aware policy: the cloud is skipped when the estimated cost of this prompt exceeds this. */
    val cloudBudgetUsd: Double = 0.002,
    val localMaxOutputTokens: Int = 256,
    val cloudMaxOutputTokens: Int = 512,
)

/** One engine in the ordered list a policy produced. `viaGateway` means the prompt is masked before it is sent. */
data class PlannedEngine(val tier: InferenceTier, val viaGateway: Boolean, val reason: String)

/**
 * The route for one prompt: the engines to try in order. `blockedReason` is set (and `engines` empty) when
 * nothing may run, for instance a sensitive prompt under the privacy policy with no usable local engine.
 */
data class RoutePlan(
    val policy: RoutingPolicy,
    val engines: List<PlannedEngine>,
    val notes: List<String>,
    val blockedReason: String? = null,
) {
    val blocked: Boolean get() = blockedReason != null
    fun describe(): String = if (blocked) "bloqueada: $blockedReason" else engines.joinToString(" → ") { it.tier.name }
}

/**
 * Maps (policy, classification, usable engines) to an ordered engine list. Pure. It copes with any mix of
 * engines: no local one, no cloud one, or neither.
 */
object RoutePlanner {

    fun expectedOutputTokens(complexity: Complexity, settings: RouterSettings): Int = when (complexity) {
        Complexity.TRIVIAL -> 40
        Complexity.MODERATE -> 200
        Complexity.COMPLEX -> 400
    }.coerceAtMost(settings.cloudMaxOutputTokens)

    /** Estimated cost of answering this prompt in the cloud, with the shared pricing assumption. */
    fun estimatedCloudCostUsd(classification: Classification, settings: RouterSettings): Double =
        CloudPricing.usd(classification.features.estimatedTokens, expectedOutputTokens(classification.complexity, settings))

    fun plan(
        policy: RoutingPolicy,
        classification: Classification,
        usable: List<InferenceTier>,
        settings: RouterSettings = RouterSettings(),
        manualTier: InferenceTier? = null,
        observedLatencyMs: Map<InferenceTier, Long> = emptyMap(),
    ): RoutePlan {
        val notes = mutableListOf<String>()
        fun engine(tier: InferenceTier, reason: String) =
            PlannedEngine(tier, viaGateway = tier.isCloud && settings.gatewayEnabled, reason = reason)

        if (usable.isEmpty()) return RoutePlan(policy, emptyList(), notes, "no hay ningún motor disponible")

        if (policy == RoutingPolicy.MANUAL) {
            val tier = manualTier
            return when {
                tier == null -> RoutePlan(policy, emptyList(), notes, "no has elegido motor")
                tier !in usable -> RoutePlan(policy, emptyList(), notes, "el motor elegido no está disponible ahora")
                else -> RoutePlan(policy, listOf(engine(tier, "elegido a mano")), notes)
            }
        }

        var locals = usable.filter { !it.isCloud }
        var clouds = usable.filter { it.isCloud }
        val privacyLocked = policy == RoutingPolicy.PRIVACY_FIRST && classification.sensitive

        // Fail closed: a sensitive prompt under the privacy policy never gets a cloud engine, whatever else happens.
        if (privacyLocked) {
            clouds = emptyList()
            notes += "sensible: no sale del dispositivo"
        }

        // Small local contexts cannot take a long prompt: drop them instead of letting them truncate it.
        val outTokens = expectedOutputTokens(classification.complexity, settings).coerceAtMost(settings.localMaxOutputTokens)
        val fitting = locals.filter { tier ->
            val ctx = EngineProfiles.of(tier).contextTokens
            ctx == null || classification.features.estimatedTokens + outTokens + CONTEXT_OVERHEAD_TOKENS <= ctx
        }
        (locals - fitting.toSet()).forEach { tier ->
            notes += "${tier.name} descartado: el prompt (~${classification.features.estimatedTokens} tok) no cabe en su contexto"
        }
        locals = fitting

        // Cost-aware: the cloud is a paid last resort.
        if (policy == RoutingPolicy.COST_AWARE && clouds.isNotEmpty() && locals.isNotEmpty()) {
            val cost = estimatedCloudCostUsd(classification, settings)
            when {
                classification.complexity == Complexity.TRIVIAL -> {
                    clouds = emptyList()
                    notes += "consulta simple: no se paga la nube"
                }
                cost > settings.cloudBudgetUsd -> {
                    clouds = emptyList()
                    notes += "coste estimado ${formatUsd(cost)} por encima del presupuesto ${formatUsd(settings.cloudBudgetUsd)}: sin nube"
                }
            }
        }

        val byQualityDesc = compareByDescending<InferenceTier> { EngineProfiles.of(it).quality }.thenBy { it.ordinal }
        val rankedLocals = locals.sortedWith(byQualityDesc).take(settings.maxLocalAttempts.coerceAtLeast(1))
        val rankedClouds = clouds.sortedBy { it.ordinal }

        var ordered: List<PlannedEngine> = when (policy) {
            RoutingPolicy.CLOUD_FIRST ->
                rankedClouds.take(2).map { engine(it, "nube primero") } + rankedLocals.map { engine(it, "respaldo local") }
            RoutingPolicy.LATENCY_AWARE -> {
                val candidates = rankedLocals + rankedClouds.take(1)
                candidates.sortedBy { observedLatencyMs[it] ?: EngineProfiles.of(it).expectedLatencyMs(outTokens) }
                    .map { engine(it, if (it in observedLatencyMs) "latencia observada ${observedLatencyMs.getValue(it)} ms" else "latencia esperada") }
            }
            else -> // LOCAL_FIRST, PRIVACY_FIRST, COST_AWARE
                rankedLocals.map { engine(it, "local, mejor calidad primero") } + rankedClouds.take(1).map { engine(it, "escalado a la nube") }
        }

        // Capability gap: a local model cannot know recent facts, so the cloud goes first when it is allowed.
        if (classification.needsFreshFacts && rankedClouds.isNotEmpty() && policy != RoutingPolicy.CLOUD_FIRST) {
            val (cloudEntries, localEntries) = ordered.partition { it.tier.isCloud }
            if (cloudEntries.isNotEmpty() && ordered.first().tier.isCloud.not()) {
                ordered = cloudEntries + localEntries
                notes += "necesita datos recientes: la nube va primero"
            }
        }

        if (usable.none { it.isCloud }) notes += "no hay motor en la nube utilizable"
        if (usable.none { !it.isCloud }) notes += "no hay motor local utilizable"

        if (ordered.isEmpty()) {
            val reason = if (privacyLocked) {
                "es sensible y ningún motor local puede atenderlo, así que no se envía a la nube"
            } else {
                "ningún motor puede atender este prompt"
            }
            return RoutePlan(policy, emptyList(), notes, reason)
        }
        return RoutePlan(policy, ordered, notes)
    }

    private const val CONTEXT_OVERHEAD_TOKENS = 80

    fun formatUsd(value: Double): String = String.format(java.util.Locale.US, "$%.4f", value)
}
