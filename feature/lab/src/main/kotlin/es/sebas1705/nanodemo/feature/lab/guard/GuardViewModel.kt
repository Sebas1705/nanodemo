package es.sebas1705.nanodemo.feature.lab.guard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.PipelineTrace
import es.sebas1705.nanodemo.domain.models.TierAvailability
import es.sebas1705.nanodemo.domain.usescases.inference.CapabilitiesOfUseCase
import es.sebas1705.nanodemo.domain.usescases.inference.ObserveAvailabilityUseCase
import es.sebas1705.nanodemo.domain.usescases.inference.RunStepUseCase
import es.sebas1705.nanodemo.feature.lab.common.usableTiers
import es.sebas1705.nanodemo.feature.lab.common.LabTexts
import es.sebas1705.nanodemo.feature.lab.trace.PipelineRunner
import es.sebas1705.nanodemo.feature.lab.trace.asExecutor
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GuardViewModel @Inject constructor(
    runStep: RunStepUseCase,
    labTexts: LabTexts,
    observeAvailability: ObserveAvailabilityUseCase,
    private val capabilitiesOf: CapabilitiesOfUseCase,
    @ApplicationContext context: Context,
) : ViewModel() {

    private val pipeline = PipelineRunner(runStep.asExecutor(labTexts))
    private val runner = GuardRunner(pipeline, AndroidGuardTexts(context.resources))

    val availability: StateFlow<Map<InferenceTier, TierAvailability>> = observeAvailability()
    val trace: StateFlow<PipelineTrace> = pipeline.recorder.trace

    /** The detector needs no model, so its report is available from the start. */
    val detectorReport: DetectorReport = evaluateDetector()

    private val _tier = MutableStateFlow(InferenceTier.ML_KIT_NANO)
    val tier: StateFlow<InferenceTier> = _tier.asStateFlow()

    private val _payload = MutableStateFlow(GuardData.payloads.first())
    val payload: StateFlow<AttackPayload> = _payload.asStateFlow()

    private val _defense = MutableStateFlow(Defense.NONE)
    val defense: StateFlow<Defense> = _defense.asStateFlow()

    /** Result of the last single attack. */
    private val _single = MutableStateFlow<AttackOutcome?>(null)
    val single: StateFlow<AttackOutcome?> = _single.asStateFlow()

    /** Results of the suite, growing while it runs. */
    private val _suite = MutableStateFlow<List<AttackOutcome>>(emptyList())
    val suite: StateFlow<List<AttackOutcome>> = _suite.asStateFlow()

    /** `done to total` while the suite runs. */
    private val _progress = MutableStateFlow<Pair<Int, Int>?>(null)
    val progress: StateFlow<Pair<Int, Int>?> = _progress.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private var job: Job? = null
    private var defaultApplied = false

    fun applyDefaultIfNeeded(availability: Map<InferenceTier, TierAvailability>) {
        if (defaultApplied) return
        val usable = availability.usableTiers().firstOrNull() ?: return
        defaultApplied = true
        _tier.value = usable
    }

    fun selectTier(tier: InferenceTier) { if (!_running.value) _tier.value = tier }
    fun selectPayload(payload: AttackPayload) { if (!_running.value) _payload.value = payload }
    fun selectDefense(defense: Defense) { if (!_running.value) _defense.value = defense }

    fun attackOnce() {
        if (_running.value) return
        val tier = _tier.value
        _running.value = true
        _single.value = null
        pipeline.recorder.reset()
        job = viewModelScope.launch {
            try {
                _single.value = runner.attack(tier, _defense.value, _payload.value, newCanary(), capabilitiesOf(tier).systemPrompt)
            } finally {
                _running.value = false
            }
        }
    }

    fun runSuite() {
        if (_running.value) return
        val tier = _tier.value
        val systemPrompt = capabilitiesOf(tier).systemPrompt
        val total = GuardData.payloads.size * Defense.entries.size
        _running.value = true
        _suite.value = emptyList()
        job = viewModelScope.launch {
            try {
                var done = 0
                for (defense in Defense.entries) {
                    for (payload in GuardData.payloads) {
                        _progress.value = done to total
                        // Keep only the trace of the current attack: 32 attacks would make the trace unreadable.
                        pipeline.recorder.reset()
                        val outcome = runner.attack(tier, defense, payload, newCanary(), systemPrompt)
                        _suite.update { it + outcome }
                        done++
                    }
                }
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
