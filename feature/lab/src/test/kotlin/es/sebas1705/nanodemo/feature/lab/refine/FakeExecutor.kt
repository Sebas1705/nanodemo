package es.sebas1705.nanodemo.feature.lab.refine

import es.sebas1705.nanodemo.domain.models.ChatMessage
import es.sebas1705.nanodemo.domain.models.GenerationConfig
import es.sebas1705.nanodemo.domain.models.GenerationMetric
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.StepResult
import es.sebas1705.nanodemo.feature.lab.trace.PipelineRunner
import es.sebas1705.nanodemo.feature.lab.trace.StepExecutor

/** One request a pipeline sent to the (fake) engine. */
data class Call(val tier: InferenceTier, val prompt: String, val config: GenerationConfig)

/**
 * Scripted engine for pipeline tests: `reply` decides each answer from the request and the zero-based call
 * index. Every request is kept in [calls] so tests can assert on prompts, tiers and configs.
 */
class FakeExecutor(private val reply: (Call, Int) -> StepResult) : StepExecutor {
    val calls = mutableListOf<Call>()

    override suspend fun run(
        tier: InferenceTier,
        messages: List<ChatMessage>,
        config: GenerationConfig,
        onPartial: (String) -> Unit,
    ): StepResult {
        val call = Call(tier, messages.last().text, config)
        calls += call
        return reply(call, calls.size - 1)
    }

    fun runner() = PipelineRunner(this)
}

fun ok(text: String, ms: Long? = null, tokens: Int = 10, tier: InferenceTier = InferenceTier.ML_KIT_NANO) = StepResult(
    text = text,
    metric = ms?.let {
        GenerationMetric(
            tier = tier, startedAtMs = 0L, firstChunkLatencyMs = null, totalDurationMs = it, promptChars = 0,
            responseChars = text.length, success = true, responseTokens = tokens, responseTokensExact = true,
            memoryDeltaBytes = 0L,
        )
    },
)

fun failure(message: String) = StepResult("", null, message)

/** Replies with `texts` in order, repeating the last one when the pipeline asks for more. */
fun scripted(vararg texts: String) = FakeExecutor { _, index -> ok(texts[minOf(index, texts.lastIndex)]) }
