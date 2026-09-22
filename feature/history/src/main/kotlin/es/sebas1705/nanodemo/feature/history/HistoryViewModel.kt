package es.sebas1705.nanodemo.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.sebas1705.nanodemo.domain.models.Conversation
import es.sebas1705.nanodemo.domain.usescases.history.DownloadSemanticSearchModelUseCase
import es.sebas1705.nanodemo.domain.usescases.history.IsSemanticSearchAvailableUseCase
import es.sebas1705.nanodemo.domain.usescases.history.ObserveConversationsUseCase
import es.sebas1705.nanodemo.domain.usescases.history.SearchConversationsUseCase
import es.sebas1705.nanodemo.domain.usescases.inference.DeleteConversationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Estado del modelo de embeddings ("búsqueda semántica"), no de los tiers de IA — ver `LocalEmbeddingService`. */
sealed interface SemanticSearchState {
    data object NotDownloaded : SemanticSearchState
    data class Downloading(val progressPercent: Int? = null) : SemanticSearchState
    data object Ready : SemanticSearchState
    data class Error(val message: String) : SemanticSearchState
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    observeConversations: ObserveConversationsUseCase,
    private val deleteConversation: DeleteConversationUseCase,
    isSemanticSearchAvailable: IsSemanticSearchAvailableUseCase,
    private val downloadSemanticSearchModel: DownloadSemanticSearchModelUseCase,
    private val searchConversationsUseCase: SearchConversationsUseCase,
) : ViewModel() {

    val conversations: Flow<List<Conversation>> = observeConversations()

    private val _searchState = MutableStateFlow<SemanticSearchState>(
        if (isSemanticSearchAvailable()) SemanticSearchState.Ready else SemanticSearchState.NotDownloaded
    )
    val searchState: StateFlow<SemanticSearchState> = _searchState.asStateFlow()

    /** `null` = sin búsqueda activa (se muestra la lista cronológica de `conversations`). */
    private val _searchResults = MutableStateFlow<List<Conversation>?>(null)
    val searchResults: StateFlow<List<Conversation>?> = _searchResults.asStateFlow()

    fun delete(conversationId: Long) = deleteConversation(conversationId)

    fun downloadSearchModel() {
        viewModelScope.launch {
            _searchState.update { SemanticSearchState.Downloading() }
            try {
                downloadSemanticSearchModel().collect { percent ->
                    _searchState.update { SemanticSearchState.Downloading(percent) }
                }
                _searchState.update { SemanticSearchState.Ready }
            } catch (e: Exception) {
                _searchState.update { SemanticSearchState.Error(e.message ?: "Error descargando el modelo de búsqueda") }
            }
        }
    }

    fun search(query: String) {
        if (query.isBlank()) {
            clearSearch()
            return
        }
        viewModelScope.launch {
            _searchResults.value = searchConversationsUseCase(query)
        }
    }

    fun clearSearch() {
        _searchResults.value = null
    }
}
