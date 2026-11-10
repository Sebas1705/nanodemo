package es.sebas1705.nanodemo.feature.lab.refine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.PipelineTrace
import es.sebas1705.nanodemo.domain.models.TierAvailability
import es.sebas1705.nanodemo.domain.usescases.inference.CapabilitiesOfUseCase
import es.sebas1705.nanodemo.domain.usescases.inference.ObserveAvailabilityUseCase
import es.sebas1705.nanodemo.domain.usescases.inference.RunStepUseCase
import es.sebas1705.nanodemo.feature.lab.common.usableTiers
import es.sebas1705.nanodemo.feature.lab.trace.PipelineRunner
import es.sebas1705.nanodemo.feature.lab.trace.asExecutor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the engine allows, as the pipelines need to know it (context budget, JSON mode). */
internal fun CapabilitiesOfUseCase.profile(tier: InferenceTier): EngineProfile =
    this(tier).let { EngineProfile(contextTokens = it.contextTokens, structuredOutput = it.structuredOutput) }

@HiltViewModel
class RefineViewModel @Inject constructor(
    runStep: RunStepUseCase,
    observeAvailability: ObserveAvailabilityUseCase,
    private val capabilitiesOf: CapabilitiesOfUseCase,
) : ViewModel() {

    private val runner = PipelineRunner(runStep.asExecutor())
    private val pipeline = RefinePipeline(runner) { capabilitiesOf.profile(it) }

    val availability: StateFlow<Map<InferenceTier, TierAvailability>> = observeAvailability()
    val trace: StateFlow<PipelineTrace> = runner.recorder.trace

    private val _setup = MutableStateFlow(RefineSetup.defaultFor(availability.value.usableTiers()))
    val setup: StateFlow<RefineSetup> = _setup.asStateFlow()

    private val _result = MutableStateFlow(RefineResult())
    val result: StateFlow<RefineResult> = _result.asStateFlow()

    private val _progress = MutableStateFlow<LabProgress?>(null)
    val progress: StateFlow<LabProgress?> = _progress.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var job: Job? = null
    private var chosenByUser = false

    init {
        // Until the user picks an engine, follow availability: engines are checked asynchronously after launch.
        viewModelScope.launch {
            availability.collect { map ->
                if (!chosenByUser) _setup.value = RefineSetup.defaultFor(map.usableTiers(), _setup.value.maxRevisions)
            }
        }
    }

    fun selectTier(role: RefineRole, tier: InferenceTier) {
        chosenByUser = true
        _setup.value = _setup.value.with(role, tier)
    }

    fun setMaxRevisions(count: Int) {
        _setup.value = _setup.value.copy(maxRevisions = count)
    }

    /** Context budget of an engine when it is small and known (1024 / 2048 tokens), else `null`. */
    fun contextOf(tier: InferenceTier): Int? = capabilitiesOf(tier).contextTokens

    fun run(task: String) {
        if (task.isBlank() || _running.value) return
        job = viewModelScope.launch {
            _running.value = true
            _error.value = null
            _result.value = RefineResult()
            runner.recorder.reset()
            try {
                pipeline.run(
                    task = task.trim(),
                    setup = _setup.value,
                    onUpdate = { _result.value = it },
                    onProgress = { _progress.value = it },
                )
            } catch (e: CancellationException) {
                runner.recorder.failRunning("Cancelado")
                throw e
            } catch (e: Exception) {
                runner.recorder.failRunning("Error")
                _error.value = e.message ?: e.javaClass.simpleName
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
