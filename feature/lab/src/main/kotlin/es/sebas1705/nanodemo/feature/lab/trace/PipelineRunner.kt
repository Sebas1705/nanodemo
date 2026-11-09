package es.sebas1705.nanodemo.feature.lab.trace

import es.sebas1705.nanodemo.core.designsystem.displayParts
import es.sebas1705.nanodemo.domain.models.ChatMessage
import es.sebas1705.nanodemo.domain.models.GenerationConfig
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.StepKind
import es.sebas1705.nanodemo.domain.models.StepResult

/**
 * Outcome of a non-model step: the `value` the pipeline continues with plus what to show in the trace.
 * `tokens` is only for steps that spend tokens without being a generation (rare).
 */
class LocalOutcome<T>(val value: T, val output: String? = null, val detail: String? = null, val tokens: Int? = null)

/**
 * Runs the steps of a pipeline and records each one in a [TraceRecorder]. Pipelines call [generate] for model
 * steps and [local] for everything else (retrieval, regex checks, tool calls, redaction...), so every
 * experiment gets the same visible trace without repeating the bookkeeping.
 */
class PipelineRunner(
    private val executor: StepExecutor,
    val recorder: TraceRecorder = TraceRecorder(),
) {
    suspend fun generate(
        kind: StepKind,
        title: String,
        tier: InferenceTier,
        messages: List<ChatMessage>,
        config: GenerationConfig = GenerationConfig(),
    ): StepResult {
        val id = recorder.begin(kind, title, tier, messages.lastOrNull()?.text)
        val result = executor.run(tier, messages, config) { partial -> recorder.preview(id, partial) }
        if (result.ok) {
            recorder.finish(
                id = id,
                output = result.text,
                tokens = result.metric?.responseTokens,
                detail = result.metric?.displayParts()?.joinToString(" · "),
            )
        } else {
            recorder.fail(id, result.error ?: "Error")
        }
        return result
    }

    /** A step that runs no model. If `block` throws, the step is marked failed and the exception continues up. */
    suspend fun <T> local(
        kind: StepKind,
        title: String,
        input: String? = null,
        tier: InferenceTier? = null,
        block: suspend () -> LocalOutcome<T>,
    ): T {
        val id = recorder.begin(kind, title, tier, input)
        return try {
            val outcome = block()
            recorder.finish(id, outcome.output, outcome.tokens, outcome.detail)
            outcome.value
        } catch (e: Exception) {
            recorder.fail(id, e.message ?: e.javaClass.simpleName)
            throw e
        }
    }
}
