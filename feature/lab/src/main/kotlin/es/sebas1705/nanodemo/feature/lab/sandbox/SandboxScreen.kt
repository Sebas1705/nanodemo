package es.sebas1705.nanodemo.feature.lab.sandbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import es.sebas1705.nanodemo.core.designsystem.AppCard
import es.sebas1705.nanodemo.core.designsystem.ErrorCard
import es.sebas1705.nanodemo.core.designsystem.MarkdownText
import es.sebas1705.nanodemo.core.designsystem.SectionLabel
import es.sebas1705.nanodemo.core.ui.DemoColors
import es.sebas1705.nanodemo.domain.models.GenerationConfig
import es.sebas1705.nanodemo.feature.lab.Experiment
import es.sebas1705.nanodemo.feature.lab.R
import es.sebas1705.nanodemo.feature.lab.ExperimentCategory
import es.sebas1705.nanodemo.feature.lab.common.LabTierSelector
import es.sebas1705.nanodemo.feature.lab.trace.PipelineTraceView

val sandboxExperiment = Experiment(
    id = "sandbox",
    category = ExperimentCategory.ARCHITECTURE,
    title = R.string.lab_sandbox_title,
    subtitle = R.string.lab_sandbox_subtitle,
    icon = Icons.Filled.PlayArrow,
    content = { SandboxScreen() },
)

@Composable
fun SandboxScreen(viewModel: SandboxViewModel = hiltViewModel()) {
    val availability by viewModel.availability.collectAsState()
    val tier by viewModel.tier.collectAsState()
    val trace by viewModel.trace.collectAsState()
    val outcome by viewModel.outcome.collectAsState()
    val running by viewModel.running.collectAsState()
    val capabilities = viewModel.capabilities(tier)

    LaunchedEffect(availability) { viewModel.applyDefaultIfNeeded(availability) }

    var prompt by rememberSaveable { mutableStateOf("") }
    var systemPrompt by rememberSaveable { mutableStateOf("") }
    var temperature by remember { mutableFloatStateOf(0.7f) }
    var jsonMode by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionLabel("Motor")
        LabTierSelector(tier, availability, viewModel::selectTier)

        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text("Prompt") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )

        AppCard(Modifier.fillMaxWidth()) {
            if (capabilities.systemPrompt) {
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("System prompt (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (capabilities.temperature) {
                Text("Temperatura ${"%.1f".format(temperature)}", style = MaterialTheme.typography.bodyMedium, color = DemoColors.Ink)
                Slider(value = temperature, onValueChange = { temperature = it }, valueRange = 0f..2f)
            }
            if (capabilities.structuredOutput) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Switch(checked = jsonMode, onCheckedChange = { jsonMode = it })
                    Text("Modo JSON", style = MaterialTheme.typography.bodyMedium, color = DemoColors.Ink)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    viewModel.run(
                        prompt,
                        GenerationConfig(
                            temperature = temperature.takeIf { capabilities.temperature },
                            systemPrompt = systemPrompt.takeIf { capabilities.systemPrompt && it.isNotBlank() },
                            jsonMode = jsonMode && capabilities.structuredOutput,
                        ),
                    )
                },
                enabled = prompt.isNotBlank() && !running,
            ) { Text(if (running) "Ejecutando…" else "Ejecutar") }
            if (running) OutlinedButton(onClick = viewModel::cancel) { Text("Cancelar") }
        }

        PipelineTraceView(trace)

        outcome.error?.let { ErrorCard(it) }
        if (outcome.answer.isNotBlank()) {
            AppCard(Modifier.fillMaxWidth()) { MarkdownText(outcome.answer) }
        }
    }
}
