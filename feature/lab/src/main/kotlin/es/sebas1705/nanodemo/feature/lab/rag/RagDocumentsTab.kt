package es.sebas1705.nanodemo.feature.lab.rag

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import es.sebas1705.nanodemo.core.designsystem.AppCard
import es.sebas1705.nanodemo.core.designsystem.ErrorCard
import es.sebas1705.nanodemo.core.designsystem.SectionLabel
import es.sebas1705.nanodemo.core.ui.DemoColors
import es.sebas1705.nanodemo.core.ui.readoutStyle

/** Documents tab: the embedding model, the corpus (sample, pasted, files), chunk size and indexing. */
@Composable
internal fun RagDocumentsTab(viewModel: RagViewModel) {
    val documents by viewModel.documents.collectAsState()
    val embedder by viewModel.embedderState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val indexing by viewModel.indexing.collectAsState()
    val notice by viewModel.notice.collectAsState()
    val busy by viewModel.busy.collectAsState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.importFiles(uris)
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Los documentos se trocean en fragmentos, cada fragmento se convierte en un vector en el dispositivo y se guarda en el almacenamiento de la app.",
            style = MaterialTheme.typography.bodyMedium, color = DemoColors.Muted,
        )
        EmbedderCard(embedder, onDownload = viewModel::downloadEmbedder)

        notice?.let { message ->
            AppCard(Modifier.fillMaxWidth()) {
                Text(message, style = MaterialTheme.typography.bodyMedium, color = DemoColors.Ink)
                TextButton(onClick = viewModel::dismissNotice) { Text("Entendido") }
            }
        }

        SectionLabel("Corpus")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = viewModel::loadSample, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Cargar ejemplo") }
            OutlinedButton(
                onClick = { picker.launch(arrayOf("text/plain", "text/markdown", "text/x-markdown", "text/*")) },
                enabled = !busy,
                modifier = Modifier.weight(1f),
            ) { Text("Importar archivos") }
        }
        Text(
            "El ejemplo es un manual ficticio de empresa en español (vacaciones, gastos, seguridad, incorporación, FAQ) con 10 preguntas para evaluar.",
            style = MaterialTheme.typography.bodySmall, color = DemoColors.Muted,
        )
        PasteCard(enabled = !busy, onAdd = viewModel::addPasted)

        SectionLabel("Fragmentos e indexado")
        ChunkSizeCard(settings.chunkTargetChars, onSelect = viewModel::setChunkSize, enabled = !busy)
        IndexControls(
            documents = documents,
            embedderReady = embedder == EmbedderState.Ready,
            indexing = indexing,
            busy = busy,
            onIndexPending = viewModel::indexPending,
            onReindexAll = viewModel::reindexAll,
            onCancel = viewModel::cancel,
        )

        SectionLabel("Documentos (${documents.size})")
        if (documents.isEmpty()) {
            Text("Todavía no hay documentos. Carga el ejemplo o importa los tuyos.", style = MaterialTheme.typography.bodyMedium, color = DemoColors.Muted)
        }
        documents.forEach { doc -> DocumentRow(doc, enabled = !busy, onDelete = { viewModel.deleteDocument(doc.id) }) }
        if (documents.isNotEmpty()) {
            TextButton(onClick = viewModel::deleteAll, enabled = !busy) { Text("Borrar todos los documentos", color = DemoColors.Danger) }
        }
    }
}

@Composable
private fun PasteCard(enabled: Boolean, onAdd: (title: String, text: String) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }
    AppCard(Modifier.fillMaxWidth()) {
        Text("Pegar texto", style = MaterialTheme.typography.titleSmall, color = DemoColors.Ink)
        OutlinedTextField(
            value = title, onValueChange = { title = it }, label = { Text("Título") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = text, onValueChange = { text = it }, label = { Text("Texto (admite Markdown)") },
            minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                onAdd(title, text)
                title = ""
                text = ""
            },
            enabled = enabled && text.isNotBlank(),
        ) { Text("Añadir documento") }
    }
}

@Composable
private fun ChunkSizeCard(selected: Int, onSelect: (Int) -> Unit, enabled: Boolean) {
    AppCard(Modifier.fillMaxWidth()) {
        Text("Tamaño de fragmento (caracteres)", style = MaterialTheme.typography.titleSmall, color = DemoColors.Ink)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CHUNK_SIZE_OPTIONS.forEach { size ->
                FilterChip(selected = selected == size, onClick = { onSelect(size) }, label = { Text("$size") }, enabled = enabled)
            }
        }
        Text(
            "Se aplica al indexar (con un solapamiento de ~1/6 entre fragmentos). Pequeños: más precisos pero con menos contexto cada uno. " +
                "Grandes: más contexto pero más ruido y menos caben en los motores de ventana pequeña.",
            style = MaterialTheme.typography.bodySmall, color = DemoColors.Muted,
        )
    }
}

@Composable
private fun IndexControls(
    documents: List<RagDocument>,
    embedderReady: Boolean,
    indexing: IndexingState,
    busy: Boolean,
    onIndexPending: () -> Unit,
    onReindexAll: () -> Unit,
    onCancel: () -> Unit,
) {
    val pending = documents.count { !it.isIndexed }
    val stats = documents.indexStats()
    AppCard(Modifier.fillMaxWidth()) {
        if (stats.chunks > 0) {
            Text(
                "${stats.documents} documentos indexados · ${stats.chunks} fragmentos · " +
                    "${stats.meanChunkMs.asDuration()} por fragmento · ${stats.totalEmbedMs.asDuration()} en total",
                style = readoutStyle, color = DemoColors.Ink,
            )
        } else {
            Text("Nada indexado todavía.", style = MaterialTheme.typography.bodyMedium, color = DemoColors.Muted)
        }
        val progress = indexing.progress
        if (indexing.running) {
            if (progress != null && progress.chunksTotal > 0) {
                LinearProgressIndicator(progress = { progress.chunksDone / progress.chunksTotal.toFloat() }, modifier = Modifier.fillMaxWidth())
                Text(
                    "${progress.chunksDone} de ${progress.chunksTotal} fragmentos · «${progress.docTitle}» · último ${progress.lastChunkMs.asDuration()}",
                    style = readoutStyle, color = DemoColors.Muted,
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        indexing.error?.let { ErrorCard(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onIndexPending, enabled = embedderReady && !busy && pending > 0, modifier = Modifier.weight(1f)) {
                Text(if (pending > 0) "Indexar pendientes ($pending)" else "Todo indexado")
            }
            OutlinedButton(onClick = onReindexAll, enabled = embedderReady && !busy && documents.isNotEmpty(), modifier = Modifier.weight(1f)) {
                Text("Reindexar todo")
            }
        }
        if (indexing.running) OutlinedButton(onClick = onCancel) { Text("Cancelar indexado") }
        if (!embedderReady) {
            Text("Descarga el modelo de embeddings para poder indexar.", style = MaterialTheme.typography.bodySmall, color = DemoColors.Muted)
        }
    }
}

@Composable
private fun DocumentRow(doc: RagDocument, enabled: Boolean, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    AppCard(Modifier.fillMaxWidth(), onClick = { expanded = !expanded }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(doc.title, style = MaterialTheme.typography.titleSmall, color = DemoColors.Ink)
                Text(
                    buildList {
                        add(
                            when (doc.origin) {
                                DocumentOrigin.SAMPLE -> "ejemplo"
                                DocumentOrigin.PASTED -> "pegado"
                                DocumentOrigin.FILE -> "archivo"
                            },
                        )
                        add("${doc.text.length} car")
                        if (doc.isIndexed) {
                            add("${doc.chunks.size} fragmentos de ~${doc.chunkTargetChars}")
                            add("${doc.meanEmbedMs.asDuration()} c/u")
                        } else {
                            add("sin indexar")
                        }
                    }.joinToString(" · "),
                    style = readoutStyle, color = if (doc.isIndexed) DemoColors.Muted else DemoColors.TierB,
                )
            }
            IconButton(onClick = onDelete, enabled = enabled) {
                Icon(Icons.Filled.Delete, contentDescription = "Borrar «${doc.title}»", tint = DemoColors.Muted)
            }
        }
        if (expanded) {
            if (doc.chunks.isEmpty()) {
                Text(doc.text.take(PREVIEW_CHARS) + if (doc.text.length > PREVIEW_CHARS) "…" else "", style = MaterialTheme.typography.bodySmall, color = DemoColors.Muted)
            }
            doc.chunks.forEach { chunk ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "#${chunk.index + 1} · ${chunk.text.length} car · ${chunk.embedMs.asDuration()}" + (chunk.heading?.let { " · $it" } ?: ""),
                        style = readoutStyle, color = DemoColors.Muted,
                    )
                    Text(chunk.text, style = MaterialTheme.typography.bodySmall, color = DemoColors.Ink)
                }
            }
        }
    }
}

private const val PREVIEW_CHARS = 600
