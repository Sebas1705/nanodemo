package es.sebas1705.nanodemo.feature.lab.bench

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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** What the user configured for the next run. */
data class BenchSetup(
    val tiers: Set<InferenceTier> = emptySet(),
    val categories: Set<BenchCategory> = BenchCategory.entries.toSet(),
    val repetitions: Int = 1,
    val temperature: Float = 0f,
    val label: String = "",
)

@HiltViewModel
class BenchViewModel @Inject constructor(
    runStep: RunStepUseCase,
    observeAvailability: ObserveAvailabilityUseCase,
    private val capabilitiesOf: CapabilitiesOfUseCase,
    private val store: BenchStore,
) : ViewModel() {

    private val runner = BenchRunner(runStep.asExecutor(), supportsJson = { capabilitiesOf(it).structuredOutput })

    val availability: StateFlow<Map<InferenceTier, TierAvailability>> = observeAvailability()

    private val _setup = MutableStateFlow(BenchSetup())
    val setup: StateFlow<BenchSetup> = _setup.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _progress = MutableStateFlow<BenchProgress?>(null)
    val progress: StateFlow<BenchProgress?> = _progress.asStateFlow()

    /** Samples of the session being shown: grows while a run is in progress. */
    private val _samples = MutableStateFlow<List<BenchSample>>(emptyList())
    val samples: StateFlow<List<BenchSample>> = _samples.asStateFlow()

    private val _sessions = MutableStateFlow(store.load())
    val sessions: StateFlow<List<BenchSession>> = _sessions.asStateFlow()

    private val _baselineId = MutableStateFlow<Long?>(null)
    val baselineId: StateFlow<Long?> = _baselineId.asStateFlow()

    private var job: Job? = null
    private var defaultsApplied = false

    /** The first time engines are known, pre-select the ones that are usable so "Ejecutar" works at once. */
    fun applyDefaultsIfNeeded(availability: Map<InferenceTier, TierAvailability>) {
        if (defaultsApplied) return
        val usable = availability.usableTiers()
        if (usable.isEmpty()) return
        defaultsApplied = true
        _setup.update { it.copy(tiers = usable.toSet()) }
    }

    fun toggleTier(tier: InferenceTier) = _setup.update {
        it.copy(tiers = if (tier in it.tiers) it.tiers - tier else it.tiers + tier)
    }

    fun toggleCategory(category: BenchCategory) = _setup.update {
        it.copy(categories = if (category in it.categories) it.categories - category else it.categories + category)
    }

    fun setRepetitions(value: Int) = _setup.update { it.copy(repetitions = value.coerceIn(1, BenchConfig.MAX_REPETITIONS)) }
    fun setTemperature(value: Float) = _setup.update { it.copy(temperature = value.coerceIn(0f, 2f)) }
    fun setLabel(value: String) = _setup.update { it.copy(label = value) }

    fun canStart(setup: BenchSetup = _setup.value): Boolean = setup.tiers.isNotEmpty() && setup.categories.isNotEmpty() && !_running.value

    fun start() {
        val setup = _setup.value
        if (!canStart(setup)) return
        val startedAt = System.currentTimeMillis()
        val config = BenchConfig(
            tiers = InferenceTier.entries.filter { it in setup.tiers },
            categories = setup.categories,
            repetitions = setup.repetitions,
            temperature = setup.temperature,
        )
        _samples.value = emptyList()
        _running.value = true
        job = viewModelScope.launch {
            try {
                val result = runner.run(
                    config,
                    onProgress = { _progress.value = it },
                    onSample = { sample -> _samples.update { it + sample } },
                )
                saveSession(startedAt, setup, result)
            } catch (e: CancellationException) {
                // Cancelled: keep what ran so far on screen but do not save a partial session.
                throw e
            } finally {
                _running.value = false
                _progress.value = null
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    private fun saveSession(startedAt: Long, setup: BenchSetup, samples: List<BenchSample>) {
        if (samples.isEmpty()) return
        val label = setup.label.ifBlank { "Sesión " + SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(startedAt)) }
        _sessions.value = store.save(
            BenchSession(
                id = startedAt, label = label, startedAtMs = startedAt,
                temperature = setup.temperature, repetitions = setup.repetitions, samples = samples,
            ),
        )
    }

    fun show(session: BenchSession) {
        if (_running.value) return
        _samples.value = session.samples
    }

    fun delete(id: Long) {
        _sessions.value = store.delete(id)
        if (_baselineId.value == id) _baselineId.value = null
    }

    fun setBaseline(id: Long?) {
        _baselineId.value = id
    }
}
