package es.sebas1705.nanodemo.feature.lab.refine

import es.sebas1705.nanodemo.domain.models.GenerationConfig
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.StepKind
import es.sebas1705.nanodemo.feature.lab.trace.PipelineRunner
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * One task answered with one strategy on one engine. `tokens` and `latencyMs` add up all the calls the strategy
 * needed (two for step-back). A run that failed (engine error, blank reply) is `failed` and counts neither as
 * right nor as wrong.
 */
data class TaskOutcome(
    val tier: InferenceTier,
    val strategy: PromptStrategy,
    val taskId: String,
    val repetition: Int,
    val correct: Boolean,
    val failed: Boolean,
    val tokens: Int,
    val latencyMs: Long,
    val answer: String,
    val error: String? = null,
)

/** Aggregate of a group of outcomes (one table cell). */
data class CellStats(
    val runs: Int,
    val completed: Int,
    val correct: Int,
    val avgTokens: Double,
    val avgLatencyMs: Double,
    /** Accuracy of each repetition, when there were several: lets the table show spread, not a single number. */
    val repetitionAccuracies: List<Double>,
) {
    val errors: Int get() = runs - completed

    /** Right answers over completed runs; `null` when nothing completed. */
    val accuracy: Double? get() = if (completed == 0) null else correct.toDouble() / completed

    /** Lowest and highest per-repetition accuracy; `null` with a single repetition. */
    val spread: ClosedFloatingPointRange<Double>?
        get() = if (repetitionAccuracies.size < 2) null else repetitionAccuracies.min()..repetitionAccuracies.max()
}

fun cellStats(outcomes: List<TaskOutcome>): CellStats {
    val done = outcomes.filterNot { it.failed }
    val perRepetition = done.groupBy { it.repetition }.toSortedMap().values.map { reps -> reps.count { it.correct }.toDouble() / reps.size }
    return CellStats(
        runs = outcomes.size,
        completed = done.size,
        correct = done.count { it.correct },
        avgTokens = if (done.isEmpty()) 0.0 else done.sumOf { it.tokens }.toDouble() / done.size,
        avgLatencyMs = if (done.isEmpty()) 0.0 else done.sumOf { it.latencyMs }.toDouble() / done.size,
        repetitionAccuracies = perRepetition,
    )
}

fun statsByStrategyAndTier(outcomes: List<TaskOutcome>): Map<Pair<PromptStrategy, InferenceTier>, CellStats> =
    outcomes.groupBy { it.strategy to it.tier }.mapValues { cellStats(it.value) }

fun statsByStrategy(outcomes: List<TaskOutcome>): Map<PromptStrategy, CellStats> =
    outcomes.groupBy { it.strategy }.mapValues { cellStats(it.value) }

fun statsByTier(outcomes: List<TaskOutcome>): Map<InferenceTier, CellStats> =
    outcomes.groupBy { it.tier }.mapValues { cellStats(it.value) }

/** The strategies with the highest accuracy (all of them when tied); empty when nothing completed. */
fun bestStrategies(stats: Map<PromptStrategy, CellStats>): Set<PromptStrategy> {
    val best = stats.values.mapNotNull { it.accuracy }.maxOrNull() ?: return emptySet()
    return stats.filterValues { it.accuracy == best }.keys
}

/**
 * Runs a task set through prompt strategies on one or more engines and scores each answer with the task's
 * deterministic check. The loop is task-major inside each engine, so an interrupted run still leaves every
 * strategy with the same tasks answered and the partial table stays comparable.
 */
class StrategyBenchmark(
    private val runner: PipelineRunner,
    private val profileOf: ProfileOf = { EngineProfile() },
) {

    suspend fun run(
        tiers: List<InferenceTier>,
        strategies: List<PromptStrategy>,
        tasks: List<StrategyTask>,
        repetitions: Int,
        temperature: Float,
        onUpdate: (List<TaskOutcome>) -> Unit = {},
        onProgress: (LabProgress) -> Unit = {},
    ): List<TaskOutcome> {
        val reps = repetitions.coerceAtLeast(1)
        val total = tiers.size * reps * tasks.size * strategies.size
        val outcomes = mutableListOf<TaskOutcome>()
        for (tier in tiers) {
            for (rep in 1..reps) {
                for (task in tasks) {
                    for (strategy in strategies) {
                        currentCoroutineContext().ensureActive()
                        onProgress(LabProgress(outcomes.size, total, "${tier.name} · ${strategy.label} · ${task.id}"))
                        outcomes += runOne(tier, strategy, task, rep, temperature)
                        onUpdate(outcomes.toList())
                    }
                }
            }
        }
        onProgress(LabProgress(total, total, "Listo"))
        return outcomes
    }

    suspend fun runOne(tier: InferenceTier, strategy: PromptStrategy, task: StrategyTask, repetition: Int, temperature: Float): TaskOutcome {
        val profile = profileOf(tier)
        var tokens = 0
        var latency = 0L

        suspend fun call(step: StrategyStep, title: String): ModelReply {
            val reply = runner.ask(
                StepKind.GENERATE, "$title · ${task.id}", tier, step.prompt,
                GenerationConfig(temperature = temperature, maxOutputTokens = profile.outputTokens(step.wantedTokens)),
            )
            tokens += reply.tokens
            latency += reply.latencyMs
            return reply
        }

        fun failed(error: String?) = TaskOutcome(tier, strategy, task.id, repetition, correct = false, failed = true, tokens, latency, "", error)

        var reply = call(StrategyPrompts.firstStep(strategy, task, profile), strategy.label)
        if (!reply.ok) return failed(reply.error)
        if (strategy == PromptStrategy.STEP_BACK) {
            reply = call(StrategyPrompts.stepBackAnswer(task, reply.text, profile), "${strategy.label} (respuesta)")
            if (!reply.ok) return failed(reply.error)
        }
        val score = scoreTask(task, reply.text)
        return TaskOutcome(tier, strategy, task.id, repetition, score.correct, failed = false, tokens, latency, score.answer)
    }
}
