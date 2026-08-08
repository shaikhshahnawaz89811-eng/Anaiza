package com.aidesktop.os.domain.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector

/** Identifies which mini-app a desktop window is hosting. */
enum class AppKind {
    BROWSER,
    AI_CHAT,
    PROJECTS,
    FILE_MANAGER,
    CODE_RUNNER,
    SETTINGS,
    ACCOUNTS
}

/**
 * Which visual state a window is currently in.
 * BUBBLE is only ever used by the AI Assistant window: instead of being
 * tucked into the taskbar like a normal minimize, it collapses to a small
 * floating draggable circle that stays on top of every other window, so it's
 * always one tap away no matter what else is open (see DesktopScreen's
 * AiChatBubble + DesktopController.minimize's AI_CHAT special-case).
 */
enum class WindowVisualState {
    NORMAL,
    MAXIMIZED,
    MINIMIZED,
    BUBBLE
}

/** Runtime state for a single desktop window. Lives in DesktopViewModel, not persisted. */
data class WindowState(
    val id: String,
    val kind: AppKind,
    val title: String,
    val icon: ImageVector,
    var position: Offset = Offset(80f, 80f),
    var size: Size = Size(420f, 300f),
    var visualState: WindowVisualState = WindowVisualState.NORMAL,
    var zIndex: Int = 0,
    // Remembers geometry from before maximizing, to restore on un-maximize
    var restorePosition: Offset? = null,
    var restoreSize: Size? = null,
    // Which half of a split-screen layout this window occupies, if any
    var splitSlot: SplitSlot? = null
)

/** Which quadrant/half of a split-screen layout a window occupies. LEFT/RIGHT
 *  split the desktop with a vertical divider in the middle; TOP/BOTTOM split
 *  it with a horizontal divider. */
enum class SplitSlot { LEFT, RIGHT, TOP, BOTTOM, NONE }

/** Orientation for a two-window split, chosen by whoever triggers the split. */
enum class SplitOrientation { VERTICAL, HORIZONTAL }
