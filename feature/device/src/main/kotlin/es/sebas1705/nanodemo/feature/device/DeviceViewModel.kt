package es.sebas1705.nanodemo.feature.device

import androidx.lifecycle.ViewModel
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.TierAvailability
import es.sebas1705.nanodemo.domain.usescases.inference.CapabilitiesOfUseCase
import es.sebas1705.nanodemo.domain.usescases.inference.ObserveAvailabilityUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class DeviceViewModel @Inject constructor(
    observeAvailability: ObserveAvailabilityUseCase,
    val capabilitiesOf: CapabilitiesOfUseCase,
) : ViewModel() {
    val availability: StateFlow<Map<InferenceTier, TierAvailability>> = observeAvailability()
}
