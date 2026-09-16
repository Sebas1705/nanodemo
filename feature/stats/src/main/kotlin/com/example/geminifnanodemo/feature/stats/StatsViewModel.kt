package com.example.geminifnanodemo.feature.stats

import androidx.lifecycle.ViewModel
import com.example.geminifnanodemo.domain.models.GenerationMetric
import com.example.geminifnanodemo.domain.usescases.inference.ExportMetricsCsvUseCase
import com.example.geminifnanodemo.domain.usescases.inference.ObserveMetricsUseCase
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
