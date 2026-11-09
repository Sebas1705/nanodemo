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

fun RunStepUseCase.asExecutor(): StepExecutor =
    StepExecutor { tier, messages, config, onPartial -> this(tier, messages, config, onPartial) }
