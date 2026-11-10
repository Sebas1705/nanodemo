package es.sebas1705.nanodemo.feature.lab.sandbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import es.sebas1705.nanodemo.domain.models.ChatMessage
import es.sebas1705.nanodemo.domain.models.GenerationConfig
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.PipelineTrace
import es.sebas1705.nanodemo.domain.models.Role
import es.sebas1705.nanodemo.domain.models.StepKind
import es.sebas1705.nanodemo.domain.models.TierAvailability
import es.sebas1705.nanodemo.domain.models.TierCapabilities
import es.sebas1705.nanodemo.domain.usescases.inference.CapabilitiesOfUseCase
import es.sebas1705.nanodemo.domain.usescases.inference.ObserveAvailabilityUseCase
import es.sebas1705.nanodemo.domain.usescases.inference.RunStepUseCase
import es.sebas1705.nanodemo.feature.lab.trace.PipelineRunner
import es.sebas1705.nanodemo.feature.lab.trace.asExecutor
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the sandbox shows after a run: the answer text, or the error that stopped it. */
data class SandboxOutcome(val answer: String = "", val error: String? = null)

/**
 * Reference experiment: one prompt, one engine, one step, with a free configuration. It exercises the whole
 * foundation (`runStep` -> `PipelineRunner` -> `TraceRecorder` -> `PipelineTraceView`) and is the template
 * other experiments copy.
 */
@HiltViewModel
class SandboxViewModel @Inject constructor(
    runStep: RunStepUseCase,
    observeAvailability: ObserveAvailabilityUseCase,
    private val capabilitiesOf: CapabilitiesOfUseCase,
) : ViewModel() {

    private val runner = PipelineRunner(runStep.asExecutor())

    val availability: StateFlow<Map<InferenceTier, TierAvailability>> = observeAvailability()
    val trace: StateFlow<PipelineTrace> = runner.recorder.trace

    private val _tier = MutableStateFlow(InferenceTier.ML_KIT_NANO)
    val tier: StateFlow<InferenceTier> = _tier.asStateFlow()

    private val _outcome = MutableStateFlow(SandboxOutcome())
    val outcome: StateFlow<SandboxOutcome> = _outcome.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private var job: Job? = null

    fun capabilities(tier: InferenceTier): TierCapabilities = capabilitiesOf(tier)

    fun selectTier(tier: InferenceTier) {
        _tier.value = tier
    }

    private var defaultApplied = false

    /** First time the engines are known, start on one that can actually run instead of an unavailable default. */
    fun applyDefaultIfNeeded(availability: Map<InferenceTier, TierAvailability>) {
        if (defaultApplied) return
        val usable = InferenceTier.entries.firstOrNull { availability[it] == TierAvailability.AVAILABLE } ?: return
        defaultApplied = true
        _tier.value = usable
    }

    fun run(prompt: String, config: GenerationConfig) {
        if (prompt.isBlank() || _running.value) return
        job = viewModelScope.launch {
            _running.value = true
            runner.recorder.reset()
            _outcome.value = SandboxOutcome()
            val result = runner.generate(
                kind = StepKind.GENERATE,
                title = "Respuesta",
                tier = _tier.value,
                messages = listOf(ChatMessage(Role.USER, prompt)),
                config = config,
            )
            _outcome.value = SandboxOutcome(result.text, result.error)
            _running.value = false
        }
    }

    fun cancel() {
        job?.cancel()
        _running.value = false
    }
}
