package es.sebas1705.nanodemo.feature.lab.refine

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import es.sebas1705.nanodemo.core.designsystem.ErrorCard
import es.sebas1705.nanodemo.core.designsystem.MarkdownText
import es.sebas1705.nanodemo.core.designsystem.SectionLabel
import es.sebas1705.nanodemo.core.designsystem.label
import es.sebas1705.nanodemo.core.ui.DemoColors
import es.sebas1705.nanodemo.core.ui.readoutStyle
import es.sebas1705.nanodemo.feature.lab.Experiment
import es.sebas1705.nanodemo.feature.lab.R
import es.sebas1705.nanodemo.feature.lab.ExperimentCategory
import es.sebas1705.nanodemo.feature.lab.trace.formatDuration

val refineExperiment = Experiment(
    id = "refine",
    category = ExperimentCategory.ARCHITECTURE,
    title = R.string.lab_refine_title,
    subtitle = R.string.lab_refine_subtitle,
    icon = Icons.Filled.AutoFixHigh,
    content = { RefineScreen() },
)

private val EXAMPLES = listOf(R.string.refine_example_1, R.string.refine_example_2, R.string.refine_example_3)

@Composable
fun RefineScreen(viewModel: RefineViewModel = hiltViewModel()) {
    val availability by viewModel.availability.collectAsState()
    val setup by viewModel.setup.collectAsState()
    val trace by viewModel.trace.collectAsState()
    val result by viewModel.result.collectAsState()
    val running by viewModel.running.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val error by viewModel.error.collectAsState()
    var task by rememberSaveable { mutableStateOf("") }

    LabPane(
        controls = {
            SectionLabel(stringResource(R.string.refine_section_task))
            OutlinedTextField(
                value = task,
                onValueChange = { task = it },
                label = { Text(stringResource(R.string.refine_task_label)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(EXAMPLES) { exampleRes ->
                    val example = stringResource(exampleRes)
                    AssistChip(onClick = { task = example }, label = { Text(example.take(34) + "…") })
                }
            }

            SectionLabel(stringResource(R.string.refine_section_roles))
            RefineRole.entries.forEach { role ->
                EngineRow(stringResource(roleHint(role)), setup.tierOf(role), availability) { viewModel.selectTier(role, it) }
            }
            if (RefineRole.entries.any { viewModel.contextOf(setup.tierOf(it)) != null }) {
                Text(
                    stringResource(R.string.refine_small_context_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = DemoColors.Muted,
                )
            }

            SectionLabel(stringResource(R.string.refine_section_rounds))
            ChoiceChips(
                listOf(0, 1, 2, 3), setup.maxRevisions,
                { if (it == 0) stringResource(R.string.refine_rounds_none) else "$it" },
                viewModel::setMaxRevisions,
            )

            RunBar(
                running, canRun = task.isNotBlank(), runLabel = stringResource(R.string.refine_run),
                onRun = { viewModel.run(task) }, onCancel = viewModel::cancel,
            )
            ProgressBlock(progress)
        },
        results = {
            error?.let { ErrorCard(it) }
            TailTrace(trace, maxSteps = 16)
            RefineResultView(result, setup)
        },
    )
}

@StringRes
private fun roleHint(role: RefineRole) = when (role) {
    RefineRole.PLANNER -> R.string.refine_role_planner_hint
    RefineRole.WORKER -> R.string.refine_role_worker_hint
    RefineRole.CRITIC -> R.string.refine_role_critic_hint
}

@StringRes
private fun roleName(role: RefineRole) = when (role) {
    RefineRole.PLANNER -> R.string.refine_role_planner
    RefineRole.WORKER -> R.string.refine_role_worker
    RefineRole.CRITIC -> R.string.refine_role_critic
}

@Composable
private fun RefineResultView(result: RefineResult, setup: RefineSetup) {
    if (result.plan.isEmpty() && result.drafts.isEmpty() && result.stop == null) return

    result.stop?.let { stop ->
        when (stop) {
            RefineStop.FAILED -> ErrorCard(result.error ?: stringResource(R.string.refine_engine_failed))
            else -> ResultCard(
                title = stringResource(R.string.refine_result_title),
                accent = if (stop == RefineStop.ACCEPTED) DemoColors.TierA else null,
            ) {
                Text(
                    stringResource(stopMessage(stop), result.drafts.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = DemoColors.Ink,
                )
            }
        }
    }

    if (result.plan.isNotEmpty()) {
        ResultCard(stringResource(R.string.refine_plan_title)) { MarkdownText(result.plan) }
    }

    result.drafts.forEach { draft -> DraftCard(draft, isLast = draft === result.drafts.last() && result.stop != null) }

    if (result.drafts.size > 1) {
        val first = result.drafts.first()
        val last = result.drafts.last()
        ResultCard(stringResource(R.string.refine_changes_title, last.version)) {
            val whole = diffDrafts(first.text, last.text)
            Text(
                stringResource(
                    R.string.refine_changes_summary,
                    whole.added, whole.removed, (whole.similarity * 100).toInt(), whole.lengthDelta,
                ),
                style = readoutStyle,
                color = DemoColors.Muted,
            )
            DiffView(whole)
        }
    }

    if (result.stats.isNotEmpty()) {
        ResultCard(stringResource(R.string.refine_cost_title)) {
            StatTable(
                header = listOf(
                    stringResource(R.string.refine_col_role),
                    stringResource(R.string.refine_col_engine),
                    stringResource(R.string.refine_col_calls),
                    stringResource(R.string.refine_col_time),
                    stringResource(R.string.refine_col_tokens),
                ),
                rows = RefineRole.entries.mapNotNull { role ->
                    result.stats[role]?.let { s ->
                        listOf(stringResource(roleName(role)), setup.tierOf(role).label(), "${s.calls}", formatDuration(s.latencyMs), "~${s.tokens}")
                    }
                } + listOf(
                    result.total.let {
                        listOf(stringResource(R.string.refine_total), "", "${it.calls}", formatDuration(it.latencyMs), "~${it.tokens}")
                    },
                ),
                firstWeight = 1.5f,
            )
        }
    }
}

@StringRes
private fun stopMessage(stop: RefineStop) = when (stop) {
    RefineStop.ACCEPTED -> R.string.refine_stop_accepted
    RefineStop.MAX_REVISIONS -> R.string.refine_stop_max_revisions
    RefineStop.CRITIC_UNREADABLE -> R.string.refine_stop_critic_unreadable
    RefineStop.FAILED -> R.string.refine_engine_failed
}

@Composable
private fun DraftCard(draft: RefineDraft, isLast: Boolean) {
    ResultCard(
        title = stringResource(if (isLast) R.string.refine_version_final else R.string.refine_version_title, draft.version),
        accent = if (isLast && draft.critique?.verdict == Verdict.ACCEPT) DemoColors.TierA else null,
    ) {
        draft.change?.let { change ->
            Text(
                stringResource(
                    R.string.refine_draft_change,
                    draft.version - 1, change.added, change.removed, (change.similarity * 100).toInt(),
                ),
                style = readoutStyle,
                color = DemoColors.Muted,
            )
        }
        MarkdownText(draft.text)
        draft.critique?.let { critique ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.refine_critique), style = readoutStyle, color = DemoColors.Muted)
                    Text(
                        stringResource(critique.verdict.labelRes()),
                        style = readoutStyle,
                        color = when (critique.verdict) {
                            Verdict.ACCEPT -> DemoColors.TierA
                            Verdict.REVISE -> DemoColors.TierB
                            Verdict.UNKNOWN -> DemoColors.Danger
                        },
                    )
                    if (!critique.structured && critique.verdict != Verdict.UNKNOWN) {
                        Text(stringResource(R.string.refine_free_text), style = MaterialTheme.typography.labelSmall, color = DemoColors.Muted)
                    }
                }
                critique.issues.forEach { issue ->
                    Text("• $issue", style = MaterialTheme.typography.bodySmall, color = DemoColors.Ink)
                }
            }
        }
    }
}

/** Lines of a diff: added in green, removed in red and struck through, unchanged muted. */
@Composable
fun DiffView(change: DraftChange) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        change.lines.forEach { line ->
            val text = buildAnnotatedString {
                when (line.kind) {
                    DiffKind.ADDED -> withStyle(SpanStyle(color = DemoColors.TierA)) { append("+ ${line.text}") }
                    DiffKind.REMOVED -> withStyle(SpanStyle(color = DemoColors.Danger, textDecoration = TextDecoration.LineThrough)) {
                        append("− ${line.text}")
                    }
                    DiffKind.SAME -> withStyle(SpanStyle(color = DemoColors.Muted)) { append("  ${line.text}") }
                }
            }
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}
