package es.sebas1705.nanodemo.feature.lab.bench

import es.sebas1705.nanodemo.domain.models.InferenceTier

/** What two "same" answers share once case, accents, punctuation and spacing are ignored. */
internal fun answerKey(text: String): String = normalizeText(text).filter { it.isLetterOrDigit() }

/** Linear-interpolated percentile of unsorted values, `p` in 0..100. `null` for an empty list. */
fun percentile(values: List<Long>, p: Double): Long? {
    if (values.isEmpty()) return null
    val sorted = values.sorted()
    if (sorted.size == 1) return sorted[0]
    val rank = (p / 100.0) * (sorted.size - 1)
    val low = rank.toInt()
    val high = minOf(low + 1, sorted.size - 1)
    val fraction = rank - low
    return Math.round(sorted[low] + (sorted[high] - sorted[low]) * fraction)
}

/** The headline numbers of one engine in a session. Failed runs count against pass rate but not against speed. */
data class TierSummary(
    val tier: InferenceTier,
    val runs: Int,
    val errors: Int,
    val passRate: Double,
    val meanScore: Double,
    val latencyP50Ms: Long?,
    val latencyP95Ms: Long?,
    val ttftP50Ms: Long?,
    val tokensPerSecond: Double?,
    val totalTokens: Int,
    val maxLoadMs: Long?,
    val maxThermalStatus: Int?,
    val batteryDrainUah: Long?,
)

fun summarize(samples: List<BenchSample>): List<TierSummary> =
    samples.groupBy { it.tier }.map { (tier, runs) ->
        val ok = runs.filter { it.error == null }
        TierSummary(
            tier = tier,
            runs = runs.size,
            errors = runs.count { it.error != null },
            passRate = runs.count { it.passed }.toDouble() / runs.size,
            meanScore = runs.map { if (it.error == null) it.score else 0.0 }.average(),
            latencyP50Ms = percentile(ok.map { it.latencyMs }, 50.0),
            latencyP95Ms = percentile(ok.map { it.latencyMs }, 95.0),
            ttftP50Ms = percentile(ok.mapNotNull { it.ttftMs }, 50.0),
            tokensPerSecond = ok.mapNotNull { it.tokensPerSecond }.takeIf { it.isNotEmpty() }?.average(),
            totalTokens = ok.sumOf { it.tokens },
            maxLoadMs = ok.mapNotNull { it.loadMs }.maxOrNull(),
            maxThermalStatus = ok.mapNotNull { it.thermalStatus }.maxOrNull(),
            batteryDrainUah = ok.mapNotNull { it.batteryDrainUah }.takeIf { it.isNotEmpty() }?.sum(),
        )
    }.sortedWith(compareByDescending<TierSummary> { it.passRate }.thenByDescending { it.meanScore }.thenBy { it.latencyP50Ms ?: Long.MAX_VALUE })

/** Pass rate of one engine on one category, and how many runs it is based on. */
data class HeatCell(val passRate: Double, val runs: Int)

fun heatmap(samples: List<BenchSample>): Map<Pair<InferenceTier, BenchCategory>, HeatCell> =
    samples.groupBy { it.tier to it.category }.mapValues { (_, runs) ->
        HeatCell(runs.count { it.passed }.toDouble() / runs.size, runs.size)
    }

/**
 * Share of tasks where every repetition gave the same normalised answer, per engine. Only tasks run at least
 * twice count; `null` when nothing was repeated. At temperature 0 this should be 1.0: anything lower is
 * non-determinism the engine adds on its own.
 */
fun determinism(samples: List<BenchSample>): Map<InferenceTier, Double?> =
    samples.groupBy { it.tier }.mapValues { (_, runs) ->
        val repeated = runs.filter { it.error == null }.groupBy { it.taskId }.filterValues { it.size >= 2 }
        if (repeated.isEmpty()) {
            null
        } else {
            repeated.count { (_, reps) -> reps.map { answerKey(it.answer) }.distinct().size == 1 }.toDouble() / repeated.size
        }
    }

/** Change of a session against a baseline, per engine present in both. Positive pass-rate delta = better. */
data class TierDelta(val tier: InferenceTier, val passRateDelta: Double, val latencyP50DeltaMs: Long?)

fun compareSessions(current: List<BenchSample>, baseline: List<BenchSample>): List<TierDelta> {
    val base = summarize(baseline).associateBy { it.tier }
    return summarize(current).mapNotNull { now ->
        val before = base[now.tier] ?: return@mapNotNull null
        TierDelta(
            tier = now.tier,
            passRateDelta = now.passRate - before.passRate,
            latencyP50DeltaMs = if (now.latencyP50Ms != null && before.latencyP50Ms != null) now.latencyP50Ms - before.latencyP50Ms else null,
        )
    }
}

/** CSV of every sample, for a spreadsheet. */
fun samplesToCsv(samples: List<BenchSample>): String = buildString {
    appendLine("tier,task,category,rep,score,passed,error,latencyMs,ttftMs,tokens,tokensPerSecond,loadMs,thermalStatus,batteryDrainUah,pssDeltaKb")
    samples.forEach { s ->
        appendLine(
            listOf(
                s.tier, s.taskId, s.category, s.rep, "%.3f".format(java.util.Locale.US, s.score), s.passed,
                s.error?.replace(',', ';')?.replace('\n', ' ') ?: "", s.latencyMs, s.ttftMs ?: "", s.tokens,
                s.tokensPerSecond?.let { "%.1f".format(java.util.Locale.US, it) } ?: "", s.loadMs ?: "",
                s.thermalStatus ?: "", s.batteryDrainUah ?: "", s.pssDeltaKb ?: "",
            ).joinToString(","),
        )
    }
}
