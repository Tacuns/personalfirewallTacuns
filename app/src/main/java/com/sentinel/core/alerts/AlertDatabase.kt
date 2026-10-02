package com.sentinel.core.alerts

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * One security event the user should be able to look back at: a look-alike site, a
 * blocklist that failed to update, protection switched off by Android, lost rules.
 *
 * Only stable keys are stored (type, severity, the site or list, a reason code); the text
 * is written in the current app language when shown, as in Change history.
 */
@Entity(tableName = "alerts", indices = [Index(value = ["timestampMs"])])
data class AlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long = System.currentTimeMillis(),
    val type: String,
    val severity: String,
    val target: String = "",   // a domain or a blocklist key; empty when not about one
    val extra: String = "",    // e.g. the trusted site a look-alike copies, or a reason code
    val read: Boolean = false
)

@Dao
interface AlertDao {
    @Insert
    suspend fun insert(alert: AlertEntity): Long

    @Query("SELECT * FROM alerts ORDER BY timestampMs DESC, id DESC")
    fun allFlow(): Flow<List<AlertEntity>>

    @Query("SELECT COUNT(*) FROM alerts WHERE read = 0")
    fun unreadCountFlow(): Flow<Int>

    /** For duplicate checks: the newest alert of this kind about this target. */
    @Query("SELECT timestampMs FROM alerts WHERE type = :type AND target = :target ORDER BY timestampMs DESC LIMIT 1")
    suspend fun lastTime(type: String, target: String): Long?

    @Query("UPDATE alerts SET read = 1 WHERE id = :id")
    suspend fun markRead(id: Long)

    @Query("UPDATE alerts SET read = 1 WHERE read = 0")
    suspend fun markAllRead()

    @Query("DELETE FROM alerts")
    suspend fun deleteAll()

    @Query("DELETE FROM alerts WHERE timestampMs < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long)

    @Query("DELETE FROM alerts WHERE id NOT IN (SELECT id FROM alerts ORDER BY timestampMs DESC, id DESC LIMIT :keep)")
    suspend fun deleteExcess(keep: Int)
}

/**
 * Alerts live in their own small database, so adding them changes nothing in the activity
 * history or rules databases. Both the app and the :vpn process write here, so changes
 * made by one reach the other's open screens (multi-instance invalidation).
 */
@Database(entities = [AlertEntity::class], version = 1, exportSchema = false)
abstract class AlertDatabase : RoomDatabase() {
    abstract fun alertDao(): AlertDao

    companion object {
        @Volatile private var INSTANCE: AlertDatabase? = null

        fun getInstance(context: Context): AlertDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext, AlertDatabase::class.java, "sentinel-alerts.db"
                )
                    .enableMultiInstanceInvalidation()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
