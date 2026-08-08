package com.aidesktop.os.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Work
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import com.aidesktop.os.domain.model.AppKind
import com.aidesktop.os.domain.model.SplitSlot
import com.aidesktop.os.domain.model.WindowState
import com.aidesktop.os.domain.model.WindowVisualState
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for the in-app desktop's window state. This used to
 * live directly inside DesktopViewModel, but the AI Assistant (a separate,
 * window-scoped ViewModel) now needs to open/close/arrange windows too, so
 * the state lives here as an app-wide singleton. Both DesktopViewModel (for
 * direct user taps/drags) and AiToolExecutor (for chat commands) read and
 * mutate the exact same windows list — there is no separate/fake copy of
 * state for the AI to "pretend" to control.
 *
 * Still fully in-process Compose state: no accessibility service, no
 * SYSTEM_ALERT_WINDOW overlay, no automation outside this app's own window.
 */
@Singleton
class DesktopController @Inject constructor() {

    val windows = mutableStateListOf<WindowState>()
    val isStartMenuOpen = mutableStateOf(false)
    val isNotificationCenterOpen = mutableStateOf(false)

    /** Updated by DesktopScreen on every measure, so AI-triggered splits use real on-screen bounds. */
    var lastKnownDesktopSize: Size = Size(1000f, 640f)

    private var zCounter = 0

    companion object {
        private const val MIN_WINDOW_WIDTH = 260f
        private const val MIN_WINDOW_HEIGHT = 200f
    }

    fun appDefinitions(): List<Triple<AppKind, String, ImageVector>> = listOf(
        Triple(AppKind.BROWSER, "Browser", Icons.Filled.Language),
        Triple(AppKind.AI_CHAT, "AI Assistant", Icons.Filled.Chat),
        Triple(AppKind.PROJECTS, "Projects", Icons.Filled.Work),
        Triple(AppKind.FILE_MANAGER, "Files", Icons.Filled.Folder),
        Triple(AppKind.CODE_RUNNER, "Termux", Icons.Filled.Code),
        Triple(AppKind.ACCOUNTS, "ID Vault", Icons.Filled.Key),
        Triple(AppKind.SETTINGS, "Settings", Icons.Filled.Settings)
    )

    fun openApp(kind: AppKind): WindowState {
        val existing = windows.firstOrNull { it.kind == kind }
        if (existing != null) {
            existing.visualState = WindowVisualState.NORMAL
            focus(existing.id)
            return existing
        }
        val def = appDefinitions().first { it.first == kind }
        val offsetStep = (windows.size % 6) * 28f
        // Cap the default spawn size to the real on-screen desktop bounds (the
        // desktop surface is a 56%-height windowed area, not the full phone
        // screen) so a freshly opened window can't spawn larger than the space
        // it has to live in.
        val bounds = lastKnownDesktopSize
        val spawnSize = Size(
            460f.coerceAtMost((bounds.width - 20f).coerceAtLeast(MIN_WINDOW_WIDTH)),
            340f.coerceAtMost((bounds.height - 20f).coerceAtLeast(MIN_WINDOW_HEIGHT))
        )
        val spawnPosition = clampPosition(Offset(60f + offsetStep, 60f + offsetStep), spawnSize, bounds)
        val newWindow = WindowState(
            id = UUID.randomUUID().toString(),
            kind = kind,
            title = def.second,
            icon = def.third,
            position = spawnPosition,
            size = spawnSize,
            zIndex = nextZ()
        )
        windows.add(newWindow)
        isStartMenuOpen.value = false
        return newWindow
    }

    fun close(id: String) {
        windows.removeAll { it.id == id }
    }

    fun closeByKind(kind: AppKind) {
        windows.removeAll { it.kind == kind }
    }

    fun focus(id: String) {
        val w = windows.firstOrNull { it.id == id } ?: return
        w.zIndex = nextZ()
    }

    fun minimize(id: String) {
        windows.firstOrNull { it.id == id }?.visualState = WindowVisualState.MINIMIZED
    }

    fun minimizeByKind(kind: AppKind) {
        windows.firstOrNull { it.kind == kind }?.visualState = WindowVisualState.MINIMIZED
    }

    fun restore(id: String) {
        val w = windows.firstOrNull { it.id == id } ?: return
        w.visualState = WindowVisualState.NORMAL
        focus(id)
    }

    fun restoreByKind(kind: AppKind) {
        val w = windows.firstOrNull { it.kind == kind } ?: return
        w.visualState = WindowVisualState.NORMAL
        focus(w.id)
    }

    fun toggleMaximize(id: String) {
        val w = windows.firstOrNull { it.id == id } ?: return
        if (w.visualState == WindowVisualState.MAXIMIZED) {
            w.position = w.restorePosition ?: w.position
            w.size = w.restoreSize ?: w.size
            w.visualState = WindowVisualState.NORMAL
        } else {
            w.restorePosition = w.position
            w.restoreSize = w.size
            w.visualState = WindowVisualState.MAXIMIZED
        }
        focus(id)
    }

    fun move(id: String, delta: Offset) {
        val w = windows.firstOrNull { it.id == id } ?: return
        if (w.visualState == WindowVisualState.MAXIMIZED) return
        // Keep the whole window (not just its top-left corner) inside the
        // real on-screen desktop bounds — dragging can no longer push a
        // window partly or fully outside the 56%-height desktop surface.
        w.position = clampPosition(w.position + delta, w.size, lastKnownDesktopSize)
    }

    fun resize(id: String, delta: Offset, minWidth: Float = MIN_WINDOW_WIDTH, minHeight: Float = MIN_WINDOW_HEIGHT) {
        val w = windows.firstOrNull { it.id == id } ?: return
        if (w.visualState == WindowVisualState.MAXIMIZED) return
        val bounds = lastKnownDesktopSize
        // Resizing from the bottom-right handle can't push the window's
        // right/bottom edge past the desktop bounds either.
        val maxWidth = (bounds.width - w.position.x).coerceAtLeast(minWidth)
        val maxHeight = (bounds.height - w.position.y).coerceAtLeast(minHeight)
        val newWidth = (w.size.width + delta.x).coerceIn(minWidth, maxWidth)
        val newHeight = (w.size.height + delta.y).coerceIn(minHeight, maxHeight)
        w.size = Size(newWidth, newHeight)
    }

    /** Keeps a window's full rectangle (position + size) inside [bounds], top-left anchored at (0,0). */
    private fun clampPosition(position: Offset, size: Size, bounds: Size): Offset {
        val maxX = (bounds.width - size.width).coerceAtLeast(0f)
        val maxY = (bounds.height - size.height).coerceAtLeast(0f)
        return Offset(position.x.coerceIn(0f, maxX), position.y.coerceIn(0f, maxY))
    }

    /** Snap window to left or right half of the desktop (split screen). */
    fun snapToSplit(id: String, slot: SplitSlot, desktopWidth: Float, desktopHeight: Float) {
        val w = windows.firstOrNull { it.id == id } ?: return
        w.visualState = WindowVisualState.NORMAL
        w.splitSlot = slot
        val half = desktopWidth / 2f
        w.size = Size(half, desktopHeight)
        w.position = if (slot == SplitSlot.LEFT) Offset(0f, 0f) else Offset(half, 0f)
        focus(id)
    }

    fun clearSplit(id: String) {
        windows.firstOrNull { it.id == id }?.splitSlot = SplitSlot.NONE
    }

    /**
     * Opens [leftKind] and [rightKind] (if not already open) and snaps them
     * left/right using the last on-screen desktop size DesktopScreen reported.
     * Used by the AI tool executor, which has no direct layout measurement of
     * its own.
     */
    fun splitTwoApps(leftKind: AppKind, rightKind: AppKind) {
        val left = openApp(leftKind)
        val right = openApp(rightKind)
        snapToSplit(left.id, SplitSlot.LEFT, lastKnownDesktopSize.width, lastKnownDesktopSize.height)
        snapToSplit(right.id, SplitSlot.RIGHT, lastKnownDesktopSize.width, lastKnownDesktopSize.height)
    }

    fun toggleStartMenu() {
        isStartMenuOpen.value = !isStartMenuOpen.value
        if (isStartMenuOpen.value) isNotificationCenterOpen.value = false
    }

    fun toggleNotificationCenter() {
        isNotificationCenterOpen.value = !isNotificationCenterOpen.value
        if (isNotificationCenterOpen.value) isStartMenuOpen.value = false
    }

    private fun nextZ(): Int {
        zCounter += 1
        return zCounter
    }
}
