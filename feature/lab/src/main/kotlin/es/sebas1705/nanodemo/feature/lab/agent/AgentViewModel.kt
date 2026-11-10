package es.sebas1705.nanodemo.feature.lab.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import androidx.annotation.StringRes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import es.sebas1705.nanodemo.core.common.WebSearchTool
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.PipelineTrace
import es.sebas1705.nanodemo.domain.models.TierAvailability
import es.sebas1705.nanodemo.domain.models.TierCapabilities
import es.sebas1705.nanodemo.domain.usescases.inference.CapabilitiesOfUseCase
import es.sebas1705.nanodemo.domain.usescases.inference.ObserveAvailabilityUseCase
import es.sebas1705.nanodemo.domain.usescases.inference.RunStepUseCase
import es.sebas1705.nanodemo.feature.lab.R
import es.sebas1705.nanodemo.feature.lab.agent.tools.AndroidDeviceInfoProvider
import es.sebas1705.nanodemo.feature.lab.agent.tools.CalculatorTool
import es.sebas1705.nanodemo.feature.lab.agent.tools.CurrentTimeTool
import es.sebas1705.nanodemo.feature.lab.agent.tools.DeviceInfoTool
import es.sebas1705.nanodemo.feature.lab.agent.tools.NotesTool
import es.sebas1705.nanodemo.feature.lab.agent.tools.PrefsNotesStore
import es.sebas1705.nanodemo.feature.lab.agent.tools.Tool
import es.sebas1705.nanodemo.feature.lab.agent.tools.ToolRegistry
import es.sebas1705.nanodemo.feature.lab.agent.tools.UnitConverterTool
import es.sebas1705.nanodemo.feature.lab.agent.tools.WebSearchAgentTool
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

/**
 * A tool as the UI lists it. `description` is the localized string resource for the user; tools without one fall
 * back to the model-facing English `fallbackDescription`.
 */
data class ToolOption(val name: String, @StringRes val description: Int?, val fallbackDescription: String)

@StringRes
private fun toolDescriptionRes(name: String): Int? = when (name) {
    "calculator" -> R.string.agent_tool_calculator_description
    "current_time" -> R.string.agent_tool_current_time_description
    "device_info" -> R.string.agent_tool_device_info_description
    "notes" -> R.string.agent_tool_notes_description
    "unit_converter" -> R.string.agent_tool_unit_converter_description
    "web_search" -> R.string.agent_tool_web_search_description
    else -> null
}

/** Live state of the reliability suite. `done` of `total` runs are finished; `current` is the one running. */
data class BenchmarkState(
    val running: Boolean = false,
    val outcomes: List<BenchmarkOutcome> = emptyList(),
    val total: Int = 0,
    val currentTier: InferenceTier? = null,
    val currentCase: BenchmarkCase? = null,
) {
    val done: Int get() = outcomes.size
}

/**
 * Agent experiment: a ReAct loop with selectable tools on any engine, plus a reliability suite that runs the
 * same tasks on every usable engine. Both use the same [AgentRunner], so what the suite measures is what the
 * interactive screen does.
 */
@HiltViewModel
class AgentViewModel @Inject constructor(
    runStep: RunStepUseCase,
    labTexts: LabTexts,
    observeAvailability: ObserveAvailabilityUseCase,
    private val capabilitiesOf: CapabilitiesOfUseCase,
    deviceInfo: AndroidDeviceInfoProvider,
    private val notesStore: PrefsNotesStore,
    @ApplicationContext context: Context,
) : ViewModel() {

    private val pipeline = PipelineRunner(runStep.asExecutor(labTexts))
    private val texts = AndroidAgentTexts(context)
    private val agent = AgentRunner(pipeline, texts = texts)

    /** Web search is only offered when the Brave key is configured. */
    val webSearchConfigured: Boolean = WebSearchTool.isConfigured

    private val allTools: List<Tool> = buildList {
        add(CalculatorTool())
        add(CurrentTimeTool())
        add(DeviceInfoTool(deviceInfo))
        add(NotesTool(notesStore))
        add(UnitConverterTool())
        if (webSearchConfigured) add(WebSearchAgentTool())
    }

    val toolOptions: List<ToolOption> = allTools.map { ToolOption(it.name, toolDescriptionRes(it.name), it.description) }

    val availability: StateFlow<Map<InferenceTier, TierAvailability>> = observeAvailability()
    val trace: StateFlow<PipelineTrace> = pipeline.recorder.trace

    private val _tier = MutableStateFlow(InferenceTier.ML_KIT_NANO)
    val tier: StateFlow<InferenceTier> = _tier.asStateFlow()

    private val _enabledTools = MutableStateFlow(allTools.map { it.name }.toSet())
    val enabledTools: StateFlow<Set<String>> = _enabledTools.asStateFlow()

    private val _maxSteps = MutableStateFlow(AgentSettings().maxSteps)
    val maxSteps: StateFlow<Int> = _maxSteps.asStateFlow()

    private val _result = MutableStateFlow<AgentRunResult?>(null)
    val result: StateFlow<AgentRunResult?> = _result.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _noteCount = MutableStateFlow(notesStore.all().size)
    val noteCount: StateFlow<Int> = _noteCount.asStateFlow()

    private val _benchmark = MutableStateFlow(BenchmarkState())
    val benchmark: StateFlow<BenchmarkState> = _benchmark.asStateFlow()

    /** Engines picked for the suite; `null` = every usable one. */
    private val _benchTiers = MutableStateFlow<Set<InferenceTier>?>(null)
    val benchTiers: StateFlow<Set<InferenceTier>?> = _benchTiers.asStateFlow()

    private var job: Job? = null

    fun capabilities(tier: InferenceTier): TierCapabilities = capabilitiesOf(tier)

    fun selectTier(tier: InferenceTier) {
        _tier.value = tier
    }

    fun setMaxSteps(steps: Int) {
        _maxSteps.value = steps
    }

    fun setToolEnabled(name: String, enabled: Boolean) {
        _enabledTools.update { if (enabled) it + name else it - name }
    }

    fun clearNotes() {
        notesStore.clear()
        _noteCount.value = 0
    }

    /** Whether the fixed part of the prompt (protocol and tool list) leaves little room for the steps. */
    fun toolsCrowdContext(tier: InferenceTier, enabled: Set<String>): Boolean {
        val budget = AgentBudget.forTier(tier, capabilities(tier).contextTokens)
        val tools = allTools.filter { it.name in enabled }
        val fixed = AgentPrompt.fixedPart(tools, budget.style).length
        return fixed > budget.promptChars * FIXED_SHARE_LIMIT
    }

    fun run(task: String) {
        if (task.isBlank() || _running.value) return
        val tier = _tier.value
        val registry = ToolRegistry(allTools.filter { it.name in _enabledTools.value })
        job = viewModelScope.launch {
            _running.value = true
            _result.value = null
            pipeline.recorder.reset()
            try {
                _result.value = agent.run(
                    task = task.trim(),
                    tier = tier,
                    registry = registry,
                    capabilities = capabilities(tier),
                    settings = AgentSettings(maxSteps = _maxSteps.value),
                )
            } finally {
                _running.value = false
                _noteCount.value = notesStore.all().size
            }
        }
    }

    fun toggleBenchTier(tier: InferenceTier, usable: List<InferenceTier>) {
        val current = _benchTiers.value ?: usable.toSet()
        _benchTiers.value = if (tier in current) current - tier else current + tier
    }

    /** Runs the whole suite on the chosen usable engines, one task after another (an engine serves one request at a time). */
    fun runBenchmark() {
        if (_running.value) return
        val tiers = (_benchTiers.value ?: availability.value.usableTiers()).filter { availability.value.usableTiers().contains(it) }
        if (tiers.isEmpty()) return
        val cases = BenchmarkSuite.cases
        job = viewModelScope.launch {
            _running.value = true
            _result.value = null
            _benchmark.value = BenchmarkState(running = true, total = tiers.size * cases.size)
            try {
                for (tier in tiers) {
                    for (case in cases) {
                        _benchmark.update { it.copy(currentTier = tier, currentCase = case) }
                        pipeline.recorder.reset()
                        val run = agent.run(
                            task = case.prompt,
                            tier = tier,
                            registry = BenchmarkEnvironment.newRegistry(),
                            capabilities = capabilities(tier),
                            settings = AgentSettings(timeBudgetMs = BENCH_TIME_BUDGET_MS),
                        )
                        _benchmark.update { it.copy(outcomes = it.outcomes + BenchmarkAggregator.check(case, run, texts)) }
                    }
                }
            } finally {
                _benchmark.update { it.copy(running = false, currentTier = null, currentCase = null) }
                _running.value = false
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    private companion object {
        const val BENCH_TIME_BUDGET_MS = 90_000L
        const val FIXED_SHARE_LIMIT = 0.6
    }
}
