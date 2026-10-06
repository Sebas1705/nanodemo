package es.sebas1705.nanodemo.feature.config

import androidx.lifecycle.ViewModel
import es.sebas1705.nanodemo.domain.models.GenerationConfig
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.withSupportedSettingsFrom
import es.sebas1705.nanodemo.domain.usescases.inference.CapabilitiesOfUseCase
import es.sebas1705.nanodemo.domain.usescases.inference.ObserveActiveTierUseCase
import es.sebas1705.nanodemo.domain.usescases.inference.ObserveConfigsUseCase
import es.sebas1705.nanodemo.domain.usescases.inference.UpdateConfigUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class ConfigViewModel @Inject constructor(
    observeConfigs: ObserveConfigsUseCase,
    observeActiveTier: ObserveActiveTierUseCase,
    val capabilitiesOf: CapabilitiesOfUseCase,
    private val updateConfigUseCase: UpdateConfigUseCase,
) : ViewModel() {
    /** Un `GenerationConfig` por tier — cada uno se edita y se guarda (`SettingsStore`) por separado. */
    val configs: StateFlow<Map<InferenceTier, GenerationConfig>> = observeConfigs()
    val activeTier: StateFlow<InferenceTier> = observeActiveTier()

    fun updateConfig(tier: InferenceTier, config: GenerationConfig) = updateConfigUseCase(tier, config)

    /**
     * Copia la configuración de `source` a los demás motores, solo en lo que cada uno soporta (ver
     * `withSupportedSettingsFrom`). Para comparar con parámetros iguales: cada motor guarda los suyos.
     */
    fun applyToAll(source: InferenceTier) {
        val from = configs.value[source] ?: GenerationConfig()
        InferenceTier.entries.filter { it != source }.forEach { tier ->
            val current = configs.value[tier] ?: GenerationConfig()
            updateConfigUseCase(tier, current.withSupportedSettingsFrom(from, capabilitiesOf(tier)))
        }
    }
}
