package es.sebas1705.nanodemo.data.history

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        MetricEntity::class,
        ConversationEmbeddingEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class NanoDemoDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun metricDao(): MetricDao
    abstract fun embeddingDao(): EmbeddingDao
}

/**
 * v1 -> v2: agrega `metrics` (antes vivía solo en memoria — ver [MetricEntity]). No toca
 * `conversations`/`messages`: sin esta migración explícita, Room tira el historial de
 * conversaciones ya guardado de cualquiera que tenga una versión anterior instalada.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `metrics` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`tier` TEXT NOT NULL, " +
                "`startedAtMs` INTEGER NOT NULL, " +
                "`firstChunkLatencyMs` INTEGER, " +
                "`totalDurationMs` INTEGER NOT NULL, " +
                "`promptChars` INTEGER NOT NULL, " +
                "`responseChars` INTEGER NOT NULL, " +
                "`success` INTEGER NOT NULL, " +
                "`responseTokens` INTEGER NOT NULL, " +
                "`responseTokensExact` INTEGER NOT NULL, " +
                "`memoryDeltaBytes` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_metrics_startedAtMs` ON `metrics` (`startedAtMs`)")
    }
}

/**
 * v2 -> v3: agrega `conversation_embeddings` (búsqueda semántica local, ver
 * [ConversationEmbeddingEntity]). `FOREIGN KEY` con `ON DELETE CASCADE` para que borrar una
 * conversación limpie su embedding solo, igual que ya pasa con `messages`.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `conversation_embeddings` (" +
                "`conversationId` INTEGER NOT NULL PRIMARY KEY, " +
                "`vector` BLOB NOT NULL, " +
                "FOREIGN KEY(`conversationId`) REFERENCES `conversations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
    }
}

/**
 * v3 -> v4: agrega `metrics.loadTimeMs` (tiempo de carga del modelo aparte del de generación, ver
 * [GenerationMetric.loadTimeMs]). Columna nullable sin default: las filas viejas quedan en NULL = "no se midió".
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `metrics` ADD COLUMN `loadTimeMs` INTEGER")
    }
}
