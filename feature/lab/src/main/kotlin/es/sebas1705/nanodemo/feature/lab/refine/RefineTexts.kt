package es.sebas1705.nanodemo.feature.lab.refine

import android.content.Context
import androidx.annotation.StringRes
import es.sebas1705.nanodemo.feature.lab.R

/**
 * Every user-visible text the pipelines of this package write outside a composable: trace step titles and
 * details, progress labels, error messages and the labels of generated answers. The pipelines stay pure Kotlin:
 * they take a [RefineTexts] and never touch Android. The defaults are English, which is what unit tests get;
 * the app passes [AndroidRefineTexts], which reads the string resources (`refine_msg_*`, `judge_msg_*`,
 * `strategies_msg_*`) in the device language.
 */
interface RefineTexts {
    // ---- shared by the three experiments ----
    val done: String get() = "Done"
    val stopped: String get() = "Stopped"
    val cancelled: String get() = "Cancelled"
    val error: String get() = "Error"
    val emptyReply: String get() = EMPTY_REPLY_MESSAGE
    val yes: String get() = "yes"
    val no: String get() = "no"
    val unreadable: String get() = "unreadable"

    // ---- plan, execute and critique ----
    fun roleName(role: RefineRole): String = when (role) {
        RefineRole.PLANNER -> "Planner"
        RefineRole.WORKER -> "Worker"
        RefineRole.CRITIC -> "Critic"
    }
    fun verdictName(verdict: Verdict): String = when (verdict) {
        Verdict.ACCEPT -> "accepted"
        Verdict.REVISE -> "asks for changes"
        Verdict.UNKNOWN -> "unreadable"
    }
    val planLabel: String get() = "Plan"
    fun draftLabel(number: Int): String = "Draft $number"
    fun critiqueLabel(version: Int): String = "Critique $version"
    fun revisionLabel(number: Int): String = "Revision $number"
    val planTitle: String get() = "Plan"
    fun draftTitle(version: Int): String = "Draft v$version"
    fun critiqueTitle(version: Int): String = "Critique of v$version"
    val readCritique: String get() = "Read the critique"
    val compareVersions: String get() = "Compare versions"
    val critiqueJson: String get() = "JSON"
    val critiqueFreeText: String get() = "free text"
    fun diffSummary(added: Int, removed: Int, equalPercent: Int): String = "+$added −$removed · $equalPercent% equal"
    fun critiqueSummary(verdict: String, issues: Int): String = "$verdict · $issues ${if (issues == 1) "issue" else "issues"}"

    // ---- a model as judge ----
    fun typedAnswerLabel(number: Int): String = "Answer $number"
    fun answerFrom(engine: String): String = "Answer from $engine"
    val answerTitle: String get() = "Answer"
    val needTwoAnswers: String get() = "At least two answers are needed to judge."
    fun scoring(candidate: String): String = "Scoring $candidate"
    fun scoreTitle(candidate: String): String = "Score $candidate"
    val readScore: String get() = "Read the score"
    fun scoreOutOfFive(average: String): String = "$average of 5"
    fun pairLabel(a: String, b: String, round: Int): String = "$a vs $b ($round/2)"
    fun pairTitle(first: String, second: String): String = "$first (A) vs $second (B)"
    val readVerdict: String get() = "Read the verdict"
    fun winsFirst(candidate: String): String = "A wins ($candidate)"
    fun winsSecond(candidate: String): String = "B wins ($candidate)"
    val tie: String get() = "tie"

    // ---- vote and compare strategies ----
    val greedyAnswer: String get() = "Greedy answer"
    fun sampleLabel(index: Int, total: Int): String = "Sample $index of $total"
    fun sampleTitle(index: Int): String = "Sample $index"
    val voting: String get() = "Voting"
    val extractAnswers: String get() = "Extract answers"
    fun answersCount(count: Int): String = "$count ${if (count == 1) "answer" else "answers"}"
    val vote: String get() = "Vote"
    fun samplesCount(count: Int): String = "$count ${if (count == 1) "sample" else "samples"}"
    val noReadableAnswers: String get() = "no readable answers"
    fun tieBetween(answers: String): String = "tie between $answers"
    fun strategyName(strategy: PromptStrategy): String = when (strategy) {
        PromptStrategy.ZERO_SHOT -> "Zero-shot"
        PromptStrategy.FEW_SHOT -> "Few-shot"
        PromptStrategy.CHAIN_OF_THOUGHT -> "Chain of thought"
        PromptStrategy.STEP_BACK -> "Step back"
        PromptStrategy.ROLE -> "Role"
    }
    fun stepBackAnswer(strategy: String): String = "$strategy (answer)"

    /** The English texts: the default of every pipeline, so unit tests need no Android. */
    companion object Default : RefineTexts
}

/** [RefineTexts] from the string resources, in the language of `context`. Pass the application context. */
class AndroidRefineTexts(private val context: Context) : RefineTexts {
    private val res get() = context.resources

    override val done: String get() = res.getString(R.string.refine_msg_done)
    override val stopped: String get() = res.getString(R.string.refine_msg_stopped)
    override val cancelled: String get() = res.getString(R.string.refine_msg_cancelled)
    override val error: String get() = res.getString(R.string.refine_msg_error)
    override val emptyReply: String get() = res.getString(R.string.refine_msg_empty_reply)
    override val yes: String get() = res.getString(R.string.refine_msg_yes)
    override val no: String get() = res.getString(R.string.refine_msg_no)
    override val unreadable: String get() = res.getString(R.string.refine_msg_unreadable)

    override fun roleName(role: RefineRole): String = res.getString(
        when (role) {
            RefineRole.PLANNER -> R.string.refine_role_planner
            RefineRole.WORKER -> R.string.refine_role_worker
            RefineRole.CRITIC -> R.string.refine_role_critic
        },
    )
    override fun verdictName(verdict: Verdict): String = res.getString(verdict.labelRes())
    override val planLabel: String get() = res.getString(R.string.refine_msg_plan_label)
    override fun draftLabel(number: Int): String = res.getString(R.string.refine_msg_draft_label, number)
    override fun critiqueLabel(version: Int): String = res.getString(R.string.refine_msg_critique_label, version)
    override fun revisionLabel(number: Int): String = res.getString(R.string.refine_msg_revision_label, number)
    override val planTitle: String get() = res.getString(R.string.refine_msg_plan_title)
    override fun draftTitle(version: Int): String = res.getString(R.string.refine_msg_draft_title, version)
    override fun critiqueTitle(version: Int): String = res.getString(R.string.refine_msg_critique_title, version)
    override val readCritique: String get() = res.getString(R.string.refine_msg_read_critique)
    override val compareVersions: String get() = res.getString(R.string.refine_msg_compare_versions)
    override val critiqueJson: String get() = res.getString(R.string.refine_msg_critique_json)
    override val critiqueFreeText: String get() = res.getString(R.string.refine_msg_critique_free_text)
    override fun diffSummary(added: Int, removed: Int, equalPercent: Int): String =
        res.getString(R.string.refine_msg_diff_summary, added, removed, equalPercent)
    override fun critiqueSummary(verdict: String, issues: Int): String =
        res.getQuantityString(R.plurals.refine_msg_critique_summary, issues, verdict, issues)

    override fun typedAnswerLabel(number: Int): String = res.getString(R.string.judge_answer_label, number)
    override fun answerFrom(engine: String): String = res.getString(R.string.judge_msg_answer_from, engine)
    override val answerTitle: String get() = res.getString(R.string.judge_msg_answer_title)
    override val needTwoAnswers: String get() = res.getString(R.string.judge_msg_need_two)
    override fun scoring(candidate: String): String = res.getString(R.string.judge_msg_scoring, candidate)
    override fun scoreTitle(candidate: String): String = res.getString(R.string.judge_msg_score_title, candidate)
    override val readScore: String get() = res.getString(R.string.judge_msg_read_score)
    override fun scoreOutOfFive(average: String): String = res.getString(R.string.judge_msg_score_out, average)
    override fun pairLabel(a: String, b: String, round: Int): String = res.getString(R.string.judge_msg_pair_label, a, b, round)
    override fun pairTitle(first: String, second: String): String = res.getString(R.string.judge_msg_pair_title, first, second)
    override val readVerdict: String get() = res.getString(R.string.judge_msg_read_verdict)
    override fun winsFirst(candidate: String): String = res.getString(R.string.judge_msg_wins_first, candidate)
    override fun winsSecond(candidate: String): String = res.getString(R.string.judge_msg_wins_second, candidate)
    override val tie: String get() = res.getString(R.string.judge_msg_tie)

    override val greedyAnswer: String get() = res.getString(R.string.strategies_msg_greedy)
    override fun sampleLabel(index: Int, total: Int): String = res.getString(R.string.strategies_msg_sample_label, index, total)
    override fun sampleTitle(index: Int): String = res.getString(R.string.strategies_msg_sample_title, index)
    override val voting: String get() = res.getString(R.string.strategies_msg_voting)
    override val extractAnswers: String get() = res.getString(R.string.strategies_msg_extract_title)
    override fun answersCount(count: Int): String = res.getQuantityString(R.plurals.strategies_msg_extract_input, count, count)
    override val vote: String get() = res.getString(R.string.strategies_msg_vote_title)
    override fun samplesCount(count: Int): String = res.getQuantityString(R.plurals.strategies_msg_vote_input, count, count)
    override val noReadableAnswers: String get() = res.getString(R.string.strategies_msg_no_readable)
    override fun tieBetween(answers: String): String = res.getString(R.string.strategies_msg_tie_between, answers)
    override fun strategyName(strategy: PromptStrategy): String = res.getString(strategy.label)
    override fun stepBackAnswer(strategy: String): String = res.getString(R.string.strategies_msg_step_back_answer, strategy)
}

/** String resource naming a critic's verdict, shared by the screen and the trace. */
@StringRes
fun Verdict.labelRes(): Int = when (this) {
    Verdict.ACCEPT -> R.string.refine_verdict_accept
    Verdict.REVISE -> R.string.refine_verdict_revise
    Verdict.UNKNOWN -> R.string.refine_verdict_unknown
}
