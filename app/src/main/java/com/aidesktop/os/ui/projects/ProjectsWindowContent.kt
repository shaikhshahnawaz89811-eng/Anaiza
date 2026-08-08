package com.aidesktop.os.ui.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aidesktop.os.ui.theme.AccentBlue
import com.aidesktop.os.ui.theme.AccentGreen
import com.aidesktop.os.ui.theme.AccentRed
import com.aidesktop.os.ui.theme.DesktopSurfaceElevated
import com.aidesktop.os.ui.theme.DesktopSurfaceHigh
import com.aidesktop.os.ui.theme.TextPrimary
import com.aidesktop.os.ui.theme.TextSecondary

@Composable
fun ProjectsWindowContent(viewModel: ProjectsViewModel = hiltViewModel()) {
    val projects by viewModel.projects.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(DesktopSurfaceElevated).padding(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Projects", color = TextPrimary, style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = { viewModel.showNewProjectForm.value = !viewModel.showNewProjectForm.value }) {
                Icon(Icons.Filled.Add, contentDescription = "New project", tint = AccentBlue)
            }
        }

        if (viewModel.showNewProjectForm.value) {
            NewProjectForm(onCreate = viewModel::createProject)
        }

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
            items(projects) { project ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DesktopSurfaceHigh)
                        .padding(10.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(project.name, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { viewModel.deleteProject(project) }, modifier = Modifier.padding(0.dp)) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = AccentRed)
                        }
                    }
                    if (project.description.isNotBlank()) {
                        Text(project.description, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (project.repositoryUrl.isNotBlank()) {
                        Text(project.repositoryUrl, color = AccentBlue, style = MaterialTheme.typography.labelSmall)
                    }
                    Row(modifier = Modifier.padding(top = 6.dp)) {
                        Text("Build: ", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                        Text(
                            project.buildStatus,
                            color = if (project.buildStatus == "Passing") AccentGreen else TextSecondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    LinearProgressIndicator(
                        progress = { project.progressPercent / 100f },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun NewProjectForm(onCreate: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var repo by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Project name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        OutlinedTextField(value = repo, onValueChange = { repo = it }, label = { Text("Repository URL") }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        Button(onClick = { onCreate(name, description, repo) }, modifier = Modifier.padding(top = 8.dp)) {
            Text("Create Project")
        }
    }
}
