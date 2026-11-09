package es.sebas1705.nanodemo.feature.lab.scaling

import es.sebas1705.nanodemo.domain.models.ChatMessage
import es.sebas1705.nanodemo.domain.models.GenerationConfig
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.Role
import es.sebas1705.nanodemo.feature.lab.trace.StepExecutor
import kotlinx.coroutines.withTimeoutOrNull

/** Prompt sizes (characters of filler before the question) the curve samples. */
val SCALING_STEPS = listOf(200, 800, 1_600, 3_200, 6_400)

/** Context assumed for engines that do not report one (cloud models have far more than any step here). */
private const val UNKNOWN_CONTEXT_TOKENS = 8_192
private const val CHARS_PER_TOKEN = 4

/** Share of the context the prompt may take: the rest is for the preamble, the question and the answer. */
private const val CONTEXT_BUDGET = 0.6

private const val QUESTION = "\n\nPregunta: ¿cuál es la capital de Francia? Responde con una sola palabra."

private val FILLER_SENTENCES = listOf(
    "El mercado de la ciudad abre cada mañana con puestos de fruta, pan recién hecho y flores de temporada.",
    "Los ingenieros revisaron el puente durante tres días antes de autorizar el paso de los camiones.",
    "En la biblioteca del barrio hay una sala silenciosa donde estudian los estudiantes de todas las edades.",
    "La receta original lleva harina, agua, sal y una pizca de azúcar, aunque cada familia añade su toque.",
    "Durante el invierno, los trenes de montaña circulan más despacio por la nieve acumulada en las vías.",
    "El equipo local ganó el campeonato después de una temporada larga y con muchos partidos disputados.",
)

/** Deterministic filler of about `chars` characters followed by a trivial question. Same input, same prompt. */
fun paddedPrompt(chars: Int): String {
    val filler = StringBuilder()
    var i = 0
    while (filler.length < chars) {
        filler.append(FILLER_SENTENCES[i % FILLER_SENTENCES.size]).append(' ')
        i++
    }
    return filler.toString().take(chars).trimEnd() + QUESTION
}

/** The sizes in [SCALING_STEPS] that fit this engine's context with room left to answer. */
fun stepsFor(contextTokens: Int?): List<Int> {
    val budgetChars = ((contextTokens ?: UNKNOWN_CONTEXT_TOKENS) * CHARS_PER_TOKEN * CONTEXT_BUDGET).toInt()
    return SCALING_STEPS.filter { it <= budgetChars }
}

data class ScalingPoint(val tier: InferenceTier, val chars: Int, val ttftMs: Long?, val totalMs: Long, val ok: Boolean)

/**
 * Least-squares slope of time-to-first-token against prompt size, in milliseconds per 1000 characters: the
 * marginal cost of a longer prompt (prefill). `null` with fewer than two usable points or no size variation.
 */
fun slopeMsPer1kChars(points: List<ScalingPoint>): Double? {
    val usable = points.filter { it.ok && it.ttftMs != null }
    if (usable.size < 2) return null
    val xs = usable.map { it.chars.toDouble() }
    val ys = usable.map { it.ttftMs!!.toDouble() }
    val meanX = xs.average()
    val meanY = ys.average()
    val varianceX = xs.sumOf { (it - meanX) * (it - meanX) }
    if (varianceX == 0.0) return null
    val covariance = xs.indices.sumOf { (xs[it] - meanX) * (ys[it] - meanY) }
    return covariance / varianceX * 1000.0
}

/** Runs the curve: for each engine, each size that fits, `repetitions` timed requests. */
class ScalingRunner(
    private val executor: StepExecutor,
    private val contextOf: (InferenceTier) -> Int?,
    private val timeoutMs: Long = 120_000,
) {
    suspend fun run(
        tiers: List<InferenceTier>,
        repetitions: Int,
        onPoint: (ScalingPoint) -> Unit = {},
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): List<ScalingPoint> {
        val plan = tiers.flatMap { tier -> stepsFor(contextOf(tier)).map { tier to it } }
        val total = plan.size * repetitions
        val points = mutableListOf<ScalingPoint>()
        var done = 0
        for ((tier, chars) in plan) {
            repeat(repetitions) {
                onProgress(done, total)
                val result = withTimeoutOrNull(timeoutMs) {
                    executor.run(tier, listOf(ChatMessage(Role.USER, paddedPrompt(chars))), GenerationConfig(temperature = 0f, maxOutputTokens = 16)) {}
                }
                val point = ScalingPoint(
                    tier = tier,
                    chars = chars,
                    ttftMs = result?.metric?.firstChunkLatencyMs,
                    totalMs = result?.metric?.totalDurationMs ?: 0,
                    ok = result != null && result.ok,
                )
                points += point
                onPoint(point)
                done++
            }
        }
        onProgress(done, total)
        return points
    }
}

/** Mean time to first token per (engine, size), for the chart: repetitions are averaged. */
fun meanTtftBySize(points: List<ScalingPoint>): Map<InferenceTier, List<Pair<Int, Double>>> =
    points.filter { it.ok && it.ttftMs != null }
        .groupBy { it.tier }
        .mapValues { (_, list) ->
            list.groupBy { it.chars }.map { (chars, runs) -> chars to runs.map { it.ttftMs!!.toDouble() }.average() }.sortedBy { it.first }
        }
