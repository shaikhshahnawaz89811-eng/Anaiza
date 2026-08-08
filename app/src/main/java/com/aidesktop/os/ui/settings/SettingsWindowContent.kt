package com.aidesktop.os.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.aidesktop.os.ui.theme.DesktopSurfaceElevated
import com.aidesktop.os.ui.theme.DesktopSurfaceHigh
import com.aidesktop.os.ui.theme.DividerColor
import com.aidesktop.os.ui.theme.TextPrimary
import com.aidesktop.os.ui.theme.TextSecondary

@Composable
fun SettingsWindowContent(viewModel: SettingsViewModel = hiltViewModel()) {
    var newKeyInput by remember { mutableStateOf("") }
    val hasApiKey by viewModel.hasApiKey

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DesktopSurfaceElevated)
            .padding(12.dp)
    ) {
        Text("Settings", color = TextPrimary, style = MaterialTheme.typography.titleLarge)

        SettingsSection(title = "AI Assistant") {
            Text(
                "Groq API key: ${viewModel.maskedApiKey()}",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedTextField(
                value = newKeyInput,
                onValueChange = { newKeyInput = it },
                label = { Text("New Groq API key") },
                placeholder = { Text("gsk_...") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Row(modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = {
                    viewModel.updateApiKey(newKeyInput)
                    newKeyInput = ""
                }) { Text("Save key") }
                OutlinedButton(
                    onClick = { viewModel.clearApiKey() },
                    modifier = Modifier.padding(start = 8.dp),
                    enabled = hasApiKey
                ) { Text("Remove key") }
            }
        }

        SettingsSection(title = "Browser") {
            Text(
                "Clears local browsing history stored by this app's browser (bookmarks are kept).",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            Button(
                onClick = { viewModel.clearBrowsingHistory() },
                modifier = Modifier.padding(top = 8.dp)
            ) { Text("Clear browsing history") }
            if (viewModel.historyCleared.value) {
                Text("History cleared.", color = AccentGreen, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
            }
        }

        SettingsSection(title = "About") {
            Text("AI Desktop OS v${viewModel.appVersion}", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            Text(
                "Everything runs inside this app. No accessibility service, no overlay window, " +
                    "no background automation, and no access to other apps' data.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(top = 14.dp)) {
        Text(title, color = AccentBlue, style = MaterialTheme.typography.titleSmall)
        Divider(color = DividerColor, modifier = Modifier.padding(vertical = 6.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(DesktopSurfaceHigh)
                .padding(10.dp)
        ) {
            content()
        }
    }
}
