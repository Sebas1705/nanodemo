package es.sebas1705.nanodemo.feature.lab.refine

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import es.sebas1705.nanodemo.core.designsystem.ErrorCard
import es.sebas1705.nanodemo.core.designsystem.MarkdownText
import es.sebas1705.nanodemo.core.designsystem.SectionLabel
import es.sebas1705.nanodemo.core.designsystem.label
import es.sebas1705.nanodemo.core.ui.DemoColors
import es.sebas1705.nanodemo.core.ui.readoutStyle
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.feature.lab.Experiment
import es.sebas1705.nanodemo.feature.lab.R
import es.sebas1705.nanodemo.feature.lab.ExperimentCategory
import es.sebas1705.nanodemo.feature.lab.trace.formatDuration
import java.util.Locale

val strategiesExperiment = Experiment(
    id = "strategies",
    category = ExperimentCategory.ARCHITECTURE,
    title = R.string.lab_strategies_title,
    subtitle = R.string.lab_strategies_subtitle,
    icon = Icons.Filled.Psychology,
    content = { StrategiesScreen() },
)

private enum class StrategiesTab(@StringRes val label: Int) {
    SELF_CONSISTENCY(R.string.strategies_tab_self_consistency),
    PROMPTS(R.string.strategies_tab_prompts),
}

@Composable
fun StrategiesScreen() {
    var tab by rememberSaveable { mutableStateOf(StrategiesTab.SELF_CONSISTENCY) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)) {
            ChoiceChips(StrategiesTab.entries, tab, { stringResource(it.label) }) { tab = it }
        }
        Column(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                StrategiesTab.SELF_CONSISTENCY -> SelfConsistencyPanel()
                StrategiesTab.PROMPTS -> StrategyBenchmarkPanel()
            }
        }
    }
}

// ---------------------------------------------------------------- self-consistency

private data class Preset(@StringRes val label: Int, val question: String, val expected: String)

private val PRESETS = listOf(
    Preset(R.string.strategies_preset_change, StrategyTasks.byId("arith-change")!!.question, "29"),
    Preset(R.string.strategies_preset_train, StrategyTasks.byId("arith-trip")!!.question, "270"),
    Preset(R.string.strategies_preset_candies, StrategyTasks.byId("arith-candies-es")!!.question, "17"),
    Preset(R.string.strategies_preset_capital, StrategyTasks.byId("es-capital")!!.question, "Canberra"),
)

@Composable
private fun SelfConsistencyPanel(viewModel: SelfConsistencyViewModel = hiltViewModel()) {
    val availability by viewModel.availability.collectAsState()
    val tier by viewModel.tier.collectAsState()
    val trace by viewModel.trace.collectAsState()
    val result by viewModel.result.collectAsState()
    val running by viewModel.running.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val error by viewModel.error.collectAsState()

    var question by rememberSaveable { mutableStateOf("") }
    var expected by rememberSaveable { mutableStateOf("") }
    var samples by rememberSaveable { mutableIntStateOf(5) }
    var temperature by rememberSaveable { mutableFloatStateOf(0.8f) }
    var kind by rememberSaveable { mutableStateOf(AnswerKind.AUTO) }

    LabPane(
        controls = {
            SectionLabel(stringResource(R.string.strategies_section_question))
            OutlinedTextField(
                value = question,
                onValueChange = { question = it },
                label = { Text(stringResource(R.string.strategies_question_label)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PRESETS) { preset ->
                    AssistChip(
                        onClick = { question = preset.question; expected = preset.expected },
                        label = { Text(stringResource(preset.label)) },
                    )
                }
            }
            OutlinedTextField(
                value = expected,
                onValueChange = { expected = it },
                label = { Text(stringResource(R.string.strategies_expected_label)) },
                supportingText = { Text(stringResource(R.string.strategies_expected_support)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            SectionLabel(stringResource(R.string.strategies_section_engine))
            EngineRow(stringResource(R.string.strategies_engine_answers), tier, availability, viewModel::selectTier)
            if (!viewModel.supportsTemperature(tier)) {
                Text(
                    stringResource(R.string.strategies_no_temperature),
                    style = MaterialTheme.typography.bodySmall,
                    color = DemoColors.TierB,
                )
            }
            if (viewModel.contextOf(tier) != null) {
                Text(
                    stringResource(R.string.strategies_small_context),
                    style = MaterialTheme.typography.bodySmall,
                    color = DemoColors.Muted,
                )
            }

            SectionLabel(stringResource(R.string.strategies_samples, samples))
            Slider(value = samples.toFloat(), onValueChange = { samples = it.toInt() }, valueRange = 3f..9f, steps = 5)
            SectionLabel(stringResource(R.string.strategies_temperature, String.format(Locale.US, "%.1f", temperature)))
            Slider(value = temperature, onValueChange = { temperature = it }, valueRange = 0.3f..1.5f)

            SectionLabel(stringResource(R.string.strategies_section_kind))
            ChoiceChips(AnswerKind.entries, kind, { stringResource(it.label) }) { kind = it }

            RunBar(
                running, canRun = question.isNotBlank(), runLabel = stringResource(R.string.strategies_run_sample),
                onRun = { viewModel.run(question, samples, temperature, kind, expected) },
                onCancel = viewModel::cancel,
            )
            ProgressBlock(progress)
        },
        results = {
            error?.let { ErrorCard(it) }
            TailTrace(trace, maxSteps = 14)
            SelfConsistencyResultView(result)
        },
    )
}

@Composable
private fun SelfConsistencyResultView(result: SelfConsistencyResult) {
    val vote = result.vote ?: return
    result.error?.let { ErrorCard(it) }

    ResultCard(stringResource(R.string.strategies_vote_title), accent = if (vote.winner != null && !vote.tie) DemoColors.TierA else null) {
        if (vote.winner == null) {
            Text(stringResource(R.string.strategies_vote_none), style = MaterialTheme.typography.bodyMedium, color = DemoColors.Ink)
        } else {
            vote.buckets.forEach { bucket ->
                val winner = bucket.key == vote.winner.key
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(
                            if (winner) R.string.strategies_vote_line_winner else R.string.strategies_vote_line,
                            bucket.display, bucket.count, vote.valid,
                        ),
                        style = readoutStyle,
                        color = if (winner) DemoColors.TierA else DemoColors.Ink,
                    )
                    ShareBar(bucket.count.toFloat() / vote.valid, if (winner) DemoColors.TierA else DemoColors.Muted)
                }
            }
            Text(
                if (vote.total > vote.valid) {
                    stringResource(R.string.strategies_agreement_unreadable, percent(vote.agreement), vote.total - vote.valid)
                } else {
                    stringResource(R.string.strategies_agreement, percent(vote.agreement))
                },
                style = MaterialTheme.typography.bodyMedium,
                color = DemoColors.Ink,
            )
            if (vote.tie) {
                Text(
                    stringResource(R.string.strategies_tie_note, vote.tiedKeys.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = DemoColors.TierB,
                )
            }
        }
    }

    result.verdict?.let { verdict ->
        ResultCard(stringResource(R.string.strategies_helped_title)) {
            Text(stringResource(verdict.label), style = MaterialTheme.typography.bodyMedium, color = DemoColors.Ink)
            Text(
                stringResource(
                    R.string.strategies_greedy_vs_voted,
                    result.greedy?.extracted?.display ?: "—", vote.winner?.display ?: "—",
                ),
                style = readoutStyle,
                color = DemoColors.Muted,
            )
            result.expected?.let { expected ->
                fun mark(ok: Boolean?) = when (ok) {
                    true -> "✓"
                    false -> "✗"
                    null -> "—"
                }
                Text(
                    stringResource(R.string.strategies_expected_line, expected, mark(result.greedyCorrect), mark(result.votedCorrect)),
                    style = readoutStyle,
                    color = DemoColors.Muted,
                )
            }
            Text(
                stringResource(R.string.strategies_anecdote_note),
                style = MaterialTheme.typography.bodySmall,
                color = DemoColors.Muted,
            )
        }
    }

    var showReasoning by rememberSaveable { mutableStateOf(false) }
    val noAnswer = stringResource(R.string.strategies_no_answer)
    ResultCard(stringResource(R.string.strategies_samples_title)) {
        result.greedy?.let {
            Text(
                stringResource(R.string.strategies_greedy_sample, it.extracted?.display ?: it.error ?: noAnswer),
                style = readoutStyle, color = DemoColors.Muted,
            )
        }
        result.samples.forEachIndexed { i, s ->
            Text("${i + 1} → ${s.extracted?.display ?: s.error ?: noAnswer}", style = readoutStyle, color = DemoColors.Ink)
        }
        TextButton(onClick = { showReasoning = !showReasoning }) {
            Text(stringResource(if (showReasoning) R.string.strategies_hide_reasoning else R.string.strategies_show_reasoning))
        }
        if (showReasoning) {
            (listOfNotNull(result.greedy) + result.samples).forEach { s ->
                if (s.text.isNotBlank()) MarkdownText(s.text)
            }
        }
    }
    ResultCard(stringResource(R.string.strategies_cost_title)) { Text(result.stats.display(), style = readoutStyle, color = DemoColors.Muted) }
}

// ---------------------------------------------------------------- strategy benchmark

@Composable
private fun StrategyBenchmarkPanel(viewModel: StrategyBenchmarkViewModel = hiltViewModel()) {
    val availability by viewModel.availability.collectAsState()
    val tiers by viewModel.tiers.collectAsState()
    val strategies by viewModel.strategies.collectAsState()
    val repetitions by viewModel.repetitions.collectAsState()
    val trace by viewModel.trace.collectAsState()
    val outcomes by viewModel.outcomes.collectAsState()
    val running by viewModel.running.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val error by viewModel.error.collectAsState()
    val planned = viewModel.plannedCalls()

    LabPane(
        controls = {
            Text(
                pluralStringResource(R.plurals.strategies_bench_intro, StrategyTasks.all.size, StrategyTasks.all.size),
                style = MaterialTheme.typography.bodyMedium,
                color = DemoColors.Muted,
            )
            SectionLabel(stringResource(R.string.strategies_section_engines))
            EngineMultiSelect(tiers, availability, viewModel::toggleTier)
            SectionLabel(stringResource(R.string.strategies_section_strategies))
            MultiChips(PromptStrategy.entries, strategies, { stringResource(it.label) }, viewModel::toggleStrategy)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                PromptStrategy.entries.filter { it in strategies }.forEach {
                    Text(
                        "${stringResource(it.label)}: ${stringResource(it.hint)}",
                        style = MaterialTheme.typography.bodySmall, color = DemoColors.Muted,
                    )
                }
            }
            SectionLabel(stringResource(R.string.strategies_section_repetitions))
            ChoiceChips(listOf(1, 2, 3), repetitions, { "$it" }, viewModel::setRepetitions)
            Text(
                stringResource(if (repetitions > 1) R.string.strategies_repetitions_many else R.string.strategies_repetitions_one),
                style = MaterialTheme.typography.bodySmall,
                color = DemoColors.Muted,
            )
            Text(
                pluralStringResource(R.plurals.strategies_planned_calls, planned, planned),
                style = readoutStyle, color = if (planned > 300) DemoColors.TierB else DemoColors.Muted,
            )
            if (planned > 300) {
                Text(
                    stringResource(R.string.strategies_long_run_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = DemoColors.TierB,
                )
            }
            RunBar(
                running, canRun = tiers.isNotEmpty() && strategies.isNotEmpty(), runLabel = stringResource(R.string.strategies_run_measure),
                onRun = viewModel::run, onCancel = viewModel::cancel,
            )
            ProgressBlock(progress)
        },
        results = {
            error?.let { ErrorCard(it) }
            TailTrace(trace, maxSteps = 8)
            BenchmarkTables(outcomes)
        },
    )
}

private fun CellStats.accuracyText(): String {
    val acc = accuracy ?: return "—"
    val base = String.format(Locale.US, "%.0f%%", acc * 100)
    val range = spread?.let { String.format(Locale.US, " (%.0f–%.0f)", it.start * 100, it.endInclusive * 100) }.orEmpty()
    return base + range
}

private fun CellStats.row(label: String): List<String> = listOf(
    label,
    accuracyText() + if (errors > 0) " !$errors" else "",
    "~${avgTokens.toInt()}",
    formatDuration(avgLatencyMs.toLong()),
)

@Composable
private fun BenchmarkTables(outcomes: List<TaskOutcome>) {
    if (outcomes.isEmpty()) return
    val tiers = InferenceTier.entries.filter { t -> outcomes.any { it.tier == t } }
    val header = listOf(
        stringResource(R.string.strategies_col_strategy),
        stringResource(R.string.strategies_col_hits),
        stringResource(R.string.strategies_col_tokens),
        stringResource(R.string.strategies_col_time),
    )

    tiers.forEach { tier ->
        val ofTier = outcomes.filter { it.tier == tier }
        val byStrategy = statsByStrategy(ofTier)
        val best = bestStrategies(byStrategy)
        val strategies = PromptStrategy.entries.filter { it in byStrategy }
        ResultCard(tier.label()) {
            StatTable(
                header = header,
                rows = strategies.map { byStrategy.getValue(it).row(stringResource(it.label)) },
                highlight = strategies.indices.filter { strategies[it] in best }.toSet(),
                firstWeight = 1.9f,
            )
        }
    }

    if (tiers.size > 1) {
        val byStrategy = statsByStrategy(outcomes)
        val best = bestStrategies(byStrategy)
        val strategies = PromptStrategy.entries.filter { it in byStrategy }
        ResultCard(stringResource(R.string.strategies_all_engines)) {
            StatTable(
                header = header,
                rows = strategies.map { byStrategy.getValue(it).row(stringResource(it.label)) },
                highlight = strategies.indices.filter { strategies[it] in best }.toSet(),
                firstWeight = 1.9f,
            )
        }
    }

    val done = outcomes.size
    val wrong = outcomes.filter { !it.failed && !it.correct }
    Text(
        pluralStringResource(R.plurals.strategies_bench_footnote, done, done),
        style = MaterialTheme.typography.bodySmall,
        color = DemoColors.Muted,
    )
    var showWrong by rememberSaveable { mutableStateOf(false) }
    if (wrong.isNotEmpty()) {
        ResultCard(stringResource(R.string.strategies_wrong_title, wrong.size)) {
            TextButton(onClick = { showWrong = !showWrong }) {
                Text(stringResource(if (showWrong) R.string.strategies_wrong_hide else R.string.strategies_wrong_show))
            }
            if (showWrong) {
                wrong.takeLast(30).forEach {
                    Text(
                        "${it.tier.label()} · ${stringResource(it.strategy.label)} · ${it.taskId}: \"${it.answer.take(60)}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = DemoColors.Muted,
                    )
                }
            }
        }
    }
}
