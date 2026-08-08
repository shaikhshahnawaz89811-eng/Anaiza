package com.aidesktop.os.ui.desktop.window

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aidesktop.os.domain.model.AppKind
import com.aidesktop.os.ui.theme.DesktopBackground
import com.aidesktop.os.ui.theme.DesktopSurface
import com.aidesktop.os.ui.theme.TextPrimary
import com.aidesktop.os.ui.theme.TextSecondary

/** Gradient wallpaper — swappable later for user-chosen images via Settings. */
@Composable
fun DesktopWallpaper(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(DesktopBackground, DesktopSurface, DesktopBackground)
            )
        )
    }
}

@Composable
fun DesktopIconsGrid(
    apps: List<Triple<AppKind, String, ImageVector>>,
    onIconDoubleClick: (AppKind) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(1),
        modifier = modifier
            .width(84.dp)
            .padding(top = 12.dp, start = 8.dp)
    ) {
        items(apps) { (kind, name, icon) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .clickable { onIconDoubleClick(kind) }
            ) {
                Icon(icon, contentDescription = name, tint = TextPrimary, modifier = Modifier.size(28.dp))
                Text(
                    text = name,
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
