package com.example.geminifnanodemo.feature.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.example.geminifnanodemo.domain.models.ChatMessage
import com.example.geminifnanodemo.domain.models.DemoState
import com.example.geminifnanodemo.domain.models.InferenceTier
import com.example.geminifnanodemo.domain.models.MediaPipeModelOption
import com.example.geminifnanodemo.domain.models.TierAvailability
import com.example.geminifnanodemo.domain.usescases.inference.CapabilitiesOfUseCase
import com.example.geminifnanodemo.domain.usescases.inference.DownloadModelUseCase
import com.example.geminifnanodemo.domain.usescases.inference.GenerateUseCase
import com.example.geminifnanodemo.domain.usescases.inference.GetMediaPipeModelsUseCase
import com.example.geminifnanodemo.domain.usescases.inference.LoadConversationUseCase
import com.example.geminifnanodemo.domain.usescases.inference.ObserveActiveTierUseCase
import com.example.geminifnanodemo.domain.usescases.inference.ObserveAvailabilityUseCase
import com.example.geminifnanodemo.domain.usescases.inference.ObserveConversationUseCase
import com.example.geminifnanodemo.domain.usescases.inference.ObservePendingImageUseCase
import com.example.geminifnanodemo.domain.usescases.inference.ObserveSelectedMediaPipeModelUseCase
import com.example.geminifnanodemo.domain.usescases.inference.ObserveStateUseCase
import com.example.geminifnanodemo.domain.usescases.inference.ResetConversationUseCase
import com.example.geminifnanodemo.domain.usescases.inference.SelectMediaPipeModelUseCase
import com.example.geminifnanodemo.domain.usescases.inference.SelectTierUseCase
import com.example.geminifnanodemo.domain.usescases.inference.SetPendingImageUseCase
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
    observeSelectedMediaPipeModel: ObserveSelectedMediaPipeModelUseCase,
    getMediaPipeModels: GetMediaPipeModelsUseCase,
    val capabilitiesOf: CapabilitiesOfUseCase,
    val selectTier: SelectTierUseCase,
    val generate: GenerateUseCase,
    val downloadModel: DownloadModelUseCase,
    val setPendingImage: SetPendingImageUseCase,
    val resetConversation: ResetConversationUseCase,
    val selectMediaPipeModel: SelectMediaPipeModelUseCase,
    private val loadConversation: LoadConversationUseCase,
) : ViewModel() {

    val conversation: StateFlow<List<ChatMessage>> = observeConversation()
    val state: StateFlow<DemoState> = observeState()
    val activeTier: StateFlow<InferenceTier> = observeActiveTier()
    val availability: StateFlow<Map<InferenceTier, TierAvailability>> = observeAvailability()
    val pendingImage: StateFlow<Uri?> = observePendingImage()
    val selectedMediaPipeModel: StateFlow<MediaPipeModelOption> = observeSelectedMediaPipeModel()
    val mediaPipeModels: List<MediaPipeModelOption> = getMediaPipeModels()

    /** Se llama una vez al entrar a Chat con un `conversationId` (reapertura desde Historial). */
    fun openConversation(conversationId: Long) = loadConversation(conversationId)
}
