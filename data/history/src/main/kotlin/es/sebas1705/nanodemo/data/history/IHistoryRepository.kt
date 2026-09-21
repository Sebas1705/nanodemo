package es.sebas1705.nanodemo.data.history

import es.sebas1705.nanodemo.domain.models.ChatMessage
import es.sebas1705.nanodemo.domain.models.Conversation
import es.sebas1705.nanodemo.domain.models.InferenceTier
import kotlinx.coroutines.flow.Flow

/**
 * Historial local de conversaciones (SQLite vía Room). Interfaz e
 * implementación viven juntas en `data:history` (a diferencia de
 * `IInferenceRepository`, cuya interfaz vive en `data:repositories`): acá no
 * hay backends por-tier que dependan de la interfaz, así que no hace falta
 * separarla para evitar un ciclo — es un repositorio autocontenido.
 */
interface IHistoryRepository {

    /** Lista de conversaciones guardadas, más nuevas primero. */
    fun observeConversations(): Flow<List<Conversation>>

    suspend fun getConversation(conversationId: Long): Conversation?

    suspend fun getMessages(conversationId: Long): List<ChatMessage>

    suspend fun deleteConversation(conversationId: Long)

    /**
     * Agrega `newMessages` a la conversación `conversationId` (creándola si es
     * `null`, usando el primer mensaje de usuario como título). Devuelve el id
     * de la conversación (nuevo o existente) para que el llamador seguir
     * agregando a la misma conversación en turnos siguientes.
     */
    suspend fun appendMessages(conversationId: Long?, tier: InferenceTier, newMessages: List<ChatMessage>): Long
}
