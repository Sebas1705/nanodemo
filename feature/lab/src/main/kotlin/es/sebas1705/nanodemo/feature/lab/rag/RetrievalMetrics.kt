package es.sebas1705.nanodemo.feature.lab.rag

import es.sebas1705.nanodemo.feature.lab.R
import java.text.Normalizer

/**
 * A question with the place that answers it. `evidence` is a short phrase that must appear in the retrieved chunk
 * (compared ignoring case, accents and spacing), so the same question set works whatever the chunk size is.
 */
data class EvalQuestion(val id: String, val question: String, val docId: String, val evidence: String)

/** Lowercase, accent-free, single-spaced text: the form in which evidence phrases are compared. */
fun normalizeForMatch(text: String): String =
    Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

/** True when `chunk` is from the question's document and contains its evidence phrase. */
fun EvalQuestion.isAnsweredBy(chunk: IndexedChunk): Boolean =
    chunk.docId == docId && normalizeForMatch(chunk.text).contains(normalizeForMatch(evidence))

/** 1-based position of the first relevant chunk in a ranking, or null when none of them is relevant. */
fun firstRelevantRank(relevant: List<Boolean>): Int? = relevant.indexOfFirst { it }.takeIf { it >= 0 }?.plus(1)

fun hitAt(rank: Int?, k: Int): Boolean = rank != null && rank <= k

fun reciprocalRank(rank: Int?): Double = if (rank == null) 0.0 else 1.0 / rank

/** Outcome of retrieving for one evaluation question. */
data class QuestionEval(
    val question: EvalQuestion,
    /** Rank of the first chunk that answers the question (1-based), null if it was not retrieved. */
    val rank: Int?,
    /** Embedding the query plus searching the index, in milliseconds. */
    val latencyMs: Double,
    val topScore: Double?,
    val retrieved: List<ScoredChunk>,
)

/** Aggregate retrieval quality over a question set. All ratios are in [0, 1]. */
data class RetrievalReport(
    val questions: Int,
    /** Questions left out because their document is not indexed. */
    val skipped: Int,
    val k: Int,
    val hitAt1: Double,
    val hitAt3: Double,
    val hitAtK: Double,
    val mrr: Double,
    val meanLatencyMs: Double,
)

/** Folds per-question outcomes into the report; an empty list gives an all-zero report. */
fun summarize(evals: List<QuestionEval>, k: Int, skipped: Int = 0): RetrievalReport {
    val n = evals.size
    fun ratio(count: Int) = if (n == 0) 0.0 else count.toDouble() / n
    return RetrievalReport(
        questions = n,
        skipped = skipped,
        k = k,
        hitAt1 = ratio(evals.count { hitAt(it.rank, 1) }),
        hitAt3 = ratio(evals.count { hitAt(it.rank, 3) }),
        hitAtK = ratio(evals.count { hitAt(it.rank, k) }),
        mrr = if (n == 0) 0.0 else evals.sumOf { reciprocalRank(it.rank) } / n,
        meanLatencyMs = if (n == 0) 0.0 else evals.sumOf { it.latencyMs } / n,
    )
}

/**
 * Runs `questions` against `index` and scores the rankings. Questions whose document has no indexed chunk are
 * skipped (they could never be found) and counted, so a half-indexed corpus does not silently lower the score.
 * Retrieval depth is `max(options.topK, 3)` so hit@3 is always defined; the thresholds and MMR setting are the
 * caller's, which is what lets the user compare settings.
 */
class RetrievalEvaluator(
    private val embedQuery: suspend (String) -> FloatArray?,
    private val texts: RagTexts,
    private val nanoClock: () -> Long = System::nanoTime,
) {
    class Result(val evals: List<QuestionEval>, val report: RetrievalReport)

    suspend fun evaluate(
        questions: List<EvalQuestion>,
        index: List<IndexedChunk>,
        options: RetrievalOptions,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Result {
        val indexedDocs = index.map { it.docId }.toSet()
        val runnable = questions.filter { it.docId in indexedDocs }
        val depth = maxOf(options.topK, 3)
        val evals = mutableListOf<QuestionEval>()
        runnable.forEachIndexed { i, question ->
            val start = nanoClock()
            val vector = embedQuery(question.question) ?: error(texts.string(R.string.rag_error_embed_eval_question, question.question))
            val hits = Retriever.search(vector, index, options.copy(topK = depth))
            val latencyMs = (nanoClock() - start) / 1_000_000.0
            val rank = firstRelevantRank(hits.map { question.isAnsweredBy(it.chunk) })
            evals += QuestionEval(question, rank, latencyMs, hits.firstOrNull()?.score, hits)
            onProgress(i + 1, runnable.size)
        }
        return Result(evals, summarize(evals, k = options.topK, skipped = questions.size - runnable.size))
    }
}
