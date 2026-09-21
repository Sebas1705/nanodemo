package es.sebas1705.nanodemo.domain.usescases.history

import es.sebas1705.nanodemo.data.history.IHistoryRepository
import es.sebas1705.nanodemo.domain.models.Conversation
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveConversationsUseCase @Inject constructor(private val repository: IHistoryRepository) {
    operator fun invoke(): Flow<List<Conversation>> = repository.observeConversations()
}

class DeleteConversationUseCase @Inject constructor(private val repository: IHistoryRepository) {
    suspend operator fun invoke(conversationId: Long) = repository.deleteConversation(conversationId)
}
