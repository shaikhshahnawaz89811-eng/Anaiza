package com.aidesktop.os.data.repository

import com.aidesktop.os.data.local.dao.ProjectDao
import com.aidesktop.os.data.local.entity.ProjectEntity
import com.aidesktop.os.data.local.entity.ProjectTaskEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepository @Inject constructor(private val dao: ProjectDao) {
    fun observeProjects(): Flow<List<ProjectEntity>> = dao.observeProjects()
    fun observeTasks(projectId: Long): Flow<List<ProjectTaskEntity>> = dao.observeTasks(projectId)

    suspend fun saveProject(project: ProjectEntity): Long = dao.upsert(project)
    suspend fun deleteProject(project: ProjectEntity) = dao.delete(project)
    suspend fun addTask(projectId: Long, title: String) =
        dao.upsertTask(ProjectTaskEntity(projectId = projectId, title = title))
    suspend fun toggleTask(task: ProjectTaskEntity) =
        dao.updateTask(task.copy(isDone = !task.isDone))
}
