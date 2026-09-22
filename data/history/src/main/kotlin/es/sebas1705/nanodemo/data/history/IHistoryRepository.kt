package es.sebas1705.nanodemo.data.history

import es.sebas1705.nanodemo.domain.models.ChatMessage
import es.sebas1705.nanodemo.domain.models.Conversation
import es.sebas1705.nanodemo.domain.models.InferenceTier
import kotlinx.coroutines.flow.Flow

/** Número de resultados que devuelve [IHistoryRepository.searchConversations] por defecto. */
const val SEMANTIC_SEARCH_TOP_K = 5

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

    /** ¿Está descargado el modelo de embeddings? Sin él, [searchConversations] no puede hacer nada. */
    fun isSemanticSearchAvailable(): Boolean

    fun downloadSemanticSearchModel(): Flow<Int>

    /**
     * Busca conversaciones por significado, no por coincidencia literal de texto — compara la
     * consulta contra el embedding de cada conversación (indexado en cada [appendMessages], ver
     * `HistoryRepository`) con similitud coseno. `null` = el modelo de embeddings no está listo
     * (distinto de una lista vacía, que sí es "no hay resultados").
     */
    suspend fun searchConversations(query: String, topK: Int = SEMANTIC_SEARCH_TOP_K): List<Conversation>?
}
