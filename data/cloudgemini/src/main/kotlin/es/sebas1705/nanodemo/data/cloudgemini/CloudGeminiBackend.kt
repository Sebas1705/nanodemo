package es.sebas1705.nanodemo.data.cloudgemini

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import es.sebas1705.nanodemo.core.common.hasInternetConnection
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
    )

    private val apiKey: String = BuildConfig.GEMINI_API_KEY
    private var lastResponseTokens: Int? = null

    override suspend fun checkAvailability(): TierAvailability {
        val hasKey = apiKey.isNotBlank()
        val hasInternet = hasInternetConnection(context)
        Log.d(TAG, "checkAvailability: hasKey=$hasKey hasInternet=$hasInternet")
        return if (hasKey && hasInternet) TierAvailability.AVAILABLE else TierAvailability.UNAVAILABLE
    }

    override fun generateContentStream(history: List<ChatMessage>, config: GenerationConfig): Flow<String> = flow {
        lastResponseTokens = null
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
        Log.d(TAG, "generateContentStream: POST a $CLOUD_MODEL:streamGenerateContent")
        try {
            val body = buildRequestBody(history, config)
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            if (connection.responseCode !in 200..299) {
                val errorBody = connection.errorStream?.bufferedReader()?.readText().orEmpty()
                error("HTTP ${connection.responseCode}: $errorBody")
            }

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
                    val text = extractText(chunk)
                    if (text.isNotEmpty()) {
                        Log.v(TAG, "chunk: \"$text\"")
                        emit(text)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    private fun buildRequestBody(history: List<ChatMessage>, config: GenerationConfig): JSONObject {
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
        val body = JSONObject().put("contents", contents)

        val generationConfig = JSONObject()
        config.temperature?.let { generationConfig.put("temperature", it) }
        config.topK?.let { generationConfig.put("topK", it) }
        config.topP?.let { generationConfig.put("topP", it) }
        config.maxOutputTokens?.let { generationConfig.put("maxOutputTokens", it) }
        if (config.jsonMode) generationConfig.put("responseMimeType", "application/json")
        if (generationConfig.length() > 0) body.put("generationConfig", generationConfig)

        config.systemPrompt?.let { systemPrompt ->
            body.put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
            )
        }
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
