package es.sebas1705.nanodemo.feature.lab.memory

import androidx.annotation.StringRes
import android.content.res.Resources
import es.sebas1705.nanodemo.domain.models.ChatMessage
import es.sebas1705.nanodemo.domain.models.GenerationConfig
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.Role
import es.sebas1705.nanodemo.domain.models.StepKind
import es.sebas1705.nanodemo.feature.lab.R
import es.sebas1705.nanodemo.feature.lab.trace.LocalOutcome
import es.sebas1705.nanodemo.feature.lab.trace.PipelineRunner

enum class MemoryStrategy(@StringRes val label: Int, @StringRes val description: Int) {
    FULL(R.string.memory_strategy_full, R.string.memory_strategy_full_desc),
    WINDOW(R.string.memory_strategy_window, R.string.memory_strategy_window_desc),
    SUMMARY(R.string.memory_strategy_summary, R.string.memory_strategy_summary_desc),
    FACTS(R.string.memory_strategy_facts, R.string.memory_strategy_facts_desc),
}

/** What one strategy achieved on one scenario. `error` is set when an engine call failed (the result is then not comparable). */
data class StrategyResult(
    val strategy: MemoryStrategy,
    val promptTokens: Int,
    val budgetTokens: Int,
    val recall: Double,
    val answer: String,
    val totalMs: Long,
    val modelCalls: Int,
    val error: String? = null,
) {
    val overflow: Boolean get() = promptTokens > budgetTokens
}

/** Texts the pipeline puts in the trace; the Android implementation reads string resources, tests use English. */
interface MemoryTexts {
    fun stepSummarize(part: Int, parts: Int): String
    fun stepExtractFacts(part: Int, parts: Int): String
    fun stepAnswer(strategy: MemoryStrategy): String
    fun stepRecall(): String
    fun recallDetail(found: Int, total: Int): String
    fun overflowDetail(promptTokens: Int, budgetTokens: Int): String
}

object EnglishMemoryTexts : MemoryTexts {
    override fun stepSummarize(part: Int, parts: Int) = "Summarize older turns ($part/$parts)"
    override fun stepExtractFacts(part: Int, parts: Int) = "Extract facts ($part/$parts)"
    override fun stepAnswer(strategy: MemoryStrategy) = "Answer with ${strategy.name.lowercase()}"
    override fun stepRecall() = "Check recall"
    override fun recallDetail(found: Int, total: Int) = "$found of $total facts recalled"
    override fun overflowDetail(promptTokens: Int, budgetTokens: Int) = "prompt ~$promptTokens tok over the $budgetTokens tok budget"
}

class AndroidMemoryTexts(private val resources: Resources) : MemoryTexts {
    override fun stepSummarize(part: Int, parts: Int) = resources.getString(R.string.memory_step_summarize, part, parts)
    override fun stepExtractFacts(part: Int, parts: Int) = resources.getString(R.string.memory_step_facts, part, parts)
    override fun stepAnswer(strategy: MemoryStrategy) = resources.getString(R.string.memory_step_answer, resources.getString(strategy.label))
    override fun stepRecall() = resources.getString(R.string.memory_step_recall)
    override fun recallDetail(found: Int, total: Int) = resources.getString(R.string.memory_recall_detail, found, total)
    override fun overflowDetail(promptTokens: Int, budgetTokens: Int) = resources.getString(R.string.memory_overflow_detail, promptTokens, budgetTokens)
}

/** Share of the budget a helper call (summary, fact extraction) may use for its input. */
private const val HELPER_SHARE = 0.5
private const val SUMMARY_WORDS = 70
private const val ANSWER_TOKENS = 120
private const val SUMMARY_TOKENS = 140
private const val FACTS_TOKENS = 220

/**
 * Runs one memory strategy on a scripted conversation: builds the prompt the strategy would send (calling the
 * engine itself to summarize or extract facts when the strategy needs it), asks the final question and scores
 * how many of the early facts the answer still has. Every step lands in the runner's trace.
 */
class MemoryRunner(
    private val runner: PipelineRunner,
    private val texts: MemoryTexts = EnglishMemoryTexts,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun run(strategy: MemoryStrategy, scenario: MemoryScenario, tier: InferenceTier, budgetTokens: Int): StrategyResult {
        val started = clock()
        var calls = 0
        fun failure(message: String, prompt: Int = 0) = StrategyResult(strategy, prompt, budgetTokens, 0.0, "", clock() - started, calls, message)

        val prepared = when (strategy) {
            MemoryStrategy.FULL -> buildFull(scenario.turns, scenario.question)
            MemoryStrategy.WINDOW -> buildWindow(scenario.turns, scenario.question, budgetTokens)
            MemoryStrategy.SUMMARY -> {
                val plan = planSummary(scenario.turns, scenario.question, budgetTokens)
                val groups = chunkTurns(plan.toSummarize, (budgetTokens * HELPER_SHARE).toInt())
                val parts = mutableListOf<String>()
                for ((index, group) in groups.withIndex()) {
                    val result = runner.generate(
                        StepKind.SUMMARIZE, texts.stepSummarize(index + 1, groups.size), tier,
                        listOf(ChatMessage(Role.USER, summarizationPrompt(group, SUMMARY_WORDS))),
                        GenerationConfig(temperature = 0f, maxOutputTokens = SUMMARY_TOKENS),
                    )
                    calls++
                    if (!result.ok) return failure(result.error ?: "error")
                    parts += result.text.trim()
                }
                buildSummarized(plan, parts.joinToString(" "), scenario.question)
            }
            MemoryStrategy.FACTS -> {
                val groups = chunkTurns(scenario.turns, (budgetTokens * HELPER_SHARE).toInt())
                val facts = mutableListOf<String>()
                for ((index, group) in groups.withIndex()) {
                    val result = runner.generate(
                        StepKind.SUMMARIZE, texts.stepExtractFacts(index + 1, groups.size), tier,
                        listOf(ChatMessage(Role.USER, factsPrompt(group))),
                        GenerationConfig(temperature = 0f, maxOutputTokens = FACTS_TOKENS),
                    )
                    calls++
                    if (!result.ok) return failure(result.error ?: "error")
                    facts += parseFacts(result.text)
                }
                val unique = facts.distinct()
                buildWithFacts(selectFacts(unique, scenario.question), planFacts(scenario.turns, scenario.question, budgetTokens), scenario.question)
            }
        }

        val promptTokens = estimateTokens(prepared)
        val answer = runner.generate(
            StepKind.GENERATE, texts.stepAnswer(strategy), tier, prepared,
            GenerationConfig(temperature = 0f, maxOutputTokens = ANSWER_TOKENS),
        )
        calls++
        if (!answer.ok) return failure(answer.error ?: "error", promptTokens)

        val recall = runner.local(StepKind.VALIDATE, texts.stepRecall(), input = answer.text) {
            val score = recallScore(answer.text, scenario.expected)
            LocalOutcome(score, output = null, detail = texts.recallDetail(Math.round(score * scenario.expected.size).toInt(), scenario.expected.size))
        }
        return StrategyResult(strategy, promptTokens, budgetTokens, recall, answer.text, clock() - started, calls)
    }
}
