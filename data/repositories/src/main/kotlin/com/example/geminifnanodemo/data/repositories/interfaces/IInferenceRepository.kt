package com.example.geminifnanodemo.data.repositories.interfaces

import android.net.Uri
import com.example.geminifnanodemo.domain.models.ChatMessage
import com.example.geminifnanodemo.domain.models.DemoState
import com.example.geminifnanodemo.domain.models.GenerationConfig
import com.example.geminifnanodemo.domain.models.GenerationMetric
import com.example.geminifnanodemo.domain.models.InferenceTier
import com.example.geminifnanodemo.domain.models.MediaPipeModelOption
import com.example.geminifnanodemo.domain.models.TierAvailability
import com.example.geminifnanodemo.domain.models.TierCapabilities
import kotlinx.coroutines.flow.StateFlow

/**
 * Única fuente de verdad para el estado de los 3 tiers de IA — reemplaza al
 * `InferenceOrchestratorViewModel` de la versión de un solo módulo. Vive como
 * `@Singleton` (no como ViewModel) para que Chat/Comparar/Parámetros/
 * Dispositivo/Estadísticas, cada uno en su propio `feature:*` con su propio
 * ViewModel, compartan el mismo estado (p.ej. el tier activo elegido en Chat
 * debe verse reflejado en Parámetros).
 */
interface IInferenceRepository {
    val availability: StateFlow<Map<InferenceTier, TierAvailability>>
    val activeTier: StateFlow<InferenceTier>
    val state: StateFlow<DemoState>
    val conversation: StateFlow<List<ChatMessage>>
    val config: StateFlow<GenerationConfig>
    val pendingImage: StateFlow<Uri?>
    val compareStates: StateFlow<Map<InferenceTier, DemoState>>
    val metrics: StateFlow<List<GenerationMetric>>

    /** Catálogo fijo de modelos que soporta el Tier B (MediaPipe) y cuál está seleccionado ahora. */
    val mediaPipeModels: List<MediaPipeModelOption>
    val selectedMediaPipeModel: StateFlow<MediaPipeModelOption>

    fun capabilitiesOf(tier: InferenceTier): TierCapabilities
    fun selectTier(tier: InferenceTier)
    fun downloadModel()
    fun updateConfig(newConfig: GenerationConfig)
    fun setPendingImage(uri: Uri?)
    fun resetConversation()
    fun generate(prompt: String)
    fun generateCompare(prompt: String)
    fun runBenchmark()
    fun exportMetricsCsv(): Uri?

    /** Carga una conversación guardada del historial: fija tier/transcript y sigue agregando a partir de ella. */
    fun loadConversation(conversationId: Long)

    /** Cambia el modelo del Tier B (MediaPipe) — recomprueba disponibilidad para ese tier. */
    fun selectMediaPipeModel(option: MediaPipeModelOption)
}
