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
import es.sebas1705.nanodemo.feature.lab.ExperimentCategory

val judgeExperiment = Experiment(
    id = "judge",
    category = ExperimentCategory.ARCHITECTURE,
    title = "Un modelo como juez",
    subtitle = "Un motor puntúa respuestas con una rúbrica o las enfrenta por parejas. Mide cuánto se fía uno del juez.",
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
            SectionLabel("Pregunta")
            OutlinedTextField(
                value = question,
                onValueChange = { question = it },
                label = { Text("La pregunta que se respondió") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            SectionLabel("Respuestas a juzgar")
            ChoiceChips(listOf(false, true), fromEngines, { if (it) "De los motores" else "Escritas por mí" }) { fromEngines = it }
            if (fromEngines) {
                Text(
                    "La misma pregunta se ejecuta en cada motor elegido y sus respuestas se juzgan.",
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
                            label = { Text("Respuesta ${index + 1}") },
                            modifier = Modifier.weight(1f),
                            minLines = 2,
                        )
                        if (typed.size > MIN_TYPED) {
                            IconButton(onClick = { typed = typed.filterIndexed { i, _ -> i != index } }) {
                                Icon(Icons.Filled.Close, contentDescription = "Quitar la respuesta ${index + 1}", tint = DemoColors.Muted)
                            }
                        }
                    }
                }
                if (typed.size < MAX_TYPED) {
                    TextButton(onClick = { typed = typed + "" }) { Text("Añadir respuesta") }
                }
            }

            SectionLabel("Cómo juzga")
            ChoiceChips(
                JudgeMode.entries, mode,
                { if (it == JudgeMode.RUBRIC) "Rúbrica 1–5" else "Torneo por parejas" },
            ) { mode = it }
            Text(
                if (mode == JudgeMode.RUBRIC) {
                    "Cada respuesta se puntúa por separado en corrección, completitud y claridad."
                } else {
                    "Todas las parejas se juzgan dos veces con las posiciones cambiadas: si el veredicto cambia con el orden, es sesgo de posición."
                },
                style = MaterialTheme.typography.bodySmall,
                color = DemoColors.Muted,
            )
            EngineRow("Juez", judge, availability, viewModel::selectJudge)
            if (viewModel.contextOf(judge) != null) {
                Text(
                    "El juez tiene un contexto pequeño: las respuestas largas se recortan antes de juzgarlas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DemoColors.Muted,
                )
            }
            if (judgeCalls > 0) {
                Text("$judgeCalls llamadas al juez", style = readoutStyle, color = DemoColors.Muted)
            }

            RunBar(
                running, canRun = question.isNotBlank() && sourceReady, runLabel = "Juzgar",
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
    result.skipped.forEach { ErrorCard("Sin respuesta de $it") }

    if (result.leaderboard.isNotEmpty()) {
        val best = result.leaderboard.maxOf { it.score }
        val maxScore = if (result.mode == JudgeMode.RUBRIC) 5.0 else (2.0 * (result.candidates.size - 1)).coerceAtLeast(1.0)
        ResultCard(if (result.mode == JudgeMode.RUBRIC) "Clasificación (media de la rúbrica)" else "Clasificación del torneo") {
            result.leaderboard.forEach { entry ->
                LeaderRow(entry, result, maxScore, isBest = entry.score == best && best > 0.0)
            }
        }
    }

    if (result.mode == JudgeMode.PAIRWISE && result.pairs.isNotEmpty()) {
        ResultCard("Fiabilidad del juez") {
            val consistency = result.consistency
            Text(
                "Consistencia: ${percent(consistency.ratio)}" +
                    if (consistency.of > 0) " (${consistency.count} de ${consistency.of} parejas con el mismo veredicto en los dos órdenes)" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = DemoColors.Ink,
            )
            result.firstShare?.let {
                Text(
                    "Eligió la posición A en ${percent(it.ratio)} de los veredictos con ganador (lo esperable sin sesgo es ~50%).",
                    style = MaterialTheme.typography.bodySmall,
                    color = DemoColors.Muted,
                )
            }
            val unreadable = result.unreadable
            if (unreadable.count > 0) {
                Text(
                    "${unreadable.count} de ${unreadable.of} veredictos no se pudieron leer y no puntúan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DemoColors.TierB,
                )
            }
            result.pairs.forEach { pair -> PairLine(pair, result.candidates) }
        }
    }

    if (result.leaderboard.isNotEmpty() && result.candidates.any { it.tier != null }) {
        ResultCard("Acuerdo con tus votos") {
            val agreement = humanAgreement(result.leaderboard, votes)
            Text(
                agreement?.let {
                    "El juez ordena igual que tus votos de Comparar en ${it.concordant} de ${it.comparable} parejas de motores (${percent(it.ratio)})."
                } ?: "Todavía no hay votos suficientes en Comparar para contrastar estos motores.",
                style = MaterialTheme.typography.bodyMedium,
                color = DemoColors.Ink,
            )
        }
    }

    if (result.judgeStats.calls > 0 || result.answerStats.calls > 0) {
        ResultCard("Coste") {
            if (result.answerStats.calls > 0) Text("Respuestas: ${result.answerStats.display()}", style = readoutStyle, color = DemoColors.Muted)
            Text("Juez: ${result.judgeStats.display()}", style = readoutStyle, color = DemoColors.Muted)
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
                        result.scores[c.id] == null -> "sin puntuar"
                        else -> String.format(java.util.Locale.US, "%.1f / 5", entry.score)
                    }
                } else {
                    String.format(java.util.Locale.US, "%.1f pts", entry.score)
                },
                style = readoutStyle,
                color = DemoColors.Ink,
            )
        }
        ShareBar((entry.score / maxScore).toFloat(), color)
        if (result.mode == JudgeMode.RUBRIC) {
            result.scores[c.id]?.let { s ->
                Text(
                    Criterion.entries.joinToString(" · ") { "${it.label} ${s[it] ?: "—"}" },
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
    val outcome = pair.runs.joinToString(" · ") { run ->
        when (run.pick) {
            Pick.FIRST -> "A (${label(run.firstId)})"
            Pick.SECOND -> "B (${label(run.secondId)})"
            Pick.TIE -> "empate"
            null -> "ilegible"
        }
    }
    val flag = when (pair.consistent) {
        true -> "✓"
        false -> "⚠ cambia con el orden"
        null -> ""
    }
    Text(
        "${label(pair.a)} vs ${label(pair.b)}: $outcome $flag".trim(),
        style = MaterialTheme.typography.bodySmall,
        color = if (pair.consistent == false) DemoColors.TierB else DemoColors.Muted,
    )
}
