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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import es.sebas1705.nanodemo.core.designsystem.AppCard
import es.sebas1705.nanodemo.core.designsystem.ErrorCard
import es.sebas1705.nanodemo.core.designsystem.SectionLabel
import es.sebas1705.nanodemo.core.ui.DemoColors
import es.sebas1705.nanodemo.core.ui.readoutStyle
import es.sebas1705.nanodemo.feature.lab.R

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
            stringResource(R.string.rag_docs_intro),
            style = MaterialTheme.typography.bodyMedium, color = DemoColors.Muted,
        )
        EmbedderCard(embedder, onDownload = viewModel::downloadEmbedder)

        notice?.let { message ->
            AppCard(Modifier.fillMaxWidth()) {
                Text(message, style = MaterialTheme.typography.bodyMedium, color = DemoColors.Ink)
                TextButton(onClick = viewModel::dismissNotice) { Text(stringResource(R.string.rag_notice_dismiss)) }
            }
        }

        SectionLabel(stringResource(R.string.rag_corpus_section))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = viewModel::loadSample, enabled = !busy, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.rag_load_sample)) }
            OutlinedButton(
                onClick = { picker.launch(arrayOf("text/plain", "text/markdown", "text/x-markdown", "text/*")) },
                enabled = !busy,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.rag_import_files)) }
        }
        Text(
            stringResource(R.string.rag_sample_hint, SampleCorpus.questions.size),
            style = MaterialTheme.typography.bodySmall, color = DemoColors.Muted,
        )
        PasteCard(enabled = !busy, onAdd = viewModel::addPasted)

        SectionLabel(stringResource(R.string.rag_chunks_section))
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

        SectionLabel(stringResource(R.string.rag_documents_section, documents.size))
        if (documents.isEmpty()) {
            Text(stringResource(R.string.rag_documents_empty), style = MaterialTheme.typography.bodyMedium, color = DemoColors.Muted)
        }
        documents.forEach { doc -> DocumentRow(doc, enabled = !busy, onDelete = { viewModel.deleteDocument(doc.id) }) }
        if (documents.isNotEmpty()) {
            TextButton(onClick = viewModel::deleteAll, enabled = !busy) { Text(stringResource(R.string.rag_delete_all), color = DemoColors.Danger) }
        }
    }
}

@Composable
private fun PasteCard(enabled: Boolean, onAdd: (title: String, text: String) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }
    AppCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.rag_paste_title), style = MaterialTheme.typography.titleSmall, color = DemoColors.Ink)
        OutlinedTextField(
            value = title, onValueChange = { title = it }, label = { Text(stringResource(R.string.rag_paste_title_label)) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = text, onValueChange = { text = it }, label = { Text(stringResource(R.string.rag_paste_text_label)) },
            minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                onAdd(title, text)
                title = ""
                text = ""
            },
            enabled = enabled && text.isNotBlank(),
        ) { Text(stringResource(R.string.rag_paste_add)) }
    }
}

@Composable
private fun ChunkSizeCard(selected: Int, onSelect: (Int) -> Unit, enabled: Boolean) {
    AppCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.rag_chunk_size_title), style = MaterialTheme.typography.titleSmall, color = DemoColors.Ink)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CHUNK_SIZE_OPTIONS.forEach { size ->
                FilterChip(selected = selected == size, onClick = { onSelect(size) }, label = { Text("$size") }, enabled = enabled)
            }
        }
        Text(
            stringResource(R.string.rag_chunk_size_hint),
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
                listOf(
                    pluralStringResource(R.plurals.rag_stat_documents_indexed, stats.documents, stats.documents),
                    pluralStringResource(R.plurals.rag_stat_chunks, stats.chunks, stats.chunks),
                    stringResource(R.string.rag_stat_per_chunk, stats.meanChunkMs.asDuration()),
                    stringResource(R.string.rag_stat_total, stats.totalEmbedMs.asDuration()),
                ).joinToString(" · "),
                style = readoutStyle, color = DemoColors.Ink,
            )
        } else {
            Text(stringResource(R.string.rag_index_none), style = MaterialTheme.typography.bodyMedium, color = DemoColors.Muted)
        }
        val progress = indexing.progress
        if (indexing.running) {
            if (progress != null && progress.chunksTotal > 0) {
                LinearProgressIndicator(progress = { progress.chunksDone / progress.chunksTotal.toFloat() }, modifier = Modifier.fillMaxWidth())
                Text(
                    pluralStringResource(
                        R.plurals.rag_index_progress, progress.chunksTotal,
                        progress.chunksDone, progress.chunksTotal, progress.docTitle, progress.lastChunkMs.asDuration(),
                    ),
                    style = readoutStyle, color = DemoColors.Muted,
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        indexing.error?.let { ErrorCard(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onIndexPending, enabled = embedderReady && !busy && pending > 0, modifier = Modifier.weight(1f)) {
                Text(if (pending > 0) stringResource(R.string.rag_index_pending, pending) else stringResource(R.string.rag_index_all_done))
            }
            OutlinedButton(onClick = onReindexAll, enabled = embedderReady && !busy && documents.isNotEmpty(), modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.rag_reindex_all))
            }
        }
        if (indexing.running) OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.rag_index_cancel)) }
        if (!embedderReady) {
            Text(stringResource(R.string.rag_index_need_embedder), style = MaterialTheme.typography.bodySmall, color = DemoColors.Muted)
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
                                DocumentOrigin.SAMPLE -> stringResource(R.string.rag_origin_sample)
                                DocumentOrigin.PASTED -> stringResource(R.string.rag_origin_pasted)
                                DocumentOrigin.FILE -> stringResource(R.string.rag_origin_file)
                            },
                        )
                        add(stringResource(R.string.rag_doc_chars, doc.text.length))
                        if (doc.isIndexed) {
                            add(pluralStringResource(R.plurals.rag_doc_chunks, doc.chunks.size, doc.chunks.size, doc.chunkTargetChars))
                            add(stringResource(R.string.rag_doc_each, doc.meanEmbedMs.asDuration()))
                        } else {
                            add(stringResource(R.string.rag_doc_not_indexed))
                        }
                    }.joinToString(" · "),
                    style = readoutStyle, color = if (doc.isIndexed) DemoColors.Muted else DemoColors.TierB,
                )
            }
            IconButton(onClick = onDelete, enabled = enabled) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.rag_doc_delete_description, doc.title), tint = DemoColors.Muted)
            }
        }
        if (expanded) {
            if (doc.chunks.isEmpty()) {
                Text(doc.text.take(PREVIEW_CHARS) + if (doc.text.length > PREVIEW_CHARS) "…" else "", style = MaterialTheme.typography.bodySmall, color = DemoColors.Muted)
            }
            doc.chunks.forEach { chunk ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.rag_chunk_info, chunk.index + 1, chunk.text.length, chunk.embedMs.asDuration()) + (chunk.heading?.let { " · $it" } ?: ""),
                        style = readoutStyle, color = DemoColors.Muted,
                    )
                    Text(chunk.text, style = MaterialTheme.typography.bodySmall, color = DemoColors.Ink)
                }
            }
        }
    }
}

private const val PREVIEW_CHARS = 600
