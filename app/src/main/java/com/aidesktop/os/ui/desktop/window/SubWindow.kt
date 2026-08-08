package com.aidesktop.os.ui.desktop.window

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.aidesktop.os.ui.theme.DesktopSurfaceElevated
import com.aidesktop.os.ui.theme.DividerColor
import com.aidesktop.os.ui.theme.TaskbarBg
import com.aidesktop.os.ui.theme.TextPrimary
import com.aidesktop.os.ui.theme.TextSecondary

/**
 * A nested "sub-window" — a small panel that opens *inside* the app window
 * that spawned it (e.g. "Add account" inside ID Vault, "Push to GitHub"
 * inside Termux) instead of a native AlertDialog.
 *
 * This is intentionally NOT a system Dialog. A real AlertDialog draws into
 * its own Android window on top of the ENTIRE app, escaping the parent
 * desktop window's rounded-corner clip/bounds — it looks like a totally
 * separate full-screen popup, doesn't scroll consistently, and visually
 * breaks the "window inside a window" illusion. SubWindow is a plain Box
 * placed by the caller inside that app's own content() composable, so it is
 * automatically clipped and constrained to exactly that parent window's
 * content area (below its title bar, inside its rounded corners) — it
 * physically cannot render outside the window it belongs to, and it always
 * has a real, working, scrollable body plus a close (exit) button that
 * actually removes it from composition when tapped.
 *
 * Call this as the LAST thing in a parent's content() Column/Box so it draws
 * on top of that window's own content but still underneath the window's own
 * title bar chrome (which the caller doesn't touch — that lives in
 * DesktopWindow, a sibling composable, not inside content()).
 */
@Composable
fun SubWindow(
    title: String,
    onClose: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(500f)
            .background(Color.Black.copy(alpha = 0.45f))
            // Swallow taps on the scrim instead of letting them fall through to
            // whatever is behind it — but deliberately does NOT dismiss on an
            // outside tap, so an accidental tap can't silently discard
            // half-typed input; the explicit close (X) button is the only way out.
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .shadow(10.dp, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(DesktopSurfaceElevated)
                .border(1.dp, DividerColor, RoundedCornerShape(10.dp))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .background(TaskbarBg)
                        .padding(horizontal = 10.dp)
                ) {
                    Text(
                        text = title,
                        color = TextPrimary,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onClose, modifier = Modifier.size(22.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // The actual scroll fix: body content lives inside a real
                // verticalScroll container, so a sub-window with more fields
                // or text than fit on screen can always be scrolled to reach
                // everything below the fold — it never gets stuck.
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp)
                ) {
                    content()
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}
