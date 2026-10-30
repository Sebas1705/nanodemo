package es.sebas1705.nanodemo.data.history

import android.net.Uri
import android.util.Log
import es.sebas1705.nanodemo.data.embeddings.LocalEmbeddingService
import es.sebas1705.nanodemo.domain.models.ChatMessage
import es.sebas1705.nanodemo.domain.models.Conversation
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "NanoDemo.History"
private const val TITLE_MAX_CHARS = 48

/** Cuánto texto de la conversación se manda a embeber — de sobra para el significado, sin pasarle al modelo un historial entero. */
private const val EMBEDDING_TEXT_MAX_CHARS = 2_000

@Singleton
class HistoryRepository @Inject constructor(
    private val dao: ConversationDao,
    private val embeddingDao: EmbeddingDao,
    private val embeddingService: LocalEmbeddingService,
) : IHistoryRepository {

    override fun observeConversations(): Flow<List<Conversation>> =
        dao.observeConversations().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getConversation(conversationId: Long): Conversation? =
        dao.getConversation(conversationId)?.let { entity ->
            entity.toDomain(messageCount = dao.getMessages(conversationId).size)
        }

    override suspend fun getMessages(conversationId: Long): List<ChatMessage> =
        dao.getMessages(conversationId).map { it.toDomain() }

    override suspend fun deleteConversation(conversationId: Long) {
        dao.deleteConversation(conversationId) // el embedding se borra solo (FK ON DELETE CASCADE).
    }

    override suspend fun appendMessages(
        conversationId: Long?,
        tier: InferenceTier,
        newMessages: List<ChatMessage>,
    ): Long {
        val now = System.currentTimeMillis()
        val id = conversationId ?: dao.insertConversation(
            ConversationEntity(
                tier = tier.name,
                // Sin texto queda vacío: el título por defecto lo pone la UI en su idioma (`Conversation.isUntitled`).
                title = newMessages.firstOrNull { it.role == Role.USER }
                    ?.text
                    ?.take(TITLE_MAX_CHARS)
                    .orEmpty(),
                createdAtMs = now,
                updatedAtMs = now,
            )
        )
        dao.insertMessages(newMessages.map { it.toEntity(id, now) })
        if (conversationId != null) dao.touchConversation(id, now)
        reindexForSearch(id)
        return id
    }

    /**
     * Best-effort: si el modelo de embeddings no está descargado o falla, la conversación se
     * guarda igual — solo no aparece en la búsqueda semántica hasta la próxima vez. Se re-embebe
     * con todo el texto de usuario acumulado (no solo el turno nuevo), así la búsqueda mejora a
     * medida que la charla avanza.
     */
    private suspend fun reindexForSearch(conversationId: Long) {
        if (!embeddingService.isAvailable()) return
        try {
            val text = dao.getMessages(conversationId)
                .filter { it.role == Role.USER.name }
                .joinToString(" ") { it.text }
                .take(EMBEDDING_TEXT_MAX_CHARS)
            if (text.isBlank()) return
            val vector = embeddingService.embed(text) ?: return
            embeddingDao.upsert(ConversationEmbeddingEntity(conversationId, vector.toByteArray()))
        } catch (e: Exception) {
            Log.e(TAG, "reindexForSearch($conversationId): falló", e)
        }
    }

    override fun isSemanticSearchAvailable(): Boolean = embeddingService.isAvailable()

    override fun downloadSemanticSearchModel(): Flow<Int> = embeddingService.download()

    override suspend fun searchConversations(query: String, topK: Int): List<Conversation>? {
        if (!embeddingService.isAvailable()) return null
        val queryVector = embeddingService.embed(query) ?: return null
        val scored = embeddingDao.getAll()
            .map { it.conversationId to embeddingService.cosineSimilarity(queryVector, it.vector.toFloatArray()) }
            .sortedByDescending { it.second }
            .take(topK)
        return scored.mapNotNull { (id, _) -> getConversation(id) }
    }
}

private fun ConversationWithCount.toDomain() =
    Conversation(id, InferenceTier.valueOf(tier), title, createdAtMs, updatedAtMs, messageCount)

private fun ConversationEntity.toDomain(messageCount: Int) =
    Conversation(id, InferenceTier.valueOf(tier), title, createdAtMs, updatedAtMs, messageCount)

private fun MessageEntity.toDomain() =
    ChatMessage(Role.valueOf(role), text, imageUri?.let(Uri::parse))

private fun ChatMessage.toEntity(conversationId: Long, timestampMs: Long) =
    MessageEntity(
        conversationId = conversationId,
        role = role.name,
        text = text,
        imageUri = imageUri?.toString(),
        timestampMs = timestampMs,
    )

/** `FloatArray` <-> `ByteArray`: Room guarda el embedding como BLOB, no como columnas separadas. */
private fun FloatArray.toByteArray(): ByteArray {
    val buffer = ByteBuffer.allocate(size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
    forEach { buffer.putFloat(it) }
    return buffer.array()
}

private fun ByteArray.toFloatArray(): FloatArray {
    val buffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
    return FloatArray(size / Float.SIZE_BYTES) { buffer.float }
}
