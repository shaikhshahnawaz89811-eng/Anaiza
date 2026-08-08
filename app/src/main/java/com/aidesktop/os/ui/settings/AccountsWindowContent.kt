package com.aidesktop.os.ui.settings

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aidesktop.os.ui.desktop.window.SubWindow
import com.aidesktop.os.ui.theme.AccentBlue
import com.aidesktop.os.ui.theme.AccentRed
import com.aidesktop.os.ui.theme.DesktopSurfaceElevated
import com.aidesktop.os.ui.theme.DesktopSurfaceHigh
import com.aidesktop.os.ui.theme.TextPrimary
import com.aidesktop.os.ui.theme.TextSecondary

/**
 * "ID Vault" — a labeled list of accounts the user has added. Secrets are
 * never rendered or held by this screen: adding an account writes the
 * password straight to Android Credential Manager, and "Reveal" opens the
 * system credential picker rather than showing anything stored in this app.
 */
@Composable
fun AccountsWindowContent(viewModel: AccountsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val accounts by viewModel.accounts.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    // Outer Box so the SubWindow overlays (added below) can stack on TOP of
    // the normal content instead of pushing it aside like another Column
    // child would — while still being confined to this same fillMaxSize
    // area, i.e. inside this window and nowhere else.
    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().background(DesktopSurfaceElevated).padding(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("ID Vault", color = TextPrimary, style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add account", tint = AccentBlue)
            }
        }
        Text(
            "Passwords are stored by your device's password manager, never by this app.",
            color = TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(accounts) { account ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DesktopSurfaceHigh)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row {
                        Icon(Icons.Filled.Key, contentDescription = null, tint = AccentBlue, modifier = Modifier.padding(end = 8.dp))
                        Column {
                            Text(account.label, color = TextPrimary, style = MaterialTheme.typography.titleSmall)
                            Text(account.username, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    IconButton(onClick = { viewModel.removeAccount(account) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = AccentRed)
                    }
                }
            }
        }

        Button(
            onClick = { viewModel.retrieveCredential(context) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            enabled = !viewModel.isBusy.value
        ) {
            Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
            Text("Reveal a saved credential")
        }

    }

        when (val result = viewModel.opResult.value) {
            is CredentialOpResult.Retrieved -> SubWindow(
                title = "Saved credential",
                onClose = viewModel::clearResult,
                confirmButton = { TextButton(onClick = viewModel::clearResult) { Text("Close") } }
            ) {
                Text("Username: ${result.username}\nPassword: ${result.password}", color = TextPrimary)
            }
            is CredentialOpResult.Error -> SubWindow(
                title = "Couldn't complete that",
                onClose = viewModel::clearResult,
                confirmButton = { TextButton(onClick = viewModel::clearResult) { Text("OK") } }
            ) {
                Text(result.message, color = TextPrimary)
            }
            CredentialOpResult.Saved -> {
                showAddDialog = false
                viewModel.clearResult()
            }
            CredentialOpResult.Idle -> Unit
        }

        if (showAddDialog) {
            AddAccountDialog(
                isBusy = viewModel.isBusy.value,
                onDismiss = { showAddDialog = false },
                onConfirm = { label, site, username, password ->
                    viewModel.addAccount(context, label, site, username, password)
                }
            )
        }
    }
}

@Composable
private fun AddAccountDialog(
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (label: String, site: String, username: String, password: String) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var site by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    SubWindow(
        title = "Add account",
        onClose = onDismiss,
        confirmButton = {
            TextButton(enabled = !isBusy, onClick = { onConfirm(label, site, username, password) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        Column {
            OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Label (e.g. GitHub)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = site, onValueChange = { site = it }, label = { Text("Site (e.g. github.com)") }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username or email") }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            )
        }
    }
}
