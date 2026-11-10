package es.sebas1705.nanodemo.feature.lab.router

import es.sebas1705.nanodemo.domain.models.InferenceTier

/** Cloud engines send the prompt off the device and bill per token; the rest run on the phone for free. */
val InferenceTier.isCloud: Boolean
    get() = this == InferenceTier.CLOUD_GEMINI || this == InferenceTier.FIREBASE_AI

/**
 * Static assumptions about an engine, used only to ORDER candidates before anything has been measured
 * (latency-aware routing, quality ranking among local engines, context-fit checks). They are priors for a
 * mid-range phone, not measurements: [LatencyBook] replaces the latency prior with observed numbers as soon as
 * the engine has run once.
 */
data class EngineProfile(
    val tier: InferenceTier,
    /** 1 (weakest) .. 5 (strongest): relative answer quality, used to rank engines of the same kind. */
    val quality: Int,
    /** Time to the first token plus fixed overhead. */
    val baseLatencyMs: Long,
    /** Generation cost per output token. */
    val msPerToken: Long,
    /** Total context budget (prompt + answer) when it is a small known cap, else null. */
    val contextTokens: Int?,
) {
    val isCloud: Boolean get() = tier.isCloud

    fun expectedLatencyMs(outputTokens: Int): Long = baseLatencyMs + msPerToken * outputTokens
}

object EngineProfiles {
    private val all = listOf(
        EngineProfile(InferenceTier.ML_KIT_NANO, quality = 3, baseLatencyMs = 700, msPerToken = 20, contextTokens = null),
        EngineProfile(InferenceTier.LITERT_LM_LOCAL, quality = 2, baseLatencyMs = 1_200, msPerToken = 60, contextTokens = 2_048),
        EngineProfile(InferenceTier.MEDIAPIPE_LOCAL, quality = 1, baseLatencyMs = 1_500, msPerToken = 90, contextTokens = 1_024),
        EngineProfile(InferenceTier.CLOUD_GEMINI, quality = 5, baseLatencyMs = 1_200, msPerToken = 10, contextTokens = null),
        EngineProfile(InferenceTier.FIREBASE_AI, quality = 5, baseLatencyMs = 1_300, msPerToken = 10, contextTokens = null),
    ).associateBy { it.tier }

    fun of(tier: InferenceTier): EngineProfile = all.getValue(tier)
}

/**
 * Cloud price assumption, the same one the Stats screen uses (`StatsScreen.kt`: Gemini Flash paid tier, USD per
 * million tokens, checked 22/09/2026, and prompt tokens approximated as characters / 4). Kept as its own copy
 * because the Stats constants are private to that module; keep the two in sync.
 */
object CloudPricing {
    const val INPUT_USD_PER_MILLION_TOKENS = 0.75
    const val OUTPUT_USD_PER_MILLION_TOKENS = 3.75
    const val CHARS_PER_TOKEN = 4.0
    private const val TOKENS_PER_MILLION = 1_000_000.0

    fun tokensFromChars(chars: Int): Int = if (chars <= 0) 0 else Math.ceil(chars / CHARS_PER_TOKEN).toInt()

    fun usd(inputTokens: Int, outputTokens: Int): Double =
        (inputTokens * INPUT_USD_PER_MILLION_TOKENS + outputTokens * OUTPUT_USD_PER_MILLION_TOKENS) / TOKENS_PER_MILLION

    fun usdForChars(promptChars: Int, answerChars: Int): Double =
        usd(tokensFromChars(promptChars), tokensFromChars(answerChars))
}

/**
 * Exponential moving average of the latency each engine actually showed in this session. Lets latency-aware
 * routing prefer what is fast on THIS phone right now instead of the static priors.
 */
class LatencyBook(private val alpha: Double = 0.5) {
    private val observed = HashMap<InferenceTier, Double>()

    fun record(tier: InferenceTier, latencyMs: Long) {
        if (latencyMs <= 0) return
        observed[tier] = observed[tier]?.let { it + alpha * (latencyMs - it) } ?: latencyMs.toDouble()
    }

    fun snapshot(): Map<InferenceTier, Long> = observed.mapValues { it.value.toLong() }
}
