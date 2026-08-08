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
import com.aidesktop.os.domain.model.SplitOrientation
import com.aidesktop.os.domain.model.SplitSlot
import com.aidesktop.os.domain.model.WindowState
import com.aidesktop.os.domain.model.WindowVisualState
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    /**
     * Updated by DesktopScreen on every measure, so AI-triggered splits use real
     * on-screen bounds. Also the trigger point that positions the floating AI
     * bubble the first time we learn the *real* desktop size: [seedAiBubble] adds
     * the bubble window before Compose has ever measured anything (the default
     * Size(1000f, 640f) below is a guess), so if we placed the bubble right then
     * it could land outside the real bounds and render clipped/invisible — i.e.
     * "the AI ball disappeared". Instead the bubble's position stays null until
     * the first real measurement arrives here.
     */
    var lastKnownDesktopSize: Size = Size(1000f, 640f)
        set(value) {
            field = value
            if (bubblePosition.value == null && windows.any { it.visualState == WindowVisualState.BUBBLE }) {
                bubblePosition.value = Offset(
                    (value.width - BUBBLE_DIAMETER - 16f).coerceAtLeast(0f),
                    (value.height - BUBBLE_DIAMETER - 60f).coerceAtLeast(0f)
                )
            }
        }

    /**
     * Screen position of the floating AI-chat bubble (see WindowVisualState.BUBBLE).
     * Only meaningful while the AI_CHAT window is actually in the BUBBLE state;
     * DesktopScreen reads/drags this directly so the bubble stays put between
     * expand/collapse cycles instead of resetting to a default spot every time.
     */
    val bubblePosition = mutableStateOf<Offset?>(null)

    private var zCounter = 0

    // --- AI docked-panel state (top 20% strip that opens from the bubble) ---
    // Own scope, since this controller isn't a ViewModel: cancels/restarts
    // the idle-collapse Job as the AI goes busy/idle, never leaks past app
    // lifetime because it's a Singleton living exactly as long as the app.
    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var idleCollapseJob: Job? = null
    private val shiftedWindowIds = mutableSetOf<String>()
    private var panelShiftAmount = 0f

    /** True while the AI chat is showing as the top docked panel (not bubble, not a normal tile). */
    val isAiPanelOpen = mutableStateOf(false)

    companion object {
        private const val MIN_WINDOW_WIDTH = 260f
        private const val MIN_WINDOW_HEIGHT = 200f
        private const val BUBBLE_DIAMETER = 56f
        private const val AI_PANEL_HEIGHT_FRACTION = 0.20f
        private const val AI_IDLE_COLLAPSE_MS = 10_000L
        // Visible gap between two split-screened windows so they read as two
        // clearly separated panes with their own borders, not one solid block
        // glued together with just a thin divider line drawn on top of the seam.
        private const val SPLIT_GUTTER = 6f
        // Matches DesktopIconsGrid's fixed footprint (84dp icon column + 8dp
        // start inset — see DesktopSurfaceLayer.kt) plus a small visible gap,
        // so the first real window spawns fully to the right of the app icons
        // instead of spawning underneath/overlapping them.
        private const val ICON_DOCK_WIDTH = 96f
    }

    init {
        // The AI bubble is meant to always be one tap away, even before the
        // user has ever opened the AI Assistant themselves — so seed it as a
        // floating bubble from the moment the app starts, instead of only
        // appearing after the user manually opens AI Chat and something else
        // bubbles it. Position is left for lastKnownDesktopSize's setter to
        // fill in once the real on-screen size is known (see its doc comment).
        seedAiBubble()
    }

    private fun seedAiBubble() {
        val def = appDefinitions().first { it.first == AppKind.AI_CHAT }
        windows.add(
            WindowState(
                id = UUID.randomUUID().toString(),
                kind = AppKind.AI_CHAT,
                title = def.second,
                icon = def.third,
                position = Offset(60f, 60f),
                size = Size(460f, 340f),
                zIndex = nextZ(),
                visualState = WindowVisualState.BUBBLE
            )
        )
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
            if (existing.visualState == WindowVisualState.BUBBLE) {
                expandAiChatBubble()
            } else {
                existing.visualState = WindowVisualState.NORMAL
                focus(existing.id) // focus() itself bubbles any *other* open AI chat window
            }
            return existing
        }
        val def = appDefinitions().first { it.first == kind }
        val bounds = lastKnownDesktopSize
        // The very first REAL window opened on an otherwise-empty desktop
        // (the always-present AI bubble doesn't count as "real" here — see
        // the bubblePosition/BUBBLE check below) spawns to fill the entire
        // desktop area to the right of the icon dock, so it reads as "this
        // IS my screen" rather than a small tile buried among other windows,
        // and never overlaps/hides the app icons on the left. Every window
        // after the first keeps the original small cascading-tile size/
        // position so multiple windows don't just stack on top of each
        // other unreadably.
        val visibleWindowCount = windows.count { it.visualState != WindowVisualState.BUBBLE }
        val isFirstWindow = visibleWindowCount == 0
        val offsetStep = (visibleWindowCount % 6) * 28f
        val spawnSize = if (isFirstWindow) {
            Size(
                (bounds.width - ICON_DOCK_WIDTH).coerceAtLeast(MIN_WINDOW_WIDTH),
                bounds.height.coerceAtLeast(MIN_WINDOW_HEIGHT)
            )
        } else {
            // Cap the default spawn size to the real on-screen desktop bounds
            // (the desktop surface is a 56%-height windowed area, not the
            // full phone screen) so a freshly opened window can't spawn
            // larger than the space it has to live in.
            Size(
                460f.coerceAtMost((bounds.width - 20f).coerceAtLeast(MIN_WINDOW_WIDTH)),
                340f.coerceAtMost((bounds.height - 20f).coerceAtLeast(MIN_WINDOW_HEIGHT))
            )
        }
        val spawnPosition = if (isFirstWindow) {
            Offset(ICON_DOCK_WIDTH, 0f)
        } else {
            clampPosition(Offset(60f + offsetStep, 60f + offsetStep), spawnSize, bounds)
        }
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
        // Opening any other app while the AI chat overlay is up on screen
        // collapses the chat down to its floating bubble instead of leaving
        // it sitting behind (or fighting for space with) the new window.
        bubbleAiChatIfCovered(exceptKind = kind)
        return newWindow
    }

    /**
     * Collapses the AI_CHAT window to a floating bubble if it's currently
     * visible (NORMAL/MAXIMIZED) and some other app was just opened/focused.
     * No-op if AI_CHAT isn't open, is already a bubble/minimized, or if
     * [exceptKind] IS AI_CHAT (opening/focusing the chat itself must never
     * bubble it).
     */
    private fun bubbleAiChatIfCovered(exceptKind: AppKind) {
        if (exceptKind == AppKind.AI_CHAT) return
        val chat = windows.firstOrNull { it.kind == AppKind.AI_CHAT } ?: return
        if (chat.visualState == WindowVisualState.NORMAL || chat.visualState == WindowVisualState.MAXIMIZED) {
            // Opening/focusing a different app while the AI panel is docked
            // open must also undo the down-shift it applied to every other
            // window — otherwise those windows would stay stuck lower than
            // where the user left them.
            collapseAiPanelToBubble(chat)
        }
    }

    private fun bubbleWindow(w: WindowState) {
        if (bubblePosition.value == null) {
            val bounds = lastKnownDesktopSize
            bubblePosition.value = Offset(
                (bounds.width - BUBBLE_DIAMETER - 16f).coerceAtLeast(0f),
                (bounds.height - BUBBLE_DIAMETER - 60f).coerceAtLeast(0f)
            )
        }
        w.visualState = WindowVisualState.BUBBLE
    }

    /** Pushes every other currently-visible window down by [panelHeight] so the
     *  docked AI panel doesn't just sit on top of / hide what's underneath it.
     *  Remembers which windows it moved so [restoreShiftedWindows] can put
     *  them back exactly, and never double-shifts a window that's already
     *  been pushed down for this same panel session. */
    private fun shiftOtherWindowsDown(panelHeight: Float, bounds: Size) {
        panelShiftAmount = panelHeight
        windows.forEach { w ->
            if (w.kind == AppKind.AI_CHAT) return@forEach
            if (w.visualState != WindowVisualState.NORMAL && w.visualState != WindowVisualState.MAXIMIZED) return@forEach
            if (!shiftedWindowIds.add(w.id)) return@forEach
            val maxY = (bounds.height - w.size.height).coerceAtLeast(0f)
            w.position = Offset(w.position.x, (w.position.y + panelHeight).coerceAtMost(maxY))
        }
    }

    /** Undoes [shiftOtherWindowsDown] exactly, moving every window it touched
     *  back up by the same amount it was pushed down. */
    private fun restoreShiftedWindows() {
        val amount = panelShiftAmount
        if (amount > 0f) {
            windows.forEach { w ->
                if (shiftedWindowIds.contains(w.id)) {
                    w.position = Offset(w.position.x, (w.position.y - amount).coerceAtLeast(0f))
                }
            }
        }
        shiftedWindowIds.clear()
        panelShiftAmount = 0f
    }

    /** Collapses the docked AI panel back to the floating bubble: restores
     *  every window it had shifted down, clears the idle-collapse timer, and
     *  bubbles the chat window itself. Safe to call even if the panel isn't
     *  actually open right now. */
    private fun collapseAiPanelToBubble(chat: WindowState) {
        idleCollapseJob?.cancel()
        idleCollapseJob = null
        restoreShiftedWindows()
        isAiPanelOpen.value = false
        bubbleWindow(chat)
    }

    private fun scheduleIdleCollapse() {
        idleCollapseJob?.cancel()
        idleCollapseJob = controllerScope.launch {
            delay(AI_IDLE_COLLAPSE_MS)
            val chat = windows.firstOrNull { it.kind == AppKind.AI_CHAT } ?: return@launch
            collapseAiPanelToBubble(chat)
        }
    }

    /** Called by the AI chat screen right when it actually sends a message to
     *  Groq, so the auto-collapse timer doesn't fire mid-task while it's
     *  still working on what the user asked for. */
    fun notifyAiTaskStarted() {
        idleCollapseJob?.cancel()
        idleCollapseJob = null
    }

    /** Called by the AI chat screen once a response (success or failure) has
     *  come back. Starts the 10-second no-new-task countdown; if the user
     *  doesn't send another message before it fires, the panel auto-collapses
     *  back to the bubble and every shifted window returns to its exact
     *  original spot. */
    fun notifyAiTaskFinished() {
        if (isAiPanelOpen.value) scheduleIdleCollapse()
    }

    /** Drags the floating AI-chat bubble, clamped to stay fully inside the desktop bounds. */
    fun moveBubble(delta: Offset) {
        val bounds = lastKnownDesktopSize
        val current = bubblePosition.value ?: return
        val maxX = (bounds.width - BUBBLE_DIAMETER).coerceAtLeast(0f)
        val maxY = (bounds.height - BUBBLE_DIAMETER).coerceAtLeast(0f)
        bubblePosition.value = Offset(
            (current.x + delta.x).coerceIn(0f, maxX),
            (current.y + delta.y).coerceIn(0f, maxY)
        )
    }

    /**
     * Expands the AI-chat bubble into a docked panel across the TOP 20% of
     * the desktop — not a full overlay tile. Every other currently-visible
     * window is pushed down by that same 20% so the panel has its own space
     * instead of covering what's already open. Tapping the bubble is the
     * only way in; there is no other window fighting it for the same screen
     * space, since everything underneath moves out of the way.
     *
     * A 10-second no-new-task timer starts immediately: if the user doesn't
     * send the AI anything before it fires, [collapseAiPanelToBubble] runs
     * automatically and every shifted window returns to exactly where it was.
     */
    fun expandAiChatBubble() {
        val chat = windows.firstOrNull { it.kind == AppKind.AI_CHAT } ?: return
        val bounds = lastKnownDesktopSize
        val panelHeight = (bounds.height * AI_PANEL_HEIGHT_FRACTION).coerceAtLeast(MIN_WINDOW_HEIGHT * 0.5f)

        chat.size = Size(bounds.width, panelHeight)
        chat.position = Offset(0f, 0f)
        chat.visualState = WindowVisualState.NORMAL
        chat.splitSlot = SplitSlot.NONE
        focus(chat.id)

        shiftOtherWindowsDown(panelHeight, bounds)
        isAiPanelOpen.value = true
        scheduleIdleCollapse()
    }

    fun close(id: String) {
        val w = windows.firstOrNull { it.id == id }
        if (w != null && w.kind == AppKind.AI_CHAT && isAiPanelOpen.value) {
            // Closing the panel entirely (not just minimizing) must still
            // undo the down-shift on every other window, same as collapsing
            // to the bubble would — the window is about to disappear, but
            // the windows it pushed down are not.
            idleCollapseJob?.cancel()
            idleCollapseJob = null
            restoreShiftedWindows()
            isAiPanelOpen.value = false
        }
        windows.removeAll { it.id == id }
    }

    fun closeByKind(kind: AppKind) {
        windows.removeAll { it.kind == kind }
    }

    fun focus(id: String) {
        val w = windows.firstOrNull { it.id == id } ?: return
        w.zIndex = nextZ()
        // Bringing a different app forward should collapse an open AI chat
        // to its bubble, same as opening a brand-new app does.
        bubbleAiChatIfCovered(exceptKind = w.kind)
    }

    /** Minimizing the AI chat collapses it to its floating bubble instead of
     *  parking it in the taskbar like every other app — it's meant to always
     *  stay one tap away. Every other app keeps the normal taskbar minimize. */
    fun minimize(id: String) {
        val w = windows.firstOrNull { it.id == id } ?: return
        if (w.kind == AppKind.AI_CHAT) collapseAiPanelToBubble(w) else w.visualState = WindowVisualState.MINIMIZED
    }

    fun minimizeByKind(kind: AppKind) {
        val w = windows.firstOrNull { it.kind == kind } ?: return
        if (kind == AppKind.AI_CHAT) collapseAiPanelToBubble(w) else w.visualState = WindowVisualState.MINIMIZED
    }

    fun restore(id: String) {
        val w = windows.firstOrNull { it.id == id } ?: return
        if (w.kind == AppKind.AI_CHAT && w.visualState == WindowVisualState.BUBBLE) {
            expandAiChatBubble()
            return
        }
        w.visualState = WindowVisualState.NORMAL
        focus(id)
    }

    fun restoreByKind(kind: AppKind) {
        val w = windows.firstOrNull { it.kind == kind } ?: return
        restore(w.id)
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

    /**
     * Snap window to a quadrant/half of the desktop (split screen).
     * LEFT/RIGHT divide the desktop with a vertical line down the middle;
     * TOP/BOTTOM divide it with a horizontal line. Both windows in a split
     * always get exactly half the relevant dimension — no gap, no overlap —
     * so DesktopScreen's split-divider line always lands exactly on the seam.
     */
    fun snapToSplit(id: String, slot: SplitSlot, desktopWidth: Float, desktopHeight: Float) {
        val w = windows.firstOrNull { it.id == id } ?: return
        w.visualState = WindowVisualState.NORMAL
        w.splitSlot = slot
        val halfW = desktopWidth / 2f
        val halfH = desktopHeight / 2f
        val half = SPLIT_GUTTER / 2f
        when (slot) {
            SplitSlot.LEFT -> {
                w.size = Size((halfW - half).coerceAtLeast(MIN_WINDOW_WIDTH), desktopHeight)
                w.position = Offset(0f, 0f)
            }
            SplitSlot.RIGHT -> {
                w.size = Size((halfW - half).coerceAtLeast(MIN_WINDOW_WIDTH), desktopHeight)
                w.position = Offset(halfW + half, 0f)
            }
            SplitSlot.TOP -> {
                w.size = Size(desktopWidth, (halfH - half).coerceAtLeast(MIN_WINDOW_HEIGHT))
                w.position = Offset(0f, 0f)
            }
            SplitSlot.BOTTOM -> {
                w.size = Size(desktopWidth, (halfH - half).coerceAtLeast(MIN_WINDOW_HEIGHT))
                w.position = Offset(0f, halfH + half)
            }
            SplitSlot.NONE -> { /* no-op */ }
        }
        focus(id)
    }

    fun clearSplit(id: String) {
        windows.firstOrNull { it.id == id }?.splitSlot = SplitSlot.NONE
    }

    /**
     * Opens [firstKind] and [secondKind] (if not already open) and snaps them
     * into a 50/50 split using the last on-screen desktop size DesktopScreen
     * reported. Used by the AI tool executor, which has no direct layout
     * measurement of its own. Defaults to a left/right (vertical divider)
     * split; pass HORIZONTAL for a top/bottom split instead.
     */
    fun splitTwoApps(
        firstKind: AppKind,
        secondKind: AppKind,
        orientation: SplitOrientation = SplitOrientation.VERTICAL
    ) {
        val first = openApp(firstKind)
        val second = openApp(secondKind)
        val (firstSlot, secondSlot) = if (orientation == SplitOrientation.VERTICAL) {
            SplitSlot.LEFT to SplitSlot.RIGHT
        } else {
            SplitSlot.TOP to SplitSlot.BOTTOM
        }
        snapToSplit(first.id, firstSlot, lastKnownDesktopSize.width, lastKnownDesktopSize.height)
        snapToSplit(second.id, secondSlot, lastKnownDesktopSize.width, lastKnownDesktopSize.height)
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
