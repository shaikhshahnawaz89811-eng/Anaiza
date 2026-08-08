package com.aidesktop.os.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.aidesktop.os.domain.model.SplitSlot
import com.aidesktop.os.domain.model.WindowVisualState
import com.aidesktop.os.ui.desktop.window.DesktopIconsGrid
import com.aidesktop.os.ui.desktop.window.DesktopWallpaper
import com.aidesktop.os.ui.desktop.window.DesktopWindow
import com.aidesktop.os.ui.desktop.window.NotificationCenter
import com.aidesktop.os.ui.desktop.window.StartMenu
import com.aidesktop.os.ui.desktop.window.Taskbar
import com.aidesktop.os.ui.desktop.WindowContentRouter
import com.aidesktop.os.ui.theme.AccentBlue
import com.aidesktop.os.ui.theme.DesktopBackground
import com.aidesktop.os.ui.theme.DividerColor

/**
 * Full in-app "desktop" surface. Everything here is rendered inside this
 * Activity's own window — there is no SYSTEM_ALERT_WINDOW overlay and no
 * interaction with other apps' UI.
 *
 * The desktop is intentionally NOT full-screen: it's a windowed area that
 * starts below the status bar / camera cutout and occupies 56% of the
 * physical display height, matching the "mini live screen" preview on the
 * home screen. Everything below (icons, windows, taskbar, popovers) is laid
 * out and measured relative to this constrained box via BoxWithConstraints,
 * so nothing — drag, resize, maximize, split-screen — can render outside it.
 */
@Composable
fun DesktopScreen(
    onExitToHome: () -> Unit,
    viewModel: DesktopViewModel = hiltViewModel()
) {
    val windows = viewModel.windows

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DesktopBackground)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars) // start below the camera/status bar, not glued to it
                .fillMaxHeight(0.56f) // desktop surface occupies 56% of the display
                // A visible frame + hard clip on all four sides, so the desktop
                // surface reads as a real bounded screen (like a monitor bezel)
                // instead of content silently fading out at an invisible edge —
                // and nothing (a window, a drag) can ever visually spill past it.
                .border(1.dp, DividerColor)
                .clipToBounds()
        ) {
        val desktopWidthDp = maxWidth
        val desktopHeightDp = maxHeight - 46.dp // minus taskbar

        // Keep the controller's notion of "how big is the desktop" current, so
        // an AI-triggered split_screen command (which has no layout of its own)
        // snaps windows to the real on-screen bounds instead of a guess.
        //
        // NOTE: window.size is dp throughout the app (default window is
        // Size(460f, 340f), lastKnownDesktopSize defaults to Size(1000f, 640f))
        // so this must report dp values, not px — reporting px here made every
        // maximized window render ~density-factor too large and spill off
        // the physical screen.
        viewModel.onDesktopSizeMeasured(
            androidx.compose.ui.geometry.Size(
                desktopWidthDp.value,
                desktopHeightDp.value
            )
        )

        DesktopWallpaper()

        DesktopIconsGrid(
            apps = viewModel.appDefinitions(),
            onIconDoubleClick = { viewModel.openApp(it) },
            modifier = Modifier
                .align(Alignment.TopStart)
                .heightIn(max = desktopHeightDp)
        )

        // Open windows, in z-order.
        //
        // IMPORTANT: minimized windows are NOT removed from composition. If we
        // conditionally skip rendering them, their WebView (and any other live
        // state — a WhatsApp Web session, a playing YouTube tab, an in-progress
        // AI automation script waiting on a callback) gets torn down the moment
        // the user taps minimize, and reloads from scratch on restore. Instead,
        // a minimized window stays fully composed and alive, just moved far
        // off the visible canvas and excluded from hit-testing/z-order, so
        // whatever it was doing keeps running in the background exactly like
        // real desktop OS minimize behavior. Only onClose ever truly tears it
        // down (DesktopController.close removes it from `windows` entirely).
        // Vertical/horizontal divider line drawn exactly on the seam between
        // two split-screened windows, so a split reads as two clearly bordered
        // panes instead of two windows that merely happen to sit edge to edge.
        //
        // zIndex is computed relative to the split windows themselves (their
        // highest zIndex + a hair), NOT a fixed constant. A fixed constant
        // (the original approach) broke the moment any window's real zIndex
        // (from the normal focus/nextZ() counter) grew past it: the divider
        // would then render UNDER that window, or — worse — a fixed constant
        // high enough to always stay on top of every window would then also
        // render on top of the AI chat overlay when it's expanded over the
        // split windows, which is wrong (the chat is meant to cover them,
        // divider included, while it's open). Basing it on the split
        // windows' own zIndex keeps the divider exactly where it belongs:
        // above the two windows it separates, below anything opened after.
        windows.filter { it.splitSlot == SplitSlot.LEFT || it.splitSlot == SplitSlot.RIGHT || it.splitSlot == SplitSlot.TOP || it.splitSlot == SplitSlot.BOTTOM }
            .let { splitWindows ->
                val dividerZ = (splitWindows.maxOfOrNull { it.zIndex } ?: 0).toFloat() + 0.5f
                splitWindows.firstOrNull { it.splitSlot == SplitSlot.LEFT || it.splitSlot == SplitSlot.TOP }
                    ?.let { splitWindow ->
                        when (splitWindow.splitSlot) {
                            SplitSlot.LEFT -> Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .offset(x = desktopWidthDp / 2f - 1.dp)
                                    .height(desktopHeightDp)
                                    .width(2.dp)
                                    .zIndex(dividerZ)
                                    .background(DividerColor)
                            )
                            SplitSlot.TOP -> Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .offset(y = desktopHeightDp / 2f - 1.dp)
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .zIndex(dividerZ)
                                    .background(DividerColor)
                            )
                            else -> Unit
                        }
                    }
            }

        // AI-chat windows currently collapsed to a bubble render only as the
        // floating AiChatBubble further below — not as a tile here.
        val aiPanelOpen = viewModel.isAiPanelOpen.value
        // While the AI panel is docked open, it IS the AI_CHAT window itself
        // (full desktop width, pinned to the top) — so its own current height
        // is exactly how much room every OTHER window must leave underneath
        // it, including one that's currently maximized.
        val aiPanelHeightDp = if (aiPanelOpen) {
            windows.firstOrNull { it.kind == com.aidesktop.os.domain.model.AppKind.AI_CHAT }?.size?.height ?: 0f
        } else {
            0f
        }

        windows.filter { it.visualState != WindowVisualState.BUBBLE }.sortedBy { it.zIndex }.forEach { w ->
            val isMinimized = w.visualState == WindowVisualState.MINIMIZED
            // A maximized window must also leave room for the docked AI panel
            // above it — otherwise it would span the full desktop height and
            // sit right underneath the panel, defeating the "two separate
            // screens" split the panel is supposed to keep.
            val reserveForPanel = if (w.kind != com.aidesktop.os.domain.model.AppKind.AI_CHAT) aiPanelHeightDp else 0f

            val displaySize = when {
                isMinimized -> w.size // keep real size so the WebView doesn't get starved to 0px
                w.visualState == WindowVisualState.MAXIMIZED -> androidx.compose.ui.geometry.Size(
                    desktopWidthDp.value,
                    (desktopHeightDp.value - reserveForPanel).coerceAtLeast(0f)
                )
                else -> w.size
            }
            val displayPos = when {
                isMinimized -> Offset(-100000f, -100000f) // parked off-canvas, not visible, not tappable
                w.visualState == WindowVisualState.MAXIMIZED -> Offset(0f, reserveForPanel)
                else -> w.position
            }

            Box(
                modifier = Modifier
                    .offset(x = displayPos.x.dp, y = displayPos.y.dp)
                    .zIndex(if (isMinimized) -1f else w.zIndex.toFloat())
                    .then(if (isMinimized) Modifier.alpha(0f) else Modifier)
            ) {
                val effectiveWindow = w.copy(size = displaySize, position = displayPos)
                DesktopWindow(
                    window = effectiveWindow,
                    onFocus = { if (!isMinimized) viewModel.focus(w.id) },
                    onMove = { delta -> if (!isMinimized) viewModel.move(w.id, delta) },
                    onResize = { delta -> if (!isMinimized) viewModel.resize(w.id, delta) },
                    onMinimize = { viewModel.minimize(w.id) },
                    onToggleMaximize = { viewModel.toggleMaximize(w.id) },
                    onClose = { viewModel.close(w.id) }
                ) {
                    WindowContentRouter(kind = w.kind)
                }
            }
        }

        // Start menu popover
        if (viewModel.isStartMenuOpen.value) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 50.dp, start = 6.dp)
                    .zIndex(1000f)
            ) {
                StartMenu(apps = viewModel.appDefinitions(), onAppClick = { viewModel.openApp(it) })
            }
        }

        // Notification center popover
        if (viewModel.isNotificationCenterOpen.value) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 50.dp, end = 6.dp)
                    .zIndex(1000f)
            ) {
                NotificationCenter(notifications = emptyList())
            }
        }

        // Floating AI-chat bubble — always drawn last (highest zIndex) so it
        // stays on top of every other window/popover, exactly like a real
        // "chat head": draggable anywhere within the desktop bounds, a tap
        // (not a drag) expands the chat back into its overlay.
        val bubbleChat = windows.firstOrNull { it.kind == com.aidesktop.os.domain.model.AppKind.AI_CHAT && it.visualState == WindowVisualState.BUBBLE }
        if (bubbleChat != null) {
            val bubblePos = viewModel.bubblePosition.value ?: Offset.Zero
            Box(
                modifier = Modifier
                    .offset(x = bubblePos.x.dp, y = bubblePos.y.dp)
                    .zIndex(2000f)
                    .size(56.dp)
                    .shadow(10.dp, CircleShape)
                    .clip(CircleShape)
                    .background(AccentBlue)
                    .pointerInput(bubbleChat.id) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            viewModel.moveBubble(dragAmount)
                        }
                    }
                    .pointerInput(bubbleChat.id) {
                        detectTapGestures { viewModel.expandAiChatBubble() }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Chat,
                    contentDescription = "Open AI Assistant",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Taskbar pinned to bottom
        Box(modifier = Modifier.align(Alignment.BottomStart).zIndex(999f)) {
            Taskbar(
                windows = windows,
                onStartClick = { viewModel.toggleStartMenu() },
                onWindowClick = { id ->
                    val w = windows.first { it.id == id }
                    when (w.visualState) {
                        WindowVisualState.MINIMIZED -> viewModel.restore(id)
                        WindowVisualState.BUBBLE -> viewModel.expandAiChatBubble()
                        else -> viewModel.minimize(id)
                    }
                },
                onNotificationsClick = { viewModel.toggleNotificationCenter() }
            )
        }
        } // end inner BoxWithConstraints (56%-height desktop surface)
    }
}
