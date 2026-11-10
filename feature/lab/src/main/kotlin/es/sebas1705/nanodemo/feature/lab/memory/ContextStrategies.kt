package es.sebas1705.nanodemo.feature.lab.memory

import es.sebas1705.nanodemo.domain.models.ChatMessage
import es.sebas1705.nanodemo.domain.models.Role
import es.sebas1705.nanodemo.feature.lab.bench.normalizeText

/** One exchange of a scripted conversation: what the user said and what the assistant answered. */
data class Turn(val user: String, val assistant: String)

private const val CHARS_PER_TOKEN = 4
private const val MESSAGE_OVERHEAD_TOKENS = 4

fun estimateTokens(text: String): Int = (text.length + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN

fun estimateTokens(messages: List<ChatMessage>): Int = messages.sumOf { estimateTokens(it.text) + MESSAGE_OVERHEAD_TOKENS }

private fun Turn.cost(): Int = estimateTokens(user) + estimateTokens(assistant) + 2 * MESSAGE_OVERHEAD_TOKENS

fun turnsToMessages(turns: List<Turn>): List<ChatMessage> =
    turns.flatMap { listOf(ChatMessage(Role.USER, it.user), ChatMessage(Role.MODEL, it.assistant)) }

/** Most recent turns whose total cost fits `budgetTokens`, oldest first. Never splits a turn. */
fun fitRecentTurns(turns: List<Turn>, budgetTokens: Int): List<Turn> {
    var used = 0
    val kept = ArrayDeque<Turn>()
    for (turn in turns.asReversed()) {
        val cost = turn.cost()
        if (used + cost > budgetTokens) break
        used += cost
        kept.addFirst(turn)
    }
    return kept.toList()
}

private fun question(text: String) = ChatMessage(Role.USER, text)

/** Strategy 1: send everything. Exact, but it overflows small engines once the conversation grows. */
fun buildFull(turns: List<Turn>, question: String): List<ChatMessage> = turnsToMessages(turns) + question(question)

/** Strategy 2: keep only the newest turns that fit; whatever was said earlier is simply forgotten. */
fun buildWindow(turns: List<Turn>, question: String, budgetTokens: Int): List<ChatMessage> {
    val kept = fitRecentTurns(turns, budgetTokens - estimateTokens(question) - MESSAGE_OVERHEAD_TOKENS)
    return turnsToMessages(kept) + question(question)
}

/** The split behind the rolling-summary strategy: what stays verbatim and what gets compressed. */
data class SummaryPlan(val recent: List<Turn>, val toSummarize: List<Turn>)

/** Share of the budget the verbatim recent turns may take; the summary and the question use the rest. */
private const val RECENT_SHARE = 0.4

fun planSummary(turns: List<Turn>, question: String, budgetTokens: Int): SummaryPlan {
    val recentBudget = (budgetTokens * RECENT_SHARE).toInt() - estimateTokens(question)
    val recent = fitRecentTurns(turns, recentBudget)
    return SummaryPlan(recent = recent, toSummarize = turns.dropLast(recent.size))
}

private fun transcript(turns: List<Turn>): String = turns.joinToString("\n") { "User: ${it.user}\nAssistant: ${it.assistant}" }

/** Model-facing instruction (English, cheaper in tokens) for compressing the older part of a conversation. */
fun summarizationPrompt(older: List<Turn>, maxWords: Int = 80): String =
    "Summarize the conversation below in at most $maxWords words. Keep every name, number, date, place and " +
        "preference the user stated. Output only the summary.\n\n${transcript(older)}"

/** Strategy 3: the older turns become a model-written summary; the newest stay word for word. */
fun buildSummarized(plan: SummaryPlan, summary: String, question: String): List<ChatMessage> =
    listOf(
        ChatMessage(Role.USER, "Summary of the earlier conversation: ${summary.trim()}"),
        ChatMessage(Role.MODEL, "Understood."),
    ) + turnsToMessages(plan.recent) + question(question)

/** Model-facing instruction for extracting durable facts about the user from the whole conversation. */
fun factsPrompt(turns: List<Turn>): String =
    "List every fact the user stated about themselves, their plans and their preferences (names, numbers, dates, " +
        "places, likes). One fact per line, each starting with \"- \". Facts only, no commentary.\n\n${transcript(turns)}"

private const val MAX_FACTS = 24
private const val MAX_FACT_CHARS = 160
private val BULLET = Regex("^\\s*(?:[-*•]|\\d+[.)])\\s+")

/** Facts from a model reply: bullets or numbered lines (or a JSON-ish list), deduplicated, capped in count and length. */
fun parseFacts(reply: String): List<String> {
    val lines = reply.replace("```", "").lines()
    val bulleted = lines.filter { BULLET.containsMatchIn(it) }.map { it.replace(BULLET, "") }
    val candidates = bulleted.ifEmpty {
        // No bullets: accept a JSON array of strings or plain lines that look like statements.
        val quoted = Regex("\"([^\"]{3,})\"").findAll(reply).map { it.groupValues[1] }.toList()
        quoted.ifEmpty { lines.map { it.trim() }.filter { it.length > 3 } }
    }
    return candidates.map { it.trim().trim(',', ';').trim('"').trim() }
        .filter { it.length >= 3 }
        .map { it.take(MAX_FACT_CHARS) }
        .distinctBy { normalizeText(it) }
        .take(MAX_FACTS)
}

private val STOPWORDS = setOf("the", "a", "an", "is", "of", "and", "to", "in", "my", "me", "what", "which", "who", "how", "do", "i", "el", "la", "los", "las", "de", "que", "y", "en", "un", "una", "mi", "es", "se", "me", "cual", "como", "con", "por", "para")

/** Facts ranked by word overlap with the question; every fact when few enough to fit. */
fun selectFacts(facts: List<String>, question: String, max: Int = 12): List<String> {
    if (facts.size <= max) return facts
    val wanted = normalizeText(question).split(' ').filter { it.length > 2 && it !in STOPWORDS }.toSet()
    return facts
        .map { fact -> fact to normalizeText(fact).split(' ').count { it in wanted } }
        .sortedByDescending { it.second }
        .take(max)
        .map { it.first }
}

/** Strategy 4: durable facts extracted once, plus the newest turns; the question sees only what matters. */
fun buildWithFacts(facts: List<String>, recent: List<Turn>, question: String): List<ChatMessage> {
    val header = if (facts.isEmpty()) "" else "Known facts about the user:\n" + facts.joinToString("\n") { "- $it" } + "\n\n"
    return turnsToMessages(recent) + question(header + question)
}

/** Share of the budget the verbatim turns may take in the facts strategy. */
private const val FACTS_RECENT_SHARE = 0.25

fun planFacts(turns: List<Turn>, question: String, budgetTokens: Int): List<Turn> =
    fitRecentTurns(turns, (budgetTokens * FACTS_RECENT_SHARE).toInt() - estimateTokens(question))

/**
 * Fraction of the expected facts present in `answer`. Each expected fact lists acceptable spellings; a fact is
 * recalled when any of them appears (case and accent insensitive).
 */
fun recallScore(answer: String, expected: List<List<String>>): Double {
    if (expected.isEmpty()) return 1.0
    val haystack = normalizeText(answer)
    return expected.count { options -> options.any { normalizeText(it) in haystack } }.toDouble() / expected.size
}

/**
 * Splits turns into consecutive groups that each cost at most `maxTokens` (a turn that is alone too big gets its
 * own group). Used so that the helper calls (summaries, fact extraction) never overflow a small engine either.
 */
fun chunkTurns(turns: List<Turn>, maxTokens: Int): List<List<Turn>> {
    val groups = mutableListOf<MutableList<Turn>>()
    var used = 0
    for (turn in turns) {
        val cost = turn.cost()
        if (groups.isEmpty() || used + cost > maxTokens) {
            groups += mutableListOf(turn)
            used = cost
        } else {
            groups.last() += turn
            used += cost
        }
    }
    return groups
}
