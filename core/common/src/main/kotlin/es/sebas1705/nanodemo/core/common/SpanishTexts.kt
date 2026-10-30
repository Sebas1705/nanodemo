package es.sebas1705.nanodemo.core.common

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal object SpanishTexts : AppTexts {
    private val DATE_TIME = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy, HH:mm", Locale.forLanguageTag("es"))

    override val appContextPreamble =
        "Sos el asistente de chat integrado en nanodemo, una app Android que en sí misma es una demo: " +
            "compara 5 motores/tiers de IA con el mismo prompt — Tier A (ML Kit Gemini Nano, on-device), " +
            "Tier B (MediaPipe con Gemma, on-device), Tier C (Gemini en la nube), Tier D (LiteRT-LM con " +
            "Gemma, on-device) y Tier E (Firebase AI Logic, en la nube). Vos sos el tier que está " +
            "respondiendo ahora mismo. La app también tiene una pantalla Comparar (corre el mismo prompt " +
            "en los 5 a la vez), Historial (conversaciones guardadas, con búsqueda semántica) y " +
            "Estadísticas (latencia/costo/tokens por tier). Si te preguntan qué sos, qué es esta app, o " +
            "qué hace, respondé con esta información."

    override val jsonModeInstruction =
        "Respondé ÚNICAMENTE con JSON válido: nada de texto antes ni después, nada de explicaciones, " +
            "nada de bloques de código markdown (sin ``` alrededor). Todo tu mensaje tiene que poder " +
            "parsearse como JSON de punta a punta."

    override val searchAnswerFormat =
        "Cuando uses resultados de búsqueda, respondé en español y bien formateada en Markdown: primero una o dos " +
            "frases que respondan directo a la pregunta; después el detalle con viñetas o secciones cortas (títulos " +
            "con ##), **negrita** en los datos clave (cifras, fechas, nombres) y sin relleno. Citá las fuentes en el " +
            "texto como [1], [2]. No inventes datos que no estén en los resultados y, si no alcanzan para responder, " +
            "decilo. No escribas una lista de fuentes al final: la agrega la app."

    override val currentTimeDescription =
        "Devuelve la fecha y hora actual en una zona horaria IANA (ej. 'Europe/Madrid', " +
            "'America/Argentina/Buenos_Aires'). Usala siempre que te pregunten la hora o la fecha " +
            "de hoy: no tenés reloj propio ni sabés qué día es."

    override val deviceTimeZoneNote = "Siempre en la zona horaria del dispositivo."
    override val timeZoneParamDescription = "Zona horaria IANA, ej. Europe/Madrid. Si no está clara, usá UTC."
    override val timeZonePlaceholder = "zona_horaria_IANA"
    override val instructionsLabel = "Instrucciones:"
    override val questionLabel = "Pregunta del usuario:"

    override fun reactInstruction(tagTemplate: String, tagExample: String) =
        "Si te preguntan la hora o la fecha de hoy, no la inventes: no tenés reloj propio. En vez de " +
            "responder directamente, respondé ÚNICAMENTE con esta etiqueta exacta, sin texto antes ni " +
            "después: $tagTemplate (ej. $tagExample). " +
            "Vas a recibir el resultado real y ahí sí completás tu respuesta con la hora/fecha correcta."

    override fun pageContentTurn(url: String, pageText: String, question: String) =
        "Contenido de $url:\n\n$pageText\n\n$questionLabel $question"

    override fun toolResult(toolName: String, readableTime: String, iso8601: String) =
        "Resultado de $toolName: $readableTime ($iso8601)"

    override fun searchResultsHeader(query: String) = "Resultados de búsqueda en internet para \"$query\":"
    override fun pageExcerpt(index: Int, excerpt: String) = "Extracto de [$index]: $excerpt"
    override fun formatDateTime(time: ZonedDateTime): String = time.format(DATE_TIME)

    override val sourcesHeading = "Fuentes"
    override fun usingToolNotice(toolName: String) = "usando \"$toolName\"…"

    override fun searchDoneNotice(query: String, resultCount: Int) =
        "busqué en internet \"$query\" — $resultCount ${if (resultCount == 1) "resultado" else "resultados"}"

    override fun searchUnavailableNotice(failure: WebSearchFailure): String {
        val reason = when (failure) {
            WebSearchFailure.NotConfigured -> "falta BRAVE_SEARCH_API_KEY"
            WebSearchFailure.QuotaReached -> "límite gratis de Brave alcanzado"
            WebSearchFailure.NoResults -> "sin resultados"
            WebSearchFailure.NetworkError -> "error de red"
            is WebSearchFailure.HttpError -> "error HTTP ${failure.code}"
        }
        return "búsqueda no disponible ($reason) — respondo sin internet"
    }

    override fun groundingNotice(queries: String, sourceCount: Int) =
        "Grounded con Google Search ($queries) — $sourceCount ${if (sourceCount == 1) "fuente" else "fuentes"}"

    override val errorPreparingModel = "Error preparando el modelo"
    override val errorGenerating = "Error generando contenido"
    override val errorNetwork = "error de red"
    override val errorMovingModel = "No se pudo mover el modelo descargado a su ubicación final"
    override val errorNoUserTurn = "No hay turno de usuario para enviar"
    override val errorMissingFirebaseConfig =
        "Falta FIREBASE_API_KEY / FIREBASE_APP_ID / FIREBASE_PROJECT_ID (Doppler o local.properties)"

    override fun errorDownloadingLink(detail: String) = "No se pudo descargar el link: $detail"
    override fun errorDownloadingModel(httpCode: Int) = "HTTP $httpCode descargando el modelo"
    override fun errorDownloadingModelPart(httpCode: Int, part: Int) = "HTTP $httpCode descargando la parte $part del modelo"
    override fun errorDownloadingEmbeddingModel(httpCode: Int) = "HTTP $httpCode descargando el modelo de embeddings"
    override fun errorMissingModelUrl(model: String) = "Falta alguna URL de descarga en local.properties para $model"

    override fun quotaBlocked(service: QuotaService, used: Int, limit: Int): String {
        val label = when (service) {
            QuotaService.GEMINI_CLOUD -> "Gemini API · Tier C (Cloud)"
            QuotaService.GEMINI_FIREBASE -> "Firebase AI Logic · Tier E"
            QuotaService.BRAVE_SEARCH -> "Brave Search · búsqueda de los tiers locales"
        }
        val (per, reset) = when (service.period) {
            QuotaPeriod.DAY -> "por día" to "a la medianoche del Pacífico"
            QuotaPeriod.MONTH -> "por mes" to "el mes que viene"
        }
        return "Has llegado al límite gratis de $label: $used/$limit $per. Se reinicia $reset. Puedes cambiar el " +
            "límite o desactivar el bloqueo en Ajustes → Uso y límites."
    }
}
