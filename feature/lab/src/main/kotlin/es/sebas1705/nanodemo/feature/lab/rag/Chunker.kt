package es.sebas1705.nanodemo.feature.lab.rag

/**
 * Chunking settings. `targetChars` is the soft maximum size of a chunk body (a chunk never exceeds it);
 * `overlapChars` is how much of the previous chunk is repeated at the start of the next one, in whole
 * sentences, so an answer that straddles a boundary is still found whole in one of the two chunks.
 */
data class ChunkerConfig(val targetChars: Int = 500, val overlapChars: Int = 80) {
    /** Target after clamping: a tiny target would make every sentence its own chunk. */
    val target: Int get() = targetChars.coerceAtLeast(MIN_TARGET_CHARS)

    /** Overlap after clamping: at most half a chunk, so each chunk always carries mostly new content. */
    val overlap: Int get() = overlapChars.coerceIn(0, target / 2)

    companion object {
        const val MIN_TARGET_CHARS = 80

        /** Overlap proportional to the chunk size (the UI exposes one slider, not two). */
        fun forTarget(targetChars: Int): ChunkerConfig = ChunkerConfig(targetChars, targetChars / 6)
    }
}

/** A chunk produced by [Chunker]: `id` is `<docId>#<index>`, stable for the same text and settings. */
data class TextChunk(val id: String, val index: Int, val heading: String?, val text: String)

/**
 * Sentence- and paragraph-aware text splitter.
 *
 * - Markdown headings start a new section; chunks never cross a heading and carry its breadcrumb
 *   (`Handbook > Vacations`) in `heading` instead of repeating the line in the body.
 * - Inside a section, paragraphs are split into sentences (list items count as their own unit) which are packed
 *   greedily up to the target size; blank-line paragraph breaks are kept inside a chunk.
 * - A sentence longer than the target is cut at the best clause or word boundary before the limit.
 * - Consecutive chunks overlap by whole sentences. Chunks are never empty and never longer than the target.
 */
object Chunker {

    /** A sentence and what goes before it when it is not first in a chunk (space, newline or blank line). */
    private class Piece(val text: String, val separator: String)
    private class Section(val heading: String?, val paragraphs: List<List<String>>)

    private val headingLine = Regex("""^\s{0,3}(#{1,6})\s+(.*?)\s*#*\s*$""")
    private val listMarker = Regex("""^\s*(?:[-*+•]|\d{1,3}[.)])\s+""")
    private val sentenceBoundary = Regex("""(?<=[.!?…])\s+(?=[¿¡"«“(\p{Lu}\d])""")

    fun chunk(docId: String, text: String, config: ChunkerConfig = ChunkerConfig()): List<TextChunk> {
        val target = config.target
        val overlap = config.overlap
        val result = mutableListOf<TextChunk>()
        for (section in parseSections(text)) {
            val pieces = section.paragraphs.flatMap { paragraph -> piecesOf(paragraph, target) }
            packSection(pieces, target, overlap).forEach { body ->
                result += TextChunk("$docId#${result.size}", result.size, section.heading, body)
            }
        }
        return result
    }

    /** Splits Markdown-ish text into sections (one per heading) of paragraphs of lines. */
    private fun parseSections(text: String): List<Section> {
        val sections = mutableListOf<Section>()
        val breadcrumb = ArrayList<Pair<Int, String>>()
        var heading: String? = null
        var paragraphs = mutableListOf<List<String>>()
        var lines = mutableListOf<String>()

        fun closeParagraph() {
            if (lines.isNotEmpty()) paragraphs += lines
            lines = mutableListOf()
        }
        fun closeSection() {
            closeParagraph()
            if (paragraphs.isNotEmpty()) sections += Section(heading, paragraphs)
            paragraphs = mutableListOf()
        }

        for (raw in text.replace("\r\n", "\n").replace('\r', '\n').lines()) {
            val match = headingLine.matchEntire(raw)
            when {
                match != null -> {
                    closeSection()
                    val level = match.groupValues[1].length
                    val title = match.groupValues[2].trim()
                    while (breadcrumb.isNotEmpty() && breadcrumb.last().first >= level) breadcrumb.removeAt(breadcrumb.lastIndex)
                    if (title.isNotEmpty()) breadcrumb += level to title
                    heading = breadcrumb.takeIf { it.isNotEmpty() }?.joinToString(" > ") { it.second }
                }
                raw.isBlank() -> closeParagraph()
                else -> lines += raw.trim()
            }
        }
        closeSection()
        return sections
    }

    /** Sentences of one paragraph; the first piece starts the paragraph. List items start their own sentence. */
    private fun piecesOf(lines: List<String>, target: Int): List<Piece> {
        val blocks = mutableListOf<String>()
        for (line in lines) {
            if (blocks.isEmpty() || listMarker.containsMatchIn(line)) blocks += line else blocks[blocks.lastIndex] += " $line"
        }
        return blocks.flatMapIndexed { blockIndex, block ->
            val sentences = block.split(sentenceBoundary)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .flatMap { splitLong(it, target) }
            // A paragraph starts after a blank line; a list item starts on its own line; the rest follow with a space.
            sentences.mapIndexed { i, s ->
                Piece(s, if (i > 0) " " else if (blockIndex == 0) "\n\n" else "\n")
            }
        }
    }

    /** Cuts a sentence longer than `target` at a clause, then word, boundary; hard-cuts words that are themselves too long. */
    private fun splitLong(sentence: String, target: Int): List<String> {
        if (sentence.length <= target) return listOf(sentence)
        val parts = mutableListOf<String>()
        var rest = sentence
        while (rest.length > target) {
            val window = rest.substring(0, target + 1)
            val minCut = target / 2
            var cut = window.lastIndexOfAny(charArrayOf(',', ';', ':'), startIndex = target - 1).takeIf { it >= minCut }?.plus(1)
            if (cut == null) cut = window.lastIndexOf(' ').takeIf { it >= minCut }
            if (cut == null) cut = target
            parts += rest.substring(0, cut).trim()
            rest = rest.substring(cut).trim()
        }
        if (rest.isNotEmpty()) parts += rest
        return parts.filter { it.isNotEmpty() }
    }

    private fun render(pieces: List<Piece>): String = buildString {
        pieces.forEachIndexed { i, p ->
            if (i > 0) append(p.separator)
            append(p.text)
        }
    }

    private fun packSection(pieces: List<Piece>, target: Int, overlap: Int): List<String> {
        val chunks = mutableListOf<String>()
        var current = mutableListOf<Piece>()
        var fresh = 0
        for (piece in pieces) {
            if (current.isNotEmpty() && render(current + piece).length > target) {
                if (fresh > 0) chunks += render(current)
                current = overlapTail(current, piece, target, overlap)
                fresh = 0
            }
            current += piece
            fresh++
        }
        if (fresh > 0) chunks += render(current)
        return chunks.filter { it.isNotBlank() }
    }

    /** The trailing whole sentences of `previous` to repeat, as long as they fit the overlap and leave room for `next`. */
    private fun overlapTail(previous: List<Piece>, next: Piece, target: Int, overlap: Int): MutableList<Piece> {
        val tail = ArrayDeque<Piece>()
        for (piece in previous.asReversed()) {
            val candidate = listOf(piece) + tail
            if (render(candidate).length > overlap || render(candidate + next).length > target) break
            tail.addFirst(piece)
        }
        return tail.toMutableList()
    }
}
