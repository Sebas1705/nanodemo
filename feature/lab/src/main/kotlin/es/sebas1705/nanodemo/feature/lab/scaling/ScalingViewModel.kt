package es.sebas1705.nanodemo.feature.lab.scaling

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.TierAvailability
import es.sebas1705.nanodemo.domain.usescases.inference.CapabilitiesOfUseCase
import es.sebas1705.nanodemo.domain.usescases.inference.ObserveAvailabilityUseCase
import es.sebas1705.nanodemo.domain.usescases.inference.RunStepUseCase
import es.sebas1705.nanodemo.feature.lab.common.usableTiers
import es.sebas1705.nanodemo.feature.lab.trace.asExecutor
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val REPETITIONS = 2

@HiltViewModel
class ScalingViewModel @Inject constructor(
    runStep: RunStepUseCase,
    observeAvailability: ObserveAvailabilityUseCase,
    capabilitiesOf: CapabilitiesOfUseCase,
) : ViewModel() {

    private val runner = ScalingRunner(runStep.asExecutor(), contextOf = { capabilitiesOf(it).contextTokens })

    val availability: StateFlow<Map<InferenceTier, TierAvailability>> = observeAvailability()

    private val _tiers = MutableStateFlow<Set<InferenceTier>>(emptySet())
    val tiers: StateFlow<Set<InferenceTier>> = _tiers.asStateFlow()

    private val _points = MutableStateFlow<List<ScalingPoint>>(emptyList())
    val points: StateFlow<List<ScalingPoint>> = _points.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _progress = MutableStateFlow<Pair<Int, Int>?>(null)
    val progress: StateFlow<Pair<Int, Int>?> = _progress.asStateFlow()

    private var job: Job? = null
    private var defaultsApplied = false

    fun applyDefaultsIfNeeded(availability: Map<InferenceTier, TierAvailability>) {
        if (defaultsApplied) return
        val usable = availability.usableTiers()
        if (usable.isEmpty()) return
        defaultsApplied = true
        _tiers.value = usable.toSet()
    }

    fun toggleTier(tier: InferenceTier) = _tiers.update { if (tier in it) it - tier else it + tier }

    fun start() {
        if (_running.value || _tiers.value.isEmpty()) return
        _points.value = emptyList()
        _running.value = true
        job = viewModelScope.launch {
            try {
                runner.run(
                    tiers = InferenceTier.entries.filter { it in _tiers.value },
                    repetitions = REPETITIONS,
                    onPoint = { point -> _points.update { it + point } },
                    onProgress = { done, total -> _progress.value = done to total },
                )
            } finally {
                _running.value = false
                _progress.value = null
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }
}
