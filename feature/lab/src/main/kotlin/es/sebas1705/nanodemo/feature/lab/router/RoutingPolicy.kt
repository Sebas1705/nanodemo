package es.sebas1705.nanodemo.feature.lab.router

import androidx.annotation.StringRes
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.feature.lab.R

enum class RoutingPolicy(@StringRes val labelRes: Int, @StringRes val descriptionRes: Int) {
    LOCAL_FIRST(R.string.router_policy_local_first_label, R.string.router_policy_local_first_description),
    CLOUD_FIRST(R.string.router_policy_cloud_first_label, R.string.router_policy_cloud_first_description),
    PRIVACY_FIRST(R.string.router_policy_privacy_first_label, R.string.router_policy_privacy_first_description),
    COST_AWARE(R.string.router_policy_cost_aware_label, R.string.router_policy_cost_aware_description),
    LATENCY_AWARE(R.string.router_policy_latency_aware_label, R.string.router_policy_latency_aware_description),
    MANUAL(R.string.router_policy_manual_label, R.string.router_policy_manual_description),
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
    fun describe(texts: RouterTexts): String = if (blocked) texts.string(R.string.router_plan_describe_blocked, blockedReason.orEmpty()) else engines.joinToString(" → ") { it.tier.name }
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
        texts: RouterTexts,
        settings: RouterSettings = RouterSettings(),
        manualTier: InferenceTier? = null,
        observedLatencyMs: Map<InferenceTier, Long> = emptyMap(),
    ): RoutePlan {
        val notes = mutableListOf<String>()
        fun engine(tier: InferenceTier, reason: String) =
            PlannedEngine(tier, viaGateway = tier.isCloud && settings.gatewayEnabled, reason = reason)

        if (usable.isEmpty()) return RoutePlan(policy, emptyList(), notes, texts.string(R.string.router_plan_no_engine))

        if (policy == RoutingPolicy.MANUAL) {
            val tier = manualTier
            return when {
                tier == null -> RoutePlan(policy, emptyList(), notes, texts.string(R.string.router_plan_no_manual_choice))
                tier !in usable -> RoutePlan(policy, emptyList(), notes, texts.string(R.string.router_plan_manual_unavailable))
                else -> RoutePlan(policy, listOf(engine(tier, texts.string(R.string.router_plan_picked_by_hand))), notes)
            }
        }

        var locals = usable.filter { !it.isCloud }
        var clouds = usable.filter { it.isCloud }
        val privacyLocked = policy == RoutingPolicy.PRIVACY_FIRST && classification.sensitive

        // Fail closed: a sensitive prompt under the privacy policy never gets a cloud engine, whatever else happens.
        if (privacyLocked) {
            clouds = emptyList()
            notes += texts.string(R.string.router_plan_sensitive_stays)
        }

        // Small local contexts cannot take a long prompt: drop them instead of letting them truncate it.
        val outTokens = expectedOutputTokens(classification.complexity, settings).coerceAtMost(settings.localMaxOutputTokens)
        val fitting = locals.filter { tier ->
            val ctx = EngineProfiles.of(tier).contextTokens
            ctx == null || classification.features.estimatedTokens + outTokens + CONTEXT_OVERHEAD_TOKENS <= ctx
        }
        (locals - fitting.toSet()).forEach { tier ->
            notes += texts.string(R.string.router_plan_discarded, tier.name, classification.features.estimatedTokens)
        }
        locals = fitting

        // Cost-aware: the cloud is a paid last resort.
        if (policy == RoutingPolicy.COST_AWARE && clouds.isNotEmpty() && locals.isNotEmpty()) {
            val cost = estimatedCloudCostUsd(classification, settings)
            when {
                classification.complexity == Complexity.TRIVIAL -> {
                    clouds = emptyList()
                    notes += texts.string(R.string.router_plan_simple_no_cloud)
                }
                cost > settings.cloudBudgetUsd -> {
                    clouds = emptyList()
                    notes += texts.string(R.string.router_plan_over_budget, formatUsd(cost), formatUsd(settings.cloudBudgetUsd))
                }
            }
        }

        val byQualityDesc = compareByDescending<InferenceTier> { EngineProfiles.of(it).quality }.thenBy { it.ordinal }
        val rankedLocals = locals.sortedWith(byQualityDesc).take(settings.maxLocalAttempts.coerceAtLeast(1))
        val rankedClouds = clouds.sortedBy { it.ordinal }

        var ordered: List<PlannedEngine> = when (policy) {
            RoutingPolicy.CLOUD_FIRST ->
                rankedClouds.take(2).map { engine(it, texts.string(R.string.router_plan_cloud_first)) } + rankedLocals.map { engine(it, texts.string(R.string.router_plan_local_backup)) }
            RoutingPolicy.LATENCY_AWARE -> {
                val candidates = rankedLocals + rankedClouds.take(1)
                candidates.sortedBy { observedLatencyMs[it] ?: EngineProfiles.of(it).expectedLatencyMs(outTokens) }
                    .map { engine(it, if (it in observedLatencyMs) texts.string(R.string.router_plan_observed_latency, observedLatencyMs.getValue(it)) else texts.string(R.string.router_plan_expected_latency)) }
            }
            else -> // LOCAL_FIRST, PRIVACY_FIRST, COST_AWARE
                rankedLocals.map { engine(it, texts.string(R.string.router_plan_local_best)) } + rankedClouds.take(1).map { engine(it, texts.string(R.string.router_plan_escalated_cloud)) }
        }

        // Capability gap: a local model cannot know recent facts, so the cloud goes first when it is allowed.
        if (classification.needsFreshFacts && rankedClouds.isNotEmpty() && policy != RoutingPolicy.CLOUD_FIRST) {
            val (cloudEntries, localEntries) = ordered.partition { it.tier.isCloud }
            if (cloudEntries.isNotEmpty() && ordered.first().tier.isCloud.not()) {
                ordered = cloudEntries + localEntries
                notes += texts.string(R.string.router_plan_fresh_cloud_first)
            }
        }

        if (usable.none { it.isCloud }) notes += texts.string(R.string.router_plan_no_cloud_usable)
        if (usable.none { !it.isCloud }) notes += texts.string(R.string.router_plan_no_local_usable)

        if (ordered.isEmpty()) {
            val reason = if (privacyLocked) {
                texts.string(R.string.router_plan_blocked_sensitive)
            } else {
                texts.string(R.string.router_plan_blocked_no_engine)
            }
            return RoutePlan(policy, emptyList(), notes, reason)
        }
        return RoutePlan(policy, ordered, notes)
    }

    private const val CONTEXT_OVERHEAD_TOKENS = 80

    fun formatUsd(value: Double): String = String.format(java.util.Locale.US, "$%.4f", value)
}
