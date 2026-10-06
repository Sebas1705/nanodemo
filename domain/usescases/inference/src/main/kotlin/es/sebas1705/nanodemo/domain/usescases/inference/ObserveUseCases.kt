package es.sebas1705.nanodemo.domain.usescases.inference

import android.net.Uri
import es.sebas1705.nanodemo.data.repositories.interfaces.IInferenceRepository
import es.sebas1705.nanodemo.domain.models.ChatMessage
import es.sebas1705.nanodemo.domain.models.DemoState
import es.sebas1705.nanodemo.domain.models.GenerationConfig
import es.sebas1705.nanodemo.domain.models.GenerationMetric
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.MediaPipeModelOption
import es.sebas1705.nanodemo.domain.models.TierAvailability
import es.sebas1705.nanodemo.domain.models.VoteTally
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Casos de uso de solo lectura: cada uno expone un `StateFlow` del
 * repositorio tal cual. Existen como seam del módulo (para que `feature:*`
 * nunca dependa de `data:*` directamente, regla explícita del plan de
 * migración) más que por lógica propia — de ahí que estén agrupados en un
 * solo archivo en vez de uno por clase.
 */
class ObserveAvailabilityUseCase @Inject constructor(private val repository: IInferenceRepository) {
    operator fun invoke(): StateFlow<Map<InferenceTier, TierAvailability>> = repository.availability
}

class ObserveActiveTierUseCase @Inject constructor(private val repository: IInferenceRepository) {
    operator fun invoke(): StateFlow<InferenceTier> = repository.activeTier
}

class ObserveStateUseCase @Inject constructor(private val repository: IInferenceRepository) {
    operator fun invoke(): StateFlow<DemoState> = repository.state
}

class ObserveConversationUseCase @Inject constructor(private val repository: IInferenceRepository) {
    operator fun invoke(): StateFlow<List<ChatMessage>> = repository.conversation
}

class ObserveConfigsUseCase @Inject constructor(private val repository: IInferenceRepository) {
    operator fun invoke(): StateFlow<Map<InferenceTier, GenerationConfig>> = repository.configs
}

class ObservePendingImageUseCase @Inject constructor(private val repository: IInferenceRepository) {
    operator fun invoke(): StateFlow<Uri?> = repository.pendingImage
}

class ObserveAttachedUrlUseCase @Inject constructor(private val repository: IInferenceRepository) {
    operator fun invoke(): StateFlow<String?> = repository.attachedUrl
}

class ObserveCompareStatesUseCase @Inject constructor(private val repository: IInferenceRepository) {
    operator fun invoke(): StateFlow<Map<InferenceTier, DemoState>> = repository.compareStates
}

class ObserveVotesUseCase @Inject constructor(private val repository: IInferenceRepository) {
    operator fun invoke(): StateFlow<Map<InferenceTier, VoteTally>> = repository.votes
}

class ObserveMetricsUseCase @Inject constructor(private val repository: IInferenceRepository) {
    operator fun invoke(): StateFlow<List<GenerationMetric>> = repository.metrics
}

class GetMediaPipeModelsUseCase @Inject constructor(private val repository: IInferenceRepository) {
    operator fun invoke(): List<MediaPipeModelOption> = repository.mediaPipeModels
}

class ObserveSelectedMediaPipeModelUseCase @Inject constructor(private val repository: IInferenceRepository) {
    operator fun invoke(): StateFlow<MediaPipeModelOption> = repository.selectedMediaPipeModel
}
