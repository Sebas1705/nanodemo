package es.sebas1705.nanodemo.feature.lab.refine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.PipelineTrace
import es.sebas1705.nanodemo.domain.models.TierAvailability
import es.sebas1705.nanodemo.domain.models.VoteTally
import es.sebas1705.nanodemo.domain.usescases.inference.CapabilitiesOfUseCase
import es.sebas1705.nanodemo.domain.usescases.inference.ObserveAvailabilityUseCase
import es.sebas1705.nanodemo.domain.usescases.inference.ObserveVotesUseCase
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
class JudgeViewModel @Inject constructor(
    runStep: RunStepUseCase,
    labTexts: LabTexts,
    observeAvailability: ObserveAvailabilityUseCase,
    observeVotes: ObserveVotesUseCase,
    private val capabilitiesOf: CapabilitiesOfUseCase,
    @ApplicationContext context: Context,
) : ViewModel() {

    private val texts = AndroidRefineTexts(context)
    private val runner = PipelineRunner(runStep.asExecutor(labTexts))
    private val pipeline = JudgePipeline(runner, { capabilitiesOf.profile(it) }, texts = texts)

    val availability: StateFlow<Map<InferenceTier, TierAvailability>> = observeAvailability()
    val trace: StateFlow<PipelineTrace> = runner.recorder.trace

    /** The human votes of Compare, read-only: used only to compare the judge's ranking with people's. */
    val votes: StateFlow<Map<InferenceTier, VoteTally>> = observeVotes()

    private val _judge = MutableStateFlow(
        availability.value.usableTiers().let { usable -> pickTier(usable, { !it.isLocal }, InferenceTier.CLOUD_GEMINI) },
    )
    val judge: StateFlow<InferenceTier> = _judge.asStateFlow()

    private val _engines = MutableStateFlow<Set<InferenceTier>>(emptySet())

    /** Engines that will answer the question in "engines" mode; defaults to every usable one until the user chooses. */
    val engines: StateFlow<Set<InferenceTier>> = _engines.asStateFlow()

    private val _result = MutableStateFlow(JudgeResult())
    val result: StateFlow<JudgeResult> = _result.asStateFlow()

    private val _progress = MutableStateFlow<LabProgress?>(null)
    val progress: StateFlow<LabProgress?> = _progress.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var job: Job? = null
    private var judgeChosen = false
    private var enginesChosen = false

    init {
        viewModelScope.launch {
            availability.collect { map ->
                val usable = map.usableTiers()
                if (!judgeChosen) _judge.value = pickTier(usable, { !it.isLocal }, InferenceTier.CLOUD_GEMINI)
                if (!enginesChosen) _engines.value = usable.toSet()
            }
        }
    }

    fun selectJudge(tier: InferenceTier) {
        judgeChosen = true
        _judge.value = tier
    }

    fun toggleEngine(tier: InferenceTier) {
        enginesChosen = true
        _engines.value = _engines.value.let { if (tier in it) it - tier else it + tier }
    }

    fun contextOf(tier: InferenceTier): Int? = capabilitiesOf(tier).contextTokens

    fun run(question: String, source: JudgeSource, mode: JudgeMode) {
        if (question.isBlank() || _running.value) return
        job = viewModelScope.launch {
            _running.value = true
            _error.value = null
            _result.value = JudgeResult(mode = mode)
            runner.recorder.reset()
            try {
                pipeline.run(
                    question = question.trim(),
                    source = source,
                    judge = _judge.value,
                    mode = mode,
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
