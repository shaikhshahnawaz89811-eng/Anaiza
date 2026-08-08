package com.aidesktop.os.ui.coderunner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aidesktop.os.data.remote.PistonRuntime
import com.aidesktop.os.ui.desktop.window.SubWindow
import com.aidesktop.os.ui.theme.AccentRed

import androidx.compose.foundation.clickable

// Termux-style terminal palette, local to this window only — real black
// terminal background + classic terminal green, not the app's normal light
// card surfaces. Purely visual; every value below still comes from the same
// real ViewModel state (real Piston run, real GitHub push) as before.
private val TerminalBg = Color(0xFF0B0F0C)
private val TerminalPanelBg = Color(0xFF101512)
private val TerminalGreen = Color(0xFF4AF626)
private val TerminalDimGreen = Color(0xFF2E8B2E)
private val TerminalText = Color(0xFFE4E7E4)
private const val TerminalPrompt = "u0_a125@localhost:~$ "

/**
 * Lets the user paste code (e.g. something the AI Assistant just suggested),
 * actually run it against the real Piston execution service, read the real
 * output, and — once they're happy with it — push it to GitHub as a real
 * commit. All inside the app, no other app needs to open. Styled to look
 * like a real Termux terminal session.
 */
@Composable
fun CodeRunnerWindowContent(viewModel: CodeRunnerViewModel = hiltViewModel()) {
    val runtimes by viewModel.runtimes
    val selected by viewModel.selectedRuntime
    var showPushDialog by remember { mutableStateOf(false) }
    var showTokenDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalBg)
            .padding(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "Termux",
                color = TerminalGreen,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.titleLarge
            )
            TextButton(onClick = { showTokenDialog = true }) {
                Text(
                    if (viewModel.hasGitHubToken.value) "github: connected" else "connect github",
                    color = TerminalDimGreen,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        Text(
            "$TerminalPrompt neofetch --runtime",
            color = TerminalDimGreen,
            fontFamily = FontFamily.Monospace,
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
            label = { Text("code", color = TerminalDimGreen, fontFamily = FontFamily.Monospace) },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = TerminalText),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedContainerColor = TerminalPanelBg,
                unfocusedContainerColor = TerminalPanelBg,
                focusedBorderColor = TerminalDimGreen,
                unfocusedBorderColor = TerminalDimGreen.copy(alpha = 0.4f),
                cursorColor = TerminalGreen
            ),
            modifier = Modifier.fillMaxWidth().height(160.dp)
        )
        OutlinedTextField(
            value = viewModel.stdin.value,
            onValueChange = { viewModel.stdin.value = it },
            label = { Text("stdin (optional)", color = TerminalDimGreen, fontFamily = FontFamily.Monospace) },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = TerminalText),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedContainerColor = TerminalPanelBg,
                unfocusedContainerColor = TerminalPanelBg,
                focusedBorderColor = TerminalDimGreen,
                unfocusedBorderColor = TerminalDimGreen.copy(alpha = 0.4f),
                cursorColor = TerminalGreen
            ),
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        )

        Row(modifier = Modifier.padding(top = 8.dp)) {
            Button(onClick = { viewModel.run() }, enabled = !viewModel.isRunning.value && selected != null) {
                if (viewModel.isRunning.value) {
                    CircularProgressIndicator(modifier = Modifier.height(16.dp).padding(end = 6.dp))
                } else {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                }
                Text("run")
            }
            OutlinedButton(
                onClick = { showPushDialog = true },
                modifier = Modifier.padding(start = 8.dp),
                enabled = viewModel.output.value != null
            ) {
                Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text("push")
            }
        }

        viewModel.runError.value?.let {
            Text(
                "$TerminalPrompt error: $it",
                color = AccentRed,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        viewModel.output.value?.let { out ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .heightIn(max = 220.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(TerminalPanelBg)
                    .padding(10.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "$TerminalPrompt run",
                    color = TerminalDimGreen,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    "exit code: ${out.exitCode ?: "—"}",
                    color = TerminalDimGreen,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall
                )
                if (out.stdout.isNotBlank()) {
                    Text(out.stdout, color = TerminalText, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
                if (out.stderr.isNotBlank()) {
                    Text(out.stderr, color = AccentRed, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        viewModel.pushMessage.value?.let {
            Text(
                "$TerminalPrompt $it",
                color = if (it.startsWith("Pushed")) TerminalGreen else AccentRed,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 6.dp)
            )
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
            .background(if (isSelected) TerminalDimGreen.copy(alpha = 0.35f) else TerminalPanelBg)
            .clickableChip(onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            "${runtime.language} ${runtime.version}",
            color = if (isSelected) TerminalGreen else TerminalText,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

private fun Modifier.clickableChip(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)

@Composable
private fun GitHubTokenDialog(
    hasToken: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onForget: () -> Unit
) {
    var token by remember { mutableStateOf("") }
    SubWindow(
        title = "GitHub Personal Access Token",
        onClose = onDismiss,
        confirmButton = { TextButton(onClick = { onSave(token) }, enabled = token.isNotBlank()) { Text("Save") } },
        dismissButton = {
            Row {
                if (hasToken) TextButton(onClick = onForget) { Text("Remove") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    ) {
        Column {
            Text(
                "Needs \"contents: write\" permission on the repos you'll push to. Stored encrypted on this device only.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    }
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

    // Note: SubWindow's own body is already wrapped in verticalScroll, so this
    // content doesn't need its own nested scroll container (a scroll-inside-
    // scroll is what caused inner content to feel "stuck"/unscrollable before).
    SubWindow(
        title = "Push to GitHub",
        onClose = onDismiss,
        confirmButton = {
            TextButton(
                enabled = !isPushing && owner.isNotBlank() && repo.isNotBlank() && path.isNotBlank(),
                onClick = { onConfirm(owner, repo, branch, path, message) }
            ) { Text("Push") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        Column {
            OutlinedTextField(value = owner, onValueChange = { owner = it }, label = { Text("Owner") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = repo, onValueChange = { repo = it }, label = { Text("Repository") }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            OutlinedTextField(value = branch, onValueChange = { branch = it }, label = { Text("Branch") }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            OutlinedTextField(value = path, onValueChange = { path = it }, label = { Text("File path (e.g. src/main.py)") }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            OutlinedTextField(value = message, onValueChange = { message = it }, label = { Text("Commit message") }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        }
    }
}
