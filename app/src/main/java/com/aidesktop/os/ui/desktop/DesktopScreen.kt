package com.aidesktop.os.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.aidesktop.os.domain.model.WindowVisualState
import com.aidesktop.os.ui.desktop.window.DesktopIconsGrid
import com.aidesktop.os.ui.desktop.window.DesktopWallpaper
import com.aidesktop.os.ui.desktop.window.DesktopWindow
import com.aidesktop.os.ui.desktop.window.NotificationCenter
import com.aidesktop.os.ui.desktop.window.StartMenu
import com.aidesktop.os.ui.desktop.window.Taskbar
import com.aidesktop.os.ui.desktop.WindowContentRouter
import com.aidesktop.os.ui.theme.DesktopBackground

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
            modifier = Modifier.align(Alignment.TopStart)
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
        windows.sortedBy { it.zIndex }.forEach { w ->
            val isMinimized = w.visualState == WindowVisualState.MINIMIZED

            val displaySize = when {
                isMinimized -> w.size // keep real size so the WebView doesn't get starved to 0px
                w.visualState == WindowVisualState.MAXIMIZED -> androidx.compose.ui.geometry.Size(
                    desktopWidthDp.value,
                    desktopHeightDp.value
                )
                else -> w.size
            }
            val displayPos = when {
                isMinimized -> Offset(-100000f, -100000f) // parked off-canvas, not visible, not tappable
                w.visualState == WindowVisualState.MAXIMIZED -> Offset.Zero
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

        // Taskbar pinned to bottom
        Box(modifier = Modifier.align(Alignment.BottomStart).zIndex(999f)) {
            Taskbar(
                windows = windows,
                onStartClick = { viewModel.toggleStartMenu() },
                onWindowClick = { id ->
                    val w = windows.first { it.id == id }
                    if (w.visualState == WindowVisualState.MINIMIZED) viewModel.restore(id) else viewModel.minimize(id)
                },
                onNotificationsClick = { viewModel.toggleNotificationCenter() }
            )
        }
        } // end inner BoxWithConstraints (56%-height desktop surface)
    }
}
