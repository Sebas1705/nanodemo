package es.sebas1705.nanodemo.data.repositories.interfaces

import android.net.Uri
import es.sebas1705.nanodemo.domain.models.ChatMessage
import es.sebas1705.nanodemo.domain.models.DemoState
import es.sebas1705.nanodemo.domain.models.GenerationConfig
import es.sebas1705.nanodemo.domain.models.GenerationMetric
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.MediaPipeModelOption
import es.sebas1705.nanodemo.domain.models.TierAvailability
import es.sebas1705.nanodemo.domain.models.TierCapabilities
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

    /** Cada tier guarda su propia configuración — cambiar de tier no pisa la de los demás. */
    val configs: StateFlow<Map<InferenceTier, GenerationConfig>>
    val pendingImage: StateFlow<Uri?>

    /** URL adjunta al próximo turno de Chat ("pegar un link y preguntar") — se limpia tras usarla. */
    val attachedUrl: StateFlow<String?>
    val compareStates: StateFlow<Map<InferenceTier, DemoState>>
    val metrics: StateFlow<List<GenerationMetric>>

    /** Catálogo fijo de modelos que soporta el Tier B (MediaPipe) y cuál está seleccionado ahora. */
    val mediaPipeModels: List<MediaPipeModelOption>
    val selectedMediaPipeModel: StateFlow<MediaPipeModelOption>

    fun capabilitiesOf(tier: InferenceTier): TierCapabilities
    fun selectTier(tier: InferenceTier)
    fun downloadModel()
    fun updateConfig(tier: InferenceTier, newConfig: GenerationConfig)
    fun setPendingImage(uri: Uri?)
    fun setAttachedUrl(url: String?)
    fun resetConversation()
    fun generate(prompt: String)
    fun generateCompare(prompt: String)
    fun runBenchmark()
    fun exportMetricsCsv(): Uri?

    /** Carga una conversación guardada del historial: fija tier/transcript y sigue agregando a partir de ella. */
    fun loadConversation(conversationId: Long)

    /**
     * Borra una conversación del historial. Pasa por acá (no directo a `data:history`) porque es quien
     * conoce `currentConversationId`: si se borra la conversación que Chat sigue mostrando, hay que
     * desengancharla para que el próximo turno no intente persistir contra una fila que ya no existe.
     */
    fun deleteConversation(conversationId: Long)

    /** Cambia el modelo del Tier B (MediaPipe) — recomprueba disponibilidad para ese tier. */
    fun selectMediaPipeModel(option: MediaPipeModelOption)
}
