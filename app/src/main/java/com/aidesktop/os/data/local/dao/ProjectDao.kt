package com.aidesktop.os.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.aidesktop.os.data.local.entity.ProjectEntity
import com.aidesktop.os.data.local.entity.ProjectFileEntity
import com.aidesktop.os.data.local.entity.ProjectTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY createdAt DESC")
    fun observeProjects(): Flow<List<ProjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(project: ProjectEntity): Long

    @Delete
    suspend fun delete(project: ProjectEntity)

    @Query("SELECT * FROM project_tasks WHERE projectId = :projectId")
    fun observeTasks(projectId: Long): Flow<List<ProjectTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: ProjectTaskEntity): Long

    @Update
    suspend fun updateTask(task: ProjectTaskEntity)

    @Query("SELECT * FROM project_files WHERE projectId = :projectId")
    fun observeFiles(projectId: Long): Flow<List<ProjectFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFile(file: ProjectFileEntity): Long
}
