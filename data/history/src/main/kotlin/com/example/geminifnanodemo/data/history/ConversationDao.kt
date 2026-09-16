package com.example.geminifnanodemo.data.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Query(
        "SELECT c.*, (SELECT COUNT(*) FROM messages m WHERE m.conversationId = c.id) AS messageCount " +
            "FROM conversations c ORDER BY updatedAtMs DESC"
    )
    fun observeConversations(): Flow<List<ConversationWithCount>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversation(id: Long): ConversationEntity?

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY id ASC")
    suspend fun getMessages(conversationId: Long): List<MessageEntity>

    @Insert
    suspend fun insertConversation(conversation: ConversationEntity): Long

    @Insert
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Query("UPDATE conversations SET updatedAtMs = :updatedAtMs WHERE id = :id")
    suspend fun touchConversation(id: Long, updatedAtMs: Long)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: Long)
}
