package es.sebas1705.nanodemo.data.history

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Una corrida de `generate()` persistida (antes solo vivía en memoria, se perdía al matar el
 * proceso — ver `GenerationMetric` en `domain:models`). Sin relación con `conversations`/`messages`
 * a propósito: una corrida de comparación/benchmark no pertenece a ninguna conversación guardada.
 */
@Entity(tableName = "metrics", indices = [Index("startedAtMs")])
data class MetricEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tier: String,
    val startedAtMs: Long,
    val firstChunkLatencyMs: Long?,
    val totalDurationMs: Long,
    val promptChars: Int,
    val responseChars: Int,
    val success: Boolean,
    val responseTokens: Int,
    val responseTokensExact: Boolean,
    val memoryDeltaBytes: Long,
)
