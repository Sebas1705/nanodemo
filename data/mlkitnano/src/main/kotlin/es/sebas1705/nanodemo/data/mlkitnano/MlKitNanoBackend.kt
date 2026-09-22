package es.sebas1705.nanodemo.data.mlkitnano

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import es.sebas1705.nanodemo.core.common.DemoTools
import es.sebas1705.nanodemo.core.common.buildLocalSystemPrompt
import es.sebas1705.nanodemo.domain.models.ChatMessage
import es.sebas1705.nanodemo.domain.models.GenerationConfig
import es.sebas1705.nanodemo.domain.models.InferenceBackend
import es.sebas1705.nanodemo.domain.models.Role
import es.sebas1705.nanodemo.domain.models.TierAvailability
import es.sebas1705.nanodemo.domain.models.TierCapabilities
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Content
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.SystemInstruction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

private const val TAG = "NanoDemo.MlKitNano"

/**
 * Tier A: ML Kit GenAI Prompt API (Gemini Nano on-device vía AICore).
 *
 * Referencia oficial: https://developers.google.com/ml-kit/genai/prompt/android/get-started
 *
 * NOTA: el Prompt API está en beta (1.0.0-beta4) y su superficie puede cambiar.
 * Las firmas usadas acá (`Content.Builder.text/image`, `GenerateContentRequest.Builder`
 * con propiedades `temperature`/`topK`/`maxOutputTokens`/`systemInstruction`,
 * `generateContentStream(GenerateContentRequest)`, `isSystemPromptAvailable()`)
 * están verificadas contra la referencia Kotlin oficial, no solo la guía de
 * inicio rápido — pero es una API en beta, revisá el autocompletado si algo
 * no compila tal cual.
 */
class MlKitNanoBackend @Inject constructor(
    @ApplicationContext private val context: Context,
) : InferenceBackend {

    // Generation.getClient() sin argumentos usa la configuración por defecto
    // (modelo "stable"). Se puede pasar un generationConfig { modelConfig { ... } }
    // para pedir explícitamente un modelo PREVIEW o preferencia FAST/FULL.
    private val generativeModel = Generation.getClient()

    override val capabilities = TierCapabilities(
        temperature = true,
        topK = true,
        topP = false, // no existe en esta API
        maxOutputTokens = true,
        systemPrompt = true, // requiere Gemini Nano V3+; se valida en runtime con isSystemPromptAvailable()
        multiTurn = true,
        images = true,
        // Ambas emuladas por prompt (ver core:common/LocalPromptEmulation.kt) -- no hay
        // tool-calling ni responseMimeType nativos en esta API. Dependen del mismo
        // isSystemPromptAvailable() que systemPrompt: si el dispositivo no lo soporta, la
        // instrucción no llega y el modelo nunca la va a respetar.
        structuredOutput = true,
        functionCalling = true,
    )

    override suspend fun checkAvailability(): TierAvailability {
        val status = generativeModel.checkStatus()
        Log.d(TAG, "checkStatus() = $status")
        return when (status) {
            FeatureStatus.AVAILABLE -> TierAvailability.AVAILABLE
            FeatureStatus.DOWNLOADABLE -> TierAvailability.DOWNLOADABLE
            FeatureStatus.DOWNLOADING -> TierAvailability.CHECKING
            else -> TierAvailability.UNAVAILABLE
        }
    }

    override fun download(): Flow<Int> = flow {
        // download() emite un Flow<DownloadStatus> con el progreso. Los subtipos
        // exactos (DownloadStarted/DownloadProgress/DownloadCompleted/DownloadFailed)
        // están documentados, pero Google no publica los nombres de sus campos
        // internos (p.ej. bytes descargados) — por eso aquí no emitimos porcentaje
        // (queda en "progreso desconocido" en la UI) y solo esperamos a que termine.
        generativeModel.download()
            .onEach { Log.d(TAG, "download(): $it") }
            .first { it is DownloadStatus.DownloadCompleted || it is DownloadStatus.DownloadFailed }
    }

    /**
     * Sin tool-calling: transmite chunk a chunk tal cual llegan (comportamiento sin cambios).
     * Con tool-calling (emulado por prompt, ver DemoTools): esta API es stateless, no hay forma de
     * "seguir" una generación en curso — el primer pase se bufferea completo (nada se emite todavía)
     * para poder decidir si el texto ENTERO fue el pedido de herramienta o una respuesta normal, y
     * recién ahí se resuelve uno u otro camino. Mismo trade-off que Cloud/Firebase: sin streaming
     * visible durante el pase que decide si hay o no pedido de herramienta.
     */
    override fun generateContentStream(history: List<ChatMessage>, config: GenerationConfig): Flow<String> = flow {
        if (!config.functionCalling) {
            runPass(history, config).collect { text ->
                Log.v(TAG, "chunk: \"$text\"")
                emit(text)
            }
            return@flow
        }

        val buffer = StringBuilder()
        runPass(history, config).collect { buffer.append(it) }
        val firstPassText = buffer.toString()
        val timezone = DemoTools.matchReactCall(firstPassText)
        if (timezone == null) {
            emit(firstPassText)
            return@flow
        }

        Log.d(TAG, "generateContentStream: pidió get_current_time($timezone)")
        emit("🔧 usando \"${DemoTools.GET_CURRENT_TIME}\"…\n\n")
        val toolResult = DemoTools.executeCurrentTime(timezone.ifBlank { null })
        val extendedHistory = history + listOf(
            ChatMessage(Role.MODEL, firstPassText),
            ChatMessage(Role.USER, "Resultado de get_current_time: ${toolResult["legible"]} (${toolResult["iso8601"]})"),
        )
        runPass(extendedHistory, config).collect { text ->
            Log.v(TAG, "chunk (2do pase): \"$text\"")
            emit(text)
        }
    }

    /** Un pase de generación: reconstruye toda la conversación (stateless) y transmite el texto crudo. */
    private fun runPass(history: List<ChatMessage>, config: GenerationConfig): Flow<String> = flow {
        // Un Content por turno (el rol se infiere por la posición/alternancia, la API no expone un
        // campo "role" explícito).
        val contents = history.map { message ->
            val builder = Content.Builder().text(message.text)
            message.imageUri
                ?.let { loadBitmap(it) }
                ?.let { bitmap -> builder.image(bitmap) }
            builder.build()
        }

        val requestBuilder = GenerateContentRequest.Builder(contents)
        config.temperature?.let { requestBuilder.temperature = it }
        config.topK?.let { requestBuilder.topK = it }
        config.maxOutputTokens?.let { requestBuilder.maxOutputTokens = it }
        val fullSystemPrompt = buildLocalSystemPrompt(config.systemPrompt, config.functionCalling, config.jsonMode)
        if (fullSystemPrompt != null && generativeModel.isSystemPromptAvailable()) {
            requestBuilder.systemInstruction = SystemInstruction(fullSystemPrompt)
        }

        generativeModel.generateContentStream(requestBuilder.build()).collect { chunk ->
            emit(chunk.candidates.firstOrNull()?.text.orEmpty())
        }
    }

    private fun loadBitmap(uri: Uri): Bitmap? = try {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) {
        Log.e(TAG, "loadBitmap: no se pudo leer $uri", e)
        null
    }

    override fun close() {
        generativeModel.close()
    }
}
