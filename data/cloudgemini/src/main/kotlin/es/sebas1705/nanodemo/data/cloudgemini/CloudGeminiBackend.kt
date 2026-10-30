package es.sebas1705.nanodemo.data.cloudgemini

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import es.sebas1705.nanodemo.core.common.AppTexts
import es.sebas1705.nanodemo.core.common.AppTextsProvider
import es.sebas1705.nanodemo.core.common.DemoTools
import es.sebas1705.nanodemo.core.common.QuotaService
import es.sebas1705.nanodemo.core.common.UsageQuota
import es.sebas1705.nanodemo.core.common.appContextPreamble
import es.sebas1705.nanodemo.core.common.groundingMarker
import es.sebas1705.nanodemo.core.common.hasInternetConnection
import es.sebas1705.nanodemo.core.common.toolMarker
import es.sebas1705.nanodemo.domain.models.ChatMessage
import es.sebas1705.nanodemo.domain.models.GenerationConfig
import es.sebas1705.nanodemo.domain.models.InferenceBackend
import es.sebas1705.nanodemo.domain.models.Role
import es.sebas1705.nanodemo.domain.models.TierAvailability
import es.sebas1705.nanodemo.domain.models.TierCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject

private const val TAG = "NanoDemo.CloudGemini"

// Modelo usado tal cual en el ejemplo oficial del SDK google-genai-kotlin
// (Client.kt de la versión 1.1.0), para minimizar el riesgo de un id inválido.
private const val CLOUD_MODEL = "gemini-3.5-flash"
private const val API_BASE = "https://generativelanguage.googleapis.com/v1beta/models"

/**
 * Tier C: Gemini API en la nube — fallback universal para cualquier dispositivo
 * con internet, sin depender de hardware on-device. El tier más completo de
 * los 3: soporta todos los parámetros de generación, system prompt,
 * multi-turno e imágenes, todo documentado y estable en el REST API público.
 *
 * NOTA IMPORTANTE: el SDK oficial Kotlin (com.google.genai:google-genai-kotlin)
 * BLOQUEA en runtime construir un `Client` con API key en Android, lanzando
 * `IllegalStateException("SECURITY FATAL: ... please use Firebase AI Logic")`.
 * Por eso este backend no usa ese SDK y llama directo al REST API de Gemini
 * (endpoint público y documentado: generativelanguage.googleapis.com) con
 * `HttpURLConnection` + `org.json`, ambos ya incluidos en el SDK de Android
 * (sin dependencias nuevas, y de paso evita el conflicto de packaging que
 * traían las dependencias transitivas del SDK, como google-auth-library).
 *
 * Es exactamente el patrón que Google intenta evitar con ese bloqueo (una API
 * key embebida en un cliente móvil): aceptable para este demo local/prototipo
 * con la key fuera de git, pero NO para una app publicada — para eso hay que
 * usar Firebase AI Logic con App Check.
 *
 * La API key nunca se hardcodea: viene de local.properties -> BuildConfig
 * (ver data/cloudgemini/build.gradle.kts). Si no está configurada, este tier
 * queda UNAVAILABLE en vez de intentar una llamada que fallaría igualmente.
 */
class CloudGeminiBackend @Inject constructor(
    @ApplicationContext private val context: Context,
    private val textsProvider: AppTextsProvider,
) : InferenceBackend {

    override val capabilities = TierCapabilities(
        temperature = true,
        topK = true,
        topP = true,
        maxOutputTokens = true,
        systemPrompt = true,
        multiTurn = true,
        images = true,
        structuredOutput = true,
        functionCalling = true,
        webSearch = true,
    )

    private val apiKey: String = BuildConfig.GEMINI_API_KEY
    private var lastResponseTokens: Int? = null

    override suspend fun checkAvailability(): TierAvailability {
        val hasKey = apiKey.isNotBlank()
        val hasInternet = hasInternetConnection(context)
        Log.d(TAG, "checkAvailability: hasKey=$hasKey hasInternet=$hasInternet")
        return if (hasKey && hasInternet) TierAvailability.AVAILABLE else TierAvailability.UNAVAILABLE
    }

    /**
     * `functionCalling`: hasta 2 idas y vueltas HTTP por turno. La primera pasada manda la
     * herramienta declarada; si el modelo la pide (`functionCall` en vez de texto), se ejecuta
     * localmente (`DemoTools`, sin red) y se hace una segunda pasada con el resultado agregado al
     * historial — ahí sí llega la respuesta final en texto. Si el modelo responde directo (el caso
     * común), es solo la primera pasada, igual que antes de esta feature.
     *
     * `webSearch`: una sola pasada — el grounding lo resuelve Google del otro lado, no hay nada
     * que ejecutar local. Al terminar, si hubo búsqueda, se agrega una línea con las consultas y
     * la cantidad de fuentes (`groundingMetadata`, ver `extractGrounding`) — no se listan las URLs
     * completas para no alargar demasiado la respuesta.
     */
    override fun generateContentStream(history: List<ChatMessage>, config: GenerationConfig): Flow<String> = flow {
        lastResponseTokens = null
        val texts = textsProvider.current()
        var result = streamRequest(history, config, texts, extraTurns = emptyList()) { text -> emit(text) }
        val call = result.functionCall
        if (call != null) {
            Log.d(TAG, "generateContentStream: pidió la función ${call.name}")
            emit(toolMarker(texts.usingToolNotice(call.name)))
            val toolResult = DemoTools.executeCurrentTime(call.args.optString(DemoTools.PARAM_TIMEZONE, null), texts)
            val resultJson = JSONObject().apply { toolResult.forEach { (k, v) -> put(k, v) } }
            val extraTurns = listOf(
                JSONObject().put("role", "model").put(
                    "parts",
                    JSONArray().put(
                        JSONObject().put("functionCall", JSONObject().put("name", call.name).put("args", call.args)),
                    ),
                ),
                JSONObject().put("role", "user").put(
                    "parts",
                    JSONArray().put(
                        JSONObject().put("functionResponse", JSONObject().put("name", call.name).put("response", resultJson)),
                    ),
                ),
            )
            result = streamRequest(history, config, texts, extraTurns) { text -> emit(text) }
        }
        result.grounding?.let { grounding ->
            Log.d(TAG, "generateContentStream: grounding con ${grounding.sourceCount} fuente(s)")
            val queries = grounding.queries.joinToString { "\"$it\"" }
            emit(groundingMarker(texts.groundingNotice(queries, grounding.sourceCount)))
        }
    }.flowOn(Dispatchers.IO)

    private data class FunctionCallInfo(val name: String, val args: JSONObject)
    private data class GroundingInfo(val queries: List<String>, val sourceCount: Int)
    private data class StreamResult(val functionCall: FunctionCallInfo?, val grounding: GroundingInfo?)

    /**
     * POST a `streamGenerateContent` y consume el SSE. Manda cada chunk de texto a [onText]. Si el
     * modelo pide la función en vez de responder texto, o si hubo grounding con Google Search, lo
     * captura en el resultado (sin llamar a [onText] para el chunk del functionCall) — el llamador
     * decide qué hacer. Solo soporta UN pedido de función por turno (alcanza para demostrar el
     * mecanismo; el API real permite encadenar varias).
     */
    private suspend fun streamRequest(
        history: List<ChatMessage>,
        config: GenerationConfig,
        texts: AppTexts,
        extraTurns: List<JSONObject>,
        onText: suspend (String) -> Unit,
    ): StreamResult {
        UsageQuota.record(QuotaService.GEMINI_CLOUD)
        val url = URL(
            "$API_BASE/$CLOUD_MODEL:streamGenerateContent" +
                "?alt=sse&key=${URLEncoder.encode(apiKey, "UTF-8")}"
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Content-Type", "application/json")
        }
        Log.d(TAG, "streamRequest: POST a $CLOUD_MODEL:streamGenerateContent (extraTurns=${extraTurns.size})")
        try {
            val body = buildRequestBody(history, config, texts, extraTurns)
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            if (connection.responseCode !in 200..299) {
                val errorBody = connection.errorStream?.bufferedReader()?.readText().orEmpty()
                error("HTTP ${connection.responseCode}: $errorBody")
            }

            var functionCall: FunctionCallInfo? = null
            var grounding: GroundingInfo? = null
            // La respuesta es Server-Sent Events: cada evento es una línea
            // "data: {...}" con un chunk de GenerateContentResponse en JSON.
            connection.inputStream.bufferedReader().use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (!line.startsWith("data:")) continue
                    val json = line.removePrefix("data:").trim()
                    if (json.isEmpty()) continue
                    val chunk = JSONObject(json)
                    chunk.optJSONObject("usageMetadata")?.let { usage ->
                        lastResponseTokens = usage.optInt("candidatesTokenCount", -1).takeIf { it >= 0 }
                    }
                    extractFunctionCall(chunk)?.let { functionCall = it }
                    extractGrounding(chunk)?.let { grounding = it }
                    val text = extractText(chunk)
                    if (text.isNotEmpty()) {
                        Log.v(TAG, "chunk: \"$text\"")
                        onText(text)
                    }
                }
            }
            return StreamResult(functionCall, grounding)
        } finally {
            connection.disconnect()
        }
    }

    private fun firstCandidate(chunk: JSONObject): JSONObject? =
        chunk.optJSONArray("candidates")?.takeIf { it.length() > 0 }?.getJSONObject(0)

    private fun extractFunctionCall(chunk: JSONObject): FunctionCallInfo? {
        val parts = firstCandidate(chunk)?.optJSONObject("content")?.optJSONArray("parts") ?: return null
        for (i in 0 until parts.length()) {
            val call = parts.getJSONObject(i).optJSONObject("functionCall") ?: continue
            return FunctionCallInfo(call.optString("name"), call.optJSONObject("args") ?: JSONObject())
        }
        return null
    }

    /** `groundingMetadata` — verificado contra ai.google.dev/gemini-api/docs/generate-content/google-search. */
    private fun extractGrounding(chunk: JSONObject): GroundingInfo? {
        val metadata = firstCandidate(chunk)?.optJSONObject("groundingMetadata") ?: return null
        val queriesJson = metadata.optJSONArray("webSearchQueries") ?: JSONArray()
        val queries = (0 until queriesJson.length()).map { queriesJson.getString(it) }
        val sourceCount = metadata.optJSONArray("groundingChunks")?.length() ?: 0
        if (queries.isEmpty() && sourceCount == 0) return null
        return GroundingInfo(queries, sourceCount)
    }

    private fun buildRequestBody(
        history: List<ChatMessage>,
        config: GenerationConfig,
        texts: AppTexts,
        extraTurns: List<JSONObject> = emptyList(),
    ): JSONObject {
        val contents = JSONArray()
        history.forEach { message ->
            val parts = JSONArray().put(JSONObject().put("text", message.text))
            message.imageUri?.let { uri -> encodeImagePart(uri)?.let { parts.put(it) } }
            contents.put(
                JSONObject()
                    .put("role", if (message.role == Role.USER) "user" else "model")
                    .put("parts", parts)
            )
        }
        extraTurns.forEach { contents.put(it) }
        val body = JSONObject().put("contents", contents)

        val generationConfig = JSONObject()
        config.temperature?.let { generationConfig.put("temperature", it) }
        config.topK?.let { generationConfig.put("topK", it) }
        config.topP?.let { generationConfig.put("topP", it) }
        config.maxOutputTokens?.let { generationConfig.put("maxOutputTokens", it) }
        if (config.jsonMode) generationConfig.put("responseMimeType", "application/json")
        if (generationConfig.length() > 0) body.put("generationConfig", generationConfig)

        // "google_search" verificado con un ejemplo real (curl) de ai.google.dev — no probado
        // combinado con functionDeclarations en la misma llamada, por eso son mutuamente
        // excluyentes acá (gana webSearch; ver el comentario en GenerationConfig.webSearch).
        if (config.webSearch) {
            body.put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
        } else if (config.functionCalling) {
            body.put(
                "tools",
                JSONArray().put(
                    JSONObject().put(
                        "functionDeclarations",
                        JSONArray().put(
                            JSONObject()
                                .put("name", DemoTools.GET_CURRENT_TIME)
                                .put("description", texts.currentTimeDescription)
                                .put(
                                    "parameters",
                                    JSONObject()
                                        .put("type", "object")
                                        .put(
                                            "properties",
                                            JSONObject().put(
                                                DemoTools.PARAM_TIMEZONE,
                                                JSONObject()
                                                    .put("type", "string")
                                                    .put("description", texts.timeZoneParamDescription),
                                            ),
                                        )
                                        .put("required", JSONArray().put(DemoTools.PARAM_TIMEZONE)),
                                )
                        )
                    )
                ),
            )
        }

        val fullSystemPrompt = listOfNotNull(appContextPreamble(texts), config.systemPrompt, texts.searchAnswerFormat.takeIf { config.webSearch }).joinToString("\n\n")
        body.put(
            "systemInstruction",
            JSONObject().put("parts", JSONArray().put(JSONObject().put("text", fullSystemPrompt)))
        )
        return body
    }

    private fun encodeImagePart(uri: Uri): JSONObject? = try {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        if (bytes == null) {
            null
        } else {
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            JSONObject().put(
                "inlineData",
                JSONObject()
                    .put("mimeType", mimeType)
                    .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))
            )
        }
    } catch (e: Exception) {
        Log.e(TAG, "encodeImagePart: no se pudo leer $uri", e)
        null
    }

    private fun extractText(chunk: JSONObject): String {
        val candidates = chunk.optJSONArray("candidates") ?: return ""
        if (candidates.length() == 0) return ""
        val parts = candidates.getJSONObject(0)
            .optJSONObject("content")
            ?.optJSONArray("parts")
            ?: return ""
        return buildString {
            for (i in 0 until parts.length()) {
                append(parts.getJSONObject(i).optString("text", ""))
            }
        }
    }

    override fun lastResponseTokens(): Int? = lastResponseTokens

    override fun close() {
        // Cada generación abre y cierra su propia conexión HTTP; no hay
        // estado persistente que cerrar.
    }
}
