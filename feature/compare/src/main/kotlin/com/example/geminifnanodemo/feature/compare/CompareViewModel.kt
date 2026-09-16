package com.example.geminifnanodemo.feature.compare

import androidx.lifecycle.ViewModel
import com.example.geminifnanodemo.domain.models.DemoState
import com.example.geminifnanodemo.domain.models.InferenceTier
import com.example.geminifnanodemo.domain.usescases.inference.GenerateCompareUseCase
import com.example.geminifnanodemo.domain.usescases.inference.ObserveCompareStatesUseCase
import com.example.geminifnanodemo.domain.usescases.inference.RunBenchmarkUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class CompareViewModel @Inject constructor(
    observeCompareStates: ObserveCompareStatesUseCase,
    val generateCompare: GenerateCompareUseCase,
    val runBenchmark: RunBenchmarkUseCase,
) : ViewModel() {
    val compareStates: StateFlow<Map<InferenceTier, DemoState>> = observeCompareStates()
}
