package es.sebas1705.nanodemo.feature.lab.trace

import es.sebas1705.nanodemo.domain.models.ChatMessage
import es.sebas1705.nanodemo.domain.models.GenerationConfig
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.StepResult
import kotlinx.coroutines.delay

/** Longest pause the retry accepts; free tiers reset per-minute limits within about a minute. */
const val MAX_RATE_LIMIT_WAIT_MS = 65_000L

private const val MARGIN_MS = 1_000L

private val RETRY_IN = Regex("retry in (\\d+(?:\\.\\d+)?)\\s*s", RegexOption.IGNORE_CASE)
private val RETRY_DELAY = Regex("retryDelay\"?\\s*:\\s*\"?(\\d+(?:\\.\\d+)?)s", RegexOption.IGNORE_CASE)

/**
 * True when `error` is a temporary request-rate limit worth waiting out: HTTP 429 or `RESOURCE_EXHAUSTED` from a
 * per-minute quota. A daily or monthly quota (or the app's own free-limit block) will not recover in a minute, so
 * those are not retried.
 */
fun isRetryableRateLimit(error: String?): Boolean {
    if (error == null) return false
    val text = error.lowercase()
    val limited = "429" in text || "resource_exhausted" in text || "rate limit" in text || "too many requests" in text
    val longLived = "perday" in text || "per day" in text || "daily" in text || "permonth" in text || "límite gratis" in text || "free limit" in text
    return limited && !longLived
}

/** The wait the server asked for ("Please retry in 47.1s" or `retryDelay: "47s"`), plus a margin; null if it gave none. */
fun parseRetryDelayMs(error: String?): Long? {
    if (error == null) return null
    val seconds = (RETRY_IN.find(error) ?: RETRY_DELAY.find(error))?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
    return (seconds * 1000).toLong() + MARGIN_MS
}

/**
 * Wraps an executor so a per-minute rate limit does not turn into a failed step: it waits the time the server
 * asked for (capped at [maxWaitMs]) and tries again, up to [maxRetries] times, telling the UI while it waits.
 * Anything else, including an exhausted daily quota, is returned as is.
 */
class RateLimitRetryExecutor(
    private val inner: StepExecutor,
    private val maxRetries: Int = 2,
    private val maxWaitMs: Long = MAX_RATE_LIMIT_WAIT_MS,
    private val fallbackWaitMs: Long = 20_000L,
    private val wait: suspend (Long) -> Unit = { delay(it) },
) : StepExecutor {

    override suspend fun run(
        tier: InferenceTier,
        messages: List<ChatMessage>,
        config: GenerationConfig,
        onPartial: (String) -> Unit,
    ): StepResult {
        var result = inner.run(tier, messages, config, onPartial)
        var attempt = 0
        while (!result.ok && attempt < maxRetries && isRetryableRateLimit(result.error)) {
            attempt++
            val waitMs = (parseRetryDelayMs(result.error) ?: fallbackWaitMs).coerceAtMost(maxWaitMs)
            onPartial("⏳ Límite de peticiones del servicio; reintento en ${(waitMs + 999) / 1000} s ($attempt/$maxRetries)…")
            wait(waitMs)
            result = inner.run(tier, messages, config, onPartial)
        }
        return result
    }
}
