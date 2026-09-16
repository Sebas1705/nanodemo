package com.example.geminifnanodemo.data.history

import android.net.Uri
import com.example.geminifnanodemo.domain.models.ChatMessage
import com.example.geminifnanodemo.domain.models.Conversation
import com.example.geminifnanodemo.domain.models.InferenceTier
import com.example.geminifnanodemo.domain.models.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private const val TITLE_MAX_CHARS = 48

@Singleton
class HistoryRepository @Inject constructor(
    private val dao: ConversationDao,
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
        dao.deleteConversation(conversationId)
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
                title = newMessages.firstOrNull { it.role == Role.USER }
                    ?.text
                    ?.take(TITLE_MAX_CHARS)
                    ?.ifBlank { null }
                    ?: "Conversación",
                createdAtMs = now,
                updatedAtMs = now,
            )
        )
        dao.insertMessages(newMessages.map { it.toEntity(id, now) })
        if (conversationId != null) dao.touchConversation(id, now)
        return id
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
