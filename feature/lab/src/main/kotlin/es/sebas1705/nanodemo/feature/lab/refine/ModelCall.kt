package es.sebas1705.nanodemo.feature.lab.refine

import es.sebas1705.nanodemo.domain.models.ChatMessage
import es.sebas1705.nanodemo.domain.models.GenerationConfig
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.Role
import es.sebas1705.nanodemo.domain.models.StepKind
import es.sebas1705.nanodemo.domain.models.StepStatus
import es.sebas1705.nanodemo.feature.lab.trace.PipelineRunner
import es.sebas1705.nanodemo.feature.lab.trace.TraceRecorder

/**
 * Temperature used where a "greedy" answer is wanted. Exactly 0 is not safe on every runtime (some samplers
 * divide by the temperature), and 0.1 is practically deterministic.
 */
const val GREEDY_TEMPERATURE = 0.1f

const val EMPTY_REPLY_MESSAGE = "The engine returned an empty reply"

/** What one model call cost and returned, in the terms the experiments report. */
data class ModelReply(val text: String, val error: String?, val latencyMs: Long, val tokens: Int) {
    val ok: Boolean get() = error == null
}

/** Calls, time and tokens accumulated by one role, engine or strategy. */
data class CallStats(val calls: Int = 0, val latencyMs: Long = 0L, val tokens: Int = 0) {
    operator fun plus(reply: ModelReply) = CallStats(calls + 1, latencyMs + reply.latencyMs, tokens + reply.tokens)
    operator fun plus(other: CallStats) = CallStats(calls + other.calls, latencyMs + other.latencyMs, tokens + other.tokens)
    val avgLatencyMs: Long get() = if (calls == 0) 0L else latencyMs / calls
}

/** Progress of a long run: `total` is the expected number of model calls (an upper bound when a run can stop early). */
data class LabProgress(val done: Int, val total: Int, val label: String) {
    val fraction: Float get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
}

/**
 * One recorded model call with a single user prompt. Latency and tokens come from the engine's metric when it
 * has one (so they match what Chat and Compare report) and fall back to wall time and an estimate otherwise.
 * An engine that answers with blank text counts as a failure: no pipeline can do anything with it.
 */
suspend fun PipelineRunner.ask(
    kind: StepKind,
    title: String,
    tier: InferenceTier,
    prompt: String,
    config: GenerationConfig = GenerationConfig(),
    clock: () -> Long = System::currentTimeMillis,
    emptyReply: String = EMPTY_REPLY_MESSAGE,
): ModelReply {
    val start = clock()
    val result = generate(kind, title, tier, listOf(ChatMessage(Role.USER, prompt)), config)
    val wall = clock() - start
    val text = result.text.trim()
    val error = result.error ?: if (text.isEmpty()) emptyReply else null
    return ModelReply(
        text = text,
        error = error,
        latencyMs = result.metric?.totalDurationMs ?: wall,
        tokens = result.metric?.responseTokens ?: approxTokens(text),
    )
}

/** Marks every step still running as failed; used when the user cancels, so the trace does not show it spinning forever. */
fun TraceRecorder.failRunning(message: String) {
    trace.value.steps.filter { it.status == StepStatus.RUNNING }.forEach { fail(it.id, message) }
}

private val LOCAL_TIERS = setOf(InferenceTier.ML_KIT_NANO, InferenceTier.MEDIAPIPE_LOCAL, InferenceTier.LITERT_LM_LOCAL)

/** Engines that run on the phone. */
val InferenceTier.isLocal: Boolean get() = this in LOCAL_TIERS

/** First usable engine among `preferred`, falling back to the first usable one, then to `fallback`. */
fun pickTier(usable: List<InferenceTier>, preferred: (InferenceTier) -> Boolean, fallback: InferenceTier): InferenceTier =
    usable.firstOrNull(preferred) ?: usable.firstOrNull() ?: fallback
