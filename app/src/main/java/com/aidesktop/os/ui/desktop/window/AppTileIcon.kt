package com.aidesktop.os.ui.desktop.window

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aidesktop.os.domain.model.AppKind

/**
 * Distinct accent color per mini-app, so every window is instantly
 * recognizable by its icon tile — like a real Windows/desktop app icon —
 * instead of every app sharing one flat monochrome tint. These are original
 * colors, not copies of any third-party product's branding.
 */
fun AppKind.tileColor(): Color = when (this) {
    AppKind.BROWSER -> Color(0xFF3E8BFF)
    AppKind.AI_CHAT -> Color(0xFF8B5CF6)
    AppKind.PROJECTS -> Color(0xFF16B8A6)
    AppKind.FILE_MANAGER -> Color(0xFFF5A623)
    AppKind.CODE_RUNNER -> Color(0xFF2ECC71)
    AppKind.ACCOUNTS -> Color(0xFFE5484D)
    AppKind.SETTINGS -> Color(0xFF97A3BF)
}

/**
 * Renders [icon] as a colored rounded-square tile (subtle gradient + white
 * glyph), the same "app icon" language used for every mini-app across the
 * desktop icons grid, start menu, taskbar chips, and window title bars — so
 * a window always looks like it belongs to a real installed app, not a
 * generic monochrome line icon.
 */
@Composable
fun AppTileIcon(
    kind: AppKind,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    cornerRadius: Dp = size * 0.28f
) {
    val base = kind.tileColor()
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.linearGradient(
                    listOf(base, base.copy(alpha = 0.78f))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.6f)
        )
    }
}
