package es.sebas1705.nanodemo.data.litertlm

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import es.sebas1705.nanodemo.core.common.DemoTools
import es.sebas1705.nanodemo.core.common.hasAtLeastRamBytes
import es.sebas1705.nanodemo.domain.models.ChatMessage
import es.sebas1705.nanodemo.domain.models.GenerationConfig
import es.sebas1705.nanodemo.domain.models.InferenceBackend
import es.sebas1705.nanodemo.domain.models.Role
import es.sebas1705.nanodemo.domain.models.TierAvailability
import es.sebas1705.nanodemo.domain.models.TierCapabilities
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

private const val TAG = "NanoDemo.LiteRtLm"
private const val MODEL_FILE_NAME = "gemma-4-e2b-it.litertlm"

// Presupuesto de contexto total del motor (prompt + respuesta, acumulado por conversación).
private const val MAX_NUM_TOKENS = 2048

// Gemma 4 E2B llega a ~3.5GB de RAM pico según la documentación de LiteRT-LM; `totalMem`
// informa menos que la RAM nominal, así que ~4.5GiB deja pasar a los teléfonos de 6GB.
private const val MIN_RAM_BYTES = 9L * 512 * 1024 * 1024 // 4.5 GiB

// Las imágenes se achican antes de mandarlas: el encoder de visión no necesita más y
// una foto de cámara completa haría lenta la inferencia en CPU.
private const val MAX_IMAGE_SIDE_PX = 1024

/**
 * Tier D: LiteRT-LM con Gemma 4 E2B — el runtime que Google recomienda como
 * sucesor de MediaPipe LLM Inference (el que usa el Tier B, hoy en mantenimiento).
 *
 * API verificada contra el `.aar` real de `litertlm-android:0.17.1` (no solo la guía):
 * `Engine(EngineConfig)` + `initialize()` (lento, ~10s, va en IO), una `Conversation`
 * con estado propio por charla (`createConversation(ConversationConfig)`), y
 * `sendMessageAsync(...)` que devuelve un `Flow<Message>` en streaming.
 *
 * Decisiones:
 * - Backend CPU: es el que no depende del hardware/driver. La guía también permite
 *   GPU/NPU, que requieren libs nativas del sistema declaradas en el manifest.
 * - Como MediaPipe, los parámetros de muestreo se fijan al crear la conversación:
 *   cambiarlos a mitad de charla requiere "Nueva conversación".
 * - Al crear la conversación se le pasa el historial previo como `initialMessages`,
 *   así retomar una charla del Historial (o volver a este tier después de que se
 *   liberó el motor) conserva el contexto — a diferencia del Tier B, que solo ve
 *   el último turno.
 * - El streaming se trata como deltas (cada `Message` es un trozo nuevo), que es lo
 *   que muestran los ejemplos oficiales; si se viera texto repetido, es la primera
 *   sospecha.
 * - Los tokens reales no se exponen por respuesta (solo el total de la conversación),
 *   así que las estadísticas usan la aproximación por caracteres.
 */
class LiteRtLmBackend @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : InferenceBackend {

    // `images` se mantiene en true: si el modelo no trae encoder de visión, el motor
    // se recrea sin él (ver createEngine) y las imágenes se ignoran con un log.
    override val capabilities = TierCapabilities(
        temperature = true,
        topK = true,
        topP = true,
        maxOutputTokens = true,
        systemPrompt = true,
        multiTurn = true,
        images = true,
        // Emulado por prompt (ver DemoTools) -- este runtime no tiene tool-calling nativo. Como el
        // resto de la config de la conversación, activarlo/desactivarlo a mitad de charla no tiene
        // efecto hasta "Nueva conversación" (se agrega al systemInstruction al crearla).
        functionCalling = true,
    )

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var visionEnabled = true

    private fun modelFile() = File(context.getExternalFilesDir(null), MODEL_FILE_NAME)
    private fun tempModelFile() = File(context.getExternalFilesDir(null), "$MODEL_FILE_NAME.part")

    override suspend fun checkAvailability(): TierAvailability {
        if (!hasAtLeastRamBytes(context, MIN_RAM_BYTES)) {
            Log.d(TAG, "checkAvailability: RAM insuficiente -> UNAVAILABLE")
            return TierAvailability.UNAVAILABLE
        }
        if (modelFile().exists()) {
            Log.d(TAG, "checkAvailability: modelo ya presente en ${modelFile().absolutePath}")
            return TierAvailability.AVAILABLE
        }
        return if (BuildConfig.LITERTLM_MODEL_URL.isNotBlank()) TierAvailability.DOWNLOADABLE else TierAvailability.UNAVAILABLE
    }

    override fun download(): Flow<Int> = flow {
        val modelUrl = BuildConfig.LITERTLM_MODEL_URL
        check(modelUrl.isNotBlank()) { "LITERTLM_MODEL_URL vacía" }
        val connection = (URL(modelUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        Log.d(TAG, "download: GET $modelUrl")
        val temp = tempModelFile()
        try {
            if (connection.responseCode !in 200..299) {
                error("HTTP ${connection.responseCode} descargando el modelo")
            }
            val totalBytes = connection.contentLengthLong
            var downloadedBytes = 0L
            var lastEmittedPercent = -1
            connection.inputStream.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        if (totalBytes > 0) {
                            val percent = ((downloadedBytes * 100) / totalBytes).toInt()
                            if (percent != lastEmittedPercent) {
                                lastEmittedPercent = percent
                                emit(percent)
                            }
                        }
                    }
                }
            }
            check(temp.renameTo(modelFile())) { "No se pudo mover el modelo descargado a su ubicación final" }
            Log.d(TAG, "download: completado, $downloadedBytes bytes")
        } finally {
            connection.disconnect()
            temp.delete() // no-op si ya se renombró a modelFile()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Sin tool-calling: un solo `sendMessageAsync`, transmite chunk a chunk (comportamiento sin
     * cambios). Con tool-calling (emulado por prompt, ver DemoTools y la instrucción agregada en
     * `getOrCreateConversation`): el primer pase se bufferea completo (nada se emite todavía) porque
     * recién al terminar el flow se puede decidir si el texto ENTERO fue el pedido de herramienta o
     * una respuesta normal — mismo trade-off que Cloud/Firebase (sin streaming visible durante el
     * pase que decide). `Conversation` es de por sí persistente entre turnos, así que el segundo pase
     * es solo otro `sendMessageAsync` con el resultado de la herramienta como mensaje nuevo.
     */
    override fun generateContentStream(history: List<ChatMessage>, config: GenerationConfig): Flow<String> = flow {
        val lastUserMessage = history.lastOrNull { it.role == Role.USER }
            ?: error("No hay turno de usuario para enviar")
        val activeConversation = getOrCreateConversation(history, config)
        val contents = buildContents(lastUserMessage)
        Log.d(TAG, "generateContentStream: enviando turno")

        if (!config.functionCalling) {
            activeConversation.sendMessageAsync(contents).collect { message ->
                val text = extractText(message)
                Log.v(TAG, "chunk: \"$text\"")
                if (text.isNotEmpty()) emit(text)
            }
            return@flow
        }

        val firstPassBuffer = StringBuilder()
        activeConversation.sendMessageAsync(contents).collect { message -> firstPassBuffer.append(extractText(message)) }
        val firstPassText = firstPassBuffer.toString()
        val timezone = DemoTools.matchReactCall(firstPassText)
        if (timezone == null) {
            emit(firstPassText)
            return@flow
        }
        Log.d(TAG, "generateContentStream: pidió get_current_time($timezone)")
        emit("🔧 usando \"${DemoTools.GET_CURRENT_TIME}\"…\n\n")
        val toolResult = DemoTools.executeCurrentTime(timezone.ifBlank { null })
        val toolResultContents = Contents.of(
            "Resultado de get_current_time: ${toolResult["legible"]} (${toolResult["iso8601"]})",
        )
        activeConversation.sendMessageAsync(toolResultContents).collect { message ->
            val text = extractText(message)
            Log.v(TAG, "chunk 2do pase: \"$text\"")
            if (text.isNotEmpty()) emit(text)
        }
    }.flowOn(Dispatchers.IO).onCompletion { cause ->
        // Si el llamador cancela a mitad de respuesta, se le avisa al motor para que deje de generar.
        if (cause != null) runCatching { conversation?.cancelProcess() }
    }

    private fun extractText(message: Message): String =
        message.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }

    private suspend fun buildContents(message: ChatMessage): Contents {
        val imageBytes = message.imageUri
            ?.takeIf { visionEnabled }
            ?.let { loadImageBytes(it) }
        return if (imageBytes != null) {
            Contents.of(Content.ImageBytes(imageBytes), Content.Text(message.text))
        } else {
            if (message.imageUri != null && !visionEnabled) {
                Log.w(TAG, "buildContents: el modelo cargado no tiene visión, se ignora la imagen")
            }
            Contents.of(message.text)
        }
    }

    /** Crea el motor (una sola vez) y la conversación (una por charla). Ambos pasos son lentos: van en IO. */
    private suspend fun getOrCreateConversation(history: List<ChatMessage>, config: GenerationConfig): Conversation =
        withContext(Dispatchers.IO) {
            conversation?.let { return@withContext it }

            val activeEngine = engine ?: createEngine().also { engine = it }

            val sampler = SamplerConfig(
                topK = config.topK ?: 40,
                topP = (config.topP ?: 0.95f).toDouble(),
                temperature = (config.temperature ?: 1.0f).toDouble(),
            )
            val previousTurns = history.dropLast(1).map { turn ->
                if (turn.role == Role.USER) Message.user(turn.text) else Message.model(turn.text)
            }
            var conversationConfig = ConversationConfig(
                initialMessages = previousTurns,
                samplerConfig = sampler,
                maxOutputToken = config.maxOutputTokens,
            )
            val systemPrompt = config.systemPrompt
            val fullSystemPrompt = if (config.functionCalling) {
                (systemPrompt?.let { "$it\n\n" } ?: "") + DemoTools.reactInstruction()
            } else {
                systemPrompt
            }
            if (fullSystemPrompt != null) {
                conversationConfig = conversationConfig.copy(systemInstruction = Contents.of(fullSystemPrompt))
            }

            Log.d(TAG, "getOrCreateConversation: config=$config, turnos previos=${previousTurns.size}")
            activeEngine.createConversation(conversationConfig).also { conversation = it }
        }

    private fun createEngine(): Engine {
        val path = modelFile().absolutePath
        val cache = context.cacheDir.absolutePath
        Log.d(TAG, "createEngine: cargando $path (puede tardar ~10s)")
        val withVision = Engine(
            EngineConfig(
                modelPath = path,
                backend = Backend.CPU(),
                visionBackend = Backend.CPU(),
                maxNumTokens = MAX_NUM_TOKENS,
                maxNumImages = 1,
                cacheDir = cache,
            )
        )
        return try {
            withVision.initialize()
            visionEnabled = true
            withVision
        } catch (e: Exception) {
            Log.w(TAG, "createEngine: falló con visión, reintentando solo texto", e)
            runCatching { withVision.close() }
            visionEnabled = false
            Engine(
                EngineConfig(
                    modelPath = path,
                    backend = Backend.CPU(),
                    maxNumTokens = MAX_NUM_TOKENS,
                    cacheDir = cache,
                )
            ).also { it.initialize() }
        }
    }

    private suspend fun loadImageBytes(uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > MAX_IMAGE_SIDE_PX) sampleSize *= 2
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
            } ?: return@withContext null
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                out.toByteArray()
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadImageBytes: no se pudo leer $uri", e)
            null
        }
    }

    override fun resetConversation() {
        Log.d(TAG, "resetConversation: cerrando conversación")
        runCatching { conversation?.close() }
        conversation = null
    }

    /** Libera también el motor (~3GB de RAM): se recrea en el próximo turno. */
    override fun close() {
        runCatching { conversation?.close() }
        conversation = null
        runCatching { engine?.close() }
        engine = null
    }
}
