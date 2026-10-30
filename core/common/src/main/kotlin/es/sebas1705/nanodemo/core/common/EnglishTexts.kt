package es.sebas1705.nanodemo.core.common

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal object EnglishTexts : AppTexts {
    private val DATE_TIME = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy, HH:mm", Locale.ENGLISH)

    override val appContextPreamble =
        "You are the chat assistant built into nanodemo, an Android app that is itself a demo: it compares " +
            "5 AI engines/tiers with the same prompt — Tier A (ML Kit Gemini Nano, on-device), Tier B " +
            "(MediaPipe with Gemma, on-device), Tier C (Gemini in the cloud), Tier D (LiteRT-LM with Gemma, " +
            "on-device) and Tier E (Firebase AI Logic, in the cloud). You are the tier answering right now. " +
            "The app also has a Compare screen (runs the same prompt on all 5 at once), History (saved " +
            "conversations, with semantic search) and Stats (latency/cost/tokens per tier). If you are asked " +
            "what you are, what this app is, or what it does, answer with this information."

    override val jsonModeInstruction =
        "Reply ONLY with valid JSON: no text before or after it, no explanations, no markdown code blocks " +
            "(no ``` around it). Your whole message must parse as JSON from start to end."

    override val searchAnswerFormat =
        "When you use search results, answer in English and format the answer well in Markdown: first one " +
            "or two sentences that answer the question directly; then the details in bullet points or short " +
            "sections (headings with ##), **bold** for key facts (figures, dates, names) and no filler. Cite " +
            "the sources in the text as [1], [2]. Do not make up anything that is not in the results and, if " +
            "they are not enough to answer, say so. Do not write a list of sources at the end: the app adds it."

    override val currentTimeDescription =
        "Returns the current date and time in an IANA time zone (e.g. 'Europe/Madrid', " +
            "'America/Argentina/Buenos_Aires'). Always use it when you are asked the time or today's date: " +
            "you have no clock of your own and do not know what day it is."

    override val deviceTimeZoneNote = "Always in the device's time zone."
    override val timeZoneParamDescription = "IANA time zone, e.g. Europe/Madrid. If it is not clear, use UTC."
    override val timeZonePlaceholder = "IANA_time_zone"
    override val instructionsLabel = "Instructions:"
    override val questionLabel = "User question:"

    override fun reactInstruction(tagTemplate: String, tagExample: String) =
        "If you are asked the time or today's date, do not make it up: you have no clock of your own. Instead " +
            "of answering directly, reply ONLY with this exact tag, with no text before or after it: " +
            "$tagTemplate (e.g. $tagExample). You will receive the real result and can then complete your " +
            "answer with the correct time/date."

    override fun pageContentTurn(url: String, pageText: String, question: String) =
        "Content of $url:\n\n$pageText\n\n$questionLabel $question"

    override fun toolResult(toolName: String, readableTime: String, iso8601: String) =
        "Result of $toolName: $readableTime ($iso8601)"

    override fun searchResultsHeader(query: String) = "Internet search results for \"$query\":"
    override fun pageExcerpt(index: Int, excerpt: String) = "Excerpt from [$index]: $excerpt"
    override fun formatDateTime(time: ZonedDateTime): String = time.format(DATE_TIME)

    override val sourcesHeading = "Sources"
    override fun usingToolNotice(toolName: String) = "using \"$toolName\"…"

    override fun searchDoneNotice(query: String, resultCount: Int) =
        "searched the internet for \"$query\" — $resultCount ${if (resultCount == 1) "result" else "results"}"

    override fun searchUnavailableNotice(failure: WebSearchFailure): String {
        val reason = when (failure) {
            WebSearchFailure.NotConfigured -> "BRAVE_SEARCH_API_KEY is missing"
            WebSearchFailure.QuotaReached -> "Brave free limit reached"
            WebSearchFailure.NoResults -> "no results"
            WebSearchFailure.NetworkError -> "network error"
            is WebSearchFailure.HttpError -> "HTTP error ${failure.code}"
        }
        return "search unavailable ($reason) — answering without internet"
    }

    override fun groundingNotice(queries: String, sourceCount: Int) =
        "Grounded with Google Search ($queries) — $sourceCount ${if (sourceCount == 1) "source" else "sources"}"

    override val errorPreparingModel = "Error preparing the model"
    override val errorGenerating = "Error generating content"
    override val errorNetwork = "network error"
    override val errorMovingModel = "Could not move the downloaded model to its final location"
    override val errorNoUserTurn = "There is no user turn to send"
    override val errorMissingFirebaseConfig =
        "FIREBASE_API_KEY / FIREBASE_APP_ID / FIREBASE_PROJECT_ID are missing (Doppler or local.properties)"

    override fun errorDownloadingLink(detail: String) = "Could not download the link: $detail"
    override fun errorDownloadingModel(httpCode: Int) = "HTTP $httpCode downloading the model"
    override fun errorDownloadingModelPart(httpCode: Int, part: Int) = "HTTP $httpCode downloading part $part of the model"
    override fun errorDownloadingEmbeddingModel(httpCode: Int) = "HTTP $httpCode downloading the embedding model"
    override fun errorMissingModelUrl(model: String) = "A download URL is missing in local.properties for $model"

    override fun quotaBlocked(service: QuotaService, used: Int, limit: Int): String {
        val label = when (service) {
            QuotaService.GEMINI_CLOUD -> "Gemini API · Tier C (Cloud)"
            QuotaService.GEMINI_FIREBASE -> "Firebase AI Logic · Tier E"
            QuotaService.BRAVE_SEARCH -> "Brave Search · search for the local tiers"
        }
        val (per, reset) = when (service.period) {
            QuotaPeriod.DAY -> "per day" to "at midnight Pacific time"
            QuotaPeriod.MONTH -> "per month" to "next month"
        }
        return "You reached the free limit of $label: $used/$limit $per. It resets $reset. You can change the " +
            "limit or turn off blocking in Settings → Usage and limits."
    }
}
