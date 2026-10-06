package es.sebas1705.nanodemo.domain.usescases.inference

import android.net.Uri
import es.sebas1705.nanodemo.data.repositories.interfaces.IInferenceRepository
import es.sebas1705.nanodemo.domain.models.GenerationConfig
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.LiteRtLmAccelerator
import es.sebas1705.nanodemo.domain.models.MediaPipeModelOption
import es.sebas1705.nanodemo.domain.models.TierCapabilities
import javax.inject.Inject

class CapabilitiesOfUseCase @Inject constructor(private val repository: IInferenceRepository) {
    operator fun invoke(tier: InferenceTier): TierCapabilities = repository.capabilitiesOf(tier)
}

class SelectTierUseCase @Inject constructor(private val repository: IInferenceRepository) {
    operator fun invoke(tier: InferenceTier) = repository.selectTier(tier)
}

class DownloadModelUseCase @Inject constructor(private val repository: IInferenceRepository) {
    operator fun invoke() = repository.downloadModel()
}

class UpdateConfigUseCase @Inject constructor(private val repository: IInferenceRepository) {
    operator fun invoke(tier: InferenceTier, config: GenerationConfig) = repository.updateConfig(tier, config)
}

class SetPendingImageUseCase @Inject constructor(private val repository: IInferenceRepository) {
    operator fun invoke(uri: Uri?) = repository.setPendingImage(uri)
}

class SetAttachedUrlUseCase @Inject constructor(private val repository: IInferenceRepository) {
    operator fun invoke(url: String?) = repository.setAttachedUrl(url)
}

class ResetConversationUseCase @Inject constructor(private val repository: IInferenceRepository) {
    operator fun invoke() = repository.resetConversation()
}

class GenerateUseCase @Inject constructor(private val repository: IInferenceRepository) {
    operator fun invoke(prompt: String) = repository.generate(prompt)
}

class GenerateCompareUseCase @Inject constructor(private val repository: IInferenceRepository) {
    operator fun invoke(prompt: String) = repository.generateCompare(prompt)
}

class ExportMetricsCsvUseCase @Inject constructor(private val repository: IInferenceRepository) {
    operator fun invoke(): Uri? = repository.exportMetricsCsv()
}

/** Reabre una conversación guardada en el historial: fija tier/transcript y sigue agregando a partir de ella. */
class LoadConversationUseCase @Inject constructor(private val repository: IInferenceRepository) {
    operator fun invoke(conversationId: Long) = repository.loadConversation(conversationId)
}

class DeleteConversationUseCase @Inject constructor(private val repository: IInferenceRepository) {
    operator fun invoke(conversationId: Long) = repository.deleteConversation(conversationId)
}

class SelectMediaPipeModelUseCase @Inject constructor(private val repository: IInferenceRepository) {
    operator fun invoke(option: MediaPipeModelOption) = repository.selectMediaPipeModel(option)
}

class VoteBestUseCase @Inject constructor(private val repository: IInferenceRepository) {
    operator fun invoke(winner: InferenceTier, participants: Set<InferenceTier>, previousWinner: InferenceTier?) =
        repository.voteBest(winner, participants, previousWinner)
}

class ClearVotesUseCase @Inject constructor(private val repository: IInferenceRepository) {
    operator fun invoke() = repository.clearVotes()
}

class SelectLiteRtLmAcceleratorUseCase @Inject constructor(private val repository: IInferenceRepository) {
    operator fun invoke(option: LiteRtLmAccelerator) = repository.selectLiteRtLmAccelerator(option)
}
