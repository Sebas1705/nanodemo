// LlmInference/LlmInferenceSession están @Deprecated en el SDK (Google
// recomienda migrar a LiteRT LM) — aceptado a propósito, ver nota en la
// clase MediaPipeBackend. Silenciado a nivel archivo porque los imports
// mismos ya generan el warning.
@file:Suppress("DEPRECATION")

package es.sebas1705.nanodemo.data.mediapipe

import android.content.Context
import android.util.Log
import es.sebas1705.nanodemo.core.common.WebSearchTool
import es.sebas1705.nanodemo.core.common.buildLocalSystemPrompt
import es.sebas1705.nanodemo.core.common.hasEnoughRamForMediaPipe
import es.sebas1705.nanodemo.core.common.resolveLocalToolCall
import es.sebas1705.nanodemo.domain.models.ChatMessage
import es.sebas1705.nanodemo.domain.models.GenerationConfig
import es.sebas1705.nanodemo.domain.models.InferenceBackend
import es.sebas1705.nanodemo.domain.models.MediaPipeModelOption
import es.sebas1705.nanodemo.domain.models.Role
import es.sebas1705.nanodemo.domain.models.TierAvailability
import es.sebas1705.nanodemo.domain.models.TierCapabilities
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession.LlmInferenceSessionOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

private const val TAG = "NanoDemo.MediaPipe"

/**
 * Tier B: MediaPipe LLM Inference con un modelo Gemma pequeño, on-device pero
 * gestionado por la app (no por AICore/Play Services como el Tier A).
 *
 * Soporta un catálogo fijo de modelos ([MediaPipeModelOption]) en vez de uno
 * solo: cada modelo se descarga a su propio archivo (nombre derivado del id),
 * así conviven varios ya descargados en el dispositivo. Cambiar de modelo
 * ([selectModel]) cierra el motor/sesión actual (se recrean con el modelo
 * nuevo en el próximo turno) — igual que "Nueva conversación", porque el
 * contexto de la sesión vieja no tiene sentido con otro modelo cargado.
 *
 * NOTA: Google marca `tasks-genai` como "maintenance-only" a nivel paquete, y
 * además `LlmInferenceSession` (que usamos acá para parámetros/multi-turno)
 * está marcada `@Deprecated` en el código fuente real ("Migrate to LiteRT LM
 * instead"). Sigue funcionando en 0.10.35 y es la única forma de tener estos
 * controles en este tier hoy — ver README para el link de migración.
 *
 * NOTA 2: no usamos `PromptTemplates` para el system prompt (su contrato
 * exacto — qué prefijos/sufijos son obligatorios — no se pudo verificar con
 * certeza sin arriesgar un crash en runtime). En cambio, si hay system
 * prompt, se agrega como el primer `addQueryChunk` de la sesión, antes de
 * cualquier turno de usuario — logra el mismo efecto de orientar la
 * conversación sin depender de una API cuyo contrato no está confirmado.
 *
 * Cada modelo `.task` requiere aceptar su licencia (Gemma en Hugging Face),
 * algo que no se puede automatizar por Gradle. Se acepta una vez con una
 * cuenta y se resube como asset de un GitHub Release privado de este mismo
 * repo — la URL de cada uno (endpoint de la API de assets, no el link
 * público) se configura en `local.properties` (ver
 * `data/mediapipe/build.gradle.kts`), y como el repo es privado hace falta
 * además un token de solo lectura compartido en `MEDIAPIPE_MODEL_TOKEN`.
 *
 * Referencia: https://developers.google.com/edge/mediapipe/solutions/genai/llm_inference/android
 */
class MediaPipeBackend @Inject constructor(
    @ApplicationContext private val context: Context,
) : InferenceBackend {

    override val capabilities = TierCapabilities(
        temperature = true,
        topK = true,
        topP = true,
        // setMaxTokens se fija al crear el motor (presupuesto de contexto total),
        // no es un límite por respuesta que se pueda tocar por turno.
        maxOutputTokens = false,
        systemPrompt = true,
        multiTurn = true,
        // Ninguno de los modelos del catálogo tiene encoder de visión —
        // habilitar esto requeriría hostear un .task multimodal aparte.
        images = false,
        // Las 3 emuladas por prompt (ver core:common/LocalPromptEmulation.kt) -- LlmInferenceSession
        // no tiene tool-calling, búsqueda ni responseMimeType nativos. Como el resto de los
        // parámetros de sesión, activar/desactivarlas a mitad de charla no tiene efecto: se agregan
        // a la sesión al crearla (getOrCreateSession), así que hace falta "Nueva conversación" para
        // que se apliquen.
        structuredOutput = true,
        functionCalling = true,
        // A diferencia de las otras 2, SÍ depende de un secreto (Google Custom Search) -- si no
        // está configurado, el toggle sale deshabilitado en vez de fallar en runtime.
        webSearch = WebSearchTool.isConfigured,
    )

    val availableModels: List<MediaPipeModelOption> = MediaPipeModelOption.entries

    private val _selectedModel = MutableStateFlow(MediaPipeModelOption.entries.first())
    val selectedModel: StateFlow<MediaPipeModelOption> = _selectedModel.asStateFlow()

    private var llmInference: LlmInference? = null
    private var session: LlmInferenceSession? = null
    private var lastResponseText: String = ""
    private val modelToken: String = BuildConfig.MEDIAPIPE_MODEL_TOKEN

    // `LlmInferenceSession` solo permite una `generateResponseAsync` en curso a la vez: cerrarla (o abrir
    // otra) mientras la anterior no llamó a su callback con done=true lanza IllegalStateException
    // ("Previous invocation still processing") — visto en Crashlytics al cambiar de tier a mitad de
    // respuesta (selectTier -> close()), verificado leyendo LlmInferenceSession.validateState() en el AAR
    // real. `cancelGenerateResponseAsync()` (misma clase) es la forma soportada de interrumpirla; su
    // callback sigue llamándose con done=true, así que el cierre real se difiere hasta ese momento.
    @Volatile
    private var isGenerating = false

    @Volatile
    private var deferredCleanup: (() -> Unit)? = null

    /** Cambia el modelo activo. Cierra el motor/sesión cargados (si había) — se recrean con el modelo nuevo al generar. */
    fun selectModel(option: MediaPipeModelOption) {
        if (_selectedModel.value == option) return
        Log.d(TAG, "selectModel: $option")
        close()
        _selectedModel.value = option
    }

    /**
     * Gemma 3 1B entra en un solo asset; Gemma 2 2B (2.71GB) supera el límite
     * de 2GB por asset de GitHub Releases, así que está partido en 2 — acá
     * cada modelo declara cuántas URLs (partes) necesita, y [download] las
     * baja todas y las concatena en orden.
     */
    private fun modelUrlsOf(option: MediaPipeModelOption): List<String> = when (option) {
        MediaPipeModelOption.GEMMA_3_1B -> listOf(BuildConfig.MEDIAPIPE_MODEL_URL_GEMMA_3_1B)
        MediaPipeModelOption.GEMMA_2_2B -> listOf(
            BuildConfig.MEDIAPIPE_MODEL_URL_GEMMA_2_2B_PART1,
            BuildConfig.MEDIAPIPE_MODEL_URL_GEMMA_2_2B_PART2,
        )
    }

    private fun modelFile(option: MediaPipeModelOption): File =
        File(context.getExternalFilesDir(null), "gemma-model-${option.id}.task")

    private fun assemblingFile(option: MediaPipeModelOption): File =
        File(context.getExternalFilesDir(null), "gemma-model-${option.id}.task.assembling")

    private fun partFile(option: MediaPipeModelOption, index: Int): File =
        File(context.getExternalFilesDir(null), "gemma-model-${option.id}.part$index.download")

    override suspend fun checkAvailability(): TierAvailability {
        if (!hasEnoughRamForMediaPipe(context)) {
            Log.d(TAG, "checkAvailability: RAM insuficiente -> UNAVAILABLE")
            return TierAvailability.UNAVAILABLE
        }
        val option = _selectedModel.value
        if (modelFile(option).exists()) {
            Log.d(TAG, "checkAvailability: modelo $option ya presente en ${modelFile(option).absolutePath}")
            return TierAvailability.AVAILABLE
        }
        val urls = modelUrlsOf(option)
        Log.d(TAG, "checkAvailability: modelo $option ausente, hasUrls=${urls.all { it.isNotBlank() }}")
        return if (urls.all { it.isNotBlank() }) TierAvailability.DOWNLOADABLE else TierAvailability.UNAVAILABLE
    }

    override fun download(): Flow<Int> = flow {
        val option = _selectedModel.value
        val urls = modelUrlsOf(option)
        check(urls.all { it.isNotBlank() }) { "Falta alguna URL en local.properties para $option" }

        val partFiles = urls.indices.map { partFile(option, it) }
        val assembling = assemblingFile(option)
        var downloadedBytes = 0L
        var totalBytes = 0L
        var lastEmittedPercent = -1
        try {
            urls.forEachIndexed { index, url ->
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    // El endpoint de assets de GitHub Releases devuelve metadata JSON por
                    // default; con este Accept devuelve los bytes del archivo directamente.
                    setRequestProperty("Accept", "application/octet-stream")
                    if (modelToken.isNotBlank()) {
                        setRequestProperty("Authorization", "Bearer $modelToken")
                    }
                }
                Log.d(TAG, "download: GET $url (modelo=$option, parte ${index + 1}/${urls.size})")
                try {
                    if (connection.responseCode !in 200..299) {
                        error("HTTP ${connection.responseCode} descargando la parte ${index + 1} del modelo")
                    }
                    // El tamaño de cada parte recién se conoce cuando se abre su
                    // conexión, así que el total (y por lo tanto el %) crece en
                    // escalones entre partes en vez de ser perfectamente lineal
                    // — aceptable para una barra de progreso, no crítico.
                    totalBytes += connection.contentLengthLong.coerceAtLeast(0)
                    connection.inputStream.use { input ->
                        partFiles[index].outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                downloadedBytes += read
                                if (totalBytes > 0) {
                                    val percent = ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 99)
                                    if (percent != lastEmittedPercent) {
                                        lastEmittedPercent = percent
                                        emit(percent)
                                    }
                                }
                            }
                        }
                    }
                } finally {
                    connection.disconnect()
                }
            }

            Log.d(TAG, "download: ${partFiles.size} parte(s) completas, concatenando")
            assembling.outputStream().use { output ->
                partFiles.forEach { part -> part.inputStream().use { it.copyTo(output) } }
            }
            check(assembling.renameTo(modelFile(option))) { "No se pudo mover el modelo descargado a su ubicación final" }
            emit(100)
            Log.d(TAG, "download: completado, $downloadedBytes bytes")
        } finally {
            partFiles.forEach { it.delete() } // no-op para las que ya se leyeron al concatenar
            assembling.delete() // no-op si ya se renombró a modelFile()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Sin herramientas: un solo `generateResponseAsync`, transmite chunk a chunk (comportamiento sin
     * cambios). Con herramientas (function calling y/o búsqueda, emuladas por prompt — ver la
     * instrucción agregada en `getOrCreateSession` y `resolveLocalToolCall`): el primer pase se
     * bufferea completo (nada se emite todavía) porque recién al llegar `done=true` se puede decidir
     * si el texto ENTERO fue un pedido de herramienta o una respuesta normal. `resolveLocalToolCall`
     * es `suspend` (la búsqueda hace red), de ahí el `launch` dentro del callback. `isGenerating` se
     * mantiene `true` durante los dos pases -- es una sola generación desde la perspectiva de
     * `close()`/`resetConversation()`, así que el cierre diferido (ver `runOrDefer`) solo se resuelve
     * una vez, al terminar el pase que de verdad sea el último.
     */
    override fun generateContentStream(history: List<ChatMessage>, config: GenerationConfig): Flow<String> = callbackFlow {
        lastResponseText = ""
        val lastUserMessage = history.lastOrNull { it.role == Role.USER }
            ?: error("No hay turno de usuario para enviar")
        val activeSession = getOrCreateSession(config)
        Log.d(TAG, "generateContentStream: enviando turno")
        activeSession.addQueryChunk(lastUserMessage.text)
        isGenerating = true

        fun finishGeneration() {
            isGenerating = false
            deferredCleanup?.let { cleanup ->
                deferredCleanup = null
                Log.d(TAG, "generateContentStream: ejecutando el cierre que quedó pendiente")
                cleanup()
            }
            close() // ProducerScope.close(): termina este Flow (no es MediaPipeBackend.close()).
        }

        if (!config.functionCalling && !config.webSearch) {
            activeSession.generateResponseAsync { partialResult, done ->
                Log.v(TAG, "chunk (done=$done): \"$partialResult\"")
                lastResponseText += partialResult
                trySend(partialResult)
                if (done) finishGeneration()
            }
        } else {
            val firstPassBuffer = StringBuilder()
            activeSession.generateResponseAsync { partialResult, done ->
                firstPassBuffer.append(partialResult)
                if (!done) return@generateResponseAsync
                val firstPassText = firstPassBuffer.toString()
                launch {
                    val toolCall = resolveLocalToolCall(firstPassText, config.functionCalling, config.webSearch)
                    if (toolCall == null) {
                        lastResponseText += firstPassText
                        trySend(firstPassText)
                        finishGeneration()
                        return@launch
                    }
                    Log.d(TAG, "generateContentStream: pidió una herramienta")
                    lastResponseText += toolCall.marker
                    trySend(toolCall.marker)
                    activeSession.addQueryChunk(toolCall.resultMessage)
                    activeSession.generateResponseAsync { partialResult2, done2 ->
                        Log.v(TAG, "chunk 2do pase (done=$done2): \"$partialResult2\"")
                        lastResponseText += partialResult2
                        trySend(partialResult2)
                        if (done2) finishGeneration()
                    }
                }
            }
        }
        awaitClose { }
    }

    /**
     * La sesión se crea una vez y se reusa entre turnos (así funciona el
     * multi-turno de MediaPipe: la sesión acumula contexto internamente).
     * Los parámetros de generación quedan fijos a los de la PRIMERA llamada
     * de la conversación — cambiarlos a mitad de charla requiere "Nueva
     * conversación" para que se apliquen (cambiar de modelo también, vía
     * [selectModel]).
     */
    private suspend fun getOrCreateSession(config: GenerationConfig): LlmInferenceSession =
        withContext(Dispatchers.IO) {
            session?.let { return@withContext it }

            val option = _selectedModel.value
            val inference = llmInference ?: run {
                Log.d(TAG, "getOrCreateSession: cargando modelo $option desde ${modelFile(option).absolutePath}")
                val options = LlmInferenceOptions.builder()
                    .setModelPath(modelFile(option).absolutePath)
                    .setMaxTokens(1024)
                    .build()
                LlmInference.createFromOptions(context, options).also { llmInference = it }
            }

            Log.d(TAG, "getOrCreateSession: creando sesión con config=$config")
            val sessionOptionsBuilder = LlmInferenceSessionOptions.builder()
            config.temperature?.let { sessionOptionsBuilder.setTemperature(it) }
            config.topK?.let { sessionOptionsBuilder.setTopK(it) }
            config.topP?.let { sessionOptionsBuilder.setTopP(it) }

            val newSession = LlmInferenceSession.createFromOptions(inference, sessionOptionsBuilder.build())
            val fullSystemPrompt =
                buildLocalSystemPrompt(config.systemPrompt, config.functionCalling, config.webSearch, config.jsonMode)
            fullSystemPrompt?.let { newSession.addQueryChunk("Instrucciones: $it\n\n") }
            session = newSession
            newSession
        }

    override fun resetConversation() {
        Log.d(TAG, "resetConversation: cerrando sesión")
        runOrDefer {
            runCatching { session?.close() }
                .onFailure { Log.e(TAG, "resetConversation: fallo cerrando la sesión", it) }
            session = null
        }
    }

    override fun lastResponseTokens(): Int? =
        lastResponseText.takeIf { it.isNotEmpty() }?.let { llmInference?.sizeInTokens(it) }

    override fun close() {
        runOrDefer {
            runCatching { session?.close() }
                .onFailure { Log.e(TAG, "close: fallo cerrando la sesión", it) }
            session = null
            runCatching { llmInference?.close() }
                .onFailure { Log.e(TAG, "close: fallo cerrando el motor", it) }
            llmInference = null
        }
    }

    /**
     * Si hay una respuesta en curso, pide cancelarla y deja `cleanup` para cuando su callback confirme
     * done=true (ver `generateContentStream`); ejecutarlo ahora tiraría la misma IllegalStateException que
     * se ve en Crashlytics. Si no hay nada en curso, se ejecuta al toque.
     */
    private fun runOrDefer(cleanup: () -> Unit) {
        if (!isGenerating) {
            cleanup()
            return
        }
        Log.w(TAG, "runOrDefer: hay una generación en curso, se cancela y el cierre queda pendiente")
        // Si ya había un cierre pendiente (p.ej. resetConversation() seguido de close() antes de que
        // termine la cancelación), se encadenan en vez de perder el primero — ambos son idempotentes.
        val previous = deferredCleanup
        deferredCleanup = if (previous != null) {
            { previous(); cleanup() }
        } else {
            cleanup
        }
        runCatching { session?.cancelGenerateResponseAsync() }
            .onFailure { Log.e(TAG, "runOrDefer: no se pudo cancelar la generación en curso", it) }
    }
}
