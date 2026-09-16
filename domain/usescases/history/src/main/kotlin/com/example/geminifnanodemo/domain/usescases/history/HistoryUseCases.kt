package com.example.geminifnanodemo.domain.usescases.history

import com.example.geminifnanodemo.data.history.IHistoryRepository
import com.example.geminifnanodemo.domain.models.Conversation
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveConversationsUseCase @Inject constructor(private val repository: IHistoryRepository) {
    operator fun invoke(): Flow<List<Conversation>> = repository.observeConversations()
}

class DeleteConversationUseCase @Inject constructor(private val repository: IHistoryRepository) {
    suspend operator fun invoke(conversationId: Long) = repository.deleteConversation(conversationId)
}
