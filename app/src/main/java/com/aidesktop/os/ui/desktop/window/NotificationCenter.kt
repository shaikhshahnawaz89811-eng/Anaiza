package com.aidesktop.os.ui.desktop.window

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.aidesktop.os.ui.theme.DesktopSurfaceHigh
import com.aidesktop.os.ui.theme.TextPrimary
import com.aidesktop.os.ui.theme.TextSecondary

data class DesktopNotification(val title: String, val message: String)

@Composable
fun NotificationCenter(notifications: List<DesktopNotification>) {
    Column(
        modifier = Modifier
            .width(280.dp)
            .shadow(16.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(DesktopSurfaceHigh)
            .padding(14.dp)
    ) {
        Text("Notifications", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
        if (notifications.isEmpty()) {
            Text(
                "You're all caught up.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 10.dp)
            )
        } else {
            notifications.forEach { n ->
                Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Text(n.title, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    Text(n.message, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
