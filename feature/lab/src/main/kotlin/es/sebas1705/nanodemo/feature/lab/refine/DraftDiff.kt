package es.sebas1705.nanodemo.feature.lab.refine

enum class DiffKind { SAME, ADDED, REMOVED }

data class DiffLine(val kind: DiffKind, val text: String)

/**
 * What changed between two versions of a draft, at the level of lines and sentences. `similarity` is the share
 * of units both versions have in common (1.0 = identical, 0.0 = nothing kept).
 */
data class DraftChange(val lines: List<DiffLine>, val lengthDelta: Int) {
    val added: Int get() = lines.count { it.kind == DiffKind.ADDED }
    val removed: Int get() = lines.count { it.kind == DiffKind.REMOVED }
    val kept: Int get() = lines.count { it.kind == DiffKind.SAME }
    val identical: Boolean get() = added == 0 && removed == 0
    val similarity: Float
        get() {
            val oldCount = kept + removed
            val newCount = kept + added
            val biggest = maxOf(oldCount, newCount)
            return if (biggest == 0) 1f else kept.toFloat() / biggest
        }
}

// Not after a digit, so list markers ("1. Mix") and decimals stay attached to their line.
private val SENTENCE_BREAK = Regex("(?<=\\D[.!?])\\s+")

private fun units(text: String): List<String> = text.lines()
    .flatMap { line -> line.trim().split(SENTENCE_BREAK) }
    .map { it.trim() }
    .filter { it.isNotEmpty() }

private fun key(unit: String) = unit.lowercase().replace(Regex("\\s+"), " ")

/** Longest-common-subsequence diff of `old` vs `new` over lines and sentences, preserving the order of both. */
fun diffDrafts(old: String, new: String): DraftChange {
    val a = units(old)
    val b = units(new)
    val ka = a.map(::key)
    val kb = b.map(::key)
    // lcs[i][j] = length of the LCS of a[i..] and b[j..]
    val lcs = Array(a.size + 1) { IntArray(b.size + 1) }
    for (i in a.indices.reversed()) {
        for (j in b.indices.reversed()) {
            lcs[i][j] = if (ka[i] == kb[j]) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
        }
    }
    val lines = mutableListOf<DiffLine>()
    var i = 0
    var j = 0
    while (i < a.size && j < b.size) {
        when {
            ka[i] == kb[j] -> { lines += DiffLine(DiffKind.SAME, b[j]); i++; j++ }
            lcs[i + 1][j] >= lcs[i][j + 1] -> { lines += DiffLine(DiffKind.REMOVED, a[i]); i++ }
            else -> { lines += DiffLine(DiffKind.ADDED, b[j]); j++ }
        }
    }
    while (i < a.size) lines += DiffLine(DiffKind.REMOVED, a[i++])
    while (j < b.size) lines += DiffLine(DiffKind.ADDED, b[j++])
    return DraftChange(lines, new.trim().length - old.trim().length)
}
