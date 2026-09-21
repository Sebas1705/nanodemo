package es.sebas1705.nanodemo.feature.device

import androidx.lifecycle.ViewModel
import es.sebas1705.nanodemo.core.common.ErrorReporter
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
    private val errorReporter: ErrorReporter,
) : ViewModel() {
    val availability: StateFlow<Map<InferenceTier, TierAvailability>> = observeAvailability()

    /** Manda un error **no fatal** de prueba: la app sigue viva y aparece en Crashlytics → No fatales. */
    fun sendTestNonFatal() {
        errorReporter.recordNonFatal(IllegalStateException("Error no fatal de prueba (Dispositivo)"), "prueba manual")
    }

    /** Provoca un crash real para comprobar que Crashlytics lo recibe (el reporte llega al reabrir la app). */
    fun forceTestCrash(): Nothing {
        errorReporter.log("crash de prueba provocado a mano")
        throw RuntimeException("Crash de prueba de Crashlytics (Dispositivo)")
    }
}
