package com.aidesktop.os.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aidesktop.os.ui.theme.AccentBlue
import com.aidesktop.os.ui.theme.AccentGreen
import com.aidesktop.os.ui.theme.DesktopBackground
import com.aidesktop.os.ui.theme.DesktopSurface
import com.aidesktop.os.ui.theme.DesktopSurfaceElevated
import com.aidesktop.os.ui.theme.DividerColor
import com.aidesktop.os.ui.theme.TextPrimary
import com.aidesktop.os.ui.theme.TextSecondary

/**
 * The app's entry screen. Tapping the mini live-preview is the only way
 * into [com.aidesktop.os.ui.desktop.DesktopScreen] — nothing here draws
 * outside this Activity's own window.
 */
@Composable
fun HomeScreen(onOpenDesktop: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DesktopBackground)
            .padding(20.dp)
    ) {
        Text(
            "AI DESKTOP OS",
            color = TextPrimary,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Windows in your phone",
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Text(
            "Mini Live Screen",
            color = TextSecondary,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LiveDesktopPreview(onClick = onOpenDesktop)

        Text(
            "Tap the mini live screen to open the desktop",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}

/**
 * A small, non-interactive preview of the desktop surface. It mirrors the
 * real desktop's wallpaper, a few pinned icons, and a "LIVE" badge — tapping
 * it navigates to the actual interactive
 * [com.aidesktop.os.ui.desktop.DesktopScreen]; it does not embed a second
 * copy of it.
 */
@Composable
private fun LiveDesktopPreview(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.6f)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(DesktopBackground, DesktopSurface, DesktopBackground)))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        LiveBadge(modifier = Modifier.align(Alignment.TopEnd))

        Column(modifier = Modifier.align(Alignment.TopStart)) {
            MiniIcon(Icons.Filled.Language, "Browser")
            MiniIcon(Icons.Filled.Chat, "AI Chat")
            MiniIcon(Icons.Filled.Work, "Projects")
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
                .background(DesktopSurfaceElevated)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text("Tap to open desktop", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun LiveBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(DividerColor)
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Circle,
                contentDescription = null,
                tint = AccentGreen,
                modifier = Modifier.padding(end = 4.dp).size(8.dp)
            )
            Text("LIVE", color = TextPrimary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun MiniIcon(icon: ImageVector, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 6.dp)
    ) {
        Icon(icon, contentDescription = label, tint = AccentBlue, modifier = Modifier.padding(end = 4.dp).size(16.dp))
        Text(label, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
    }
}
