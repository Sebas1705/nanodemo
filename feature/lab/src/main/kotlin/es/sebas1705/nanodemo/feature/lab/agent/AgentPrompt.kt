package es.sebas1705.nanodemo.feature.lab.agent

import es.sebas1705.nanodemo.core.common.appContextPreamble
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.feature.lab.agent.tools.Tool
import es.sebas1705.nanodemo.feature.lab.agent.tools.truncateWithMark

/** How verbose the protocol text is. Tiny context windows get the compact one. */
enum class PromptStyle { FULL, COMPACT }

/**
 * Character budgets for one agent run on one tier, derived from the engine's context window (~4 characters per
 * token, with a safety margin because Spanish and JSON tokenise worse than English).
 *
 * `promptChars` is the most the prompt may use: total window minus the reply allowance and the system preamble
 * every backend prepends on its own. `toolOutputChars` caps an observation.
 */
data class AgentBudget(
    val promptChars: Int,
    val toolOutputChars: Int,
    val maxOutputTokens: Int,
    val style: PromptStyle,
) {
    companion object {
        const val CHARS_PER_TOKEN = 4
        private const val SAFETY = 0.8
        private const val NANO_TOKENS = 3_000       // not measured by the app; conservative for the on-device prompt API
        private const val CLOUD_CHARS = 14_000      // large windows: just keep the loop from growing without bound

        /** Characters the backends add in front of the prompt (see `appContextPreamble`). */
        val BACKEND_OVERHEAD_CHARS: Int get() = appContextPreamble().length + 40

        /**
         * `contextTokens` comes from `TierCapabilities.contextTokens`; `null` means "not measured" and then the
         * tier decides (Nano is treated as small, the cloud engines as large).
         */
        fun forTier(tier: InferenceTier, contextTokens: Int?): AgentBudget {
            val window = contextTokens ?: if (tier == InferenceTier.ML_KIT_NANO) NANO_TOKENS else null
            if (window == null) {
                return AgentBudget(promptChars = CLOUD_CHARS, toolOutputChars = 1_200, maxOutputTokens = 512, style = PromptStyle.FULL)
            }
            val maxOut = when {
                window <= 1_024 -> 160
                window <= 2_048 -> 224
                else -> 288
            }
            val total = (window * CHARS_PER_TOKEN * SAFETY).toInt()
            val prompt = (total - BACKEND_OVERHEAD_CHARS - maxOut * CHARS_PER_TOKEN).coerceAtLeast(600)
            return AgentBudget(
                promptChars = prompt,
                toolOutputChars = (prompt / 6).coerceIn(120, 600),
                maxOutputTokens = maxOut,
                style = if (window <= 2_048) PromptStyle.COMPACT else PromptStyle.FULL,
            )
        }
    }
}

/** One finished iteration of the loop, as the model sees it in the scratchpad. */
data class ScratchEntry(
    val thought: String?,
    val action: String?,
    val input: String?,
    val observation: String,
)

/** Builds the prompts of the loop. Pure: same inputs, same text. */
object AgentPrompt {
    private const val OLD_OBSERVATION_CHARS = 100
    private const val OLD_INPUT_CHARS = 100
    private const val MIN_LAST_OBSERVATION_CHARS = 80

    private const val PROTOCOL_FULL = """You are an agent that solves the task step by step, using tools when they help. Each reply is exactly ONE of:

A) To use a tool:
Thought: <why>
Action: <tool name>
Action Input: <JSON>

B) When you can answer:
Thought: <why>
Final Answer: <answer, in the language of the task>

After an Action, STOP and wait: the system answers with "Observation:". Never write the Observation yourself. If no tool is needed, give the Final Answer straight away."""

    private const val PROTOCOL_COMPACT = """Solve the task. Reply with ONE of:
Thought: <why>
Action: <tool>
Action Input: <JSON>
(then STOP; the system answers "Observation:")
or
Thought: <why>
Final Answer: <answer, in the task's language>
Use a tool only if needed."""

    private const val EXAMPLE = """Example:
Task: What is 12% of 50?
Thought: I need to calculate.
Action: calculator
Action Input: {"expression": "12% of 50"}
Observation: 6
Thought: I have the result.
Final Answer: 12% of 50 is 6."""

    const val LAST_STEP_NUDGE = "This is your LAST step: reply with \"Final Answer:\" now, using what you know."

    /** Tool list for the prompt: compact style drops the descriptions. */
    fun toolLines(tools: List<Tool>, style: PromptStyle): String = tools.joinToString("\n") { tool ->
        if (style == PromptStyle.COMPACT) "- ${tool.name} ${tool.parameterHint}" else "- ${tool.name} ${tool.parameterHint}: ${tool.description}"
    }

    /** The part of the prompt that never changes during a run (protocol, tools, example). */
    fun fixedPart(tools: List<Tool>, style: PromptStyle): String = buildString {
        append(if (style == PromptStyle.COMPACT) PROTOCOL_COMPACT else PROTOCOL_FULL)
        if (tools.isNotEmpty()) {
            append("\n\nTools:\n").append(toolLines(tools, style))
        } else {
            append("\n\nYou have no tools: answer directly.")
        }
        if (style == PromptStyle.FULL && tools.any { it.name == "calculator" }) append("\n\n").append(EXAMPLE)
    }

    /**
     * The whole prompt for the next model call: fixed part, the task, and as much of the scratchpad as fits in
     * `budget.promptChars` (see [renderScratchpad]). `lastStep` adds the "answer now" nudge.
     */
    fun build(task: String, tools: List<Tool>, entries: List<ScratchEntry>, budget: AgentBudget, lastStep: Boolean): String {
        val head = fixedPart(tools, budget.style) + "\n\nTask: " + task.trim()
        val tail = if (lastStep) "\n" + LAST_STEP_NUDGE else ""
        val room = budget.promptChars - head.length - tail.length - SCRATCH_JOINER_CHARS
        val scratch = renderScratchpad(entries, room.coerceAtLeast(MIN_LAST_OBSERVATION_CHARS))
        return buildString {
            append(head)
            if (scratch.isNotEmpty()) append("\n").append(scratch)
            append(tail)
        }
    }

    private const val SCRATCH_JOINER_CHARS = 2

    /**
     * Renders the iterations so far in at most `maxChars`, degrading in this order until it fits:
     * 1. drop the thoughts of all but the last iteration;
     * 2. shorten old observations and inputs;
     * 3. drop the oldest iterations, leaving a note of how many;
     * 4. as a last resort, shorten the last observation itself.
     * The last iteration is always kept: it is what the model has to react to.
     */
    fun renderScratchpad(entries: List<ScratchEntry>, maxChars: Int): String {
        if (entries.isEmpty()) return ""
        fun render(list: List<ScratchEntry>, omitted: Int): String = buildString {
            if (omitted > 0) appendLine("[$omitted earlier step(s) omitted]")
            list.forEachIndexed { i, e ->
                if (i > 0) append('\n')
                append(renderEntry(e))
            }
        }

        var work = entries
        render(work, 0).let { if (it.length <= maxChars) return it }

        work = work.mapIndexed { i, e -> if (i == work.lastIndex) e else e.copy(thought = null) }
        render(work, 0).let { if (it.length <= maxChars) return it }

        work = work.mapIndexed { i, e ->
            if (i == work.lastIndex) e else e.copy(
                input = e.input?.truncateWithMark(OLD_INPUT_CHARS),
                observation = e.observation.truncateWithMark(OLD_OBSERVATION_CHARS),
            )
        }
        render(work, 0).let { if (it.length <= maxChars) return it }

        var dropped = 0
        while (work.size > 1) {
            work = work.drop(1)
            dropped++
            render(work, dropped).let { if (it.length <= maxChars) return it }
        }

        // Only the last iteration is left and it is still too big: shorten its observation, then its thought.
        val last = work.single()
        val fixedLen = render(listOf(last.copy(observation = "")), dropped).length
        val observationRoom = (maxChars - fixedLen).coerceAtLeast(MIN_LAST_OBSERVATION_CHARS)
        val shrunk = last.copy(thought = null, observation = last.observation.truncateWithMark(observationRoom))
        return render(listOf(shrunk), dropped)
    }

    private fun renderEntry(e: ScratchEntry): String = buildString {
        e.thought?.takeIf { it.isNotBlank() }?.let { append("Thought: ").append(it.replace('\n', ' ').trim()).append('\n') }
        if (e.action != null) {
            append("Action: ").append(e.action).append('\n')
            append("Action Input: ").append((e.input ?: "").replace('\n', ' ')).append('\n')
        }
        append("Observation: ").append(e.observation.trim())
    }
}
