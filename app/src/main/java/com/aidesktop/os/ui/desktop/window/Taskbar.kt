package com.aidesktop.os.ui.desktop.window

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Battery1Bar
import androidx.compose.material.icons.filled.Battery3Bar
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.aidesktop.os.domain.model.WindowState
import com.aidesktop.os.domain.model.WindowVisualState
import com.aidesktop.os.ui.theme.AccentBlue
import com.aidesktop.os.ui.theme.AccentGreen
import com.aidesktop.os.ui.theme.AccentRed
import com.aidesktop.os.ui.theme.DividerColor
import com.aidesktop.os.ui.theme.TaskbarBg
import com.aidesktop.os.ui.theme.TextPrimary
import com.aidesktop.os.ui.theme.TextSecondary

@Composable
fun Taskbar(
    windows: List<WindowState>,
    onStartClick: () -> Unit,
    onWindowClick: (String) -> Unit,
    onNotificationsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(TaskbarBg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Start button
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .clickable { onStartClick() }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Apps, contentDescription = "Start", tint = AccentBlue, modifier = Modifier.size(20.dp))
        }

        // Open window chips
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            windows.forEach { w ->
                val active = w.visualState != WindowVisualState.MINIMIZED
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (active) DividerColor else TaskbarBg)
                        .clickable { onWindowClick(w.id) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(w.icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(13.dp))
                    Text(
                        text = w.title,
                        color = TextPrimary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        }

        // System tray — every icon/value here is read live from real Android
        // platform APIs (see SystemStatus.kt): actual network transport,
        // actual ringer mode, actual battery level/charging state, actual
        // clock. Nothing is a fixed "always full / always connected" icon.
        val status = rememberSystemStatus()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            val (networkIcon, networkTint) = when (status.network) {
                NetworkKind.WIFI -> Icons.Filled.Wifi to TextSecondary
                NetworkKind.CELLULAR -> Icons.Filled.SignalCellularAlt to TextSecondary
                NetworkKind.NONE -> Icons.Filled.SignalWifiOff to AccentRed
            }
            Icon(
                networkIcon,
                contentDescription = status.network.name,
                tint = networkTint,
                modifier = Modifier.size(15.dp)
            )

            Icon(
                if (status.isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                contentDescription = if (status.isMuted) "Muted" else "Sound on",
                tint = TextSecondary,
                modifier = Modifier.size(15.dp).padding(start = 8.dp)
            )

            val batteryIcon = when {
                status.isCharging -> Icons.Filled.BatteryChargingFull
                status.batteryPct <= 15 -> Icons.Filled.BatteryAlert
                status.batteryPct <= 40 -> Icons.Filled.Battery1Bar
                status.batteryPct <= 75 -> Icons.Filled.Battery3Bar
                status.batteryPct <= 95 -> Icons.Filled.Battery5Bar
                else -> Icons.Filled.BatteryFull
            }
            val batteryTint = when {
                status.isCharging -> AccentGreen
                status.batteryPct <= 15 -> AccentRed
                else -> TextSecondary
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Icon(batteryIcon, contentDescription = "Battery ${status.batteryPct}%", tint = batteryTint, modifier = Modifier.size(15.dp))
                Text(
                    text = "${status.batteryPct}%",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 3.dp)
                )
            }

            Box(
                modifier = Modifier
                    .padding(start = 10.dp)
                    .clip(CircleShape)
                    .clickable { onNotificationsClick() }
                    .padding(4.dp)
            ) {
                Icon(Icons.Filled.Notifications, contentDescription = "Notifications", tint = TextSecondary, modifier = Modifier.size(15.dp))
            }

            Text(
                text = status.timeText,
                color = TextPrimary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 10.dp)
            )
            Text(
                text = status.dateText,
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}
