package es.sebas1705.nanodemo.data.repositories.repos

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import es.sebas1705.nanodemo.core.common.ErrorReporter
import es.sebas1705.nanodemo.data.cloudgemini.CloudGeminiBackend
import es.sebas1705.nanodemo.data.firebaseai.FirebaseAiBackend
import es.sebas1705.nanodemo.data.history.IHistoryRepository
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
    private val errorReporter: ErrorReporter,
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
        scope.launch { _activeTier.collect { errorReporter.setKey("active_tier", it.name) } }
        scope.launch { mediaPipeBackend.selectedModel.collect { errorReporter.setKey("mediapipe_model", it.name) } }
        autoSelectTier()
    }

    override fun capabilitiesOf(tier: InferenceTier): TierCapabilities = backends.getValue(tier).capabilities

    private fun autoSelectTier() {
        Log.d(TAG, "autoSelectTier: probando tiers en orden ${InferenceTier.entries}")
        scope.launch {
            for (tier in InferenceTier.entries) {
                val result = checkTier(tier)
                if (result != TierAvailability.UNAVAILABLE) {
                    Log.i(TAG, "autoSelectTier: usando $tier (resultado=$result)")
                    _activeTier.update { tier }
                    applyAvailabilityToState(result)
                    return@launch
                }
            }
            Log.w(TAG, "autoSelectTier: ningún tier disponible")
            _activeTier.update { InferenceTier.CLOUD_GEMINI }
            _state.update { DemoState.Unavailable }
        }
    }

    override fun selectTier(tier: InferenceTier) {
        Log.d(TAG, "selectTier: override manual a $tier")
        residentEngineTiers.filter { it != tier }.forEach { backends.getValue(it).close() }
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
            report(e, "checkAvailability($tier)")
            TierAvailability.UNAVAILABLE
        }
        Log.d(TAG, "checkTier($tier): $result")
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
        errorReporter.log("download($tier): inicio")
        scope.launch {
            _state.update { DemoState.Downloading() }
            try {
                backends.getValue(tier).download().collect { percent ->
                    _state.update { DemoState.Downloading(percent) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "downloadModel($tier): falló", e)
                report(e, "download($tier)")
                _state.update { DemoState.Error(e.message ?: "Error preparando el modelo") }
                return@launch
            }
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
        backends.getValue(_activeTier.value).resetConversation()
        currentConversationId = null
        _conversation.update { emptyList() }
        _pendingImage.update { null }
        _state.update { DemoState.Ready }
    }

    override fun selectMediaPipeModel(option: MediaPipeModelOption) {
        Log.d(TAG, "selectMediaPipeModel: $option")
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
            backends.getValue(_activeTier.value).resetConversation()
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
            val result = runGeneration(tier, history) { newState -> _state.update { newState } }
            result.onSuccess { response ->
                val modelMessage = ChatMessage(Role.MODEL, response)
                _conversation.update { it + userMessage + modelMessage }
                _pendingImage.update { null }
                currentConversationId = historyRepository.appendMessages(
                    currentConversationId,
                    tier,
                    listOf(userMessage, modelMessage),
                )
            }
        }
    }

    override fun generateCompare(prompt: String) {
        if (prompt.isBlank()) return
        InferenceTier.entries.forEach { tier ->
            scope.launch {
                if (_availability.value[tier] != TierAvailability.AVAILABLE) {
                    _compareStates.update { it + (tier to DemoState.Unavailable) }
                    return@launch
                }
                runGeneration(tier, listOf(ChatMessage(Role.USER, prompt))) { newState ->
                    _compareStates.update { it + (tier to newState) }
                }
            }
        }
    }

    override fun runBenchmark() {
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
                            runGeneration(tier, listOf(ChatMessage(Role.USER, preset.text))) { newState ->
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
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            Log.e(TAG, "exportMetricsCsv: falló", e)
            report(e, "exportMetricsCsv")
            null
        }
    }

    private suspend fun runGeneration(
        tier: InferenceTier,
        history: List<ChatMessage>,
        onStateChange: (DemoState) -> Unit,
    ): Result<String> {
        Log.d(TAG, "runGeneration($tier): history de ${history.size} turnos")
        errorReporter.log("generate($tier): ${history.size} turnos, imagen=${history.last().imageUri != null}")
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
            Result.success(fullResponse)
        } catch (e: Exception) {
            Log.e(TAG, "runGeneration($tier): falló", e)
            report(e, "generate($tier)")
            onStateChange(DemoState.Error(e.message ?: "Error generando contenido"))
            recordMetric(tier, history.last().text, fullResponse, startedAtMs, firstChunkLatencyMs, memoryBeforeBytes, success = false)
            Result.failure(e)
        }
    }

    /** Errores manejados que la app convierte en estado de UI: se mandan como no fatales, salvo cancelaciones. */
    private fun report(e: Exception, context: String) {
        if (e !is CancellationException) errorReporter.recordNonFatal(e, context)
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
        _metrics.update { it + metric }
    }

    private fun usedMemoryBytes(): Long =
        Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
}
