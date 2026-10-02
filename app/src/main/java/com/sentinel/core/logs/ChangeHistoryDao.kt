package com.sentinel.core.logs

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChangeHistoryDao {

    @Insert
    suspend fun insert(entity: ChangeHistoryEntity)

    @Query("SELECT * FROM change_history ORDER BY timestampMs DESC, id DESC LIMIT :limit")
    fun recentFlow(limit: Int): Flow<List<ChangeHistoryEntity>>

    @Query("SELECT COUNT(*) FROM change_history")
    suspend fun count(): Int

    // Keeps the newest rows only, so the history cannot grow without limit.
    @Query("""DELETE FROM change_history WHERE id NOT IN
              (SELECT id FROM change_history ORDER BY timestampMs DESC, id DESC LIMIT :keepCount)""")
    suspend fun deleteExcess(keepCount: Int)

    @Query("DELETE FROM change_history")
    suspend fun deleteAll()
}
