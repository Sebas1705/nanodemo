package es.sebas1705.nanodemo.feature.lab.refine

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

private enum class StrategiesTab(val label: String) {
    SELF_CONSISTENCY("Autoconsistencia"),
    PROMPTS("Estrategias de prompt"),
}

@Composable
fun StrategiesScreen() {
    var tab by rememberSaveable { mutableStateOf(StrategiesTab.SELF_CONSISTENCY) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)) {
            ChoiceChips(StrategiesTab.entries, tab, { it.label }) { tab = it }
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

private data class Preset(val label: String, val question: String, val expected: String)

private val PRESETS = listOf(
    Preset("Cambio", StrategyTasks.byId("arith-change")!!.question, "29"),
    Preset("Viaje en tren", StrategyTasks.byId("arith-trip")!!.question, "270"),
    Preset("Caramelos", StrategyTasks.byId("arith-candies-es")!!.question, "17"),
    Preset("Capital", StrategyTasks.byId("es-capital")!!.question, "Canberra"),
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
            SectionLabel("Pregunta")
            OutlinedTextField(
                value = question,
                onValueChange = { question = it },
                label = { Text("Una pregunta con respuesta corta") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PRESETS) { preset ->
                    AssistChip(onClick = { question = preset.question; expected = preset.expected }, label = { Text(preset.label) })
                }
            }
            OutlinedTextField(
                value = expected,
                onValueChange = { expected = it },
                label = { Text("Respuesta esperada (opcional)") },
                supportingText = { Text("Con ella se puede decir si votar acertó donde la respuesta voraz falló.") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            SectionLabel("Motor")
            EngineRow("Quien responde", tier, availability, viewModel::selectTier)
            if (!viewModel.supportsTemperature(tier)) {
                Text(
                    "Este motor no admite temperatura: las muestras saldrán casi idénticas y el voto no aportará nada.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DemoColors.TierB,
                )
            }
            if (viewModel.contextOf(tier) != null) {
                Text(
                    "Contexto pequeño: la pregunta se recorta si no cabe junto a la respuesta.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DemoColors.Muted,
                )
            }

            SectionLabel("Muestras: $samples")
            Slider(value = samples.toFloat(), onValueChange = { samples = it.toInt() }, valueRange = 3f..9f, steps = 5)
            SectionLabel("Temperatura: ${String.format(Locale.US, "%.1f", temperature)}")
            Slider(value = temperature, onValueChange = { temperature = it }, valueRange = 0.3f..1.5f)

            SectionLabel("Forma de la respuesta")
            ChoiceChips(AnswerKind.entries, kind, { it.label }) { kind = it }

            RunBar(
                running, canRun = question.isNotBlank(), runLabel = "Muestrear y votar",
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

    ResultCard("Votación", accent = if (vote.winner != null && !vote.tie) DemoColors.TierA else null) {
        if (vote.winner == null) {
            Text("Ninguna muestra dio una respuesta legible.", style = MaterialTheme.typography.bodyMedium, color = DemoColors.Ink)
        } else {
            vote.buckets.forEach { bucket ->
                val winner = bucket.key == vote.winner.key
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "${bucket.display} · ${bucket.count} de ${vote.valid}" + if (winner) "  ← gana" else "",
                        style = readoutStyle,
                        color = if (winner) DemoColors.TierA else DemoColors.Ink,
                    )
                    ShareBar(bucket.count.toFloat() / vote.valid, if (winner) DemoColors.TierA else DemoColors.Muted)
                }
            }
            Text(
                "Acuerdo entre muestras: ${percent(vote.agreement)}" +
                    if (vote.total > vote.valid) " · ${vote.total - vote.valid} sin respuesta legible" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = DemoColors.Ink,
            )
            if (vote.tie) {
                Text(
                    "Empate entre ${vote.tiedKeys.size} respuestas: se decidió a favor de la respuesta voraz si estaba entre ellas, o de la que apareció primero.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DemoColors.TierB,
                )
            }
        }
    }

    result.verdict?.let { verdict ->
        ResultCard("¿Votar ayudó?") {
            Text(verdict.label, style = MaterialTheme.typography.bodyMedium, color = DemoColors.Ink)
            Text(
                "Respuesta voraz: ${result.greedy?.extracted?.display ?: "—"}  ·  Votada: ${vote.winner?.display ?: "—"}",
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
                    "Esperada: $expected  ·  voraz ${mark(result.greedyCorrect)}  ·  voto ${mark(result.votedCorrect)}",
                    style = readoutStyle,
                    color = DemoColors.Muted,
                )
            }
            Text(
                "Con una sola pregunta es una anécdota, no una medida: repítelo con varias para ver si votar mejora de verdad.",
                style = MaterialTheme.typography.bodySmall,
                color = DemoColors.Muted,
            )
        }
    }

    var showReasoning by rememberSaveable { mutableStateOf(false) }
    ResultCard("Muestras") {
        result.greedy?.let { Text("Voraz → ${it.extracted?.display ?: it.error ?: "sin respuesta"}", style = readoutStyle, color = DemoColors.Muted) }
        result.samples.forEachIndexed { i, s ->
            Text("${i + 1} → ${s.extracted?.display ?: s.error ?: "sin respuesta"}", style = readoutStyle, color = DemoColors.Ink)
        }
        TextButton(onClick = { showReasoning = !showReasoning }) { Text(if (showReasoning) "Ocultar razonamientos" else "Ver razonamientos") }
        if (showReasoning) {
            (listOfNotNull(result.greedy) + result.samples).forEach { s ->
                if (s.text.isNotBlank()) MarkdownText(s.text)
            }
        }
    }
    ResultCard("Coste") { Text(result.stats.display(), style = readoutStyle, color = DemoColors.Muted) }
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
                "${StrategyTasks.all.size} tareas (aritmética, lógica, extracción, clasificación y preguntas en español) con " +
                    "comprobación automática, probadas con cada estrategia.",
                style = MaterialTheme.typography.bodyMedium,
                color = DemoColors.Muted,
            )
            SectionLabel("Motores a comparar")
            EngineMultiSelect(tiers, availability, viewModel::toggleTier)
            SectionLabel("Estrategias")
            MultiChips(PromptStrategy.entries, strategies, { it.label }, viewModel::toggleStrategy)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                PromptStrategy.entries.filter { it in strategies }.forEach {
                    Text("${it.label}: ${it.hint}", style = MaterialTheme.typography.bodySmall, color = DemoColors.Muted)
                }
            }
            SectionLabel("Repeticiones")
            ChoiceChips(listOf(1, 2, 3), repetitions, { "$it" }, viewModel::setRepetitions)
            Text(
                if (repetitions > 1) {
                    "Con varias repeticiones se usa temperatura 0.6 y la tabla muestra el rango de aciertos entre repeticiones."
                } else {
                    "Una repetición con temperatura casi cero: comparación determinista."
                },
                style = MaterialTheme.typography.bodySmall,
                color = DemoColors.Muted,
            )
            Text("$planned llamadas al modelo", style = readoutStyle, color = if (planned > 300) DemoColors.TierB else DemoColors.Muted)
            if (planned > 300) {
                Text(
                    "Es una tirada larga en motores locales: se puede cancelar y la tabla conserva lo ya medido.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DemoColors.TierB,
                )
            }
            RunBar(
                running, canRun = tiers.isNotEmpty() && strategies.isNotEmpty(), runLabel = "Medir",
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
    val header = listOf("Estrategia", "Aciertos", "Tokens", "Tiempo")

    tiers.forEach { tier ->
        val ofTier = outcomes.filter { it.tier == tier }
        val byStrategy = statsByStrategy(ofTier)
        val best = bestStrategies(byStrategy)
        val strategies = PromptStrategy.entries.filter { it in byStrategy }
        ResultCard(tier.label()) {
            StatTable(
                header = header,
                rows = strategies.map { byStrategy.getValue(it).row(it.label) },
                highlight = strategies.indices.filter { strategies[it] in best }.toSet(),
                firstWeight = 1.9f,
            )
        }
    }

    if (tiers.size > 1) {
        val byStrategy = statsByStrategy(outcomes)
        val best = bestStrategies(byStrategy)
        val strategies = PromptStrategy.entries.filter { it in byStrategy }
        ResultCard("Todos los motores juntos") {
            StatTable(
                header = header,
                rows = strategies.map { byStrategy.getValue(it).row(it.label) },
                highlight = strategies.indices.filter { strategies[it] in best }.toSet(),
                firstWeight = 1.9f,
            )
        }
    }

    val done = outcomes.size
    val wrong = outcomes.filter { !it.failed && !it.correct }
    Text(
        "$done respuestas medidas. En verde, la estrategia con más aciertos; \"!n\" son llamadas que fallaron y no cuentan. " +
            "Los tokens y el tiempo suman todas las llamadas de la estrategia (paso atrás usa dos).",
        style = MaterialTheme.typography.bodySmall,
        color = DemoColors.Muted,
    )
    var showWrong by rememberSaveable { mutableStateOf(false) }
    if (wrong.isNotEmpty()) {
        ResultCard("Respuestas incorrectas (${wrong.size})") {
            TextButton(onClick = { showWrong = !showWrong }) { Text(if (showWrong) "Ocultar" else "Ver las últimas 30") }
            if (showWrong) {
                wrong.takeLast(30).forEach {
                    Text(
                        "${it.tier.label()} · ${it.strategy.label} · ${it.taskId}: \"${it.answer.take(60)}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = DemoColors.Muted,
                    )
                }
            }
        }
    }
}
