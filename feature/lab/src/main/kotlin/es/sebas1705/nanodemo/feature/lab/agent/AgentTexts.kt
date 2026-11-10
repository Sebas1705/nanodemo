package es.sebas1705.nanodemo.feature.lab.agent

import android.content.Context
import androidx.annotation.StringRes
import es.sebas1705.nanodemo.feature.lab.R

/**
 * User-visible texts the agent code writes outside a composable: trace titles and reasons, task check
 * failures and outcome labels. The loop and the checks stay pure Kotlin and take an [AgentTexts]; the defaults
 * are English (what unit tests get) and the app passes [AndroidAgentTexts], which reads the `agent_*` string
 * resources in the device language. Text that goes to the model (prompts, observations, tool output) is not
 * here on purpose: it must not change with the UI language.
 */
interface AgentTexts {
    val noAnswer: String get() = "No answer"
    fun maxStepsReason(maxSteps: Int): String = "limit of $maxSteps ${if (maxSteps == 1) "step" else "steps"} reached"
    val timeBudgetSpent: String get() = "time budget exhausted"
    fun thinkTitle(step: Int): String = "Step $step · think"
    val modelError: String get() = "Model error"
    val finalTitle: String get() = "Final answer"
    fun finalDetail(steps: Int, tools: Int, implicit: Boolean): String = buildString {
        append("$steps ${if (steps == 1) "step" else "steps"}")
        append(" · $tools ${if (tools == 1) "tool" else "tools"}")
        if (implicit) append(" · no «Final Answer» label")
    }
    val invalidFormat: String get() = "Invalid format"
    val modelIgnoresFormat: String get() = "the model does not follow the format"
    fun repeatedTitle(tool: String): String = "Repeated · $tool"
    val repeatedNotRun: String get() = "same action and input as before; not run"
    val repeatsAction: String get() = "the model repeats the same action"
    fun toolTitle(step: Int, tool: String): String = "Step $step · $tool"
    val cancelled: String get() = "cancelled"

    fun outcome(outcome: AgentOutcome): String = when (outcome) {
        AgentOutcome.ANSWERED -> "answered"
        AgentOutcome.MAX_STEPS -> "ran out of steps"
        AgentOutcome.TIMEOUT -> "ran out of time"
        AgentOutcome.LOOP -> "got into a loop"
        AgentOutcome.FORMAT_FAILURE -> "did not follow the format"
        AgentOutcome.MODEL_ERROR -> "engine error"
    }

    // ---- why a task check failed ----
    fun missingValue(value: String): String = "the value $value was missing from the answer"
    fun missingWord(word: String): String = "«$word» was missing from the answer"
    fun toolNotUsed(tool: String): String = "it did not use the $tool tool"
    fun toolsNotNeeded(tools: String): String = "it used tools without needing them ($tools)"
    val noZone: String get() = "it did not ask for the Asia/Tokyo zone"
    val noClock: String get() = "the answer has no time"
    val noteNotSaved: String get() = "it never managed to save the note"
    val noteNotRead: String get() = "it did not read the notes after saving"

    /** The English texts: the default of the runner and the checks, so unit tests need no Android. */
    companion object Default : AgentTexts
}

/** [AgentTexts] from the string resources, in the language of `context`. Pass the application context. */
class AndroidAgentTexts(private val context: Context) : AgentTexts {
    private val res get() = context.resources

    override val noAnswer: String get() = res.getString(R.string.agent_msg_no_answer)
    override fun maxStepsReason(maxSteps: Int): String = res.getQuantityString(R.plurals.agent_msg_max_steps_reason, maxSteps, maxSteps)
    override val timeBudgetSpent: String get() = res.getString(R.string.agent_msg_time_budget_spent)
    override fun thinkTitle(step: Int): String = res.getString(R.string.agent_msg_think_title, step)
    override val modelError: String get() = res.getString(R.string.agent_msg_model_error)
    override val finalTitle: String get() = res.getString(R.string.agent_msg_final_title)
    override fun finalDetail(steps: Int, tools: Int, implicit: Boolean): String = buildString {
        append(res.getQuantityString(R.plurals.agent_msg_steps, steps, steps))
        append(" · ").append(res.getQuantityString(R.plurals.agent_msg_tools, tools, tools))
        if (implicit) append(" · ").append(res.getString(R.string.agent_msg_no_label))
    }
    override val invalidFormat: String get() = res.getString(R.string.agent_msg_invalid_format)
    override val modelIgnoresFormat: String get() = res.getString(R.string.agent_msg_model_ignores_format)
    override fun repeatedTitle(tool: String): String = res.getString(R.string.agent_msg_repeated_title, tool)
    override val repeatedNotRun: String get() = res.getString(R.string.agent_msg_repeated_not_run)
    override val repeatsAction: String get() = res.getString(R.string.agent_msg_repeats_action)
    override fun toolTitle(step: Int, tool: String): String = res.getString(R.string.agent_msg_tool_title, step, tool)
    override val cancelled: String get() = res.getString(R.string.agent_msg_cancelled)

    override fun outcome(outcome: AgentOutcome): String = res.getString(outcome.labelRes())

    override fun missingValue(value: String): String = res.getString(R.string.agent_check_missing_value, value)
    override fun missingWord(word: String): String = res.getString(R.string.agent_check_missing_word, word)
    override fun toolNotUsed(tool: String): String = res.getString(R.string.agent_check_tool_not_used, tool)
    override fun toolsNotNeeded(tools: String): String = res.getString(R.string.agent_check_tools_not_needed, tools)
    override val noZone: String get() = res.getString(R.string.agent_check_no_zone)
    override val noClock: String get() = res.getString(R.string.agent_check_no_clock)
    override val noteNotSaved: String get() = res.getString(R.string.agent_check_note_not_saved)
    override val noteNotRead: String get() = res.getString(R.string.agent_check_note_not_read)
}

/** String resource describing why a run ended. */
@StringRes
fun AgentOutcome.labelRes(): Int = when (this) {
    AgentOutcome.ANSWERED -> R.string.agent_outcome_answered
    AgentOutcome.MAX_STEPS -> R.string.agent_outcome_max_steps
    AgentOutcome.TIMEOUT -> R.string.agent_outcome_timeout
    AgentOutcome.LOOP -> R.string.agent_outcome_loop
    AgentOutcome.FORMAT_FAILURE -> R.string.agent_outcome_format_failure
    AgentOutcome.MODEL_ERROR -> R.string.agent_outcome_model_error
}
