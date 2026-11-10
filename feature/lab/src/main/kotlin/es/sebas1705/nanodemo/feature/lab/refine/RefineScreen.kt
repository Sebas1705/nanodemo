package es.sebas1705.nanodemo.feature.lab.refine

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
import es.sebas1705.nanodemo.feature.lab.ExperimentCategory
import es.sebas1705.nanodemo.feature.lab.trace.formatDuration

val refineExperiment = Experiment(
    id = "refine",
    category = ExperimentCategory.ARCHITECTURE,
    title = "Planificar, ejecutar y criticar",
    subtitle = "Un motor planifica, otro redacta y un crítico revisa hasta aceptar. Cada rol puede usar un motor distinto.",
    icon = Icons.Filled.AutoFixHigh,
    content = { RefineScreen() },
)

private val EXAMPLES = listOf(
    "Explica la fotosíntesis en tres frases para un niño de 8 años.",
    "Escribe un correo cortés para pedir una semana más de plazo en una entrega.",
    "Dame tres ventajas y tres riesgos de ejecutar un modelo de IA en el móvil.",
)

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
            SectionLabel("Tarea")
            OutlinedTextField(
                value = task,
                onValueChange = { task = it },
                label = { Text("¿Qué quieres que redacte el trabajador?") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(EXAMPLES) { example ->
                    AssistChip(onClick = { task = example }, label = { Text(example.take(34) + "…") })
                }
            }

            SectionLabel("Roles y motores")
            RefineRole.entries.forEach { role ->
                EngineRow(roleTitle(role), setup.tierOf(role), availability) { viewModel.selectTier(role, it) }
            }
            if (RefineRole.entries.any { viewModel.contextOf(setup.tierOf(it)) != null }) {
                Text(
                    "Alguno de los motores elegidos tiene un contexto pequeño: los textos intermedios se recortan para que quepan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DemoColors.Muted,
                )
            }

            SectionLabel("Rondas de revisión como máximo")
            ChoiceChips(listOf(0, 1, 2, 3), setup.maxRevisions, { if (it == 0) "Ninguna" else "$it" }, viewModel::setMaxRevisions)

            RunBar(running, canRun = task.isNotBlank(), runLabel = "Ejecutar", onRun = { viewModel.run(task) }, onCancel = viewModel::cancel)
            ProgressBlock(progress)
        },
        results = {
            error?.let { ErrorCard(it) }
            TailTrace(trace, maxSteps = 16)
            RefineResultView(result, setup)
        },
    )
}

private fun roleTitle(role: RefineRole) = when (role) {
    RefineRole.PLANNER -> "Planificador: escribe el plan"
    RefineRole.WORKER -> "Trabajador: redacta y reescribe"
    RefineRole.CRITIC -> "Crítico: acepta o pide cambios"
}

private fun roleName(role: RefineRole) = when (role) {
    RefineRole.PLANNER -> "Planificador"
    RefineRole.WORKER -> "Trabajador"
    RefineRole.CRITIC -> "Crítico"
}

@Composable
private fun RefineResultView(result: RefineResult, setup: RefineSetup) {
    if (result.plan.isEmpty() && result.drafts.isEmpty() && result.stop == null) return

    result.stop?.let { stop ->
        when (stop) {
            RefineStop.FAILED -> ErrorCard(result.error ?: "Un motor falló.")
            else -> ResultCard(
                title = "Resultado",
                accent = if (stop == RefineStop.ACCEPTED) DemoColors.TierA else null,
            ) {
                Text(stopMessage(stop, result.drafts.size), style = MaterialTheme.typography.bodyMedium, color = DemoColors.Ink)
            }
        }
    }

    if (result.plan.isNotEmpty()) {
        ResultCard("Plan") { MarkdownText(result.plan) }
    }

    result.drafts.forEach { draft -> DraftCard(draft, isLast = draft === result.drafts.last() && result.stop != null) }

    if (result.drafts.size > 1) {
        val first = result.drafts.first()
        val last = result.drafts.last()
        ResultCard("Qué cambió de v1 a v${last.version}") {
            val whole = diffDrafts(first.text, last.text)
            Text(
                "${whole.added} añadidas · ${whole.removed} quitadas · ${(whole.similarity * 100).toInt()}% igual · " +
                    "${if (whole.lengthDelta >= 0) "+" else ""}${whole.lengthDelta} caracteres",
                style = readoutStyle,
                color = DemoColors.Muted,
            )
            DiffView(whole)
        }
    }

    if (result.stats.isNotEmpty()) {
        ResultCard("Coste por rol") {
            StatTable(
                header = listOf("Rol", "Motor", "Llamadas", "Tiempo", "Tokens"),
                rows = RefineRole.entries.mapNotNull { role ->
                    result.stats[role]?.let { s ->
                        listOf(roleName(role), setup.tierOf(role).label(), "${s.calls}", formatDuration(s.latencyMs), "~${s.tokens}")
                    }
                } + listOf(
                    result.total.let { listOf("Total", "", "${it.calls}", formatDuration(it.latencyMs), "~${it.tokens}") },
                ),
                firstWeight = 1.5f,
            )
        }
    }
}

private fun stopMessage(stop: RefineStop, drafts: Int) = when (stop) {
    RefineStop.ACCEPTED -> "El crítico aceptó la versión $drafts."
    RefineStop.MAX_REVISIONS -> "Se agotaron las rondas de revisión y el crítico seguía viendo problemas en la versión $drafts."
    RefineStop.CRITIC_UNREADABLE -> "No se pudo leer la crítica, así que se detuvo con la versión $drafts en vez de adivinar."
    RefineStop.FAILED -> "Un motor falló."
}

@Composable
private fun DraftCard(draft: RefineDraft, isLast: Boolean) {
    ResultCard(
        title = "Versión ${draft.version}" + if (isLast) " (final)" else "",
        accent = if (isLast && draft.critique?.verdict == Verdict.ACCEPT) DemoColors.TierA else null,
    ) {
        draft.change?.let { change ->
            Text(
                "Respecto a v${draft.version - 1}: +${change.added} −${change.removed} · ${(change.similarity * 100).toInt()}% igual",
                style = readoutStyle,
                color = DemoColors.Muted,
            )
        }
        MarkdownText(draft.text)
        draft.critique?.let { critique ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Crítica", style = readoutStyle, color = DemoColors.Muted)
                    Text(
                        when (critique.verdict) {
                            Verdict.ACCEPT -> "aceptada"
                            Verdict.REVISE -> "pide cambios"
                            Verdict.UNKNOWN -> "ilegible"
                        },
                        style = readoutStyle,
                        color = when (critique.verdict) {
                            Verdict.ACCEPT -> DemoColors.TierA
                            Verdict.REVISE -> DemoColors.TierB
                            Verdict.UNKNOWN -> DemoColors.Danger
                        },
                    )
                    if (!critique.structured && critique.verdict != Verdict.UNKNOWN) {
                        Text("(texto libre)", style = MaterialTheme.typography.labelSmall, color = DemoColors.Muted)
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
