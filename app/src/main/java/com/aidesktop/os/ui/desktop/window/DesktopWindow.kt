package com.aidesktop.os.ui.desktop.window

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.aidesktop.os.domain.model.WindowState
import com.aidesktop.os.domain.model.WindowVisualState
import com.aidesktop.os.ui.theme.AccentRed
import com.aidesktop.os.ui.theme.DividerColor
import com.aidesktop.os.ui.theme.TaskbarBg
import com.aidesktop.os.ui.theme.TextSecondary
import com.aidesktop.os.ui.theme.WindowChrome
import kotlin.math.roundToInt

private const val TITLE_BAR_HEIGHT_DP = 34

/**
 * Chrome (title bar + border + resize handle) wrapping arbitrary window content.
 * Drag on the title bar moves the window; drag on the corner handle resizes it.
 */
@Composable
fun DesktopWindow(
    window: WindowState,
    onFocus: () -> Unit,
    onMove: (Offset) -> Unit,
    onResize: (Offset) -> Unit,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onClose: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(window.size.width.dp, window.size.height.dp)
            .shadow(12.dp, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(WindowChrome)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Content area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = TITLE_BAR_HEIGHT_DP.dp)
                    .fillMaxSize()
            ) {
                content()
            }

            // Title bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TITLE_BAR_HEIGHT_DP.dp)
                    .background(TaskbarBg)
                    .pointerInput(window.id) {
                        detectDragGestures(
                            onDragStart = { onFocus() }
                        ) { change, dragAmount ->
                            change.consume()
                            onMove(dragAmount)
                        }
                    }
                    .padding(horizontal = 10.dp)
            ) {
                AppTileIcon(
                    kind = window.kind,
                    icon = window.icon,
                    size = 17.dp,
                    cornerRadius = 4.dp
                )
                Text(
                    text = window.title,
                    color = TextSecondary,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                )
                WindowControlButton(icon = Icons.Filled.Remove, onClick = onMinimize)
                WindowControlButton(icon = Icons.Filled.CropSquare, onClick = onToggleMaximize)
                WindowControlButton(icon = Icons.Filled.Close, onClick = onClose, dangerHover = true)
            }

            // Resize handle, bottom-right corner — only when not maximized
            if (window.visualState != WindowVisualState.MAXIMIZED) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(18.dp)
                        .pointerInput(window.id) {
                            detectDragGestures(onDragStart = { onFocus() }) { change, dragAmount ->
                                change.consume()
                                onResize(dragAmount)
                            }
                        }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Circle,
                        contentDescription = "Resize",
                        tint = DividerColor,
                        modifier = Modifier
                            .size(6.dp)
                            .align(Alignment.BottomEnd)
                            .padding(2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WindowControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    dangerHover: Boolean = false
) {
    IconButton(onClick = onClick, modifier = Modifier.size(26.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (dangerHover) AccentRed else TextSecondary,
            modifier = Modifier.size(13.dp)
        )
    }
}

/** Helper to place a window at its absolute position within a Box(desktop canvas). */
fun WindowState.toOffsetModifier(): Modifier = Modifier
