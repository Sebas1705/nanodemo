package es.sebas1705.nanodemo.feature.lab.agent

/** What the model's reply means for the loop. */
sealed interface AgentReply {
    val thought: String?

    /** The model wants to run [tool] (canonical name, not yet checked against the registry) with [input] (raw text, maybe empty). */
    data class Act(override val thought: String?, val tool: String, val input: String) : AgentReply

    /** The model is done. [implicit] means it answered with no `Final Answer:` label at all (tolerated, but counted). */
    data class Final(override val thought: String?, val answer: String, val implicit: Boolean) : AgentReply

    /** Nothing usable: the loop tells the model what was wrong and asks again. */
    data class Malformed(override val thought: String?, val reason: String, val raw: String) : AgentReply
}

/**
 * Parses one model reply of the ReAct protocol (`Thought:` / `Action:` / `Action Input:` / `Final Answer:`).
 * Pure and deliberately tolerant, because the small on-device models drift from the format all the time:
 *
 * - labels are case-insensitive, may be bold (`**Action:**`), inline after other text, or in Spanish
 *   (`Acción:`, `Entrada de la acción:`, `Respuesta final:`);
 * - code fences are ignored; text before the first label is ignored;
 * - several actions in one reply: the first one wins (the rest was written without seeing an observation);
 * - `Final Answer` ends the run only if no `Action` precedes it, because an action that comes first has not
 *   been executed yet and the answer after it was guessed;
 * - no labels at all is read as a direct answer ([AgentReply.Final.implicit]);
 * - `Action: calculator("2+2")` / `calculator[2+2]` is accepted when there is no `Action Input` line.
 */
object AgentParser {

    private enum class Label { THOUGHT, ACTION, INPUT, FINAL, OBSERVATION }

    private class Mark(val label: Label, val start: Int, val end: Int)

    /**
     * Labels that are safe to recognise anywhere in a line. The bare ones (`Answer`, `Input`, Spanish
     * `Respuesta`/`Entrada`) are only accepted at the start of a line, see [findMarks].
     */
    private val LABEL = Regex(
        """(?<![\p{L}\p{N}_])(?:\*\*|__)?(?:\s*#+\s*)?""" +
            """(Thought|Pensamiento|Razonamiento|Action[ _]?Input|Entrada\s+de\s+(?:la\s+)?acci[oó]n|Acci[oó]n[ _]?Input|""" +
            """Action|Acci[oó]n|Final[ _]?Answer|Respuesta[ _]?final|Observation|Observaci[oó]n|Answer|Input|Entrada|Respuesta)""" +
            """(?:\*\*|__)?\s*:\s*(?:\*\*|__)?""",
        RegexOption.IGNORE_CASE,
    )

    private val FENCE_LINE = Regex("""(?m)^[ \t]*(?:```|~~~)[\w+-]*[ \t]*$""")

    fun parse(raw: String): AgentReply {
        val text = FENCE_LINE.replace(raw.replace("\r\n", "\n").replace('\r', '\n'), "").trim()
        if (text.isEmpty()) return AgentReply.Malformed(null, "empty reply", raw)

        val marks = findMarks(text)
        if (marks.isEmpty()) return AgentReply.Final(null, text, implicit = true)

        val thought = marks.firstOrNull { it.label == Label.THOUGHT }
            ?.let { sliceUntilNext(text, marks, it) }?.takeIf { it.isNotEmpty() }
        val action = marks.firstOrNull { it.label == Label.ACTION }
        val final = marks.firstOrNull { it.label == Label.FINAL }

        if (final != null && (action == null || final.start < action.start)) {
            val answer = answerAfter(text, marks, final)
            return if (answer.isEmpty()) {
                AgentReply.Malformed(thought, "empty Final Answer", raw)
            } else {
                AgentReply.Final(thought, answer, implicit = false)
            }
        }

        if (action != null) return parseAction(text, marks, action, thought, raw)

        return AgentReply.Malformed(thought, "no Action and no Final Answer", raw)
    }

    private fun findMarks(text: String): List<Mark> = LABEL.findAll(text).mapNotNull { m ->
        val name = m.groupValues[1].lowercase().replace('_', ' ').replace(Regex("\\s+"), " ")
        val label = when {
            name.startsWith("thought") || name == "pensamiento" || name == "razonamiento" -> Label.THOUGHT
            name.replace(" ", "") in setOf("actioninput", "accióninput", "accioninput") || name.startsWith("entrada de") -> Label.INPUT
            name.startsWith("action") || name.startsWith("acci") -> Label.ACTION
            name.replace(" ", "") in setOf("finalanswer", "respuestafinal") -> Label.FINAL
            name.startsWith("observ") -> Label.OBSERVATION
            name == "answer" || name == "respuesta" -> Label.FINAL
            name == "input" || name == "entrada" -> Label.INPUT
            else -> return@mapNotNull null
        }
        // Bare words ("Answer:", "Input:", "Respuesta:", "Entrada:") only count as labels at the start of a line.
        val bare = name in setOf("answer", "input", "respuesta", "entrada")
        if (bare && !atLineStart(text, m.range.first)) return@mapNotNull null
        // "Action: ..." inside a sentence of free text is ambiguous; keep it, it is still the best signal we have.
        Mark(label, m.range.first, m.range.last + 1)
    }.toList()

    private fun atLineStart(text: String, index: Int): Boolean {
        var i = index - 1
        while (i >= 0 && text[i] != '\n') {
            if (!text[i].isWhitespace() && text[i] !in "-*>#•") return false
            i--
        }
        return true
    }

    /** Text of `mark` up to the start of the next mark (or the end). */
    private fun sliceUntilNext(text: String, marks: List<Mark>, mark: Mark): String {
        val next = marks.firstOrNull { it.start >= mark.end }?.start ?: text.length
        return text.substring(mark.end, next).trim()
    }

    /** Everything after `Final Answer:`, cut at a following Thought/Action/Observation that starts a line (the model kept going). */
    private fun answerAfter(text: String, marks: List<Mark>, final: Mark): String {
        val cut = marks.firstOrNull {
            it.start >= final.end && it.label != Label.FINAL && it.label != Label.INPUT && atLineStart(text, it.start)
        }?.start ?: text.length
        return text.substring(final.end, cut).trim().trimEnd('`').trim()
    }

    private fun parseAction(text: String, marks: List<Mark>, action: Mark, thought: String?, raw: String): AgentReply {
        val nextMark = marks.firstOrNull { it.start >= action.end }
        val inputMark = nextMark?.takeIf { it.label == Label.INPUT }
        val nameZoneEnd = nextMark?.start ?: text.length
        val nameZone = text.substring(action.end, nameZoneEnd).trim()

        var tool: String
        var input: String
        if (inputMark != null) {
            tool = nameZone.lineSequence().firstOrNull().orEmpty()
            input = extractInput(text, marks, inputMark)
        } else {
            // No "Action Input": maybe "calculator(...)", "calculator[...]" or the input on the next line.
            val firstLine = nameZone.lineSequence().firstOrNull().orEmpty()
            val rest = nameZone.substringAfter('\n', "").trim()
            val call = Regex("""^([^(\[{]*?)\s*[(\[](.*)[)\]]\s*$""", RegexOption.DOT_MATCHES_ALL).matchEntire(firstLine)
            if (call != null) {
                tool = call.groupValues[1]
                input = call.groupValues[2].trim()
            } else {
                val braceAt = firstLine.indexOf('{')
                if (braceAt >= 0) {
                    tool = firstLine.substring(0, braceAt)
                    input = firstLine.substring(braceAt)
                } else {
                    tool = firstLine
                    input = rest
                }
            }
            input = cleanInput(input)
        }
        tool = canonicalToolName(tool)
        if (tool.isEmpty()) return AgentReply.Malformed(thought, "Action has no tool name", raw)
        return AgentReply.Act(thought, tool, input)
    }

    /**
     * The value after `Action Input:`. An object is read to its balanced closing brace (so trailing
     * prose or a hallucinated `Observation:` after it is dropped, and a `Thought:` inside a string is kept);
     * anything else runs to the next label.
     */
    private fun extractInput(text: String, marks: List<Mark>, inputMark: Mark): String {
        val after = text.substring(inputMark.end)
        val trimmed = after.trimStart()
        val offset = after.length - trimmed.length
        if (trimmed.startsWith("{")) {
            val end = balancedEnd(trimmed)
            return if (end >= 0) trimmed.substring(0, end + 1) else cleanInput(untilNextMark(text, marks, inputMark.end + offset))
        }
        return cleanInput(untilNextMark(text, marks, inputMark.end))
    }

    private fun untilNextMark(text: String, marks: List<Mark>, from: Int): String {
        val next = marks.firstOrNull { it.start >= from }?.start ?: text.length
        return text.substring(from, next)
    }

    /** Index of the bracket that closes the one at index 0, ignoring brackets inside quotes; -1 if it never closes. */
    private fun balancedEnd(s: String): Int {
        var depth = 0
        var quote: Char? = null
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (quote != null) {
                if (c == '\\') i++ else if (c == quote) quote = null
            } else when (c) {
                '"', '\'' -> quote = c
                '{', '[' -> depth++
                '}', ']' -> if (--depth == 0) return i
            }
            i++
        }
        return -1
    }

    /** Strips code fences, backticks and wrapping quotes; keeps the first paragraph only. */
    private fun cleanInput(input: String): String {
        var s = input.trim()
        s = s.replace(Regex("""^```[\w-]*\s*|\s*```$"""), "").trim()
        s = s.trim('`').trim()
        val firstParagraph = s.split(Regex("\n\\s*\n")).firstOrNull().orEmpty().trim()
        s = firstParagraph
        if (s.length >= 2 && ((s.first() == '"' && s.last() == '"') || (s.first() == '\'' && s.last() == '\''))) {
            s = s.substring(1, s.length - 1).trim()
        }
        return s
    }

    /** "`Current Time`." -> "current_time". */
    fun canonicalToolName(raw: String): String =
        raw.trim().trim('`', '"', '\'', '*', '.', ':', ';', ' ').lowercase()
            .replace(Regex("[\\s\\-]+"), "_")
            .filter { it.isLetterOrDigit() || it == '_' }
}
