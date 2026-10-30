package es.sebas1705.nanodemo.core.common

import java.time.ZonedDateTime

/**
 * Prefijos que la UI parsea para separar los avisos del cuerpo de la respuesta (ver `splitAssistantText` en
 * `feature:chat`). Son lo único estable: el texto que sigue depende del idioma, y por eso también se parsean
 * bien las conversaciones guardadas con avisos en otro idioma. Armar los avisos solo con [toolMarker] y
 * [groundingMarker].
 */
const val TOOL_MARKER_PREFIX = "🔧 "
const val GROUNDING_MARKER_PREFIX = "🔎 "

/** Aviso al principio de la respuesta (el Chat lo pinta como chip): `notice` viene de [AppTexts]. */
fun toolMarker(notice: String): String = "$TOOL_MARKER_PREFIX$notice\n\n"

/** Pie al final de la respuesta: `notice` viene de [AppTexts]. */
fun groundingMarker(notice: String): String = "\n\n$GROUNDING_MARKER_PREFIX$notice"

/**
 * Textos fuera de Compose (prompts para el modelo, avisos del chat y errores de la capa de datos), que no
 * pueden usar `stringResource`: la mayoría sale de funciones y `object`s de este módulo, sin `Context`, y
 * los prompts además se prueban en tests de JVM. Cada idioma es un `object` completo: si falta un texto, no
 * compila. Se piden con [AppTextsProvider] por operación, no se guardan, para seguir un cambio de idioma.
 */
interface AppTexts {
    // Prompts: lo que lee el modelo. Van en el idioma de la UI para que responda en ese idioma.
    val appContextPreamble: String
    val jsonModeInstruction: String
    val searchAnswerFormat: String
    val currentTimeDescription: String
    val deviceTimeZoneNote: String
    val timeZoneParamDescription: String
    val timeZonePlaceholder: String
    val instructionsLabel: String

    /** Etiqueta antes de la pregunta en un turno enriquecido. Se parsea: ver [extractQuestion]. */
    val questionLabel: String

    /** `tagTemplate`/`tagExample` son la etiqueta parseable ya armada (ver `DemoTools.reactInstruction`). */
    fun reactInstruction(tagTemplate: String, tagExample: String): String
    fun pageContentTurn(url: String, pageText: String, question: String): String
    fun toolResult(toolName: String, readableTime: String, iso8601: String): String
    fun searchResultsHeader(query: String): String
    fun pageExcerpt(index: Int, excerpt: String): String
    fun formatDateTime(time: ZonedDateTime): String

    // Avisos en el chat: el texto de los marcadores, sin el prefijo.
    val sourcesHeading: String
    fun usingToolNotice(toolName: String): String
    fun searchDoneNotice(query: String, resultCount: Int): String
    fun searchUnavailableNotice(failure: WebSearchFailure): String
    fun groundingNotice(queries: String, sourceCount: Int): String

    // Errores que llegan a la UI (`DemoState.Error`, o el `message` de una excepción).
    val errorPreparingModel: String
    val errorGenerating: String
    val errorNetwork: String
    val errorMovingModel: String
    val errorNoUserTurn: String
    val errorMissingFirebaseConfig: String
    fun errorDownloadingLink(detail: String): String
    fun errorDownloadingModel(httpCode: Int): String
    fun errorDownloadingModelPart(httpCode: Int, part: Int): String
    fun errorDownloadingEmbeddingModel(httpCode: Int): String
    fun errorMissingModelUrl(model: String): String
    fun quotaBlocked(service: QuotaService, used: Int, limit: Int): String
}

/** De dónde sale el idioma actual; lo provee Hilt (ver `LocalizationModule`) y en los tests es una lambda. */
fun interface AppTextsProvider {
    fun current(): AppTexts
}

private val ALL_TEXTS: List<AppTexts> = listOf(EnglishTexts, SpanishTexts)

/** Mismo criterio que `values/` y `values-es/`: español si la etiqueta es "es", inglés para todo lo demás. */
fun appTextsFor(languageTag: String): AppTexts =
    if (languageTag.substringBefore('-').equals("es", ignoreCase = true)) SpanishTexts else EnglishTexts

/**
 * La pregunta real de un turno enriquecido (lo que sigue a la última etiqueta), o `text` entero si no
 * tiene etiqueta. Se aceptan las de todos los idiomas: quien arma el turno y quien lo lee piden los textos
 * por separado, y un cambio de idioma entre los dos no debe romper la búsqueda.
 */
internal fun extractQuestion(text: String): String {
    val start = ALL_TEXTS.maxOf { texts ->
        val index = text.lastIndexOf(texts.questionLabel)
        if (index < 0) -1 else index + texts.questionLabel.length
    }
    return (if (start < 0) text else text.substring(start)).trim()
}

internal fun hasQuestionLabel(text: String): Boolean = ALL_TEXTS.any { text.contains(it.questionLabel) }
