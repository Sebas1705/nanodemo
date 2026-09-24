package es.sebas1705.nanodemo.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import es.sebas1705.nanodemo.core.common.Telemetry
import es.sebas1705.nanodemo.domain.models.Conversation
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.TierAvailability
import es.sebas1705.nanodemo.domain.usescases.history.ObserveConversationsUseCase
import es.sebas1705.nanodemo.domain.usescases.inference.ObserveActiveTierUseCase
import es.sebas1705.nanodemo.domain.usescases.inference.ObserveAvailabilityUseCase
import es.sebas1705.nanodemo.domain.usescases.inference.ResetConversationUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

private const val RECENT_CONVERSATIONS = 8

/**
 * Además de registrar la pantalla vista, expone lo que necesita el shell de navegación: el tier activo
 * y su disponibilidad (chip de la barra superior, `ChannelTopBar` en `AppNav.kt`) y las conversaciones
 * recientes + "nueva conversación" del menú lateral (`AppDrawer.kt`).
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val telemetry: Telemetry,
    observeActiveTier: ObserveActiveTierUseCase,
    observeAvailability: ObserveAvailabilityUseCase,
    observeConversations: ObserveConversationsUseCase,
    private val resetConversation: ResetConversationUseCase,
) : ViewModel() {

    val activeTier: StateFlow<InferenceTier> = observeActiveTier()
    val availability: StateFlow<Map<InferenceTier, TierAvailability>> = observeAvailability()

    val recentConversations: StateFlow<List<Conversation>> = observeConversations()
        .map { it.take(RECENT_CONVERSATIONS) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun startNewConversation() = resetConversation()

    fun onScreenShown(screenName: String) = telemetry.screen(screenName)
}
