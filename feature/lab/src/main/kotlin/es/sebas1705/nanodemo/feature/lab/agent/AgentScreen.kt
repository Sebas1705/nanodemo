package es.sebas1705.nanodemo.feature.lab.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import es.sebas1705.nanodemo.core.designsystem.AdaptiveGrid
import es.sebas1705.nanodemo.core.designsystem.AppCard
import es.sebas1705.nanodemo.core.designsystem.ErrorCard
import es.sebas1705.nanodemo.core.designsystem.MarkdownText
import es.sebas1705.nanodemo.core.designsystem.SectionLabel
import es.sebas1705.nanodemo.core.designsystem.TierAvatar
import es.sebas1705.nanodemo.core.designsystem.TierChip
import es.sebas1705.nanodemo.core.designsystem.channelLetter
import es.sebas1705.nanodemo.core.designsystem.label
import es.sebas1705.nanodemo.core.designsystem.rememberScreenSizeClass
import es.sebas1705.nanodemo.core.designsystem.signalColor
import es.sebas1705.nanodemo.core.designsystem.tierColumns
import es.sebas1705.nanodemo.core.ui.DemoColors
import es.sebas1705.nanodemo.core.ui.readoutStyle
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.feature.lab.Experiment
import es.sebas1705.nanodemo.feature.lab.R
import es.sebas1705.nanodemo.feature.lab.ExperimentCategory
import es.sebas1705.nanodemo.feature.lab.common.LabTierSelector
import es.sebas1705.nanodemo.feature.lab.common.isUsable
import es.sebas1705.nanodemo.feature.lab.common.usableTiers
import es.sebas1705.nanodemo.feature.lab.trace.PipelineTraceView
import es.sebas1705.nanodemo.feature.lab.trace.formatDuration

val agentExperiment = Experiment(
    id = "agent",
    category = ExperimentCategory.ARCHITECTURE,
    title = R.string.lab_agent_title,
    subtitle = R.string.lab_agent_subtitle,
    icon = Icons.Filled.Build,
    content = { AgentScreen() },
)

private val SUGGESTED_TASKS = listOf(
    "¿Cuánto es el 17% de 2350?",
    "¿Qué hora es ahora mismo en Tokio?",
    "¿Cuál es mi nivel de batería?",
    "Convierte 72 grados Fahrenheit a Celsius.",
    "Guarda la nota «comprar leche» y después léeme mis notas.",
    "Mira cuánta batería tengo y calcula cuántos puntos me faltan para el 100%.",
    "¿Cuál es la capital de Francia?",
)

private val STEP_CHOICES = listOf(4, 6, 8, 10)

@Composable
fun AgentScreen(viewModel: AgentViewModel = hiltViewModel()) {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    Column(
        modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 760.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ModeSwitch(mode) { mode = it }
            if (mode == 0) AgentRunPanel(viewModel) else BenchmarkPanel(viewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSwitch(selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf("Agente", "Fiabilidad")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, text ->
            SegmentedButton(
                selected = selected == index,
                onClick = { onSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index, labels.size),
            ) { Text(text) }
        }
    }
}

@Composable
private fun AgentRunPanel(viewModel: AgentViewModel) {
    val availability by viewModel.availability.collectAsState()
    val tier by viewModel.tier.collectAsState()
    val trace by viewModel.trace.collectAsState()
    val result by viewModel.result.collectAsState()
    val running by viewModel.running.collectAsState()
    val enabled by viewModel.enabledTools.collectAsState()
    val maxSteps by viewModel.maxSteps.collectAsState()
    val noteCount by viewModel.noteCount.collectAsState()
    var task by rememberSaveable { mutableStateOf("") }

    SectionLabel("Motor")
    LabTierSelector(tier, availability, viewModel::selectTier)

    OutlinedTextField(
        value = task,
        onValueChange = { task = it },
        label = { Text("Tarea") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(SUGGESTED_TASKS) { suggestion ->
            AssistChip(onClick = { task = suggestion }, label = { Text(suggestion, maxLines = 1) })
        }
    }

    AppCard(Modifier.fillMaxWidth()) {
        SectionLabel("Herramientas")
        viewModel.toolOptions.forEach { option ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Switch(checked = option.name in enabled, onCheckedChange = { viewModel.setToolEnabled(option.name, it) })
                Column(Modifier.weight(1f)) {
                    Text(option.name, style = readoutStyle, color = DemoColors.Ink)
                    Text(option.description, style = MaterialTheme.typography.bodySmall, color = DemoColors.Muted)
                }
            }
        }
        if (!viewModel.webSearchConfigured) {
            Text(
                "web_search no está disponible: falta configurar BRAVE_SEARCH_API_KEY.",
                style = MaterialTheme.typography.bodySmall,
                color = DemoColors.Muted,
            )
        }
        if (noteCount > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Notas guardadas: $noteCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = DemoColors.Muted,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = viewModel::clearNotes) { Text("Borrar notas") }
            }
        }
        viewModel.contextWarning(tier, enabled)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = DemoColors.TierB)
        }
    }

    AppCard(Modifier.fillMaxWidth()) {
        SectionLabel("Pasos máximos")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(STEP_CHOICES) { n ->
                FilterChip(selected = maxSteps == n, onClick = { viewModel.setMaxSteps(n) }, label = { Text("$n") })
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = { viewModel.run(task) },
            enabled = task.isNotBlank() && !running && availability.isUsable(tier),
        ) { Text(if (running) "Trabajando…" else "Ejecutar") }
        if (running) OutlinedButton(onClick = viewModel::cancel) { Text("Cancelar") }
    }
    if (!availability.isUsable(tier)) {
        Text("Este motor no está disponible ahora mismo: elige otro.", style = MaterialTheme.typography.bodySmall, color = DemoColors.Muted)
    }

    PipelineTraceView(trace)

    result?.let { ResultCard(it) }
}

@Composable
private fun ResultCard(result: AgentRunResult) {
    if (result.succeeded) {
        AppCard(Modifier.fillMaxWidth(), accent = result.tier.signalColor) {
            MarkdownText(result.answer)
            Text(runReadout(result), style = readoutStyle, color = DemoColors.Muted)
        }
    } else {
        ErrorCard(failureMessage(result))
        if (result.answer.isNotBlank()) {
            AppCard(Modifier.fillMaxWidth()) {
                SectionLabel("Lo último que se obtuvo")
                MarkdownText(result.answer)
            }
        }
        Text(runReadout(result), style = readoutStyle, color = DemoColors.Muted)
    }
}

private fun runReadout(result: AgentRunResult): String = buildList {
    add("${result.steps}/${result.maxSteps} pasos")
    add("${result.toolCalls.size} ${if (result.toolCalls.size == 1) "herramienta" else "herramientas"}")
    add(formatDuration(result.durationMs))
    if (result.tokens > 0) add("~${result.tokens} tok")
    if (result.parseFailures > 0) add("${result.parseFailures} fallos de formato")
}.joinToString(" · ")

private fun failureMessage(result: AgentRunResult): String = when (result.outcome) {
    AgentOutcome.ANSWERED -> ""
    AgentOutcome.MAX_STEPS -> "El agente agotó los ${result.maxSteps} pasos sin dar una respuesta final."
    AgentOutcome.TIMEOUT -> "Se agotó el presupuesto de tiempo antes de terminar."
    AgentOutcome.LOOP -> "El agente repitió la misma acción y se detuvo para no dar vueltas."
    AgentOutcome.FORMAT_FAILURE -> "El modelo no consigue seguir el formato Pensamiento/Acción: se detuvo tras varios intentos."
    AgentOutcome.MODEL_ERROR -> "El motor falló: ${result.error ?: "error desconocido"}"
}

@Composable
private fun BenchmarkPanel(viewModel: AgentViewModel) {
    val availability by viewModel.availability.collectAsState()
    val state by viewModel.benchmark.collectAsState()
    val trace by viewModel.trace.collectAsState()
    val picked by viewModel.benchTiers.collectAsState()
    val usable = availability.usableTiers()
    val selected = (picked ?: usable.toSet()).filter { it in usable }
    val cases = BenchmarkSuite.cases

    Text(
        "Las mismas ${cases.size} tareas en cada motor, con comprobaciones automáticas. El móvil es simulado " +
            "(batería ${BenchmarkEnvironment.BATTERY_PERCENT}%) para que la respuesta correcta sea siempre la misma.",
        style = MaterialTheme.typography.bodyMedium,
        color = DemoColors.Muted,
    )

    SectionLabel("Motores a medir")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(InferenceTier.entries) { tier ->
            val isUsable = tier in usable
            TierChip(
                tier = tier,
                selected = tier in selected,
                onClick = { if (isUsable && !state.running) viewModel.toggleBenchTier(tier, usable) },
                modifier = Modifier.alpha(if (isUsable) 1f else 0.4f),
            )
        }
    }
    if (usable.isEmpty()) {
        Text("Ningún motor está disponible ahora mismo.", style = MaterialTheme.typography.bodySmall, color = DemoColors.Muted)
    }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = viewModel::runBenchmark, enabled = !state.running && selected.isNotEmpty()) {
            Text(if (state.running) "Midiendo…" else "Ejecutar ${cases.size} tareas × ${selected.size} motor(es)")
        }
        if (state.running) OutlinedButton(onClick = viewModel::cancel) { Text("Cancelar") }
    }

    if (state.running) {
        AppCard(Modifier.fillMaxWidth()) {
            Text(
                "${state.currentTier?.label().orEmpty()} · ${state.currentCase.orEmpty()}",
                style = MaterialTheme.typography.bodyMedium,
                color = DemoColors.Ink,
            )
            LinearProgressIndicator(
                progress = { if (state.total == 0) 0f else state.done.toFloat() / state.total },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("${state.done} de ${state.total} ejecuciones", style = readoutStyle, color = DemoColors.Muted)
        }
        PipelineTraceView(trace)
    }

    if (state.outcomes.isNotEmpty()) {
        val summaries = BenchmarkAggregator.summarize(state.outcomes)
        SectionLabel("Resultado por motor")
        AdaptiveGrid(summaries, rememberScreenSizeClass().tierColumns, Modifier.fillMaxWidth()) { summary, modifier ->
            SummaryCard(summary, modifier)
        }
        SectionLabel("Detalle por tarea")
        cases.forEach { case ->
            val rows = state.outcomes.filter { it.case.id == case.id }
            if (rows.isNotEmpty()) CaseRow(case, rows)
        }
    }
}

@Composable
private fun SummaryCard(summary: TierSummary, modifier: Modifier) {
    AppCard(modifier, accent = summary.tier.signalColor) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TierAvatar(summary.tier, 28.dp)
            Text(summary.tier.label(), style = MaterialTheme.typography.titleSmall, color = DemoColors.Ink, modifier = Modifier.weight(1f))
        }
        Text("${(summary.successRate * 100).toInt()}%", style = MaterialTheme.typography.headlineMedium, color = DemoColors.Ink)
        Text("${summary.passed} de ${summary.runs} tareas correctas", style = readoutStyle, color = DemoColors.Muted)
        Readout("pasos de media", "%.1f".format(summary.avgSteps))
        Readout("fallos de formato", "${summary.parseFailures}")
        Readout("errores de herramienta", "${summary.toolErrors}")
        Readout("bucles", "${summary.loops}")
        Readout("tiempo medio", formatDuration(summary.avgDurationMs))
    }
}

@Composable
private fun Readout(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = DemoColors.Muted, modifier = Modifier.weight(1f))
        Text(value, style = readoutStyle, color = DemoColors.Ink)
    }
}

@Composable
private fun CaseRow(case: BenchmarkCase, outcomes: List<BenchmarkOutcome>) {
    var expanded by remember { mutableStateOf(false) }
    val anyFailure = outcomes.any { !it.passed }
    AppCard(
        Modifier.fillMaxWidth().then(if (anyFailure) Modifier.clickable { expanded = !expanded } else Modifier),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(case.title, style = MaterialTheme.typography.bodyMedium, color = DemoColors.Ink, modifier = Modifier.weight(1f))
            outcomes.forEach { o ->
                Text(
                    "${o.tier.channelLetter()} ${if (o.passed) "✓" else "✗"}",
                    style = readoutStyle,
                    color = if (o.passed) DemoColors.TierA else DemoColors.Danger,
                )
            }
        }
        if (expanded) {
            outcomes.filter { !it.passed }.forEach { o ->
                Text(
                    "[${o.tier.channelLetter()}] ${o.failureReason.orEmpty()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = DemoColors.Muted,
                )
            }
        }
    }
}
