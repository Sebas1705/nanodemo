package es.sebas1705.nanodemo.feature.lab.rag

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import es.sebas1705.nanodemo.core.designsystem.AppCard
import es.sebas1705.nanodemo.core.designsystem.ErrorCard
import es.sebas1705.nanodemo.core.designsystem.MarkdownText
import es.sebas1705.nanodemo.core.designsystem.MetricsLine
import es.sebas1705.nanodemo.core.designsystem.ScreenSizeClass
import es.sebas1705.nanodemo.core.designsystem.SectionLabel
import es.sebas1705.nanodemo.core.designsystem.TypingDots
import es.sebas1705.nanodemo.core.ui.DemoColors
import es.sebas1705.nanodemo.core.ui.readoutStyle
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.feature.lab.common.LabTierSelector
import es.sebas1705.nanodemo.feature.lab.trace.PipelineTraceView

/** Questions offered as one-tap examples: one per sample document, plus one the corpus cannot answer. */
private val SuggestedQuestions: List<String> =
    SampleCorpus.documents.mapNotNull { doc -> SampleCorpus.questions.firstOrNull { it.docId == doc.id }?.question } +
        SampleCorpus.OUT_OF_SCOPE_QUESTION

/** Ask tab: pick the engine and retrieval settings, ask once, and see the answer without and with retrieval. */
@Composable
internal fun RagAskTab(viewModel: RagViewModel, sizeClass: ScreenSizeClass, onGoToDocuments: () -> Unit) {
    val documents by viewModel.documents.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val availability by viewModel.availability.collectAsState()
    val ask by viewModel.ask.collectAsState()
    val trace by viewModel.trace.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current

    var question by rememberSaveable { mutableStateOf("") }
    val indexedChunks = documents.sumOf { if (it.isIndexed) it.chunks.size else 0 }
    val capabilities = viewModel.capabilities(settings.tier)
    val send = {
        keyboard?.hide()
        viewModel.ask(question)
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (indexedChunks == 0) {
            AppCard(Modifier.fillMaxWidth()) {
                Text("Aún no hay nada indexado", style = MaterialTheme.typography.titleSmall, color = DemoColors.Ink)
                Text(
                    "Carga el corpus de ejemplo (o tus documentos) e indéxalo para poder preguntar.",
                    style = MaterialTheme.typography.bodyMedium, color = DemoColors.Muted,
                )
                Button(onClick = onGoToDocuments) { Text("Ir a Documentos") }
            }
        }

        SectionLabel("Motor que responde")
        LabTierSelector(settings.tier, availability, viewModel::setTier)
        Text(contextHint(capabilities.contextTokens, settings, question.length), style = MaterialTheme.typography.bodySmall, color = DemoColors.Muted)

        RetrievalSettingsCard(
            settings = settings,
            onTopK = viewModel::setTopK,
            onMmr = viewModel::setMmr,
            onMinScore = viewModel::setMinScore,
            enabled = !busy,
        )
        JudgeCard(settings, availability, viewModel::setJudgeEnabled, viewModel::setJudgeTier, enabled = !busy)

        SectionLabel("Pregunta")
        OutlinedTextField(
            value = question,
            onValueChange = { question = it },
            label = { Text("Pregunta sobre tus documentos") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 5,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (question.isNotBlank() && !busy) send() }),
        )
        SuggestedQuestionsList(onPick = { question = it })
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { send() }, enabled = question.isNotBlank() && !busy && indexedChunks > 0) {
                Text(if (ask.running) "Preguntando…" else "Preguntar con y sin RAG")
            }
            if (ask.running) OutlinedButton(onClick = viewModel::cancel) { Text("Cancelar") }
        }

        ask.error?.let { ErrorCard(it) }
        if (ask.question != null && ask.error == null) {
            ask.stage?.let { stage ->
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Ahora: $stage", style = readoutStyle, color = DemoColors.Muted)
            }
            AnswerComparison(ask, sizeClass)
            ask.rag?.let { rag ->
                if (rag.retrieved.isNotEmpty()) SourcesCard(rag)
                GroundingCard(rag)
            }
        }
        PipelineTraceView(trace)
    }
}

/** How much retrieved text the chosen engine can take, so the user sees why a small engine gets fewer chunks. */
private fun contextHint(contextTokens: Int?, settings: RagSettings, questionChars: Int): String {
    val budget = RagPrompts.contextBudgetChars(contextTokens, RagPrompts.maxAnswerTokens(contextTokens), questionChars.coerceAtLeast(60))
    val fits = (budget / settings.chunkTargetChars).coerceAtLeast(1)
    return if (contextTokens == null) {
        "Este motor tiene una ventana grande: caben unos $budget caracteres de contexto (~$fits fragmentos de ${settings.chunkTargetChars})."
    } else {
        "Ventana de $contextTokens tokens en total: quedan unos $budget caracteres para el contexto (~$fits fragmentos de ${settings.chunkTargetChars}); " +
            "si k es mayor, los últimos no entran."
    }
}

@Composable
private fun JudgeCard(
    settings: RagSettings,
    availability: Map<InferenceTier, es.sebas1705.nanodemo.domain.models.TierAvailability>,
    onEnabled: (Boolean) -> Unit,
    onTier: (InferenceTier) -> Unit,
    enabled: Boolean,
) {
    AppCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Switch(checked = settings.judgeEnabled, onCheckedChange = onEnabled, enabled = enabled)
            Column(Modifier.weight(1f)) {
                Text("Comprobar la fundamentación", style = MaterialTheme.typography.bodyMedium, color = DemoColors.Ink)
                Text(
                    "Un segundo modelo revisa si cada afirmación de la respuesta está respaldada por el contexto.",
                    style = MaterialTheme.typography.bodySmall, color = DemoColors.Muted,
                )
            }
        }
        if (settings.judgeEnabled) {
            SectionLabel("Motor que juzga")
            LabTierSelector(settings.judgeTier, availability, onTier)
        }
    }
}

@Composable
private fun SuggestedQuestionsList(onPick: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Ocultar preguntas de ejemplo" else "Preguntas de ejemplo (corpus de muestra)") }
        if (expanded) {
            SuggestedQuestions.forEach { q ->
                Text(
                    q,
                    style = MaterialTheme.typography.bodyMedium,
                    color = DemoColors.TierC,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .clickable { onPick(q) }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** The two answers side by side on wide screens, stacked on phones. */
@Composable
private fun AnswerComparison(ask: AskState, sizeClass: ScreenSizeClass) {
    val plain: @Composable (Modifier) -> Unit = { modifier ->
        AnswerCard("Sin recuperación", "Solo lo que el modelo sabe", ask.plain, waiting = ask.running, accent = DemoColors.Muted, modifier = modifier)
    }
    val rag: @Composable (Modifier) -> Unit = { modifier ->
        AnswerCard(
            "Con recuperación", "Con los fragmentos de tus documentos", ask.rag,
            waiting = ask.running, queued = ask.plain == null, accent = DemoColors.TierA, modifier = modifier,
        )
    }
    if (sizeClass == ScreenSizeClass.COMPACT) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            plain(Modifier.fillMaxWidth())
            rag(Modifier.fillMaxWidth())
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            plain(Modifier.weight(1f))
            rag(Modifier.weight(1f))
        }
    }
}

@Composable
private fun AnswerCard(
    title: String,
    subtitle: String,
    run: AnswerRun?,
    waiting: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    queued: Boolean = false,
) {
    AppCard(modifier, accent = accent) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = DemoColors.Ink)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = DemoColors.Muted)
        when {
            run == null && queued && waiting -> Text("En cola…", style = MaterialTheme.typography.bodyMedium, color = DemoColors.Muted)
            run == null && waiting -> TypingDots(accent)
            run == null -> Text("—", style = MaterialTheme.typography.bodyMedium, color = DemoColors.Muted)
            run.error != null -> ErrorCard(run.error)
            else -> {
                if (run.modelSkipped) {
                    Text(
                        "Ningún fragmento superó el umbral, así que no se llamó al modelo.",
                        style = MaterialTheme.typography.bodySmall, color = DemoColors.TierB,
                    )
                }
                MarkdownText(run.answer.ifBlank { "(respuesta vacía)" })
                run.metric?.let { MetricsLine(it) }
                if (run.retrieved.isNotEmpty() || run.modelSkipped) {
                    Text(
                        "búsqueda ${run.retrievalMs.asDuration()} (embedding ${run.embedMs.asDuration()} + similitud ${run.searchMs.asDuration()})" +
                            " · ${run.citedChunks} de ${run.retrieved.size} fragmentos en el contexto (${run.contextChars}/${run.contextBudgetChars} car)",
                        style = MaterialTheme.typography.labelSmall, color = DemoColors.Muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun SourcesCard(rag: AnswerRun) {
    AppCard(Modifier.fillMaxWidth()) {
        Text("Fragmentos recuperados", style = MaterialTheme.typography.titleSmall, color = DemoColors.Ink)
        rag.retrieved.forEachIndexed { i, hit ->
            val inContext = i < rag.citedChunks
            var expanded by remember(hit.chunk.chunkId) { mutableStateOf(false) }
            Column(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("[${i + 1}]", style = readoutStyle, color = if (inContext) DemoColors.TierA else DemoColors.Muted)
                    Text(hit.chunk.label, style = MaterialTheme.typography.bodyMedium, color = DemoColors.Ink, modifier = Modifier.weight(1f))
                    Text(hit.score.format(2), style = readoutStyle, color = DemoColors.Muted)
                }
                LinearProgressIndicator(
                    progress = { hit.score.coerceIn(0.0, 1.0).toFloat() },
                    color = if (inContext) DemoColors.TierA else DemoColors.Muted,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    if (expanded) hit.chunk.text else hit.chunk.text.replace('\n', ' ').take(SOURCE_PREVIEW_CHARS) +
                        if (hit.chunk.text.length > SOURCE_PREVIEW_CHARS) "…" else "",
                    style = MaterialTheme.typography.bodySmall, color = DemoColors.Muted,
                )
                if (!inContext) {
                    Text("No cupo en el contexto del motor", style = MaterialTheme.typography.labelSmall, color = DemoColors.TierB)
                }
            }
        }
    }
}

private const val SOURCE_PREVIEW_CHARS = 160

@Composable
private fun GroundingCard(rag: AnswerRun) {
    val grounding = rag.grounding
    val error = rag.groundingError
    if (grounding == null && error == null) return
    AppCard(Modifier.fillMaxWidth()) {
        Text("Fundamentación", style = MaterialTheme.typography.titleSmall, color = DemoColors.Ink)
        if (error != null) ErrorCard("El juez falló: $error")
        if (grounding != null) {
            val color = when (grounding.verdict) {
                GroundingVerdict.SUPPORTED -> DemoColors.TierA
                GroundingVerdict.PARTIAL -> DemoColors.TierB
                GroundingVerdict.UNSUPPORTED -> DemoColors.Danger
                GroundingVerdict.UNKNOWN -> DemoColors.Muted
            }
            Text(describe(grounding).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium, color = color)
            Text("Dijo el juez: ${grounding.raw.replace('\n', ' ')}", style = MaterialTheme.typography.bodySmall, color = DemoColors.Muted)
            rag.groundingMetric?.let { MetricsLine(it) }
        }
    }
}
