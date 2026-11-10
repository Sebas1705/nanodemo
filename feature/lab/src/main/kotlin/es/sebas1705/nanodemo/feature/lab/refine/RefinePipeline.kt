package es.sebas1705.nanodemo.feature.lab.refine

import es.sebas1705.nanodemo.domain.models.GenerationConfig
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.StepKind
import es.sebas1705.nanodemo.feature.lab.trace.LocalOutcome
import es.sebas1705.nanodemo.feature.lab.trace.PipelineRunner
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

enum class RefineRole { PLANNER, WORKER, CRITIC }

/** Which engine plays each role, and how many times the worker may rewrite its answer after a critique. */
data class RefineSetup(
    val planner: InferenceTier,
    val worker: InferenceTier,
    val critic: InferenceTier,
    val maxRevisions: Int = 2,
) {
    fun tierOf(role: RefineRole): InferenceTier = when (role) {
        RefineRole.PLANNER -> planner
        RefineRole.WORKER -> worker
        RefineRole.CRITIC -> critic
    }

    fun with(role: RefineRole, tier: InferenceTier): RefineSetup = when (role) {
        RefineRole.PLANNER -> copy(planner = tier)
        RefineRole.WORKER -> copy(worker = tier)
        RefineRole.CRITIC -> copy(critic = tier)
    }

    companion object {
        /**
         * A sensible starting cast from the engines usable now: a cheap local worker (and planner), and a cloud
         * critic when there is one, since judging is where a stronger model pays off most.
         */
        fun defaultFor(usable: List<InferenceTier>, maxRevisions: Int = 2): RefineSetup {
            val worker = pickTier(usable, { it.isLocal }, InferenceTier.ML_KIT_NANO)
            val critic = pickTier(usable, { !it.isLocal }, worker)
            return RefineSetup(planner = worker, worker = worker, critic = critic, maxRevisions = maxRevisions)
        }
    }
}

/**
 * One version of the answer. `critique` is the critic's reading of THIS version (null if it was never
 * critiqued); `change` is the diff from the previous version (null for v1).
 */
data class RefineDraft(
    val version: Int,
    val text: String,
    val critique: Critique? = null,
    val change: DraftChange? = null,
)

enum class RefineStop {
    /** The critic accepted the last draft. */
    ACCEPTED,

    /** The critic still asked for changes but the revision budget was spent. */
    MAX_REVISIONS,

    /** The critic's reply could not be read, so the loop stopped instead of guessing. */
    CRITIC_UNREADABLE,

    /** An engine failed (unavailable, error, blank reply). */
    FAILED,
}

/** Everything a refine run produced, also while it is still running (`stop == null`). */
data class RefineResult(
    val plan: String = "",
    val drafts: List<RefineDraft> = emptyList(),
    val stop: RefineStop? = null,
    val error: String? = null,
    val stats: Map<RefineRole, CallStats> = emptyMap(),
) {
    /** The answer to hand to the user: the latest draft. */
    val finalAnswer: String? get() = drafts.lastOrNull()?.text
    val total: CallStats get() = stats.values.fold(CallStats()) { acc, s -> acc + s }
}

/**
 * Planner -> worker -> critic with revision rounds. The planner writes a short numbered plan, the worker
 * executes it, the critic returns a verdict and a list of issues, and the worker rewrites the answer until the
 * critic accepts or the revision budget runs out. Each role may run on a different engine; every call and every
 * parse is a step in the runner's trace.
 */
class RefinePipeline(
    private val runner: PipelineRunner,
    private val profileOf: ProfileOf = { EngineProfile() },
) {

    suspend fun run(
        task: String,
        setup: RefineSetup,
        onUpdate: (RefineResult) -> Unit = {},
        onProgress: (LabProgress) -> Unit = {},
    ): RefineResult {
        val maxRevisions = setup.maxRevisions.coerceAtLeast(0)
        // Plan + first draft + one critique per draft, plus one rewrite per revision.
        val total = 2 + (maxRevisions + 1) + maxRevisions
        var calls = 0
        var result = RefineResult()
        val stats = mutableMapOf<RefineRole, CallStats>()

        fun publish(change: (RefineResult) -> RefineResult) {
            result = change(result).copy(stats = stats.toMap())
            onUpdate(result)
        }

        fun end(stop: RefineStop, error: String? = null): RefineResult {
            publish { it.copy(stop = stop, error = error) }
            onProgress(LabProgress(total, total, if (stop == RefineStop.FAILED) "Detenido" else "Listo"))
            return result
        }

        fun fail(error: String?) = end(RefineStop.FAILED, error)

        suspend fun call(role: RefineRole, label: String, kind: StepKind, title: String, prompt: String, config: GenerationConfig): ModelReply {
            currentCoroutineContext().ensureActive()
            onProgress(LabProgress(calls, total, label))
            val reply = runner.ask(kind, title, setup.tierOf(role), prompt, config)
            calls++
            stats[role] = (stats[role] ?: CallStats()) + reply
            return reply
        }

        // 1. Plan
        val planProfile = profileOf(setup.planner)
        val planOut = planProfile.outputTokens(PLAN_TOKENS)
        val planPrompt = planPrompt(task, planProfile, planOut)
        val planReply = call(
            RefineRole.PLANNER, "Plan", StepKind.PLAN, "Plan", planPrompt,
            GenerationConfig(temperature = 0.3f, maxOutputTokens = planOut),
        )
        if (!planReply.ok) return fail("Planificador: ${planReply.error}")
        publish { it.copy(plan = planReply.text) }

        // 2. First draft
        val workProfile = profileOf(setup.worker)
        val workOut = workProfile.outputTokens(WORK_TOKENS)
        val workConfig = GenerationConfig(temperature = 0.5f, maxOutputTokens = workOut)
        val firstReply = call(
            RefineRole.WORKER, "Borrador 1", StepKind.GENERATE, "Borrador v1",
            workerPrompt(task, planReply.text, workProfile, workOut), workConfig,
        )
        if (!firstReply.ok) return fail("Trabajador: ${firstReply.error}")
        publish { it.copy(drafts = listOf(RefineDraft(1, firstReply.text))) }

        // 3. Critique / revise
        var revisions = 0
        while (true) {
            val version = revisions + 1
            val draft = result.drafts.last()
            val criticProfile = profileOf(setup.critic)
            val criticOut = criticProfile.outputTokens(CRITIC_TOKENS)
            val criticReply = call(
                RefineRole.CRITIC, "Crítica $version", StepKind.CRITIQUE, "Crítica de v$version",
                criticPrompt(task, draft.text, criticProfile, criticOut),
                GenerationConfig(temperature = 0.1f, maxOutputTokens = criticOut, jsonMode = criticProfile.structuredOutput),
            )
            if (!criticReply.ok) return fail("Crítico: ${criticReply.error}")

            val critique = runner.local(StepKind.VALIDATE, "Leer la crítica", input = criticReply.text) {
                val parsed = parseCritique(criticReply.text)
                LocalOutcome(
                    value = parsed,
                    output = "${parsed.verdict.name.lowercase()} · ${parsed.issues.size} problemas",
                    detail = if (parsed.structured) "JSON" else "texto libre",
                )
            }
            publish { current ->
                current.copy(drafts = current.drafts.dropLast(1) + current.drafts.last().copy(critique = critique))
            }

            when {
                critique.verdict == Verdict.ACCEPT -> return end(RefineStop.ACCEPTED)
                critique.verdict == Verdict.UNKNOWN -> return end(RefineStop.CRITIC_UNREADABLE)
                revisions >= maxRevisions -> return end(RefineStop.MAX_REVISIONS)
            }

            revisions++
            val reviseReply = call(
                RefineRole.WORKER, "Revisión $revisions", StepKind.GENERATE, "Borrador v${revisions + 1}",
                revisionPrompt(task, draft.text, critique.issues, workProfile, workOut), workConfig,
            )
            if (!reviseReply.ok) return fail("Trabajador: ${reviseReply.error}")
            val change = runner.local(StepKind.SUMMARIZE, "Comparar versiones", input = "v$version → v${revisions + 1}") {
                val diff = diffDrafts(draft.text, reviseReply.text)
                LocalOutcome(diff, output = "+${diff.added} −${diff.removed} · ${(diff.similarity * 100).toInt()}% igual")
            }
            publish { it.copy(drafts = it.drafts + RefineDraft(revisions + 1, reviseReply.text, change = change)) }
        }
    }

    internal companion object {
        const val PLAN_TOKENS = 200
        const val WORK_TOKENS = 384
        const val CRITIC_TOKENS = 200

        fun planPrompt(task: String, profile: EngineProfile, outputTokens: Int): String {
            val template = "Write a short numbered plan (3 to 5 steps, one line each) to solve the task below. Output only the plan.\n\nTask: "
            val (t) = profile.fitParts(outputTokens, template, task)
            return template + t
        }

        fun workerPrompt(task: String, plan: String, profile: EngineProfile, outputTokens: Int): String {
            val template = "Task: \n\nPlan:\n\nFollow the plan and write the final answer. Be concise. Reply in the language of the task."
            val (t, p) = profile.fitParts(outputTokens, template, task, plan)
            return "Task: $t\n\nPlan:\n$p\n\nFollow the plan and write the final answer. Be concise. Reply in the language of the task."
        }

        fun criticPrompt(task: String, draft: String, profile: EngineProfile, outputTokens: Int): String {
            val head = "You are a strict reviewer. Check the answer against the task. Reply ONLY with JSON: " +
                "{\"verdict\":\"accept\" or \"revise\",\"issues\":[\"short issue\", ...]}. " +
                "Use \"accept\" only if the answer is correct and complete."
            val (t, d) = profile.fitParts(outputTokens, head, task, draft)
            return "$head\n\nTask: $t\n\nAnswer:\n$d"
        }

        fun revisionPrompt(task: String, draft: String, issues: List<String>, profile: EngineProfile, outputTokens: Int): String {
            val feedback = issues.joinToString("\n") { "- $it" }
                .ifBlank { "- The reviewer asked for a revision without details: improve correctness, completeness and clarity." }
            val tail = "Rewrite the answer fixing every issue. Output only the improved answer."
            val (t, d, f) = profile.fitParts(outputTokens, "Task: \n\nYour previous answer:\n\nReviewer feedback:\n\n$tail", task, draft, feedback)
            return "Task: $t\n\nYour previous answer:\n$d\n\nReviewer feedback:\n$f\n\n$tail"
        }
    }
}
