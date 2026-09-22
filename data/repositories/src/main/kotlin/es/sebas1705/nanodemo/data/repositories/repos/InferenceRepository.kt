package es.sebas1705.nanodemo.data.repositories.repos

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import es.sebas1705.nanodemo.core.common.Telemetry
import es.sebas1705.nanodemo.core.common.TelemetryResult
import es.sebas1705.nanodemo.data.cloudgemini.CloudGeminiBackend
import es.sebas1705.nanodemo.data.firebaseai.FirebaseAiBackend
import es.sebas1705.nanodemo.data.history.IHistoryRepository
import es.sebas1705.nanodemo.data.history.IMetricsRepository
import es.sebas1705.nanodemo.data.litertlm.LiteRtLmBackend
import es.sebas1705.nanodemo.data.mediapipe.MediaPipeBackend
import es.sebas1705.nanodemo.data.mlkitnano.MlKitNanoBackend
import es.sebas1705.nanodemo.data.repositories.interfaces.IInferenceRepository
import es.sebas1705.nanodemo.domain.models.ChatMessage
import es.sebas1705.nanodemo.domain.models.DemoState
import es.sebas1705.nanodemo.domain.models.GenerationConfig
import es.sebas1705.nanodemo.domain.models.GenerationMetric
import es.sebas1705.nanodemo.domain.models.InferenceBackend
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.MediaPipeModelOption
import es.sebas1705.nanodemo.domain.models.PROMPT_BANK
import es.sebas1705.nanodemo.domain.models.Role
import es.sebas1705.nanodemo.domain.models.TierAvailability
import es.sebas1705.nanodemo.domain.models.TierCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers

private const val TAG = "NanoDemo"
private const val DOWNLOAD_MILESTONE_STEP = 25
private const val BYTES_PER_KB = 1024L

/**
 * Implementación única — antes era `InferenceOrchestratorViewModel`, movida
 * un nivel más abajo (deja de heredar de `AndroidViewModel`, usa
 * `@ApplicationContext` inyectado) para poder ser compartida por los 5
 * `feature:*` a través de sus propios ViewModels y casos de uso.
 */
@Singleton
class InferenceRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    mlKitNanoBackend: MlKitNanoBackend,
    private val mediaPipeBackend: MediaPipeBackend,
    cloudGeminiBackend: CloudGeminiBackend,
    liteRtLmBackend: LiteRtLmBackend,
    firebaseAiBackend: FirebaseAiBackend,
    private val historyRepository: IHistoryRepository,
    private val metricsRepository: IMetricsRepository,
    private val telemetry: Telemetry,
) : IInferenceRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val backends: Map<InferenceTier, InferenceBackend> = mapOf(
        InferenceTier.ML_KIT_NANO to mlKitNanoBackend,
        InferenceTier.MEDIAPIPE_LOCAL to mediaPipeBackend,
        InferenceTier.CLOUD_GEMINI to cloudGeminiBackend,
        InferenceTier.LITERT_LM_LOCAL to liteRtLmBackend,
        InferenceTier.FIREBASE_AI to firebaseAiBackend,
    )

    /**
     * Tiers cuyo motor mantiene el modelo cargado en RAM (~1-3GB cada uno). Al cambiar de
     * tier se libera el de los demás para no acumular varios a la vez y que el sistema
     * mate la app; el motor se recrea solo en el próximo turno de ese tier.
     */
    private val residentEngineTiers = setOf(InferenceTier.MEDIAPIPE_LOCAL, InferenceTier.LITERT_LM_LOCAL)

    override val mediaPipeModels: List<MediaPipeModelOption> = mediaPipeBackend.availableModels
    override val selectedMediaPipeModel: StateFlow<MediaPipeModelOption> = mediaPipeBackend.selectedModel

    /** Conversación actualmente persistida (null hasta el primer turno, o tras "Nueva conversación"). */
    private var currentConversationId: Long? = null

    private val _availability = MutableStateFlow(
        InferenceTier.entries.associateWith { TierAvailability.UNKNOWN }
    )
    override val availability: StateFlow<Map<InferenceTier, TierAvailability>> = _availability.asStateFlow()

    private val _activeTier = MutableStateFlow(InferenceTier.ML_KIT_NANO)
    override val activeTier: StateFlow<InferenceTier> = _activeTier.asStateFlow()

    private val _state = MutableStateFlow<DemoState>(DemoState.CheckingAvailability)
    override val state: StateFlow<DemoState> = _state.asStateFlow()

    private val _metrics = MutableStateFlow<List<GenerationMetric>>(emptyList())
    override val metrics: StateFlow<List<GenerationMetric>> = _metrics.asStateFlow()

    private val _conversation = MutableStateFlow<List<ChatMessage>>(emptyList())
    override val conversation: StateFlow<List<ChatMessage>> = _conversation.asStateFlow()

    private val _config = MutableStateFlow(GenerationConfig())
    override val config: StateFlow<GenerationConfig> = _config.asStateFlow()

    private val _pendingImage = MutableStateFlow<Uri?>(null)
    override val pendingImage: StateFlow<Uri?> = _pendingImage.asStateFlow()

    private val _compareStates = MutableStateFlow<Map<InferenceTier, DemoState>>(emptyMap())
    override val compareStates: StateFlow<Map<InferenceTier, DemoState>> = _compareStates.asStateFlow()

    init {
        // Contexto técnico que acompaña a cualquier crash o error no fatal (sin contenido del usuario).
        scope.launch { _activeTier.collect { telemetry.setContext("active_tier", it.name) } }
        scope.launch { mediaPipeBackend.selectedModel.collect { telemetry.setContext("mediapipe_model", it.name) } }
        // Antes las corridas de generate() solo vivían en `_metrics` (memoria, se perdían al matar el
        // proceso). Se recuperan las persistidas una vez al arrancar; `recordMetric()` sigue actualizando
        // este StateFlow al toque en cada turno (la UI no espera al disco) y las persiste en paralelo.
        scope.launch {
            try {
                val persisted = metricsRepository.loadAll()
                // Por si un generate() ya completó (y agregó a _metrics) antes de que termine esta carga:
                // no pisar lo nuevo con una foto del disco que todavía no lo tiene.
                _metrics.update { current -> if (current.isEmpty()) persisted else current }
            } catch (e: Exception) {
                Log.e(TAG, "init: no se pudieron cargar las métricas guardadas", e)
                telemetry.failure(InferenceOperations.LOAD_METRICS, e)
            }
        }
        autoSelectTier()
    }

    /** Modelo concreto de un tier cuando hay catálogo (solo MediaPipe hoy); `null` en el resto. */
    private fun modelOf(tier: InferenceTier): String? =
        if (tier == InferenceTier.MEDIAPIPE_LOCAL) mediaPipeBackend.selectedModel.value.name else null

    override fun capabilitiesOf(tier: InferenceTier): TierCapabilities = backends.getValue(tier).capabilities

    private fun autoSelectTier() {
        Log.d(TAG, "autoSelectTier: probando tiers en orden ${InferenceTier.entries}")
        scope.launch {
            for (tier in InferenceTier.entries) {
                val result = checkTier(tier)
                if (result != TierAvailability.UNAVAILABLE) {
                    Log.i(TAG, "autoSelectTier: usando $tier (resultado=$result)")
                    telemetry.event(
                        InferenceEvents.TIER_SELECTED,
                        InferenceParams.TIER to tier.name,
                        InferenceParams.REASON to "auto",
                    )
                    _activeTier.update { tier }
                    applyAvailabilityToState(result)
                    return@launch
                }
            }
            Log.w(TAG, "autoSelectTier: ningún tier disponible")
            telemetry.event(
                InferenceEvents.TIER_SELECTED,
                InferenceParams.TIER to InferenceTier.CLOUD_GEMINI.name,
                InferenceParams.REASON to "none_available",
            )
            _activeTier.update { InferenceTier.CLOUD_GEMINI }
            _state.update { DemoState.Unavailable }
        }
    }

    override fun selectTier(tier: InferenceTier) {
        Log.d(TAG, "selectTier: override manual a $tier")
        telemetry.event(
            InferenceEvents.TIER_SELECTED,
            InferenceParams.TIER to tier.name,
            InferenceParams.REASON to "manual",
        )
        residentEngineTiers.filter { it != tier }.forEach { closeBackendSafely(it) }
        _activeTier.update { tier }
        scope.launch {
            applyAvailabilityToState(checkTier(tier))
        }
    }

    private suspend fun checkTier(tier: InferenceTier): TierAvailability {
        _availability.update { it + (tier to TierAvailability.CHECKING) }
        val result = try {
            backends.getValue(tier).checkAvailability()
        } catch (e: Exception) {
            Log.e(TAG, "checkTier($tier): excepción comprobando disponibilidad", e)
            telemetry.failure(InferenceOperations.CHECK_AVAILABILITY, e, null, InferenceParams.TIER to tier.name)
            TierAvailability.UNAVAILABLE
        }
        Log.d(TAG, "checkTier($tier): $result")
        telemetry.event(
            InferenceEvents.TIER_AVAILABILITY,
            InferenceParams.TIER to tier.name,
            InferenceParams.MODEL to modelOf(tier),
            InferenceParams.AVAILABILITY to result.name,
        )
        _availability.update { it + (tier to result) }
        return result
    }

    private fun applyAvailabilityToState(result: TierAvailability) {
        _state.update {
            when (result) {
                TierAvailability.AVAILABLE -> DemoState.Ready
                TierAvailability.DOWNLOADABLE -> DemoState.Downloadable
                else -> DemoState.Unavailable
            }
        }
    }

    override fun downloadModel() {
        val tier = _activeTier.value
        Log.d(TAG, "downloadModel: iniciando para $tier")
        val model = modelOf(tier)
        telemetry.snapshotMemory()
        telemetry.breadcrumb("descarga($tier${model?.let { "/$it" } ?: ""}): inicio")
        scope.launch {
            val startedAtMs = System.currentTimeMillis()
            var lastMilestone = -1
            _state.update { DemoState.Downloading() }
            try {
                backends.getValue(tier).download().collect { percent ->
                    _state.update { DemoState.Downloading(percent) }
                    // Migas de pan cada 25 %: una por cada punto llenaría el registro de Crashlytics.
                    val milestone = percent / DOWNLOAD_MILESTONE_STEP
                    if (milestone > lastMilestone) {
                        lastMilestone = milestone
                        telemetry.breadcrumb("descarga($tier): ${milestone * DOWNLOAD_MILESTONE_STEP}%")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "downloadModel($tier): falló", e)
                val duration = System.currentTimeMillis() - startedAtMs
                if (e is CancellationException) {
                    telemetry.operation(InferenceOperations.DOWNLOAD, TelemetryResult.CANCELLED, duration, InferenceParams.TIER to tier.name)
                } else {
                    telemetry.failure(
                        InferenceOperations.DOWNLOAD, e, duration,
                        InferenceParams.TIER to tier.name,
                        InferenceParams.MODEL to model,
                    )
                }
                _state.update { DemoState.Error(e.message ?: "Error preparando el modelo") }
                return@launch
            }
            telemetry.operation(
                InferenceOperations.DOWNLOAD, TelemetryResult.SUCCESS, System.currentTimeMillis() - startedAtMs,
                InferenceParams.TIER to tier.name,
                InferenceParams.MODEL to model,
            )
            Log.d(TAG, "downloadModel: terminado para $tier, recomprobando")
            applyAvailabilityToState(checkTier(tier))
        }
    }

    override fun updateConfig(newConfig: GenerationConfig) {
        _config.update { newConfig }
    }

    override fun setPendingImage(uri: Uri?) {
        _pendingImage.update { uri }
    }

    override fun resetConversation() {
        Log.d(TAG, "resetConversation: tier=${_activeTier.value}")
        telemetry.feature(InferenceFeatures.CONVERSATION_RESET, InferenceParams.TIER to _activeTier.value.name)
        resetBackendSafely(_activeTier.value)
        currentConversationId = null
        _conversation.update { emptyList() }
        _pendingImage.update { null }
        _state.update { DemoState.Ready }
    }

    override fun selectMediaPipeModel(option: MediaPipeModelOption) {
        Log.d(TAG, "selectMediaPipeModel: $option")
        telemetry.feature(InferenceFeatures.MEDIAPIPE_MODEL_SELECT, InferenceParams.MODEL to option.name)
        val wasActive = _activeTier.value == InferenceTier.MEDIAPIPE_LOCAL
        mediaPipeBackend.selectModel(option)
        if (wasActive) {
            currentConversationId = null
            _conversation.update { emptyList() }
            _pendingImage.update { null }
        }
        scope.launch {
            val result = checkTier(InferenceTier.MEDIAPIPE_LOCAL)
            if (wasActive) applyAvailabilityToState(result)
        }
    }

    override fun loadConversation(conversationId: Long) {
        Log.d(TAG, "loadConversation: id=$conversationId")
        scope.launch {
            val conv = historyRepository.getConversation(conversationId) ?: return@launch
            val messages = historyRepository.getMessages(conversationId)
            telemetry.feature(
                InferenceFeatures.CONVERSATION_OPEN,
                InferenceParams.TIER to conv.tier.name,
                InferenceParams.TURNS to messages.size,
            )
            resetBackendSafely(_activeTier.value)
            currentConversationId = conversationId
            _activeTier.update { conv.tier }
            _conversation.update { messages }
            _pendingImage.update { null }
            applyAvailabilityToState(checkTier(conv.tier))
        }
    }

    override fun generate(prompt: String) {
        if (prompt.isBlank()) return
        val tier = _activeTier.value
        val userMessage = ChatMessage(Role.USER, prompt, _pendingImage.value)
        val history = _conversation.value + userMessage
        scope.launch {
            val result = runGeneration(tier, history, GenerationMode.CHAT) { newState -> _state.update { newState } }
            result.onSuccess { response ->
                val modelMessage = ChatMessage(Role.MODEL, response)
                _conversation.update { it + userMessage + modelMessage }
                _pendingImage.update { null }
                try {
                    currentConversationId = historyRepository.appendMessages(
                        currentConversationId,
                        tier,
                        listOf(userMessage, modelMessage),
                    )
                } catch (e: Exception) {
                    // No debería pasar ya que deleteConversation() limpia currentConversationId al borrar la
                    // activa, pero si algo más lo deja apuntando a una fila inválida (u otro fallo de Room),
                    // no tiene que tirar abajo la app: el turno ya está en pantalla, solo se pierde su
                    // persistencia en el historial. Se limpia el id para que el próximo turno cree una
                    // conversación nueva en vez de repetir el mismo error.
                    Log.e(TAG, "generate($tier): no se pudo guardar el turno en el historial", e)
                    telemetry.failure(InferenceOperations.PERSIST_TURN, e, null, InferenceParams.TIER to tier.name)
                    currentConversationId = null
                }
            }
        }
    }

    override fun generateCompare(prompt: String) {
        if (prompt.isBlank()) return
        telemetry.feature(InferenceFeatures.COMPARE_RUN, InferenceParams.TURNS to InferenceTier.entries.size)
        InferenceTier.entries.forEach { tier ->
            scope.launch {
                if (_availability.value[tier] != TierAvailability.AVAILABLE) {
                    _compareStates.update { it + (tier to DemoState.Unavailable) }
                    return@launch
                }
                runGeneration(tier, listOf(ChatMessage(Role.USER, prompt)), GenerationMode.COMPARE) { newState ->
                    _compareStates.update { it + (tier to newState) }
                }
            }
        }
    }

    override fun runBenchmark() {
        telemetry.feature(InferenceFeatures.BENCHMARK_RUN, InferenceParams.TURNS to PROMPT_BANK.size)
        scope.launch {
            for (preset in PROMPT_BANK) {
                Log.i(TAG, "runBenchmark: preset=${preset.name}")
                coroutineScope {
                    InferenceTier.entries.map { tier ->
                        async {
                            if (_availability.value[tier] != TierAvailability.AVAILABLE) {
                                _compareStates.update { it + (tier to DemoState.Unavailable) }
                                return@async
                            }
                            runGeneration(tier, listOf(ChatMessage(Role.USER, preset.text)), GenerationMode.BENCHMARK) { newState ->
                                _compareStates.update { it + (tier to newState) }
                            }
                            Unit
                        }
                    }.awaitAll()
                }
            }
            Log.i(TAG, "runBenchmark: completo (${PROMPT_BANK.size} presets)")
        }
    }

    override fun exportMetricsCsv(): Uri? {
        val runs = _metrics.value
        if (runs.isEmpty()) return null
        val csv = buildString {
            appendLine("tier,startedAtMs,success,firstChunkLatencyMs,totalDurationMs,promptChars,responseChars,responseTokens,responseTokensExact,memoryDeltaBytes")
            runs.forEach { m ->
                appendLine(
                    "${m.tier},${m.startedAtMs},${m.success},${m.firstChunkLatencyMs ?: ""}," +
                        "${m.totalDurationMs},${m.promptChars},${m.responseChars}," +
                        "${m.responseTokens},${m.responseTokensExact},${m.memoryDeltaBytes}"
                )
            }
        }
        return try {
            val file = File(context.cacheDir, "nanodemo-metrics.csv")
            file.writeText(csv)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file).also {
                telemetry.operation(InferenceOperations.EXPORT_CSV, TelemetryResult.SUCCESS, null, InferenceParams.TURNS to runs.size)
            }
        } catch (e: Exception) {
            Log.e(TAG, "exportMetricsCsv: falló", e)
            telemetry.failure(InferenceOperations.EXPORT_CSV, e)
            null
        }
    }

    private suspend fun runGeneration(
        tier: InferenceTier,
        history: List<ChatMessage>,
        mode: String,
        onStateChange: (DemoState) -> Unit,
    ): Result<String> {
        Log.d(TAG, "runGeneration($tier): history de ${history.size} turnos")
        val model = modelOf(tier)
        val hasImage = history.last().imageUri != null
        // Estado de memoria justo antes de generar: sin él, un cierre por falta de RAM no deja rastro.
        telemetry.snapshotMemory()
        telemetry.breadcrumb("generar($tier${model?.let { "/$it" } ?: ""}, modo=$mode): ${history.size} turnos, imagen=$hasImage")
        val startedAtMs = System.currentTimeMillis()
        var firstChunkLatencyMs: Long? = null
        var fullResponse = ""
        val memoryBeforeBytes = usedMemoryBytes()
        onStateChange(DemoState.Generating(""))
        return try {
            backends.getValue(tier).generateContentStream(history, _config.value).collect { chunkText ->
                if (firstChunkLatencyMs == null) {
                    firstChunkLatencyMs = System.currentTimeMillis() - startedAtMs
                }
                fullResponse += chunkText
                onStateChange(DemoState.Generating(fullResponse))
            }
            Log.d(TAG, "runGeneration($tier): completado (${fullResponse.length} chars)")
            onStateChange(DemoState.Result(fullResponse))
            recordMetric(tier, history.last().text, fullResponse, startedAtMs, firstChunkLatencyMs, memoryBeforeBytes, success = true)
            val metric = _metrics.value.last()
            telemetry.operation(
                InferenceOperations.GENERATE, TelemetryResult.SUCCESS, metric.totalDurationMs,
                InferenceParams.TIER to tier.name,
                InferenceParams.MODEL to model,
                InferenceParams.MODE to mode,
                InferenceParams.TURNS to history.size,
                InferenceParams.HAS_IMAGE to hasImage,
                InferenceParams.FIRST_CHUNK_MS to metric.firstChunkLatencyMs,
                InferenceParams.PROMPT_CHARS to metric.promptChars,
                InferenceParams.RESPONSE_CHARS to metric.responseChars,
                InferenceParams.RESPONSE_TOKENS to metric.responseTokens,
                InferenceParams.TOKENS_EXACT to metric.responseTokensExact,
                InferenceParams.MEMORY_DELTA_KB to metric.memoryDeltaBytes / BYTES_PER_KB,
            )
            Result.success(fullResponse)
        } catch (e: Exception) {
            Log.e(TAG, "runGeneration($tier): falló", e)
            val duration = System.currentTimeMillis() - startedAtMs
            if (e is CancellationException) {
                telemetry.operation(
                    InferenceOperations.GENERATE, TelemetryResult.CANCELLED, duration,
                    InferenceParams.TIER to tier.name,
                    InferenceParams.MODE to mode,
                )
            } else {
                telemetry.failure(
                    InferenceOperations.GENERATE, e, duration,
                    InferenceParams.TIER to tier.name,
                    InferenceParams.MODEL to model,
                    InferenceParams.MODE to mode,
                    InferenceParams.TURNS to history.size,
                    InferenceParams.HAS_IMAGE to hasImage,
                )
            }
            onStateChange(DemoState.Error(e.message ?: "Error generando contenido"))
            recordMetric(tier, history.last().text, fullResponse, startedAtMs, firstChunkLatencyMs, memoryBeforeBytes, success = false)
            Result.failure(e)
        }
    }

    private fun recordMetric(
        tier: InferenceTier,
        prompt: String,
        response: String,
        startedAtMs: Long,
        firstChunkLatencyMs: Long?,
        memoryBeforeBytes: Long,
        success: Boolean,
    ) {
        val exactTokens = backends.getValue(tier).lastResponseTokens()
        val approxTokens = if (response.isEmpty()) 0 else (response.length / 4).coerceAtLeast(1)
        val metric = GenerationMetric(
            tier = tier,
            startedAtMs = startedAtMs,
            firstChunkLatencyMs = firstChunkLatencyMs,
            totalDurationMs = System.currentTimeMillis() - startedAtMs,
            promptChars = prompt.length,
            responseChars = response.length,
            success = success,
            responseTokens = exactTokens ?: approxTokens,
            responseTokensExact = exactTokens != null,
            memoryDeltaBytes = usedMemoryBytes() - memoryBeforeBytes,
        )
        _metrics.update { it + metric } // la UI ve la corrida nueva al toque, sin esperar al disco.
        scope.launch {
            try {
                metricsRepository.record(metric)
            } catch (e: Exception) {
                Log.e(TAG, "recordMetric($tier): no se pudo persistir", e)
                telemetry.failure(InferenceOperations.PERSIST_METRIC, e, null, InferenceParams.TIER to tier.name)
            }
        }
    }

    private fun usedMemoryBytes(): Long =
        Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }

    /**
     * `close()`/`resetConversation()` de un `InferenceBackend` no deberían lanzar (ver la nota de
     * `MediaPipeBackend`, que es la que tenía el bug real: cerraba la sesión mientras seguía procesando
     * una respuesta y tiraba `IllegalStateException`, visto en Crashlytics — ya corregido ahí cancelando
     * en vez de cerrar a ciegas). Estas dos envolturas quedan igual como red de seguridad: si algún
     * backend futuro repite el error, un fallo puntual no debe tirar abajo toda la app.
     */
    private fun closeBackendSafely(tier: InferenceTier) {
        try {
            backends.getValue(tier).close()
        } catch (e: Exception) {
            Log.e(TAG, "closeBackendSafely($tier): close() falló", e)
            telemetry.failure(InferenceOperations.CLOSE_BACKEND, e, null, InferenceParams.TIER to tier.name)
        }
    }

    private fun resetBackendSafely(tier: InferenceTier) {
        try {
            backends.getValue(tier).resetConversation()
        } catch (e: Exception) {
            Log.e(TAG, "resetBackendSafely($tier): resetConversation() falló", e)
            telemetry.failure(InferenceOperations.RESET_BACKEND, e, null, InferenceParams.TIER to tier.name)
        }
    }

    override fun deleteConversation(conversationId: Long) {
        Log.d(TAG, "deleteConversation: id=$conversationId")
        telemetry.feature(InferenceFeatures.CONVERSATION_DELETE)
        scope.launch {
            try {
                historyRepository.deleteConversation(conversationId)
            } catch (e: Exception) {
                Log.e(TAG, "deleteConversation($conversationId): falló", e)
                telemetry.failure(InferenceOperations.DELETE_CONVERSATION, e)
                return@launch
            }
            // La fila borrada podía ser la que Chat sigue mostrando (currentConversationId desactualizado)
            // — si no se limpia acá, el próximo turno intentaría persistir mensajes contra una conversación
            // que ya no existe (FOREIGN KEY constraint failed, visto en Crashlytics). El motor del backend
            // no se toca: su sesión en RAM sigue viva, solo se desengancha de la fila borrada.
            if (currentConversationId == conversationId) {
                currentConversationId = null
                _conversation.update { emptyList() }
                _pendingImage.update { null }
            }
        }
    }
}
