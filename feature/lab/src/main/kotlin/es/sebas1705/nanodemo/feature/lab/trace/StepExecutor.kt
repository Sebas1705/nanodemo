package es.sebas1705.nanodemo.feature.lab.trace

import es.sebas1705.nanodemo.domain.models.ChatMessage
import es.sebas1705.nanodemo.domain.models.GenerationConfig
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.StepResult
import es.sebas1705.nanodemo.domain.usescases.inference.RunStepUseCase

/**
 * The one operation a pipeline needs from the inference layer. Production code adapts `RunStepUseCase`
 * ([asExecutor]); unit tests pass a fake that returns scripted answers, so pipeline logic (routing,
 * retries, voting...) is testable without a model.
 */
fun interface StepExecutor {
    suspend fun run(
        tier: InferenceTier,
        messages: List<ChatMessage>,
        config: GenerationConfig,
        onPartial: (String) -> Unit,
    ): StepResult
}

/**
 * `texts` is what the retry wrapper shows while it waits out a rate limit; pass the Android `LabTexts` from a
 * ViewModel so the message follows the app language (the default is English, for tests and plain callers).
 */
fun RunStepUseCase.asExecutor(texts: RetryTexts = RetryTexts.English): StepExecutor {
    val direct = StepExecutor { tier, messages, config, onPartial -> this(tier, messages, config, onPartial) }
    // Free cloud tiers answer 429 above a per-minute rate; waiting it out beats failing every step after the fifth.
    return RateLimitRetryExecutor(direct, texts = texts)
}
