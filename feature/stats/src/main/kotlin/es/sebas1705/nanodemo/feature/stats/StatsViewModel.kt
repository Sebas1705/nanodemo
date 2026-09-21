package es.sebas1705.nanodemo.feature.stats

import androidx.lifecycle.ViewModel
import es.sebas1705.nanodemo.domain.models.GenerationMetric
import es.sebas1705.nanodemo.domain.usescases.inference.ExportMetricsCsvUseCase
import es.sebas1705.nanodemo.domain.usescases.inference.ObserveMetricsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    observeMetrics: ObserveMetricsUseCase,
    val exportMetricsCsv: ExportMetricsCsvUseCase,
) : ViewModel() {
    val metrics: StateFlow<List<GenerationMetric>> = observeMetrics()
}
