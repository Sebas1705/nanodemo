package es.sebas1705.nanodemo.domain.models

/** What a pipeline step does; drives the icon and colour of the step in the trace view. */
enum class StepKind { ROUTE, REDACT, RETRIEVE, GENERATE, TOOL, JUDGE, PLAN, CRITIQUE, SUMMARIZE, VALIDATE }

enum class StepStatus { RUNNING, DONE, FAILED, SKIPPED }

/**
 * One visible step of a pipeline run. `tier` is the engine that executed it (null for steps that run no
 * model, such as retrieval or a regex check). `input`/`output` are short previews, not full payloads.
 */
data class TraceStep(
    val id: Int,
    val kind: StepKind,
    val title: String,
    val tier: InferenceTier? = null,
    val status: StepStatus = StepStatus.RUNNING,
    val startedAtMs: Long = 0L,
    val durationMs: Long? = null,
    val tokens: Int? = null,
    val input: String? = null,
    val output: String? = null,
    val detail: String? = null,
)

/** The ordered steps of one pipeline run. */
data class PipelineTrace(val steps: List<TraceStep> = emptyList()) {
    val totalDurationMs: Long get() = steps.sumOf { it.durationMs ?: 0L }
    val totalTokens: Int get() = steps.sumOf { it.tokens ?: 0 }
    val tiersUsed: Set<InferenceTier> get() = steps.mapNotNull { it.tier }.toSet()
    val failed: Boolean get() = steps.any { it.status == StepStatus.FAILED }
    val running: Boolean get() = steps.any { it.status == StepStatus.RUNNING }
}

/** Outcome of an isolated generation (`IInferenceRepository.runStep`). */
data class StepResult(
    val text: String,
    val metric: GenerationMetric?,
    val error: String? = null,
) {
    val ok: Boolean get() = error == null
}
