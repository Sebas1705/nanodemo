package es.sebas1705.nanodemo.feature.history

import androidx.lifecycle.ViewModel
import es.sebas1705.nanodemo.domain.models.Conversation
import es.sebas1705.nanodemo.domain.usescases.inference.DeleteConversationUseCase
import es.sebas1705.nanodemo.domain.usescases.history.ObserveConversationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    observeConversations: ObserveConversationsUseCase,
    private val deleteConversation: DeleteConversationUseCase,
) : ViewModel() {

    val conversations: Flow<List<Conversation>> = observeConversations()

    fun delete(conversationId: Long) = deleteConversation(conversationId)
}
