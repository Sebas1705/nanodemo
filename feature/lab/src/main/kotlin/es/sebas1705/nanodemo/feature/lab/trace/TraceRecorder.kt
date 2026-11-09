package es.sebas1705.nanodemo.feature.lab.trace

import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.PipelineTrace
import es.sebas1705.nanodemo.domain.models.StepKind
import es.sebas1705.nanodemo.domain.models.StepStatus
import es.sebas1705.nanodemo.domain.models.TraceStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicInteger

/** Longest input/output preview kept in a trace step; the full payload stays with the experiment. */
const val TRACE_PREVIEW_CHARS = 400

fun String.tracePreview(max: Int = TRACE_PREVIEW_CHARS): String {
    val flat = trim()
    return if (flat.length <= max) flat else flat.take(max).trimEnd() + "…"
}

/**
 * Builds a [PipelineTrace] live while a pipeline runs. Thread-safe: steps may be begun and finished from
 * different coroutines (e.g. parallel branches). `clock` is injectable so tests control time.
 */
class TraceRecorder(private val clock: () -> Long = System::currentTimeMillis) {
    private val nextId = AtomicInteger(1)
    private val _trace = MutableStateFlow(PipelineTrace())
    val trace: StateFlow<PipelineTrace> = _trace.asStateFlow()

    fun reset() {
        nextId.set(1)
        _trace.value = PipelineTrace()
    }

    fun begin(kind: StepKind, title: String, tier: InferenceTier? = null, input: String? = null): Int {
        val id = nextId.getAndIncrement()
        val step = TraceStep(
            id = id,
            kind = kind,
            title = title,
            tier = tier,
            status = StepStatus.RUNNING,
            startedAtMs = clock(),
            input = input?.tracePreview(),
        )
        _trace.update { it.copy(steps = it.steps + step) }
        return id
    }

    /** Live preview of what a running step has produced so far (streaming text). */
    fun preview(id: Int, partialOutput: String) = modify(id) { it.copy(output = partialOutput.tracePreview()) }

    fun finish(id: Int, output: String? = null, tokens: Int? = null, detail: String? = null) = modify(id) {
        it.copy(
            status = StepStatus.DONE,
            durationMs = clock() - it.startedAtMs,
            output = output?.tracePreview() ?: it.output,
            tokens = tokens ?: it.tokens,
            detail = detail ?: it.detail,
        )
    }

    fun fail(id: Int, message: String) = modify(id) {
        it.copy(status = StepStatus.FAILED, durationMs = clock() - it.startedAtMs, detail = message)
    }

    /** Records a step that was deliberately not run (e.g. "cloud skipped: the local answer was good enough"). */
    fun skip(kind: StepKind, title: String, reason: String, tier: InferenceTier? = null): Int {
        val id = nextId.getAndIncrement()
        val step = TraceStep(
            id = id, kind = kind, title = title, tier = tier,
            status = StepStatus.SKIPPED, startedAtMs = clock(), durationMs = 0L, detail = reason,
        )
        _trace.update { it.copy(steps = it.steps + step) }
        return id
    }

    private fun modify(id: Int, change: (TraceStep) -> TraceStep) {
        _trace.update { current ->
            current.copy(steps = current.steps.map { if (it.id == id) change(it) else it })
        }
    }
}
