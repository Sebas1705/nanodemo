package es.sebas1705.nanodemo.feature.lab.soak

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.TierAvailability
import es.sebas1705.nanodemo.domain.usescases.inference.ObserveAvailabilityUseCase
import es.sebas1705.nanodemo.domain.usescases.inference.RunStepUseCase
import es.sebas1705.nanodemo.feature.lab.trace.asExecutor
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Run lengths the user can pick, in minutes. */
val SOAK_MINUTES = listOf(1, 3, 5, 10)

@HiltViewModel
class SoakViewModel @Inject constructor(
    runStep: RunStepUseCase,
    observeAvailability: ObserveAvailabilityUseCase,
) : ViewModel() {

    private val runner = SoakRunner(runStep.asExecutor())

    val availability: StateFlow<Map<InferenceTier, TierAvailability>> = observeAvailability()

    private val _tier = MutableStateFlow<InferenceTier?>(null)
    val tier: StateFlow<InferenceTier?> = _tier.asStateFlow()

    private val _minutes = MutableStateFlow(3)
    val minutes: StateFlow<Int> = _minutes.asStateFlow()

    private val _points = MutableStateFlow<List<SoakPoint>>(emptyList())
    val points: StateFlow<List<SoakPoint>> = _points.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private var job: Job? = null

    fun selectTier(tier: InferenceTier) {
        if (!_running.value) _tier.value = tier
    }

    fun selectMinutes(minutes: Int) {
        if (!_running.value) _minutes.value = minutes
    }

    fun start(tier: InferenceTier) {
        if (_running.value) return
        _tier.value = tier
        _points.value = emptyList()
        _running.value = true
        job = viewModelScope.launch {
            try {
                runner.run(tier, durationMs = _minutes.value * 60_000L, onPoint = { p -> _points.update { it + p } })
            } finally {
                _running.value = false
            }
        }
    }

    fun stop() {
        job?.cancel()
    }
}
