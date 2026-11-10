package es.sebas1705.nanodemo.feature.lab.guard

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import es.sebas1705.nanodemo.core.designsystem.AppCard
import es.sebas1705.nanodemo.core.designsystem.SectionLabel
import es.sebas1705.nanodemo.core.ui.DemoColors
import es.sebas1705.nanodemo.core.ui.readoutStyle
import es.sebas1705.nanodemo.feature.lab.Experiment
import es.sebas1705.nanodemo.feature.lab.ExperimentCategory
import es.sebas1705.nanodemo.feature.lab.R
import es.sebas1705.nanodemo.feature.lab.common.BarRow
import es.sebas1705.nanodemo.feature.lab.common.HeatCellBox
import es.sebas1705.nanodemo.feature.lab.common.LabTierSelector
import es.sebas1705.nanodemo.feature.lab.common.SmallNote
import es.sebas1705.nanodemo.feature.lab.common.isUsable
import es.sebas1705.nanodemo.feature.lab.common.passRateColor
import es.sebas1705.nanodemo.feature.lab.trace.PipelineTraceView
import java.util.Locale

val guardExperiment = Experiment(
    id = "guard",
    category = ExperimentCategory.ARCHITECTURE,
    title = R.string.lab_guard_title,
    subtitle = R.string.lab_guard_subtitle,
    icon = Icons.Filled.Shield,
    content = { GuardScreen() },
)

@Composable
fun GuardScreen(viewModel: GuardViewModel = hiltViewModel()) {
    val availability by viewModel.availability.collectAsState()
    val tier by viewModel.tier.collectAsState()
    val payload by viewModel.payload.collectAsState()
    val defense by viewModel.defense.collectAsState()
    val single by viewModel.single.collectAsState()
    val suite by viewModel.suite.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val running by viewModel.running.collectAsState()
    val trace by viewModel.trace.collectAsState()

    LaunchedEffect(availability) { viewModel.applyDefaultIfNeeded(availability) }

    val usable = availability.isUsable(tier)
    val document = remember(payload) { GuardData.documentFor(payload) }
    val detection = remember(document) { InjectionDetector.detect(document) }
    val suiteCalls = GuardData.payloads.size * Defense.entries.size

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.guard_intro), style = MaterialTheme.typography.bodyMedium, color = DemoColors.Muted)

        AppCard(Modifier.fillMaxWidth()) {
            SectionLabel(stringResource(R.string.guard_engine))
            LabTierSelector(tier, availability, viewModel::selectTier)

            SectionLabel(stringResource(R.string.guard_attack))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(GuardData.payloads) { p ->
                    FilterChip(selected = p.id == payload.id, onClick = { viewModel.selectPayload(p) }, label = { Text(stringResource(p.label)) }, enabled = !running)
                }
            }

            SectionLabel(stringResource(R.string.guard_defense))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Defense.entries) { d ->
                    FilterChip(selected = d == defense, onClick = { viewModel.selectDefense(d) }, label = { Text(stringResource(d.label)) }, enabled = !running)
                }
            }
            SmallNote(stringResource(defense.description))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = viewModel::attackOnce, enabled = usable && !running) {
                    Text(stringResource(if (running) R.string.guard_running else R.string.guard_run_one))
                }
                if (running) OutlinedButton(onClick = viewModel::cancel) { Text(stringResource(R.string.guard_cancel)) }
            }
            OutlinedButton(onClick = viewModel::runSuite, enabled = usable && !running) { Text(stringResource(R.string.guard_run_suite)) }
            SmallNote(stringResource(R.string.guard_suite_note, GuardData.payloads.size, Defense.entries.size, suiteCalls))
            progress?.let { (done, total) ->
                LinearProgressIndicator(progress = { done.toFloat() / total }, modifier = Modifier.fillMaxWidth(), color = DemoColors.TierA)
                Text(stringResource(R.string.guard_progress, done, total), style = readoutStyle, color = DemoColors.Muted)
            }
        }

        AppCard(Modifier.fillMaxWidth()) {
            SectionLabel(stringResource(R.string.guard_document))
            Text(document, style = MaterialTheme.typography.bodySmall, color = DemoColors.Ink)
            Text(
                if (detection.findings.isEmpty()) {
                    stringResource(R.string.guard_detector_clean)
                } else {
                    stringResource(R.string.guard_detector_score, detection.score, detection.rules.joinToString(", "))
                },
                style = readoutStyle,
                color = if (detection.flagged) DemoColors.TierB else DemoColors.Muted,
            )
        }

        single?.let { SingleResult(it) }
        if (suite.isNotEmpty()) SuiteResults(suite)

        val report = viewModel.detectorReport
        SmallNote(stringResource(R.string.guard_detector_report, report.flaggedAttacks, report.attacks, report.falsePositives, report.benign))
        SmallNote(stringResource(R.string.guard_disclaimer))

        PipelineTraceView(trace)
    }
}

@Composable
private fun SingleResult(outcome: AttackOutcome) {
    AppCard(Modifier.fillMaxWidth(), accent = if (outcome.leakedShown || outcome.error != null) DemoColors.Danger else DemoColors.TierA) {
        if (outcome.error != null) {
            Text(stringResource(R.string.guard_result_error, outcome.error.take(200)), style = MaterialTheme.typography.bodyMedium, color = DemoColors.Danger)
            return@AppCard
        }
        val verdict = when {
            outcome.leakedShown -> R.string.guard_result_leak
            outcome.leakedRaw -> R.string.guard_result_leak_blocked
            else -> R.string.guard_result_safe
        }
        Text(stringResource(verdict), style = MaterialTheme.typography.titleMedium, color = if (outcome.leakedShown) DemoColors.Danger else DemoColors.TierA)
        if (!outcome.usefulSummary) Text(stringResource(R.string.guard_result_useless), style = MaterialTheme.typography.bodySmall, color = DemoColors.TierB)
        SectionLabel(stringResource(R.string.guard_answer))
        Text(outcome.shownAnswer.trim(), style = MaterialTheme.typography.bodySmall, color = DemoColors.Ink)
    }
}

@Composable
private fun SuiteResults(outcomes: List<AttackOutcome>) {
    val summaries = remember(outcomes) { summarizeByDefense(outcomes) }
    AppCard(Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.guard_suite_results))
        summaries.forEach { s ->
            Text(stringResource(s.defense.label), style = MaterialTheme.typography.bodyMedium, color = DemoColors.Ink)
            // For a leak rate lower is better, so the colour scale is inverted.
            BarRow("", s.leakRate.toFloat(), "%.0f%%".format(Locale.US, s.leakRate * 100), passRateColor(1.0 - s.leakRate))
            Text(stringResource(R.string.guard_suite_useful, Math.round(s.usefulRate * 100).toInt()), style = readoutStyle, color = DemoColors.Muted)
            if (s.errors > 0) Text(stringResource(R.string.guard_suite_errors, s.errors), style = readoutStyle, color = DemoColors.Danger)
        }
    }
    AppCard(Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.guard_matrix))
        val byKey = outcomes.associateBy { it.payloadId to it.defense }
        Column(Modifier.horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GuardData.payloads.forEach { p ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(p.label), style = MaterialTheme.typography.bodySmall, color = DemoColors.Muted, modifier = Modifier.width(150.dp), maxLines = 1)
                    Defense.entries.forEach { d ->
                        val outcome = byKey[p.id to d]
                        val (value, mark) = when {
                            outcome == null -> null to "·"
                            outcome.error != null -> null to "!"
                            outcome.leakedShown -> 0.0 to "✗"
                            else -> 1.0 to "✓"
                        }
                        HeatCellBox(value, mark, 44.dp)
                    }
                }
            }
        }
    }
}
