package es.sebas1705.nanodemo.feature.lab.memory

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
import es.sebas1705.nanodemo.feature.lab.trace.PipelineRunner
import es.sebas1705.nanodemo.feature.lab.trace.asExecutor
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Context assumed for engines that do not report one (cloud models). */
private const val UNKNOWN_CONTEXT_TOKENS = 8_192

/** Share of an engine's context the prompt may use; the rest is for the app preamble and the answer. */
private const val PROMPT_SHARE = 0.7

/** Fixed prompt budgets the user can force, to see the effect on engines with a large context too. */
val MEMORY_BUDGET_CHOICES = listOf(400, 800, 1_500)

@HiltViewModel
class MemoryViewModel @Inject constructor(
    runStep: RunStepUseCase,
    observeAvailability: ObserveAvailabilityUseCase,
    private val capabilitiesOf: CapabilitiesOfUseCase,
    @ApplicationContext context: Context,
) : ViewModel() {

    private val pipeline = PipelineRunner(runStep.asExecutor())
    private val runner = MemoryRunner(pipeline, AndroidMemoryTexts(context.resources))

    val availability: StateFlow<Map<InferenceTier, TierAvailability>> = observeAvailability()
    val trace: StateFlow<PipelineTrace> = pipeline.recorder.trace

    private val _tier = MutableStateFlow(InferenceTier.ML_KIT_NANO)
    val tier: StateFlow<InferenceTier> = _tier.asStateFlow()

    private val _scenario = MutableStateFlow(MemoryScenarios.trip)
    val scenario: StateFlow<MemoryScenario> = _scenario.asStateFlow()

    /** `null` = derive the budget from the engine context. */
    private val _budgetOverride = MutableStateFlow<Int?>(null)
    val budgetOverride: StateFlow<Int?> = _budgetOverride.asStateFlow()

    private val _results = MutableStateFlow<List<StrategyResult>>(emptyList())
    val results: StateFlow<List<StrategyResult>> = _results.asStateFlow()

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

    fun selectTier(tier: InferenceTier) {
        if (!_running.value) _tier.value = tier
    }

    fun selectScenario(scenario: MemoryScenario) {
        if (!_running.value) _scenario.value = scenario
    }

    fun selectBudget(tokens: Int?) {
        if (!_running.value) _budgetOverride.value = tokens
    }

    /** The prompt budget in tokens for the selected engine and override. */
    fun budgetTokens(tier: InferenceTier = _tier.value, override: Int? = _budgetOverride.value): Int =
        override ?: ((capabilitiesOf(tier).contextTokens ?: UNKNOWN_CONTEXT_TOKENS) * PROMPT_SHARE).toInt()

    fun run() {
        if (_running.value) return
        val tier = _tier.value
        val scenario = _scenario.value
        val budget = budgetTokens(tier)
        pipeline.recorder.reset()
        _results.value = emptyList()
        _running.value = true
        job = viewModelScope.launch {
            try {
                for (strategy in MemoryStrategy.entries) {
                    _results.update { it + runner.run(strategy, scenario, tier, budget) }
                }
            } finally {
                _running.value = false
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }
}
