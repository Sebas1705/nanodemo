package es.sebas1705.nanodemo.domain.usescases.history

import es.sebas1705.nanodemo.data.history.IHistoryRepository
import es.sebas1705.nanodemo.domain.models.Conversation
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveConversationsUseCase @Inject constructor(private val repository: IHistoryRepository) {
    operator fun invoke(): Flow<List<Conversation>> = repository.observeConversations()
}

class IsSemanticSearchAvailableUseCase @Inject constructor(private val repository: IHistoryRepository) {
    operator fun invoke(): Boolean = repository.isSemanticSearchAvailable()
}

class DownloadSemanticSearchModelUseCase @Inject constructor(private val repository: IHistoryRepository) {
    operator fun invoke(): Flow<Int> = repository.downloadSemanticSearchModel()
}

class SearchConversationsUseCase @Inject constructor(private val repository: IHistoryRepository) {
    suspend operator fun invoke(query: String): List<Conversation>? = repository.searchConversations(query)
}

// El borrado vive en domain:usescases:inference (DeleteConversationUseCase), no acá: pasa por
// IInferenceRepository porque es quien conoce currentConversationId — borrar directo contra
// IHistoryRepository (como hacía este archivo antes) dejaba esa referencia desactualizada y producía un
// FOREIGN KEY constraint failed en el siguiente turno (visto en Crashlytics).
