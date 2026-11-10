package es.sebas1705.nanodemo.feature.lab.router

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.PipelineTrace
import es.sebas1705.nanodemo.domain.models.TierAvailability
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Everything the user can set in the router screen except the prompt text. */
data class RouterForm(
    val policy: RoutingPolicy = RoutingPolicy.LOCAL_FIRST,
    val manualTier: InferenceTier? = null,
    val gatewayEnabled: Boolean = true,
    val markedSensitive: Boolean = false,
    val expectJson: Boolean = false,
    val confidenceMode: ConfidenceMode = ConfidenceMode.NONE,
    /** null = automatic. */
    val judgeTier: InferenceTier? = null,
    /** null = heuristic classifier only. */
    val classifierTier: InferenceTier? = null,
    val maxEscalations: Int = 2,
)

/** State of the "compare policies" panel. */
data class CompareState(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val label: String = "",
    val summaries: List<PolicySummary> = emptyList(),
    val promptCount: Int = 0,
    val error: String? = null,
)

/**
 * Hybrid edge/cloud router: classify -> route -> cascade, with a PII gateway in front of the cloud engines. All the
 * logic is in the pure classes of this package; this only wires the form, the engines' availability and the trace.
 */
@HiltViewModel
class RouterViewModel @Inject constructor(
    runStep: RunStepUseCase,
    observeAvailability: ObserveAvailabilityUseCase,
    @ApplicationContext context: Context,
) : ViewModel() {

    private val executor = runStep.asExecutor()
    private val runner = PipelineRunner(executor)
    private val texts: RouterTexts = AndroidRouterTexts(context.resources)
    private val pipeline = RouterPipeline(runner, texts)
    private val latencyBook = LatencyBook()

    val availability: StateFlow<Map<InferenceTier, TierAvailability>> = observeAvailability()
    val trace: StateFlow<PipelineTrace> = runner.recorder.trace

    private val _form = MutableStateFlow(RouterForm())
    val form: StateFlow<RouterForm> = _form.asStateFlow()

    private val _outcome = MutableStateFlow<RouterOutcome?>(null)
    val outcome: StateFlow<RouterOutcome?> = _outcome.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _compare = MutableStateFlow(CompareState())
    val compare: StateFlow<CompareState> = _compare.asStateFlow()

    private var job: Job? = null

    fun update(change: (RouterForm) -> RouterForm) = _form.update(change)

    /** Whatever the user is waiting on (a run or a comparison) blocks starting another: the engines serve one request at a time. */
    val busy: Boolean get() = _running.value || _compare.value.running

    fun run(prompt: String) {
        if (prompt.isBlank() || busy) return
        val form = _form.value
        val usable = availability.value.usableTiers()
        job = viewModelScope.launch {
            _running.value = true
            _outcome.value = null
            try {
                val result = pipeline.run(
                    RouterRequest(
                        prompt = prompt,
                        policy = form.policy,
                        usable = usable,
                        manualTier = form.manualTier,
                        markedSensitive = form.markedSensitive,
                        expectJson = form.expectJson,
                        settings = RouterSettings(gatewayEnabled = form.gatewayEnabled, maxEscalations = form.maxEscalations),
                        confidenceMode = form.confidenceMode,
                        judgeTier = form.judgeTier,
                        classifierTier = form.classifierTier,
                        observedLatencyMs = latencyBook.snapshot(),
                    ),
                )
                result.attempts.filter { it.error == null }.forEach { latencyBook.record(it.tier, it.latencyMs) }
                _outcome.value = result
            } catch (e: CancellationException) {
                throw e
            } finally {
                _running.value = false
            }
        }
    }

    fun runComparison(quick: Boolean) {
        if (busy) return
        val form = _form.value
        val usable = availability.value.usableTiers()
        val prompts = if (quick) RouterBenchSet.quick else RouterBenchSet.prompts
        job = viewModelScope.launch {
            _compare.value = CompareState(running = true, total = prompts.size * COMPARED_POLICIES.size, promptCount = prompts.size)
            try {
                val runs = PolicyComparison(executor, texts).run(
                    prompts = prompts,
                    policies = COMPARED_POLICIES,
                    usable = usable,
                    settings = RouterSettings(gatewayEnabled = form.gatewayEnabled, maxEscalations = form.maxEscalations),
                ) { done, total, label ->
                    _compare.update { it.copy(done = done, total = total, label = label) }
                }
                _compare.update { it.copy(running = false, summaries = PolicyAggregator.summarize(runs)) }
            } catch (e: CancellationException) {
                _compare.update { it.copy(running = false) }
                throw e
            } catch (e: Exception) {
                _compare.update { it.copy(running = false, error = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _running.value = false
        _compare.update { it.copy(running = false) }
    }
}
