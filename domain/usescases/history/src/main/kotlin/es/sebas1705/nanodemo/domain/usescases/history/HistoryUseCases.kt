package es.sebas1705.nanodemo.domain.usescases.history

import es.sebas1705.nanodemo.data.history.IHistoryRepository
import es.sebas1705.nanodemo.domain.models.Conversation
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveConversationsUseCase @Inject constructor(private val repository: IHistoryRepository) {
    operator fun invoke(): Flow<List<Conversation>> = repository.observeConversations()
}

// El borrado vive en domain:usescases:inference (DeleteConversationUseCase), no acá: pasa por
// IInferenceRepository porque es quien conoce currentConversationId — borrar directo contra
// IHistoryRepository (como hacía este archivo antes) dejaba esa referencia desactualizada y producía un
// FOREIGN KEY constraint failed en el siguiente turno (visto en Crashlytics).
