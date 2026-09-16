package com.example.geminifnanodemo.feature.device

import androidx.lifecycle.ViewModel
import com.example.geminifnanodemo.domain.models.InferenceTier
import com.example.geminifnanodemo.domain.models.TierAvailability
import com.example.geminifnanodemo.domain.usescases.inference.CapabilitiesOfUseCase
import com.example.geminifnanodemo.domain.usescases.inference.ObserveAvailabilityUseCase
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
