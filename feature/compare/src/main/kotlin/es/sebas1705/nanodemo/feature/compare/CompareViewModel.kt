package es.sebas1705.nanodemo.feature.compare

import androidx.lifecycle.ViewModel
import es.sebas1705.nanodemo.domain.models.DemoState
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.usescases.inference.GenerateCompareUseCase
import es.sebas1705.nanodemo.domain.usescases.inference.ObserveCompareStatesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class CompareViewModel @Inject constructor(
    observeCompareStates: ObserveCompareStatesUseCase,
    val generateCompare: GenerateCompareUseCase,
) : ViewModel() {
    val compareStates: StateFlow<Map<InferenceTier, DemoState>> = observeCompareStates()
}
