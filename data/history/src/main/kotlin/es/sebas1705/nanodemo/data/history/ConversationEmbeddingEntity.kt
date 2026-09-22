package es.sebas1705.nanodemo.data.history

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Vector de embedding de una conversación, para búsqueda semántica local ("¿qué le pregunté sobre
 * X?") — ver `HistoryRepository.searchConversations`. `vector` es un `FloatArray` serializado a
 * bytes ([toByteArray]/[toFloatArray] en `HistoryRepository`), no una lista de columnas: Room no
 * necesita indexar sus componentes, solo guardarlo y devolverlo entero.
 *
 * `onDelete = CASCADE` (igual que [MessageEntity]): al borrar una conversación desde Historial, su
 * embedding se limpia solo — no hace falta un `DELETE` aparte en `deleteConversation`.
 */
@Entity(
    tableName = "conversation_embeddings",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ConversationEmbeddingEntity(
    @PrimaryKey val conversationId: Long,
    val vector: ByteArray,
)
