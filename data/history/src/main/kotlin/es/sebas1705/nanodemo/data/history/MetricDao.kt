package es.sebas1705.nanodemo.data.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface MetricDao {

    @Query("SELECT * FROM metrics ORDER BY startedAtMs ASC")
    suspend fun getAll(): List<MetricEntity>

    @Insert
    suspend fun insert(metric: MetricEntity)

    /**
     * Se llama después de cada [insert] para no acumular corridas sin límite (cada "Ejecutar
     * benchmark" son varios prompts × 5 tiers). `ORDER BY ... DESC LIMIT` en una subquery es la
     * forma portable de podar en SQLite — `DELETE ... ORDER BY/LIMIT` directo no está garantizado
     * en todos los Android.
     */
    @Query("DELETE FROM metrics WHERE id NOT IN (SELECT id FROM metrics ORDER BY startedAtMs DESC LIMIT :maxRows)")
    suspend fun pruneOlderThan(maxRows: Int)
}
