package com.aidesktop.os.ui.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.lifecycle.ViewModel
import com.aidesktop.os.data.DesktopController
import com.aidesktop.os.domain.model.AppKind
import com.aidesktop.os.domain.model.SplitSlot
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Thin per-screen wrapper around DesktopController, the app-wide singleton
 * that actually owns window state. Kept as a ViewModel so DesktopScreen can
 * still grab it via hiltViewModel() exactly as before; everything below
 * delegates straight through to the shared controller, so direct user
 * input (taps/drags here) and AI tool calls (routed to the same controller
 * from AiToolExecutor) always stay in sync — one real state, not two.
 */
@HiltViewModel
class DesktopViewModel @Inject constructor(
    private val controller: DesktopController
) : ViewModel() {

    val windows get() = controller.windows
    val isStartMenuOpen get() = controller.isStartMenuOpen
    val isNotificationCenterOpen get() = controller.isNotificationCenterOpen
    val bubblePosition get() = controller.bubblePosition
    /** True while the AI chat is docked open as the top panel (not bubble). */
    val isAiPanelOpen get() = controller.isAiPanelOpen

    fun moveBubble(delta: Offset) = controller.moveBubble(delta)
    fun expandAiChatBubble() = controller.expandAiChatBubble()

    fun appDefinitions() = controller.appDefinitions()

    fun openApp(kind: AppKind) {
        controller.openApp(kind)
    }

    fun close(id: String) = controller.close(id)
    fun focus(id: String) = controller.focus(id)
    fun minimize(id: String) = controller.minimize(id)
    fun restore(id: String) = controller.restore(id)
    fun toggleMaximize(id: String) = controller.toggleMaximize(id)
    fun move(id: String, delta: Offset) = controller.move(id, delta)

    fun resize(id: String, delta: Offset, minWidth: Float = 260f, minHeight: Float = 200f) =
        controller.resize(id, delta, minWidth, minHeight)

    fun snapToSplit(id: String, slot: SplitSlot, desktopWidth: Float, desktopHeight: Float) =
        controller.snapToSplit(id, slot, desktopWidth, desktopHeight)

    fun clearSplit(id: String) = controller.clearSplit(id)
    fun toggleStartMenu() = controller.toggleStartMenu()
    fun toggleNotificationCenter() = controller.toggleNotificationCenter()

    /** Reported by DesktopScreen on every measure, so AI-triggered split_screen uses real on-screen bounds. */
    fun onDesktopSizeMeasured(size: Size) {
        controller.lastKnownDesktopSize = size
    }
}
