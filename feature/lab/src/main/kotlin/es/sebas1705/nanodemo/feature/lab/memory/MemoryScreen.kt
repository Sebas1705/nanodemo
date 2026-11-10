package es.sebas1705.nanodemo.feature.lab.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
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
import es.sebas1705.nanodemo.feature.lab.common.LabTierSelector
import es.sebas1705.nanodemo.feature.lab.common.SmallNote
import es.sebas1705.nanodemo.feature.lab.common.isUsable
import es.sebas1705.nanodemo.feature.lab.common.passRateColor
import es.sebas1705.nanodemo.feature.lab.trace.PipelineTraceView
import es.sebas1705.nanodemo.feature.lab.trace.formatDuration
import java.util.Locale

val memoryExperiment = Experiment(
    id = "memory",
    category = ExperimentCategory.ARCHITECTURE,
    title = R.string.lab_memory_title,
    subtitle = R.string.lab_memory_subtitle,
    icon = Icons.Filled.Memory,
    content = { MemoryScreen() },
)

@Composable
fun MemoryScreen(viewModel: MemoryViewModel = hiltViewModel()) {
    val availability by viewModel.availability.collectAsState()
    val tier by viewModel.tier.collectAsState()
    val scenario by viewModel.scenario.collectAsState()
    val override by viewModel.budgetOverride.collectAsState()
    val results by viewModel.results.collectAsState()
    val running by viewModel.running.collectAsState()
    val trace by viewModel.trace.collectAsState()

    LaunchedEffect(availability) { viewModel.applyDefaultIfNeeded(availability) }

    val budget = viewModel.budgetTokens(tier, override)
    val fullTokens = estimateTokens(buildFull(scenario.turns, scenario.question))

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.memory_intro), style = MaterialTheme.typography.bodyMedium, color = DemoColors.Muted)

        AppCard(Modifier.fillMaxWidth()) {
            SectionLabel(stringResource(R.string.memory_engine))
            LabTierSelector(tier, availability, viewModel::selectTier)

            SectionLabel(stringResource(R.string.memory_scenario))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MemoryScenarios.all.forEach { s ->
                    FilterChip(
                        selected = s.id == scenario.id,
                        onClick = { viewModel.selectScenario(s) },
                        label = { Text(stringResource(scenarioLabel(s.id))) },
                        enabled = !running,
                    )
                }
            }

            SectionLabel(stringResource(R.string.memory_budget))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = override == null,
                        onClick = { viewModel.selectBudget(null) },
                        label = { Text(stringResource(R.string.memory_budget_auto)) },
                        enabled = !running,
                    )
                }
                items(MEMORY_BUDGET_CHOICES) { tokens ->
                    FilterChip(
                        selected = override == tokens,
                        onClick = { viewModel.selectBudget(tokens) },
                        label = { Text(stringResource(R.string.memory_budget_tokens, tokens)) },
                        enabled = !running,
                    )
                }
            }
            SmallNote(stringResource(R.string.memory_setup_summary, scenario.turns.size, fullTokens, budget))

            if (!availability.isUsable(tier)) SmallNote(stringResource(R.string.memory_no_engine))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = viewModel::run, enabled = !running && availability.isUsable(tier)) {
                    Text(stringResource(if (running) R.string.memory_running else R.string.memory_run))
                }
                if (running) OutlinedButton(onClick = viewModel::cancel) { Text(stringResource(R.string.memory_cancel)) }
            }
        }

        results.forEach { StrategyCard(it) }
        if (results.isNotEmpty()) SmallNote(stringResource(R.string.memory_note))

        PipelineTraceView(trace)
    }
}

private fun scenarioLabel(id: String): Int = when (id) {
    MemoryScenarios.project.id -> R.string.memory_scenario_project
    else -> R.string.memory_scenario_trip
}

@Composable
private fun StrategyCard(result: StrategyResult) {
    AppCard(Modifier.fillMaxWidth()) {
        Text(stringResource(result.strategy.label), style = MaterialTheme.typography.titleMedium, color = DemoColors.Ink)
        Text(stringResource(result.strategy.description), style = MaterialTheme.typography.bodySmall, color = DemoColors.Muted)
        if (result.error != null) {
            Text(stringResource(R.string.memory_result_error, result.error.take(200)), style = MaterialTheme.typography.bodySmall, color = DemoColors.Danger)
            return@AppCard
        }
        BarRow(
            label = stringResource(R.string.memory_result_tokens),
            fraction = result.promptTokens.toFloat() / maxOf(result.promptTokens, result.budgetTokens),
            valueText = "~${result.promptTokens}",
            color = if (result.overflow) DemoColors.Danger else DemoColors.TierC,
        )
        BarRow(
            label = stringResource(R.string.memory_result_recall),
            fraction = result.recall.toFloat(),
            valueText = "%.0f%%".format(Locale.US, result.recall * 100),
            color = passRateColor(result.recall),
        )
        if (result.overflow) {
            Text(stringResource(R.string.memory_result_overflow), style = MaterialTheme.typography.bodySmall, color = DemoColors.Danger)
        }
        Text(
            formatDuration(result.totalMs) + " · " + pluralStringResource(R.plurals.memory_result_calls, result.modelCalls, result.modelCalls),
            style = readoutStyle,
            color = DemoColors.Muted,
        )
        Text(result.answer.trim(), style = MaterialTheme.typography.bodySmall, color = DemoColors.Ink, maxLines = 4)
    }
}
