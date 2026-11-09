package es.sebas1705.nanodemo.feature.lab.bench

import es.sebas1705.nanodemo.domain.models.InferenceTier
import kotlinx.serialization.Serializable

enum class BenchCategory(val label: String) {
    REASONING("Razonamiento"),
    EXTRACTION("Extracción"),
    INSTRUCTIONS("Instrucciones"),
    KNOWLEDGE("Conocimiento"),
    TRANSLATION("Traducción"),
    CODE("Código"),
    SUMMARY("Resumen"),
}

/**
 * One benchmark question with an automatic scorer. `jsonMode` asks the engine for structured output (only
 * sent to engines that support it); `maxTokens` keeps small engines inside their context.
 */
class BenchTask(
    val id: String,
    val category: BenchCategory,
    val title: String,
    val prompt: String,
    val checker: Checker,
    val jsonMode: Boolean = false,
    val maxTokens: Int = 160,
)

/** What a benchmark session runs: which engines, which categories, how many repetitions and at what temperature. */
data class BenchConfig(
    val tiers: List<InferenceTier>,
    val categories: Set<BenchCategory> = BenchCategory.entries.toSet(),
    val repetitions: Int = 1,
    val temperature: Float = 0f,
) {
    init {
        require(repetitions in 1..MAX_REPETITIONS) { "repetitions must be in 1..$MAX_REPETITIONS" }
    }

    companion object {
        const val MAX_REPETITIONS = 5
    }
}

/** One answer to one task on one engine, with everything measured about it. Serializable so sessions can be saved. */
@Serializable
data class BenchSample(
    val tier: InferenceTier,
    val taskId: String,
    val category: BenchCategory,
    val rep: Int,
    val score: Double,
    val error: String? = null,
    val latencyMs: Long = 0,
    val ttftMs: Long? = null,
    val tokens: Int = 0,
    val tokensPerSecond: Double? = null,
    val loadMs: Long? = null,
    val thermalStatus: Int? = null,
    val batteryDrainUah: Long? = null,
    val pssDeltaKb: Long? = null,
    val answer: String = "",
) {
    val passed: Boolean get() = error == null && score >= PASS_SCORE

    companion object {
        const val PASS_SCORE = 0.999
    }
}

/** A saved run: the samples plus enough context to compare it with another session later. */
@Serializable
data class BenchSession(
    val id: Long,
    val label: String,
    val startedAtMs: Long,
    val temperature: Float,
    val repetitions: Int,
    val samples: List<BenchSample>,
)

/** Progress of a running session, for the progress bar. */
data class BenchProgress(val done: Int, val total: Int, val tier: InferenceTier?, val taskTitle: String?)
