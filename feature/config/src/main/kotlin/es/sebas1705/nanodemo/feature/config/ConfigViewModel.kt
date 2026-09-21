package es.sebas1705.nanodemo.feature.config

import androidx.lifecycle.ViewModel
import es.sebas1705.nanodemo.domain.models.GenerationConfig
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.usescases.inference.CapabilitiesOfUseCase
import es.sebas1705.nanodemo.domain.usescases.inference.ObserveActiveTierUseCase
import es.sebas1705.nanodemo.domain.usescases.inference.ObserveConfigUseCase
import es.sebas1705.nanodemo.domain.usescases.inference.UpdateConfigUseCase
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
