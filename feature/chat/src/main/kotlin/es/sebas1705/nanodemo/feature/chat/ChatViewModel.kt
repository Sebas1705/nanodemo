package es.sebas1705.nanodemo.feature.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import es.sebas1705.nanodemo.domain.models.ChatMessage
import es.sebas1705.nanodemo.domain.models.DemoState
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.MediaPipeModelOption
import es.sebas1705.nanodemo.domain.models.TierAvailability
import es.sebas1705.nanodemo.domain.usescases.inference.CapabilitiesOfUseCase
import es.sebas1705.nanodemo.domain.usescases.inference.DownloadModelUseCase
import es.sebas1705.nanodemo.domain.usescases.inference.GenerateUseCase
import es.sebas1705.nanodemo.domain.usescases.inference.GetMediaPipeModelsUseCase
import es.sebas1705.nanodemo.domain.usescases.inference.LoadConversationUseCase
import es.sebas1705.nanodemo.domain.usescases.inference.ObserveActiveTierUseCase
import es.sebas1705.nanodemo.domain.usescases.inference.ObserveAttachedUrlUseCase
import es.sebas1705.nanodemo.domain.usescases.inference.ObserveAvailabilityUseCase
import es.sebas1705.nanodemo.domain.usescases.inference.ObserveConversationUseCase
import es.sebas1705.nanodemo.domain.usescases.inference.ObservePendingImageUseCase
import es.sebas1705.nanodemo.domain.usescases.inference.ObserveSelectedMediaPipeModelUseCase
import es.sebas1705.nanodemo.domain.usescases.inference.ObserveStateUseCase
import es.sebas1705.nanodemo.domain.usescases.inference.ResetConversationUseCase
import es.sebas1705.nanodemo.domain.usescases.inference.SelectMediaPipeModelUseCase
import es.sebas1705.nanodemo.domain.usescases.inference.SelectTierUseCase
import es.sebas1705.nanodemo.domain.usescases.inference.SetAttachedUrlUseCase
import es.sebas1705.nanodemo.domain.usescases.inference.SetPendingImageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Cada `UseCase` inyectado con visibilidad `val` queda expuesto tal cual a
 * `ChatScreen` — como todos son clases con un solo `operator fun invoke(...)`,
 * `viewModel.generate(prompt)`/`viewModel.selectTier(tier)`/etc. ya funcionan
 * por la convención de operador de Kotlin, sin necesitar un método wrapper
 * por cada acción.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    observeConversation: ObserveConversationUseCase,
    observeState: ObserveStateUseCase,
    observeActiveTier: ObserveActiveTierUseCase,
    observeAvailability: ObserveAvailabilityUseCase,
    observePendingImage: ObservePendingImageUseCase,
    observeAttachedUrl: ObserveAttachedUrlUseCase,
    observeSelectedMediaPipeModel: ObserveSelectedMediaPipeModelUseCase,
    getMediaPipeModels: GetMediaPipeModelsUseCase,
    val capabilitiesOf: CapabilitiesOfUseCase,
    val selectTier: SelectTierUseCase,
    val generate: GenerateUseCase,
    val downloadModel: DownloadModelUseCase,
    val setPendingImage: SetPendingImageUseCase,
    val setAttachedUrl: SetAttachedUrlUseCase,
    val resetConversation: ResetConversationUseCase,
    val selectMediaPipeModel: SelectMediaPipeModelUseCase,
    private val loadConversation: LoadConversationUseCase,
) : ViewModel() {

    val conversation: StateFlow<List<ChatMessage>> = observeConversation()
    val state: StateFlow<DemoState> = observeState()
    val activeTier: StateFlow<InferenceTier> = observeActiveTier()
    val availability: StateFlow<Map<InferenceTier, TierAvailability>> = observeAvailability()
    val pendingImage: StateFlow<Uri?> = observePendingImage()
    val attachedUrl: StateFlow<String?> = observeAttachedUrl()
    val selectedMediaPipeModel: StateFlow<MediaPipeModelOption> = observeSelectedMediaPipeModel()
    val mediaPipeModels: List<MediaPipeModelOption> = getMediaPipeModels()

    /** Se llama una vez al entrar a Chat con un `conversationId` (reapertura desde Historial). */
    fun openConversation(conversationId: Long) = loadConversation(conversationId)
}
