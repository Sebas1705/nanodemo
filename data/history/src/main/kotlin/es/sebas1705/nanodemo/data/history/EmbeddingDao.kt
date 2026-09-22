package es.sebas1705.nanodemo.data.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EmbeddingDao {

    @Query("SELECT * FROM conversation_embeddings")
    suspend fun getAll(): List<ConversationEmbeddingEntity>

    /** `REPLACE`: reindexar una conversación (turno nuevo) pisa el vector anterior, no lo duplica. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(embedding: ConversationEmbeddingEntity)
}
