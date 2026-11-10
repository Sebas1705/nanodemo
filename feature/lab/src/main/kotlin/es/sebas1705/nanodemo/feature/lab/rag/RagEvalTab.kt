package es.sebas1705.nanodemo.feature.lab.rag

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import es.sebas1705.nanodemo.core.designsystem.AdaptiveGrid
import es.sebas1705.nanodemo.core.designsystem.AppCard
import es.sebas1705.nanodemo.core.designsystem.ErrorCard
import es.sebas1705.nanodemo.core.designsystem.SectionLabel
import es.sebas1705.nanodemo.core.designsystem.rememberScreenSizeClass
import es.sebas1705.nanodemo.core.ui.DemoColors
import es.sebas1705.nanodemo.core.ui.readoutStyle
import es.sebas1705.nanodemo.core.designsystem.ScreenSizeClass

/**
 * Evaluate tab: runs the sample questions through retrieval only (no model, so it works with any engine and
 * is fast) and reports hit@1, hit@3, hit@k, MRR and the mean retrieval latency. Each run is kept so settings
 * (chunk size, k, MMR, threshold) can be compared side by side.
 */
@Composable
internal fun RagEvalTab(viewModel: RagViewModel, onGoToDocuments: () -> Unit) {
    val documents by viewModel.documents.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val eval by viewModel.eval.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val sizeClass = rememberScreenSizeClass()

    val indexedDocs = documents.count { it.isIndexed }
    val sampleIndexed = documents.count { it.isIndexed && SampleCorpus.document(it.id) != null }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Mide si la búsqueda encuentra el fragmento que contiene la respuesta, sin llamar a ningún modelo. " +
                "Cambia el tamaño de fragmento (Documentos, reindexando), k, MMR o el umbral y compara las ejecuciones.",
            style = MaterialTheme.typography.bodyMedium, color = DemoColors.Muted,
        )
        if (sampleIndexed == 0) {
            AppCard(Modifier.fillMaxWidth()) {
                Text(
                    if (indexedDocs == 0) "Aún no hay nada indexado." else "Las preguntas de evaluación son del corpus de ejemplo y no está indexado.",
                    style = MaterialTheme.typography.titleSmall, color = DemoColors.Ink,
                )
                Button(onClick = onGoToDocuments) { Text("Ir a Documentos") }
            }
        }

        RetrievalSettingsCard(
            settings = settings,
            onTopK = viewModel::setTopK,
            onMmr = viewModel::setMmr,
            onMinScore = viewModel::setMinScore,
            enabled = !busy,
        )
        Text(
            "Se evaluará con: ${settingsLabel(documents.commonChunkTarget(), settings.retrieval)}",
            style = readoutStyle, color = DemoColors.Ink,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = viewModel::runEvaluation, enabled = !busy && sampleIndexed > 0) {
                Text(if (eval.running) "Evaluando…" else "Evaluar ${SampleCorpus.questions.size} preguntas")
            }
            if (eval.running) OutlinedButton(onClick = viewModel::cancel) { Text("Cancelar") }
        }
        if (eval.running) {
            if (eval.total > 0) {
                LinearProgressIndicator(progress = { eval.done / eval.total.toFloat() }, modifier = Modifier.fillMaxWidth())
                Text("${eval.done} de ${eval.total}", style = readoutStyle, color = DemoColors.Muted)
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        eval.error?.let { ErrorCard(it) }

        eval.latest?.let { latest ->
            ReportTiles(latest.report, sizeClass)
            QuestionRows(latest.evals)
        }
        if (eval.history.size > 1) {
            HistoryTable(eval.history, onClear = viewModel::clearEvalHistory)
        }
    }
}

@Composable
private fun ReportTiles(report: RetrievalReport, sizeClass: ScreenSizeClass) {
    val tiles = listOf(
        Triple("hit@1", report.hitAt1.asPercent(), report.hitAt1),
        Triple("hit@3", report.hitAt3.asPercent(), report.hitAt3),
        Triple("hit@${report.k}", report.hitAtK.asPercent(), report.hitAtK),
        Triple("MRR", report.mrr.format(2), report.mrr),
        Triple("latencia media", report.meanLatencyMs.asDuration(), null),
    )
    SectionLabel("Resultado · ${report.questions} preguntas")
    AdaptiveGrid(items = tiles, columns = if (sizeClass == ScreenSizeClass.COMPACT) 2 else 5, spacing = 10.dp) { (caption, value, ratio), modifier ->
        MetricTile(value, caption, ratio, if (ratio == null) DemoColors.TierC else DemoColors.TierA, modifier)
    }
    if (report.skipped > 0) {
        Text(
            "${report.skipped} preguntas se omitieron porque su documento no está indexado.",
            style = MaterialTheme.typography.bodySmall, color = DemoColors.TierB,
        )
    }
    Text(
        "hit@n: la respuesta está entre los n primeros fragmentos. MRR: media de 1/posición del primer acierto (1 = siempre primero). " +
            "La latencia incluye vectorizar la pregunta y buscar.",
        style = MaterialTheme.typography.bodySmall, color = DemoColors.Muted,
    )
}

@Composable
private fun QuestionRows(evals: List<QuestionEval>) {
    AppCard(Modifier.fillMaxWidth()) {
        Text("Por pregunta", style = MaterialTheme.typography.titleSmall, color = DemoColors.Ink)
        evals.forEach { e ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Text(
                    e.rank?.let { "#$it" } ?: "✗",
                    style = readoutStyle,
                    color = when (e.rank) {
                        1 -> DemoColors.TierA
                        null -> DemoColors.Danger
                        else -> DemoColors.TierB
                    },
                    modifier = Modifier.width(28.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(e.question.question, style = MaterialTheme.typography.bodyMedium, color = DemoColors.Ink)
                    Text(
                        "mejor puntuación ${e.topScore?.format(2) ?: "—"} · ${e.latencyMs.asDuration()}",
                        style = readoutStyle, color = DemoColors.Muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryTable(history: List<EvalRun>, onClear: () -> Unit) {
    val bestMrr = history.maxOf { it.report.mrr }
    AppCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Comparar ejecuciones", style = MaterialTheme.typography.titleSmall, color = DemoColors.Ink, modifier = Modifier.weight(1f))
            TextButton(onClick = onClear) { Text("Borrar") }
        }
        history.forEach { run ->
            val best = run.report.mrr == bestMrr
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    run.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = DemoColors.Ink,
                    fontWeight = if (best) FontWeight.SemiBold else FontWeight.Normal,
                )
                Text(
                    "hit@1 ${run.report.hitAt1.asPercent()} · hit@3 ${run.report.hitAt3.asPercent()} · MRR ${run.report.mrr.format(2)} · " +
                        run.report.meanLatencyMs.asDuration() + if (best) " · mejor MRR" else "",
                    style = readoutStyle, color = if (best) DemoColors.TierA else DemoColors.Muted,
                )
            }
        }
    }
}
