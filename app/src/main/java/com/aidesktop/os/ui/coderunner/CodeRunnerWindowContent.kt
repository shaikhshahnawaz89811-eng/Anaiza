package com.aidesktop.os.ui.coderunner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aidesktop.os.data.remote.PistonRuntime
import com.aidesktop.os.ui.theme.AccentBlue
import com.aidesktop.os.ui.theme.AccentGreen
import com.aidesktop.os.ui.theme.AccentRed
import com.aidesktop.os.ui.theme.DesktopSurfaceElevated
import com.aidesktop.os.ui.theme.DesktopSurfaceHigh
import com.aidesktop.os.ui.theme.DividerColor
import com.aidesktop.os.ui.theme.TextPrimary
import com.aidesktop.os.ui.theme.TextSecondary

import androidx.compose.foundation.clickable
/**
 * Lets the user paste code (e.g. something the AI Assistant just suggested),
 * actually run it against the real Piston execution service, read the real
 * output, and — once they're happy with it — push it to GitHub as a real
 * commit. All inside the app, no other app needs to open.
 */
@Composable
fun CodeRunnerWindowContent(viewModel: CodeRunnerViewModel = hiltViewModel()) {
    val runtimes by viewModel.runtimes
    val selected by viewModel.selectedRuntime
    var showPushDialog by remember { mutableStateOf(false) }
    var showTokenDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(DesktopSurfaceElevated).padding(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Code Runner", color = TextPrimary, style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { showTokenDialog = true }) {
                Text(if (viewModel.hasGitHubToken.value) "GitHub: connected" else "Connect GitHub")
            }
        }
        Text(
            "Runs your code for real via a sandboxed execution service, then you can push it to GitHub.",
            color = TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyRow(modifier = Modifier.padding(bottom = 8.dp)) {
            items(runtimes) { rt ->
                RuntimeChip(
                    runtime = rt,
                    isSelected = rt.language == selected?.language,
                    onClick = { viewModel.selectRuntime(rt) }
                )
            }
        }

        OutlinedTextField(
            value = viewModel.code.value,
            onValueChange = { viewModel.code.value = it },
            label = { Text("Code") },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth().height(160.dp)
        )
        OutlinedTextField(
            value = viewModel.stdin.value,
            onValueChange = { viewModel.stdin.value = it },
            label = { Text("stdin (optional)") },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        )

        Row(modifier = Modifier.padding(top = 8.dp)) {
            Button(onClick = { viewModel.run() }, enabled = !viewModel.isRunning.value && selected != null) {
                if (viewModel.isRunning.value) {
                    CircularProgressIndicator(modifier = Modifier.height(16.dp).padding(end = 6.dp))
                } else {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                }
                Text("Run")
            }
            OutlinedButton(
                onClick = { showPushDialog = true },
                modifier = Modifier.padding(start = 8.dp),
                enabled = viewModel.output.value != null
            ) {
                Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text("Push to GitHub")
            }
        }

        viewModel.runError.value?.let {
            Text(it, color = AccentRed, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
        }

        viewModel.output.value?.let { out ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DesktopSurfaceHigh)
                    .padding(10.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Exit code: ${out.exitCode ?: "—"}", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                if (out.stdout.isNotBlank()) {
                    Text("stdout", color = AccentGreen, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
                    Text(out.stdout, color = TextPrimary, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
                if (out.stderr.isNotBlank()) {
                    Text("stderr", color = AccentRed, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
                    Text(out.stderr, color = TextPrimary, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        viewModel.pushMessage.value?.let {
            Text(it, color = if (it.startsWith("Pushed")) AccentGreen else AccentRed, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
        }
    }

    if (showTokenDialog) {
        GitHubTokenDialog(
            hasToken = viewModel.hasGitHubToken.value,
            onDismiss = { showTokenDialog = false },
            onSave = { viewModel.saveGitHubToken(it); showTokenDialog = false },
            onForget = { viewModel.forgetGitHubToken(); showTokenDialog = false }
        )
    }

    if (showPushDialog) {
        PushToGitHubDialog(
            isPushing = viewModel.isPushing.value,
            onDismiss = { showPushDialog = false },
            onConfirm = { owner, repo, branch, path, message ->
                viewModel.pushToGitHub(owner, repo, branch, path, message)
                showPushDialog = false
            }
        )
    }
}

@Composable
private fun RuntimeChip(runtime: PistonRuntime, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(end = 6.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) AccentBlue else DividerColor)
            .clickableChip(onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text("${runtime.language} ${runtime.version}", color = TextPrimary, style = MaterialTheme.typography.labelSmall)
    }
}

private fun Modifier.clickableChip(onClick: () -> Unit): Modifier =
    this.then(androidx.compose.foundation.clickable(onClick = onClick))

@Composable
private fun GitHubTokenDialog(
    hasToken: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onForget: () -> Unit
) {
    var token by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("GitHub Personal Access Token") },
        text = {
            Column {
                Text(
                    "Needs \"contents: write\" permission on the repos you'll push to. Stored encrypted on this device only.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Token") },
                    placeholder = { Text("ghp_...") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(token) }, enabled = token.isNotBlank()) { Text("Save") } },
        dismissButton = {
            Row {
                if (hasToken) TextButton(onClick = onForget) { Text("Remove") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
private fun PushToGitHubDialog(
    isPushing: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (owner: String, repo: String, branch: String, path: String, message: String) -> Unit
) {
    var owner by remember { mutableStateOf("") }
    var repo by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("main") }
    var path by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Push to GitHub") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = owner, onValueChange = { owner = it }, label = { Text("Owner") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = repo, onValueChange = { repo = it }, label = { Text("Repository") }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                OutlinedTextField(value = branch, onValueChange = { branch = it }, label = { Text("Branch") }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                OutlinedTextField(value = path, onValueChange = { path = it }, label = { Text("File path (e.g. src/main.py)") }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                OutlinedTextField(value = message, onValueChange = { message = it }, label = { Text("Commit message") }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isPushing && owner.isNotBlank() && repo.isNotBlank() && path.isNotBlank(),
                onClick = { onConfirm(owner, repo, branch, path, message) }
            ) { Text("Push") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
