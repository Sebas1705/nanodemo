package es.sebas1705.nanodemo.feature.lab.refine

import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.VoteTally

/** An answer to be judged. `tier` is set when an engine produced it (needed to compare with the human votes). */
data class Candidate(val id: Int, val label: String, val tier: InferenceTier?, val text: String)

/**
 * One judge call on a pair: `firstId` was shown in position A and `secondId` in position B. `pick` is `null`
 * when the reply could not be read.
 */
data class PairRun(val firstId: Int, val secondId: Int, val pick: Pick?) {
    val parsed: Boolean get() = pick != null

    /** Candidate that won this run; `null` for a tie or an unreadable reply. */
    val winnerId: Int?
        get() = when (pick) {
            Pick.FIRST -> firstId
            Pick.SECOND -> secondId
            else -> null
        }
}

/** A pair judged twice with the positions swapped, to expose position bias. */
data class PairResult(val a: Int, val b: Int, val runs: List<PairRun>) {
    /** Share of the pair's points each candidate got: a win is 1, a tie 0.5 each, an unreadable run 0. */
    fun points(): Map<Int, Double> {
        val out = mutableMapOf(a to 0.0, b to 0.0)
        runs.forEach { run ->
            when {
                run.pick == null -> Unit
                run.pick == Pick.TIE -> {
                    out[a] = out.getValue(a) + 0.5
                    out[b] = out.getValue(b) + 0.5
                }
                else -> run.winnerId?.let { out[it] = out.getValue(it) + 1.0 }
            }
        }
        return out
    }

    /**
     * Whether the judge gave the same verdict (same candidate, or tie) in both orders. `null` when a run was
     * unreadable, so there is nothing to compare.
     */
    val consistent: Boolean?
        get() {
            if (runs.size < 2 || runs.any { !it.parsed }) return null
            val outcomes = runs.map { it.winnerId ?: TIE_OUTCOME }
            return outcomes.distinct().size == 1
        }

    private companion object {
        const val TIE_OUTCOME = -1
    }
}

/** `count` out of `of`, with the ratio when there is anything to divide by. */
data class Rate(val count: Int, val of: Int) {
    val ratio: Double? get() = if (of == 0) null else count.toDouble() / of
}

/** Share of pairs where the verdict survived swapping the positions. The headline number for judge reliability. */
fun consistencyRate(results: List<PairResult>): Rate {
    val comparable = results.mapNotNull { it.consistent }
    return Rate(comparable.count { it }, comparable.size)
}

/**
 * Share of decisive, readable runs that picked the first position. 0.5 is what an unbiased judge gives over a
 * balanced design; well above it means "A" is favoured regardless of content. `null` without decisive runs.
 */
fun firstPositionShare(results: List<PairResult>): Rate? {
    val decisive = results.flatMap { it.runs }.filter { it.pick == Pick.FIRST || it.pick == Pick.SECOND }
    return decisive.takeIf { it.isNotEmpty() }?.let { runs -> Rate(runs.count { it.pick == Pick.FIRST }, runs.size) }
}

/** Runs whose reply could not be read, out of all runs. */
fun unreadableRuns(results: List<PairResult>): Rate {
    val runs = results.flatMap { it.runs }
    return Rate(runs.count { !it.parsed }, runs.size)
}

data class LeaderEntry(val candidate: Candidate, val score: Double, val rank: Int)

/** Competition ranking (1, 1, 3): equal scores share a rank. Input order breaks ties in the listing only. */
private fun rank(scored: List<Pair<Candidate, Double>>): List<LeaderEntry> {
    val sorted = scored.sortedWith(compareByDescending<Pair<Candidate, Double>> { it.second }.thenBy { it.first.id })
    return sorted.map { (candidate, score) -> LeaderEntry(candidate, score, rank = sorted.indexOfFirst { it.second == score } + 1) }
}

/** Pairwise tournament standings: total points over all pairs, both orders. */
fun pairwiseLeaderboard(candidates: List<Candidate>, results: List<PairResult>): List<LeaderEntry> {
    val totals = candidates.associate { it.id to 0.0 }.toMutableMap()
    results.forEach { r -> r.points().forEach { (id, pts) -> totals[id] = (totals[id] ?: 0.0) + pts } }
    return rank(candidates.map { it to totals.getValue(it.id) })
}

/** Rubric standings: mean of the criteria; a candidate the judge could not score ranks last with 0. */
fun rubricLeaderboard(candidates: List<Candidate>, scores: Map<Int, RubricScores?>): List<LeaderEntry> =
    rank(candidates.map { it to (scores[it.id]?.average ?: 0.0) })

/** How well the judge's ranking matches the people's votes in Compare, over pairs of engines both rank differently. */
data class Agreement(val concordant: Int, val comparable: Int) {
    val ratio: Double get() = concordant.toDouble() / comparable
}

/**
 * Pairwise concordance between the judge's scores and the human win rates: among engine pairs where both
 * sides differ, the share ordered the same way. Only candidates produced by an engine with at least one
 * voted round count. `null` when there is nothing to compare (fewer than two such engines, or all tied).
 */
fun humanAgreement(entries: List<LeaderEntry>, votes: Map<InferenceTier, VoteTally>): Agreement? {
    val rated = entries.mapNotNull { e ->
        val tier = e.candidate.tier ?: return@mapNotNull null
        val human = votes[tier]?.winRate ?: return@mapNotNull null
        Triple(tier, e.score, human.toDouble())
    }.distinctBy { it.first }
    var concordant = 0
    var comparable = 0
    for (i in rated.indices) {
        for (j in i + 1 until rated.size) {
            val judge = rated[i].second.compareTo(rated[j].second)
            val human = rated[i].third.compareTo(rated[j].third)
            if (judge == 0 || human == 0) continue
            comparable++
            if (judge == human) concordant++
        }
    }
    return if (comparable == 0) null else Agreement(concordant, comparable)
}
