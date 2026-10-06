package es.sebas1705.nanodemo.feature.stats

import androidx.lifecycle.ViewModel
import es.sebas1705.nanodemo.domain.models.GenerationMetric
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.VoteTally
import es.sebas1705.nanodemo.domain.usescases.inference.ClearVotesUseCase
import es.sebas1705.nanodemo.domain.usescases.inference.ExportMetricsCsvUseCase
import es.sebas1705.nanodemo.domain.usescases.inference.ObserveMetricsUseCase
import es.sebas1705.nanodemo.domain.usescases.inference.ObserveVotesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    observeMetrics: ObserveMetricsUseCase,
    observeVotes: ObserveVotesUseCase,
    val exportMetricsCsv: ExportMetricsCsvUseCase,
    val clearVotes: ClearVotesUseCase,
) : ViewModel() {
    val metrics: StateFlow<List<GenerationMetric>> = observeMetrics()
    val votes: StateFlow<Map<InferenceTier, VoteTally>> = observeVotes()
}
