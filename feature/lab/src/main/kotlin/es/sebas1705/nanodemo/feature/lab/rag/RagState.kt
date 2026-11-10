package es.sebas1705.nanodemo.feature.lab.rag

import androidx.annotation.StringRes
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.feature.lab.R

/** State of the embedding model download (the History search shows the same states for the same model). */
sealed interface EmbedderState {
    data object NotDownloaded : EmbedderState
    data class Downloading(val percent: Int? = null) : EmbedderState
    data object Ready : EmbedderState
    data class Error(val message: String) : EmbedderState
}

enum class RagTab(@StringRes val labelRes: Int) {
    DOCUMENTS(R.string.rag_tab_documents),
    ASK(R.string.rag_tab_ask),
    EVALUATE(R.string.rag_tab_evaluate),
}

/** Which half of the A/B is running; the UI shows it as "Now: ...". */
enum class AskStage(@StringRes val labelRes: Int) {
    PLAIN(R.string.rag_stage_plain),
    RAG(R.string.rag_stage_rag),
}

/** Chunk sizes (characters) offered at indexing time. */
val CHUNK_SIZE_OPTIONS = listOf(250, 400, 600, 900)
const val DEFAULT_CHUNK_SIZE = 400

data class RagSettings(
    val chunkTargetChars: Int = DEFAULT_CHUNK_SIZE,
    val topK: Int = 3,
    val mmr: Boolean = false,
    val minScore: Float = 0f,
    val tier: InferenceTier = InferenceTier.ML_KIT_NANO,
    val judgeEnabled: Boolean = false,
    val judgeTier: InferenceTier = InferenceTier.ML_KIT_NANO,
) {
    val chunker: ChunkerConfig get() = ChunkerConfig.forTarget(chunkTargetChars)
    val retrieval: RetrievalOptions get() = RetrievalOptions(topK = topK, minScore = minScore.toDouble(), mmr = mmr)
}

data class IndexingState(
    val running: Boolean = false,
    val progress: IndexProgress? = null,
    val error: String? = null,
)

/** The A/B result of one question: the baseline arrives first, then the answer with retrieval. */
data class AskState(
    val running: Boolean = false,
    val stage: AskStage? = null,
    val question: String? = null,
    val tier: InferenceTier? = null,
    val plain: AnswerRun? = null,
    val rag: AnswerRun? = null,
    val error: String? = null,
)

/** One evaluation run kept for comparison: the settings it ran with and what it scored. */
data class EvalRun(val label: String, val report: RetrievalReport)

data class EvalState(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val latest: RetrievalEvaluator.Result? = null,
    val history: List<EvalRun> = emptyList(),
    val error: String? = null,
)

/**
 * Human label of the settings an evaluation ran with, e.g. `chunk 400 · k=3 · MMR · ≥0.20`. `chunkChars` is null
 * when the indexed documents were built with different sizes (shown as "mixed").
 */
fun settingsLabel(chunkChars: Int?, options: RetrievalOptions, texts: RagTexts): String = buildList {
    add(texts.string(R.string.rag_settings_label_chunk, chunkChars ?: texts.string(R.string.rag_settings_label_mixed)))
    add("k=${options.topK}")
    if (options.mmr) add("MMR")
    if (options.minScore > 0) add("≥${"%.2f".format(options.minScore)}")
}.joinToString(" · ")

/** The single chunk size all indexed documents share, or null when they differ (or none is indexed). */
fun List<RagDocument>.commonChunkTarget(): Int? =
    filter { it.isIndexed }.map { it.chunkTargetChars }.distinct().singleOrNull()
