package es.sebas1705.nanodemo.feature.lab.refine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SelfConsistencyViewModel @Inject constructor(
    runStep: RunStepUseCase,
    labTexts: LabTexts,
    observeAvailability: ObserveAvailabilityUseCase,
    private val capabilitiesOf: CapabilitiesOfUseCase,
    @ApplicationContext context: Context,
) : ViewModel() {

    private val texts = AndroidRefineTexts(context)
    private val runner = PipelineRunner(runStep.asExecutor(labTexts))
    private val pipeline = SelfConsistencyPipeline(runner, texts) { capabilitiesOf.profile(it) }

    val availability: StateFlow<Map<InferenceTier, TierAvailability>> = observeAvailability()
    val trace: StateFlow<PipelineTrace> = runner.recorder.trace

    private val _tier = MutableStateFlow(availability.value.usableTiers().firstOrNull() ?: InferenceTier.ML_KIT_NANO)
    val tier: StateFlow<InferenceTier> = _tier.asStateFlow()

    private val _result = MutableStateFlow(SelfConsistencyResult())
    val result: StateFlow<SelfConsistencyResult> = _result.asStateFlow()

    private val _progress = MutableStateFlow<LabProgress?>(null)
    val progress: StateFlow<LabProgress?> = _progress.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var job: Job? = null
    private var chosen = false

    init {
        viewModelScope.launch {
            availability.collect { map ->
                if (!chosen) _tier.value = map.usableTiers().firstOrNull() ?: _tier.value
            }
        }
    }

    fun selectTier(tier: InferenceTier) {
        chosen = true
        _tier.value = tier
    }

    /** Sampling only makes sense if the engine honours the temperature; otherwise the samples are near-identical. */
    fun supportsTemperature(tier: InferenceTier): Boolean = capabilitiesOf(tier).temperature

    fun contextOf(tier: InferenceTier): Int? = capabilitiesOf(tier).contextTokens

    fun run(question: String, samples: Int, temperature: Float, kind: AnswerKind, expected: String) {
        if (question.isBlank() || _running.value) return
        job = viewModelScope.launch {
            _running.value = true
            _error.value = null
            _result.value = SelfConsistencyResult()
            runner.recorder.reset()
            try {
                pipeline.run(
                    question = question.trim(),
                    tier = _tier.value,
                    samples = samples,
                    temperature = temperature,
                    kind = kind,
                    expected = expected,
                    onUpdate = { _result.value = it },
                    onProgress = { _progress.value = it },
                )
            } catch (e: CancellationException) {
                runner.recorder.failRunning(texts.cancelled)
                throw e
            } catch (e: Exception) {
                runner.recorder.failRunning(texts.error)
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

@HiltViewModel
class StrategyBenchmarkViewModel @Inject constructor(
    runStep: RunStepUseCase,
    labTexts: LabTexts,
    observeAvailability: ObserveAvailabilityUseCase,
    private val capabilitiesOf: CapabilitiesOfUseCase,
    @ApplicationContext context: Context,
) : ViewModel() {

    private val texts = AndroidRefineTexts(context)
    private val runner = PipelineRunner(runStep.asExecutor(labTexts))
    private val benchmark = StrategyBenchmark(runner, texts) { capabilitiesOf.profile(it) }

    val availability: StateFlow<Map<InferenceTier, TierAvailability>> = observeAvailability()
    val trace: StateFlow<PipelineTrace> = runner.recorder.trace

    private val _tiers = MutableStateFlow<Set<InferenceTier>>(availability.value.usableTiers().take(1).toSet())

    /** Engines to compare. Starts with the first usable one: every extra engine multiplies the run. */
    val tiers: StateFlow<Set<InferenceTier>> = _tiers.asStateFlow()

    private val _strategies = MutableStateFlow(PromptStrategy.entries.toSet())
    val strategies: StateFlow<Set<PromptStrategy>> = _strategies.asStateFlow()

    private val _repetitions = MutableStateFlow(1)
    val repetitions: StateFlow<Int> = _repetitions.asStateFlow()

    private val _outcomes = MutableStateFlow<List<TaskOutcome>>(emptyList())
    val outcomes: StateFlow<List<TaskOutcome>> = _outcomes.asStateFlow()

    private val _progress = MutableStateFlow<LabProgress?>(null)
    val progress: StateFlow<LabProgress?> = _progress.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var job: Job? = null
    private var chosen = false

    init {
        viewModelScope.launch {
            availability.collect { map ->
                if (!chosen) _tiers.value = map.usableTiers().take(1).toSet()
            }
        }
    }

    fun toggleTier(tier: InferenceTier) {
        chosen = true
        _tiers.value = _tiers.value.let { if (tier in it) it - tier else it + tier }
    }

    fun toggleStrategy(strategy: PromptStrategy) {
        _strategies.value = _strategies.value.let { if (strategy in it) it - strategy else it + strategy }
    }

    fun setRepetitions(count: Int) {
        _repetitions.value = count
    }

    /** Model calls the current selection will make (step-back counts twice). */
    fun plannedCalls(): Int =
        _tiers.value.size * _repetitions.value * StrategyTasks.all.size * _strategies.value.sumOf { it.callsPerTask }

    fun run() {
        if (_running.value || _tiers.value.isEmpty() || _strategies.value.isEmpty()) return
        job = viewModelScope.launch {
            _running.value = true
            _error.value = null
            _outcomes.value = emptyList()
            runner.recorder.reset()
            try {
                benchmark.run(
                    tiers = InferenceTier.entries.filter { it in _tiers.value },
                    strategies = PromptStrategy.entries.filter { it in _strategies.value },
                    tasks = StrategyTasks.all,
                    repetitions = _repetitions.value,
                    // One repetition is a deterministic comparison; several only mean something if answers can vary.
                    temperature = if (_repetitions.value > 1) REPEAT_TEMPERATURE else GREEDY_TEMPERATURE,
                    onUpdate = { _outcomes.value = it },
                    onProgress = { _progress.value = it },
                )
            } catch (e: CancellationException) {
                runner.recorder.failRunning(texts.cancelled)
                throw e
            } catch (e: Exception) {
                runner.recorder.failRunning(texts.error)
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

    private companion object {
        const val REPEAT_TEMPERATURE = 0.6f
    }
}
