package com.aidesktop.os.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aidesktop.os.data.local.entity.AiActivityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AiActivityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: AiActivityEntity): Long

    @Query("SELECT * FROM ai_activity WHERE actionType = :actionType ORDER BY occurredAt DESC LIMIT :limit")
    suspend fun recentByType(actionType: String, limit: Int = 15): List<AiActivityEntity>

    @Query("SELECT * FROM ai_activity ORDER BY occurredAt DESC LIMIT :limit")
    suspend fun recent(limit: Int = 30): List<AiActivityEntity>

    @Query("SELECT * FROM ai_activity ORDER BY occurredAt DESC")
    fun observeAll(): Flow<List<AiActivityEntity>>

    @Query("DELETE FROM ai_activity")
    suspend fun clear()
}
