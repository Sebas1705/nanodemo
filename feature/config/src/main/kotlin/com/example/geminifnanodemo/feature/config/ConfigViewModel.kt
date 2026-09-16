package com.example.geminifnanodemo.feature.config

import androidx.lifecycle.ViewModel
import com.example.geminifnanodemo.domain.models.GenerationConfig
import com.example.geminifnanodemo.domain.models.InferenceTier
import com.example.geminifnanodemo.domain.usescases.inference.CapabilitiesOfUseCase
import com.example.geminifnanodemo.domain.usescases.inference.ObserveActiveTierUseCase
import com.example.geminifnanodemo.domain.usescases.inference.ObserveConfigUseCase
import com.example.geminifnanodemo.domain.usescases.inference.UpdateConfigUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class ConfigViewModel @Inject constructor(
    observeConfig: ObserveConfigUseCase,
    observeActiveTier: ObserveActiveTierUseCase,
    val capabilitiesOf: CapabilitiesOfUseCase,
    val updateConfig: UpdateConfigUseCase,
) : ViewModel() {
    val config: StateFlow<GenerationConfig> = observeConfig()
    val activeTier: StateFlow<InferenceTier> = observeActiveTier()
}
