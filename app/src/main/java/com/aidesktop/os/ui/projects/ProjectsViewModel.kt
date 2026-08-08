package com.aidesktop.os.ui.projects

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aidesktop.os.data.local.entity.ProjectEntity
import com.aidesktop.os.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val repository: ProjectRepository
) : ViewModel() {

    val projects = repository.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedProjectId = mutableStateOf<Long?>(null)
    val showNewProjectForm = mutableStateOf(false)

    fun createProject(name: String, description: String, repoUrl: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.saveProject(
                ProjectEntity(
                    name = name,
                    description = description,
                    repositoryUrl = repoUrl,
                    progressPercent = 0,
                    buildStatus = "Not built",
                    notes = ""
                )
            )
        }
        showNewProjectForm.value = false
    }

    fun deleteProject(project: ProjectEntity) {
        viewModelScope.launch { repository.deleteProject(project) }
    }

    fun updateProgress(project: ProjectEntity, percent: Int) {
        viewModelScope.launch { repository.saveProject(project.copy(progressPercent = percent)) }
    }

    fun updateBuildStatus(project: ProjectEntity, status: String) {
        viewModelScope.launch { repository.saveProject(project.copy(buildStatus = status)) }
    }
}
