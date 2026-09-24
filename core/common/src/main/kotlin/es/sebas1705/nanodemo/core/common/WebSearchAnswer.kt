package es.sebas1705.nanodemo.core.common

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

// Presupuesto de contexto chico a propósito: MediaPipe tiene ~1024 tokens en total (prompt + respuesta)
// y LiteRT-LM 2048, así que el bloque de búsqueda no puede pasar de unos ~600 tokens.
private const val PAGES_TO_READ = 2
private const val PAGE_EXCERPT_CHARS = 700
private const val DESCRIPTION_CHARS = 220
private const val PAGE_TIMEOUT_MS = 6_000L
private const val QUERY_MAX_CHARS = 200
private const val SHORT_QUESTION_CHARS = 25
private const val QUESTION_LABEL = "Pregunta del usuario:"

/**
 * Cómo tiene que quedar la respuesta cuando se usó búsqueda: bien armada y en Markdown (la app lo renderiza).
 * Se le pide al modelo citar en el texto con [1], [2]… pero NO listar las fuentes: esa lista la agrega la
 * app (es determinista; un modelo chico se equivoca o se la salta).
 */
const val SEARCH_ANSWER_FORMAT =
    "Cuando uses resultados de búsqueda, respondé en español y bien formateada en Markdown: primero una o dos " +
        "frases que respondan directo a la pregunta; después el detalle con viñetas o secciones cortas (títulos " +
        "con ##), **negrita** en los datos clave (cifras, fechas, nombres) y sin relleno. Citá las fuentes en el " +
        "texto como [1], [2]. No inventes datos que no estén en los resultados y, si no alcanzan para responder, " +
        "decilo. No escribas una lista de fuentes al final: la agrega la app."

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
suspend fun augmentWithWebSearch(userText: String, previousUserText: String?): SearchAugmentation {
    val question = userText.substringAfterLast(QUESTION_LABEL).trim()
    val query = buildQuery(question, previousUserText?.substringAfterLast(QUESTION_LABEL)?.trim())
    val results = when (val outcome = WebSearchTool.search(query)) {
        is WebSearchOutcome.Unavailable ->
            return SearchAugmentation("🔧 búsqueda no disponible (${outcome.reason}) — respondo sin internet\n\n", null, null)
        is WebSearchOutcome.Found -> outcome.results
    }

    val excerpts = coroutineScope {
        results.take(PAGES_TO_READ).map { result ->
            async {
                withTimeoutOrNull(PAGE_TIMEOUT_MS) { runCatching { fetchUrlText(result.url) }.getOrNull() }
                    ?.take(PAGE_EXCERPT_CHARS)
                    ?.takeIf { it.isNotBlank() }
            }
        }.awaitAll()
    }

    val prompt = buildString {
        appendLine("Resultados de búsqueda en internet para \"$query\":")
        results.forEachIndexed { index, result ->
            appendLine("[${index + 1}] ${result.title} — ${result.description.take(DESCRIPTION_CHARS)}")
        }
        excerpts.forEachIndexed { index, excerpt ->
            if (excerpt != null) appendLine("Extracto de [${index + 1}]: $excerpt")
        }
        appendLine()
        val original = userText.trim()
        appendLine(if (original.contains(QUESTION_LABEL)) original else "$QUESTION_LABEL $original")
        appendLine()
        append(SEARCH_ANSWER_FORMAT)
    }
    val sources = results.mapIndexed { index, result ->
        val title = result.title.replace("[", "(").replace("]", ")")
        "${index + 1}. [$title](${result.url.replace(")", "%29")})"
    }.joinToString("\n")

    return SearchAugmentation(
        marker = "🔧 busqué en internet \"$query\" — ${results.size} resultados\n\n",
        prompt = prompt,
        sourcesBlock = "**Fuentes**\n$sources",
    )
}

/** La consulta sale de la pregunta; si es muy corta ("¿y en Madrid?") se le suma la anterior para darle contexto. */
private fun buildQuery(question: String, previousQuestion: String?): String {
    val base = if (question.length < SHORT_QUESTION_CHARS && !previousQuestion.isNullOrBlank()) {
        "$previousQuestion $question"
    } else {
        question
    }
    return base.replace(Regex("\\s+"), " ").trim().take(QUERY_MAX_CHARS)
}
