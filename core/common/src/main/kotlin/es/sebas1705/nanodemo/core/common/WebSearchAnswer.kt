package es.sebas1705.nanodemo.core.common

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

// Presupuesto de contexto chico a propósito: MediaPipe tiene 1024 tokens en total (prompt + respuesta)
// y LiteRT-LM 2048, así que el bloque de búsqueda tiene que ser corto (y MediaPipe usa el modo compacto).
private const val PAGES_TO_READ = 2
private const val PAGE_EXCERPT_CHARS = 700
private const val DESCRIPTION_CHARS = 220
private const val PAGE_TIMEOUT_MS = 6_000L
private const val QUERY_MAX_CHARS = 200
private const val SHORT_QUESTION_CHARS = 25
private const val COMPACT_RESULTS = 3
private const val COMPACT_DESCRIPTION_CHARS = 160

// Cuánto contexto suma, como mucho, UN turno con búsqueda (estimación en tokens, chars/4): 5 resultados de
// ~280 chars + 2 extractos de 700 + instrucción de formato ≈ 3.4k chars. El compacto (3 resultados de ~220
// chars + formato) ≈ 1.1k chars. Lo usa el medidor de contexto del Chat.
const val SEARCH_CONTEXT_OVERHEAD_TOKENS = 850
const val SEARCH_CONTEXT_OVERHEAD_TOKENS_COMPACT = 280

/**
 * Resultado de intentar buscar antes de responder. `marker` siempre se muestra (es el aviso de "hubo un paso
 * de búsqueda", que el Chat pinta como chip: empieza con 🔧). `prompt` reemplaza el texto del turno del
 * usuario para ESTA generación (no el guardado en el historial); `null` si no se pudo buscar y se responde
 * sin resultados. `sourcesBlock` es la lista de fuentes en Markdown que la app agrega al final.
 */
data class SearchAugmentation(val marker: String, val prompt: String?, val sourcesBlock: String?)

/**
 * Búsqueda SIEMPRE antes de responder (no depende de que un modelo chico decida buscar): arma la consulta,
 * busca en Brave, lee un extracto de las primeras páginas y devuelve el turno enriquecido + instrucciones de
 * síntesis y formato. Los 3 tiers locales usan esto en una sola pasada, así la respuesta también se ve en streaming.
 */
suspend fun augmentWithWebSearch(
    userText: String,
    previousUserText: String?,
    texts: AppTexts,
    compact: Boolean = false,
): SearchAugmentation {
    val query = buildQuery(extractQuestion(userText), previousUserText?.let(::extractQuestion))
    val results = when (val outcome = WebSearchTool.search(query)) {
        is WebSearchOutcome.Unavailable ->
            return SearchAugmentation(toolMarker(texts.searchUnavailableNotice(outcome.failure)), null, null)
        is WebSearchOutcome.Found -> if (compact) outcome.results.take(COMPACT_RESULTS) else outcome.results
    }

    // Modo compacto (contexto de ~1024 tokens, p.ej. MediaPipe): sin extractos de páginas y con menos texto.
    val excerpts = if (compact) emptyList() else coroutineScope {
        results.take(PAGES_TO_READ).map { result ->
            async {
                withTimeoutOrNull(PAGE_TIMEOUT_MS) { runCatching { fetchUrlText(result.url) }.getOrNull() }
                    ?.take(PAGE_EXCERPT_CHARS)
                    ?.takeIf { it.isNotBlank() }
            }
        }.awaitAll()
    }

    return SearchAugmentation(
        marker = toolMarker(texts.searchDoneNotice(query, results.size)),
        prompt = buildSearchPrompt(query, results, excerpts, userText, texts, compact),
        sourcesBlock = buildSourcesBlock(results, texts),
    )
}

/** El turno enriquecido: resultados, extractos, la pregunta y las instrucciones de formato. */
internal fun buildSearchPrompt(
    query: String,
    results: List<WebResult>,
    excerpts: List<String?>,
    userText: String,
    texts: AppTexts,
    compact: Boolean,
): String = buildString {
    appendLine(texts.searchResultsHeader(query))
    results.forEachIndexed { index, result ->
        appendLine("[${index + 1}] ${result.title} — ${result.description.take(if (compact) COMPACT_DESCRIPTION_CHARS else DESCRIPTION_CHARS)}")
    }
    excerpts.forEachIndexed { index, excerpt ->
        if (excerpt != null) appendLine(texts.pageExcerpt(index + 1, excerpt))
    }
    appendLine()
    val original = userText.trim()
    appendLine(if (hasQuestionLabel(original)) original else "${texts.questionLabel} $original")
    appendLine()
    append(texts.searchAnswerFormat)
}

/**
 * La lista de fuentes en Markdown. Al modelo se le pide citar en el texto con [1], [2]… pero NO listarlas:
 * esta lista la agrega la app (es determinista; un modelo chico se equivoca o se la salta).
 */
internal fun buildSourcesBlock(results: List<WebResult>, texts: AppTexts): String {
    val sources = results.mapIndexed { index, result ->
        val title = result.title.replace("[", "(").replace("]", ")")
        "${index + 1}. [$title](${result.url.replace(")", "%29")})"
    }.joinToString("\n")
    return "**${texts.sourcesHeading}**\n$sources"
}

/** La consulta sale de la pregunta; si es muy corta ("¿y en Madrid?") se le suma la anterior para darle contexto. */
internal fun buildQuery(question: String, previousQuestion: String?): String {
    val base = if (question.length < SHORT_QUESTION_CHARS && !previousQuestion.isNullOrBlank()) {
        "$previousQuestion $question"
    } else {
        question
    }
    return base.replace(Regex("\\s+"), " ").trim().take(QUERY_MAX_CHARS)
}
