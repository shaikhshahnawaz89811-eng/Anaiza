package com.aidesktop.os.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aidesktop.os.ui.theme.AccentBlue
import com.aidesktop.os.ui.theme.DesktopSurfaceElevated
import com.aidesktop.os.ui.theme.TextPrimary
import com.aidesktop.os.ui.theme.TextSecondary

/**
 * File Manager backed entirely by the Storage Access Framework: the user
 * explicitly picks each file or folder through the system picker. This app
 * is never granted broad filesystem access.
 */
@Composable
fun FileManagerWindowContent() {
    val context = LocalContext.current
    val openedItems = remember { mutableStateListOf<Uri>() }

    val pickFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            openedItems.add(it)
        }
    }
    val pickFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { openedItems.add(it) }
    }

    Column(modifier = Modifier.fillMaxSize().background(DesktopSurfaceElevated).padding(12.dp)) {
        Text("Files", color = TextPrimary, style = MaterialTheme.typography.titleLarge)
        Row(modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)) {
            Button(onClick = { pickFileLauncher.launch(arrayOf("*/*")) }) {
                Text("Open File")
            }
            Button(onClick = { pickFolderLauncher.launch(null) }, modifier = Modifier.padding(start = 8.dp)) {
                Text("Open Folder")
            }
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(openedItems) { uri ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.InsertDriveFile, contentDescription = null, tint = AccentBlue, modifier = Modifier.padding(end = 8.dp))
                    Text(uri.lastPathSegment ?: uri.toString(), color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
