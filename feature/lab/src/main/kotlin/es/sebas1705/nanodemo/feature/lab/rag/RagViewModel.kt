package es.sebas1705.nanodemo.feature.lab.rag

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import es.sebas1705.nanodemo.data.embeddings.LocalEmbeddingService
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.PipelineTrace
import es.sebas1705.nanodemo.domain.models.TierAvailability
import es.sebas1705.nanodemo.domain.usescases.inference.CapabilitiesOfUseCase
import es.sebas1705.nanodemo.domain.usescases.inference.ObserveAvailabilityUseCase
import es.sebas1705.nanodemo.domain.usescases.inference.RunStepUseCase
import es.sebas1705.nanodemo.feature.lab.common.usableTiers
import es.sebas1705.nanodemo.feature.lab.trace.PipelineRunner
import es.sebas1705.nanodemo.feature.lab.trace.asExecutor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * On-device RAG: documents are chunked and embedded on the phone ([LocalEmbeddingService]), retrieved by cosine
 * similarity and answered by whichever of the five engines the user picks. This class owns the state and glues
 * the pure pieces ([Chunker], [DocumentIndexer], [Retriever], [RagPipeline], [RetrievalEvaluator]) to storage
 * and the UI; the logic worth testing lives in those.
 */
@HiltViewModel
class RagViewModel @Inject constructor(
    runStep: RunStepUseCase,
    observeAvailability: ObserveAvailabilityUseCase,
    private val capabilitiesOf: CapabilitiesOfUseCase,
    embeddingService: LocalEmbeddingService,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val embedder: ChunkEmbedder = embeddingService.asChunkEmbedder()
    private val store = RagDocumentStore(File(context.filesDir, "rag"))
    private val runner = PipelineRunner(runStep.asExecutor())
    private val pipeline = RagPipeline(runner, embedder::embed)
    private val indexer = DocumentIndexer(embedder)

    val availability: StateFlow<Map<InferenceTier, TierAvailability>> = observeAvailability()
    val trace: StateFlow<PipelineTrace> = runner.recorder.trace

    private val _documents = MutableStateFlow<List<RagDocument>>(emptyList())
    val documents: StateFlow<List<RagDocument>> = _documents.asStateFlow()

    private val _embedderState = MutableStateFlow<EmbedderState>(
        if (embedder.isAvailable()) EmbedderState.Ready else EmbedderState.NotDownloaded,
    )
    val embedderState: StateFlow<EmbedderState> = _embedderState.asStateFlow()

    private val _settings = MutableStateFlow(RagSettings())
    val settings: StateFlow<RagSettings> = _settings.asStateFlow()

    private val _indexing = MutableStateFlow(IndexingState())
    val indexing: StateFlow<IndexingState> = _indexing.asStateFlow()

    private val _ask = MutableStateFlow(AskState())
    val ask: StateFlow<AskState> = _ask.asStateFlow()

    private val _eval = MutableStateFlow(EvalState())
    val eval: StateFlow<EvalState> = _eval.asStateFlow()

    /** One-line message about the last import or storage problem; cleared when read. */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private var job: Job? = null

    init {
        viewModelScope.launch {
            _documents.value = store.loadAll()
            if (store.lastSkipped.isNotEmpty()) {
                _notice.value = "No se pudieron leer ${store.lastSkipped.size} documentos guardados y se omitieron."
            }
        }
        // Start on an engine that works instead of on one that is not installed.
        viewModelScope.launch {
            val usable = availability.first { map -> map.usableTiers().isNotEmpty() }.usableTiers()
            _settings.update { s ->
                if (s.tier in usable) s else s.copy(tier = usable.first(), judgeTier = usable.first())
            }
        }
    }

    fun capabilities(tier: InferenceTier) = capabilitiesOf(tier)

    /** True while indexing, asking or evaluating: they all use the engines and the embedder, so only one runs at a time. */
    val busy: StateFlow<Boolean> = combine(_indexing, _ask, _eval) { indexing, ask, eval ->
        indexing.running || ask.running || eval.running
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val isBusy: Boolean
        get() = _indexing.value.running || _ask.value.running || _eval.value.running

    // ---- settings -------------------------------------------------------------------------------------------

    fun setChunkSize(chars: Int) = _settings.update { it.copy(chunkTargetChars = chars) }
    fun setTopK(k: Int) = _settings.update { it.copy(topK = k.coerceIn(1, MAX_TOP_K)) }
    fun setMmr(on: Boolean) = _settings.update { it.copy(mmr = on) }
    fun setMinScore(score: Float) = _settings.update { it.copy(minScore = score.coerceIn(0f, 1f)) }
    fun setTier(tier: InferenceTier) = _settings.update { it.copy(tier = tier) }
    fun setJudgeEnabled(on: Boolean) = _settings.update { it.copy(judgeEnabled = on) }
    fun setJudgeTier(tier: InferenceTier) = _settings.update { it.copy(judgeTier = tier) }
    fun dismissNotice() {
        _notice.value = null
    }

    // ---- embedding model ------------------------------------------------------------------------------------

    fun downloadEmbedder() {
        if (_embedderState.value is EmbedderState.Downloading) return
        viewModelScope.launch {
            _embedderState.value = EmbedderState.Downloading()
            try {
                embedder.download().collect { percent -> _embedderState.value = EmbedderState.Downloading(percent) }
                _embedderState.value = EmbedderState.Ready
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _embedderState.value = EmbedderState.Error(e.message ?: "Error descargando el modelo de embeddings")
            }
        }
    }

    // ---- documents ------------------------------------------------------------------------------------------

    fun addPasted(title: String, text: String) {
        val cleaned = TextImport.clean(text)
        if (cleaned == null) {
            _notice.value = "El texto está vacío."
            return
        }
        addDocuments(listOf(TextImport.newDocument(title, cleaned.text, DocumentOrigin.PASTED, System.currentTimeMillis())), cleaned.truncated)
    }

    fun importFiles(uris: List<Uri>) {
        viewModelScope.launch {
            val reader = DocumentFileReader(context.contentResolver)
            var truncated = false
            val now = System.currentTimeMillis()
            val docs = withContext(Dispatchers.IO) {
                uris.mapIndexedNotNull { i, uri ->
                    val picked = try {
                        reader.read(uri)
                    } catch (e: Exception) {
                        null
                    }
                    picked?.also { truncated = truncated || it.cleaned.truncated }
                        ?.let { TextImport.newDocument(it.title, it.cleaned.text, DocumentOrigin.FILE, now + i) }
                }
            }
            if (docs.size < uris.size) _notice.value = "${uris.size - docs.size} archivo(s) no se pudieron leer como texto."
            if (docs.isNotEmpty()) addDocuments(docs, truncated)
        }
    }

    /** Adds the bundled Spanish handbook, skipping the documents that are already there. */
    fun loadSample() {
        val present = _documents.value.map { it.id }.toSet()
        val now = System.currentTimeMillis()
        val docs = SampleCorpus.documents.filter { it.id !in present }.mapIndexed { i, sample ->
            RagDocument(sample.id, sample.title, sample.text, DocumentOrigin.SAMPLE, addedAtMs = now + i)
        }
        if (docs.isEmpty()) {
            _notice.value = "El corpus de ejemplo ya está cargado."
            return
        }
        addDocuments(docs, truncated = false)
    }

    private fun addDocuments(docs: List<RagDocument>, truncated: Boolean) {
        viewModelScope.launch {
            _documents.update { it + docs }
            docs.forEach { store.save(it) }
            if (truncated) _notice.value = "El texto era muy largo y se recortó a ${TextImport.MAX_CHARS} caracteres."
            if (_embedderState.value == EmbedderState.Ready) indexPending()
        }
    }

    fun deleteDocument(id: String) {
        viewModelScope.launch {
            _documents.update { docs -> docs.filterNot { it.id == id } }
            store.delete(id)
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            _documents.value = emptyList()
            store.deleteAll()
        }
    }

    // ---- indexing -------------------------------------------------------------------------------------------

    /** Embeds the documents that have no vectors yet, with the current chunk size. */
    fun indexPending() = index { docs -> docs.filterNot { it.isIndexed } }

    /** Re-chunks and re-embeds everything, e.g. after changing the chunk size. */
    fun reindexAll() = index { it }

    private fun index(select: (List<RagDocument>) -> List<RagDocument>) {
        if (isBusy) return
        if (_embedderState.value != EmbedderState.Ready) {
            _indexing.value = IndexingState(error = "Descarga primero el modelo de embeddings.")
            return
        }
        val targets = select(_documents.value)
        if (targets.isEmpty()) return
        job = viewModelScope.launch {
            _indexing.value = IndexingState(running = true)
            try {
                indexer.indexAll(
                    documents = targets,
                    config = _settings.value.chunker,
                    onProgress = { progress -> _indexing.update { it.copy(progress = progress) } },
                    onDocument = { indexed ->
                        store.save(indexed)
                        _documents.update { docs -> docs.map { if (it.id == indexed.id) indexed else it } }
                    },
                )
                _indexing.value = IndexingState()
            } catch (e: CancellationException) {
                _indexing.value = IndexingState()
                throw e
            } catch (e: Exception) {
                _indexing.value = IndexingState(error = e.message ?: "Falló la indexación")
            }
        }
    }

    // ---- ask (A/B) ------------------------------------------------------------------------------------------

    fun ask(question: String) {
        if (question.isBlank() || isBusy) return
        val index = _documents.value.toIndex()
        if (index.isEmpty()) {
            _ask.value = AskState(question = question, error = "Indexa algún documento antes de preguntar.")
            return
        }
        val settings = _settings.value
        val contextTokens = capabilitiesOf(settings.tier).contextTokens
        job = viewModelScope.launch {
            runner.recorder.reset()
            _ask.value = AskState(running = true, stage = "Sin recuperación", question = question, tier = settings.tier)
            try {
                val plain = pipeline.answerPlain(question, settings.tier, contextTokens)
                _ask.update { it.copy(plain = plain, stage = "Con recuperación") }
                val rag = pipeline.answerWithRag(
                    question = question,
                    tier = settings.tier,
                    contextTokens = contextTokens,
                    index = index,
                    options = settings.retrieval,
                    judgeTier = settings.judgeTier.takeIf { settings.judgeEnabled },
                )
                _ask.update { it.copy(rag = rag, running = false, stage = null) }
            } catch (e: CancellationException) {
                _ask.update { it.copy(running = false, stage = null) }
                throw e
            } catch (e: Exception) {
                _ask.update { it.copy(running = false, stage = null, error = e.message ?: "Falló la pregunta") }
            }
        }
    }

    // ---- evaluation -----------------------------------------------------------------------------------------

    fun runEvaluation() {
        if (isBusy) return
        val docs = _documents.value
        val index = docs.toIndex()
        if (index.isEmpty()) {
            _eval.update { it.copy(error = "Indexa el corpus de ejemplo antes de evaluar.") }
            return
        }
        val options = _settings.value.retrieval
        val label = settingsLabel(docs.commonChunkTarget(), options)
        job = viewModelScope.launch {
            _eval.update { it.copy(running = true, done = 0, total = 0, error = null) }
            try {
                val result = RetrievalEvaluator(embedder::embed).evaluate(SampleCorpus.questions, index, options) { done, total ->
                    _eval.update { it.copy(done = done, total = total) }
                }
                _eval.update {
                    it.copy(
                        running = false, latest = result,
                        history = (listOf(EvalRun(label, result.report)) + it.history).take(MAX_EVAL_HISTORY),
                    )
                }
            } catch (e: CancellationException) {
                _eval.update { it.copy(running = false) }
                throw e
            } catch (e: Exception) {
                _eval.update { it.copy(running = false, error = e.message ?: "Falló la evaluación") }
            }
        }
    }

    fun clearEvalHistory() = _eval.update { it.copy(history = emptyList(), latest = null) }

    fun cancel() {
        job?.cancel()
    }

    private companion object {
        const val MAX_TOP_K = 5
        const val MAX_EVAL_HISTORY = 8
    }
}
