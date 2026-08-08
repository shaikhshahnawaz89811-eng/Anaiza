package com.aidesktop.os.ui.aichat

import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aidesktop.os.ui.theme.AccentBlue
import com.aidesktop.os.ui.theme.DesktopSurfaceElevated
import com.aidesktop.os.ui.theme.DesktopSurfaceHigh
import com.aidesktop.os.ui.theme.TextPrimary
import com.aidesktop.os.ui.theme.TextSecondary

@Composable
fun AiChatWindowContent(viewModel: AiChatViewModel = hiltViewModel()) {
    Box(modifier = Modifier.fillMaxSize().background(DesktopSurfaceElevated)) {
        if (viewModel.needsApiKey.value) {
            ApiKeyPrompt(onSave = { viewModel.saveApiKey(it) })
        } else {
            ChatBody(viewModel)
        }
    }
}

@Composable
private fun ApiKeyPrompt(onSave: (String) -> Unit) {
    var key by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Connect your Groq API key", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
        Text(
            "Your key is stored encrypted on this device only, and is used only to call Groq on your behalf.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)
        )
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            placeholder = { Text("gsk_...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { onSave(key) },
            enabled = key.isNotBlank(),
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Text("Save & Continue")
        }
    }
}

@Composable
private fun ChatBody(viewModel: AiChatViewModel) {
    var input by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(viewModel.messages) { msg ->
                val isUser = msg.role == "user"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isUser) AccentBlue.copy(alpha = 0.25f) else DesktopSurfaceHigh)
                            .padding(10.dp)
                    ) {
                        Text(msg.content, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (viewModel.isSending.value) {
                item {
                    CircularProgressIndicator(modifier = Modifier.padding(8.dp), strokeWidth = 2.dp)
                }
            }
            viewModel.errorMessage.value?.let { err ->
                item {
                    Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Ask about your code, tasks, or project...") },
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                viewModel.send(input)
                input = ""
            }) {
                Icon(Icons.Filled.Send, contentDescription = "Send", tint = AccentBlue)
            }
        }
    }
}
