package es.sebas1705.nanodemo.feature.lab.refine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import es.sebas1705.nanodemo.core.designsystem.ErrorCard
import es.sebas1705.nanodemo.core.designsystem.MarkdownText
import es.sebas1705.nanodemo.core.designsystem.SectionLabel
import es.sebas1705.nanodemo.core.designsystem.TierAvatar
import es.sebas1705.nanodemo.core.designsystem.signalColor
import es.sebas1705.nanodemo.core.ui.DemoColors
import es.sebas1705.nanodemo.core.ui.readoutStyle
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.VoteTally
import es.sebas1705.nanodemo.feature.lab.Experiment
import es.sebas1705.nanodemo.feature.lab.R
import es.sebas1705.nanodemo.feature.lab.ExperimentCategory

val judgeExperiment = Experiment(
    id = "judge",
    category = ExperimentCategory.ARCHITECTURE,
    title = R.string.lab_judge_title,
    subtitle = R.string.lab_judge_subtitle,
    icon = Icons.Filled.Gavel,
    content = { JudgeScreen() },
)

private const val MAX_TYPED = 5
private const val MIN_TYPED = 2

@Composable
fun JudgeScreen(viewModel: JudgeViewModel = hiltViewModel()) {
    val availability by viewModel.availability.collectAsState()
    val judge by viewModel.judge.collectAsState()
    val engines by viewModel.engines.collectAsState()
    val trace by viewModel.trace.collectAsState()
    val result by viewModel.result.collectAsState()
    val running by viewModel.running.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val error by viewModel.error.collectAsState()
    val votes by viewModel.votes.collectAsState()

    var question by rememberSaveable { mutableStateOf("") }
    var fromEngines by rememberSaveable { mutableStateOf(false) }
    var mode by rememberSaveable { mutableStateOf(JudgeMode.PAIRWISE) }
    var typed by rememberSaveable { mutableStateOf(listOf("", "")) }

    val sourceReady = if (fromEngines) engines.size >= 2 else typed.count { it.isNotBlank() } >= 2
    val answers = if (fromEngines) engines.size else typed.count { it.isNotBlank() }
    val judgeCalls = when {
        answers < 2 -> 0
        mode == JudgeMode.RUBRIC -> answers
        else -> answers * (answers - 1)
    }

    LabPane(
        controls = {
            SectionLabel(stringResource(R.string.judge_section_question))
            OutlinedTextField(
                value = question,
                onValueChange = { question = it },
                label = { Text(stringResource(R.string.judge_question_label)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            SectionLabel(stringResource(R.string.judge_section_answers))
            ChoiceChips(
                listOf(false, true), fromEngines,
                { stringResource(if (it) R.string.judge_source_engines else R.string.judge_source_typed) },
            ) { fromEngines = it }
            if (fromEngines) {
                Text(
                    stringResource(R.string.judge_engines_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = DemoColors.Muted,
                )
                EngineMultiSelect(engines, availability, viewModel::toggleEngine)
            } else {
                typed.forEachIndexed { index, text ->
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { value -> typed = typed.mapIndexed { i, old -> if (i == index) value else old } },
                            label = { Text(stringResource(R.string.judge_answer_label, index + 1)) },
                            modifier = Modifier.weight(1f),
                            minLines = 2,
                        )
                        if (typed.size > MIN_TYPED) {
                            IconButton(onClick = { typed = typed.filterIndexed { i, _ -> i != index } }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.judge_remove_answer, index + 1),
                                    tint = DemoColors.Muted,
                                )
                            }
                        }
                    }
                }
                if (typed.size < MAX_TYPED) {
                    TextButton(onClick = { typed = typed + "" }) { Text(stringResource(R.string.judge_add_answer)) }
                }
            }

            SectionLabel(stringResource(R.string.judge_section_how))
            ChoiceChips(
                JudgeMode.entries, mode,
                { stringResource(if (it == JudgeMode.RUBRIC) R.string.judge_mode_rubric else R.string.judge_mode_pairwise) },
            ) { mode = it }
            Text(
                stringResource(if (mode == JudgeMode.RUBRIC) R.string.judge_mode_rubric_hint else R.string.judge_mode_pairwise_hint),
                style = MaterialTheme.typography.bodySmall,
                color = DemoColors.Muted,
            )
            EngineRow(stringResource(R.string.judge_engine_judge), judge, availability, viewModel::selectJudge)
            if (viewModel.contextOf(judge) != null) {
                Text(
                    stringResource(R.string.judge_small_context_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = DemoColors.Muted,
                )
            }
            if (judgeCalls > 0) {
                Text(pluralStringResource(R.plurals.judge_calls, judgeCalls, judgeCalls), style = readoutStyle, color = DemoColors.Muted)
            }

            RunBar(
                running, canRun = question.isNotBlank() && sourceReady, runLabel = stringResource(R.string.judge_run),
                onRun = {
                    val source = if (fromEngines) {
                        JudgeSource.Engines(InferenceTier.entries.filter { it in engines })
                    } else {
                        JudgeSource.Typed(typed)
                    }
                    viewModel.run(question, source, mode)
                },
                onCancel = viewModel::cancel,
            )
            ProgressBlock(progress)
        },
        results = {
            error?.let { ErrorCard(it) }
            TailTrace(trace, maxSteps = 12)
            JudgeResultView(result, votes)
        },
    )
}

@Composable
private fun JudgeResultView(result: JudgeResult, votes: Map<InferenceTier, VoteTally>) {
    if (result.candidates.isEmpty() && result.error == null) return
    result.error?.let { ErrorCard(it) }
    result.skipped.forEach { ErrorCard(stringResource(R.string.judge_no_answer_from, it)) }

    if (result.leaderboard.isNotEmpty()) {
        val best = result.leaderboard.maxOf { it.score }
        val maxScore = if (result.mode == JudgeMode.RUBRIC) 5.0 else (2.0 * (result.candidates.size - 1)).coerceAtLeast(1.0)
        ResultCard(
            stringResource(if (result.mode == JudgeMode.RUBRIC) R.string.judge_leaderboard_rubric else R.string.judge_leaderboard_tournament),
        ) {
            result.leaderboard.forEach { entry ->
                LeaderRow(entry, result, maxScore, isBest = entry.score == best && best > 0.0)
            }
        }
    }

    if (result.mode == JudgeMode.PAIRWISE && result.pairs.isNotEmpty()) {
        ResultCard(stringResource(R.string.judge_reliability_title)) {
            val consistency = result.consistency
            Text(
                if (consistency.of > 0) {
                    pluralStringResource(
                        R.plurals.judge_consistency_pairs, consistency.of,
                        consistency.count, consistency.of, percent(consistency.ratio),
                    )
                } else {
                    stringResource(R.string.judge_consistency, percent(consistency.ratio))
                },
                style = MaterialTheme.typography.bodyMedium,
                color = DemoColors.Ink,
            )
            result.firstShare?.let {
                Text(
                    stringResource(R.string.judge_first_share, percent(it.ratio)),
                    style = MaterialTheme.typography.bodySmall,
                    color = DemoColors.Muted,
                )
            }
            val unreadable = result.unreadable
            if (unreadable.count > 0) {
                Text(
                    pluralStringResource(R.plurals.judge_unreadable, unreadable.of, unreadable.count, unreadable.of),
                    style = MaterialTheme.typography.bodySmall,
                    color = DemoColors.TierB,
                )
            }
            result.pairs.forEach { pair -> PairLine(pair, result.candidates) }
        }
    }

    if (result.leaderboard.isNotEmpty() && result.candidates.any { it.tier != null }) {
        ResultCard(stringResource(R.string.judge_agreement_title)) {
            val agreement = humanAgreement(result.leaderboard, votes)
            Text(
                agreement?.let {
                    pluralStringResource(R.plurals.judge_agreement, it.comparable, it.concordant, it.comparable, percent(it.ratio))
                } ?: stringResource(R.string.judge_agreement_none),
                style = MaterialTheme.typography.bodyMedium,
                color = DemoColors.Ink,
            )
        }
    }

    if (result.judgeStats.calls > 0 || result.answerStats.calls > 0) {
        ResultCard(stringResource(R.string.judge_cost_title)) {
            if (result.answerStats.calls > 0) {
                Text(
                    stringResource(R.string.judge_cost_answers, result.answerStats.display()),
                    style = readoutStyle, color = DemoColors.Muted,
                )
            }
            Text(stringResource(R.string.judge_cost_judge, result.judgeStats.display()), style = readoutStyle, color = DemoColors.Muted)
        }
    }

    result.candidates.forEach { c ->
        ResultCard(c.label) { MarkdownText(c.text) }
    }
}

@Composable
private fun LeaderRow(entry: LeaderEntry, result: JudgeResult, maxScore: Double, isBest: Boolean) {
    val c = entry.candidate
    val color = c.tier?.signalColor ?: DemoColors.TierA
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${entry.rank}.", style = readoutStyle, color = if (isBest) DemoColors.TierA else DemoColors.Muted)
            c.tier?.let { TierAvatar(it, 22.dp) }
            Text(c.label, style = MaterialTheme.typography.bodyMedium, color = DemoColors.Ink, modifier = Modifier.weight(1f))
            Text(
                if (result.mode == JudgeMode.RUBRIC) {
                    when {
                        c.id !in result.scores -> "…"
                        result.scores[c.id] == null -> stringResource(R.string.judge_unscored)
                        else -> stringResource(R.string.judge_score_rubric, String.format(java.util.Locale.US, "%.1f", entry.score))
                    }
                } else {
                    stringResource(R.string.judge_score_points, String.format(java.util.Locale.US, "%.1f", entry.score))
                },
                style = readoutStyle,
                color = DemoColors.Ink,
            )
        }
        ShareBar((entry.score / maxScore).toFloat(), color)
        if (result.mode == JudgeMode.RUBRIC) {
            result.scores[c.id]?.let { s ->
                Text(
                    Criterion.entries.map { "${stringResource(it.label)} ${s[it] ?: "—"}" }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = DemoColors.Muted,
                )
            }
        }
    }
}

@Composable
private fun PairLine(pair: PairResult, candidates: List<Candidate>) {
    fun label(id: Int) = candidates.firstOrNull { it.id == id }?.label ?: "#$id"
    val outcome = pair.runs.map { run ->
        when (run.pick) {
            Pick.FIRST -> stringResource(R.string.judge_pick_first, label(run.firstId))
            Pick.SECOND -> stringResource(R.string.judge_pick_second, label(run.secondId))
            Pick.TIE -> stringResource(R.string.judge_pick_tie)
            null -> stringResource(R.string.judge_pick_unreadable)
        }
    }.joinToString(" · ")
    val flag = when (pair.consistent) {
        true -> "✓"
        false -> stringResource(R.string.judge_pair_flipped)
        null -> ""
    }
    Text(
        "${label(pair.a)} vs ${label(pair.b)}: $outcome $flag".trim(),
        style = MaterialTheme.typography.bodySmall,
        color = if (pair.consistent == false) DemoColors.TierB else DemoColors.Muted,
    )
}
