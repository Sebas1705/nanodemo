package es.sebas1705.nanodemo.feature.main

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import es.sebas1705.nanodemo.core.common.Telemetry
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.TierAvailability
import es.sebas1705.nanodemo.domain.usescases.inference.ObserveActiveTierUseCase
import es.sebas1705.nanodemo.domain.usescases.inference.ObserveAvailabilityUseCase
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Además de registrar la pantalla vista, expone el tier activo y su disponibilidad para el chip
 * de la barra superior compartida (`ChannelTopBar` en `AppNav.kt`) — el mismo estado que ya
 * consumen Chat y Dispositivo, ahora visible desde cualquier pantalla.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val telemetry: Telemetry,
    observeActiveTier: ObserveActiveTierUseCase,
    observeAvailability: ObserveAvailabilityUseCase,
) : ViewModel() {

    val activeTier: StateFlow<InferenceTier> = observeActiveTier()
    val availability: StateFlow<Map<InferenceTier, TierAvailability>> = observeAvailability()

    fun onScreenShown(screenName: String) = telemetry.screen(screenName)
}
