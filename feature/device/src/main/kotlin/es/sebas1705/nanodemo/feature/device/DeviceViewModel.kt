package es.sebas1705.nanodemo.feature.device

import androidx.lifecycle.ViewModel
import es.sebas1705.nanodemo.core.common.Telemetry
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
    private val telemetry: Telemetry,
) : ViewModel() {
    val availability: StateFlow<Map<InferenceTier, TierAvailability>> = observeAvailability()

    /** Evento de analytics de prueba: aparece en DebugView al momento y en Eventos en unas horas. */
    fun sendTestEvent() {
        telemetry.feature("diagnostics_test_event")
    }

    /** Error **no fatal** de prueba: la app sigue viva y aparece en Crashlytics → No fatales. */
    fun sendTestNonFatal() {
        telemetry.failure("diagnostics_test", IllegalStateException("Error no fatal de prueba (Dispositivo)"))
    }

    /** Provoca un crash real para comprobar que Crashlytics lo recibe (el reporte llega al reabrir la app). */
    fun forceTestCrash(): Nothing {
        telemetry.breadcrumb("crash de prueba provocado a mano")
        throw RuntimeException("Crash de prueba de Crashlytics (Dispositivo)")
    }
}
