package com.aidesktop.os.ui.browser


import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.DisposableEffect
import com.aidesktop.os.data.BrowserController
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.key
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.aidesktop.os.ui.theme.AccentBlue
import com.aidesktop.os.ui.theme.DesktopSurface
import com.aidesktop.os.ui.theme.DesktopSurfaceElevated
import com.aidesktop.os.ui.theme.DesktopSurfaceHigh
import com.aidesktop.os.ui.theme.TaskbarBg
import com.aidesktop.os.ui.theme.TextPrimary
import com.aidesktop.os.ui.theme.TextSecondary

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
    "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

@Composable
fun BrowserWindowContent(viewModel: BrowserViewModel = hiltViewModel()) {
    Column(modifier = Modifier.fillMaxSize().background(DesktopSurfaceElevated)) {
        TabBar(viewModel)
        AddressBar(viewModel)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                viewModel.showBookmarksPanel.value -> BookmarksPanel(viewModel)
                viewModel.showHistoryPanel.value -> HistoryPanel(viewModel)
                else -> WebViewContent(viewModel)
            }
        }
    }
}

@Composable
private fun TabBar(viewModel: BrowserViewModel) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(32.dp).background(TaskbarBg)
    ) {
        LazyRow(modifier = Modifier.weight(1f)) {
            items(viewModel.tabs) { tab ->
                val selected = tab.id == viewModel.activeTabId.value
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (selected) DesktopSurfaceHigh else TaskbarBg)
                        .clickable { viewModel.selectTab(tab.id) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = tab.title.take(14),
                        color = TextPrimary,
                        style = MaterialTheme.typography.labelSmall
                    )
                    IconButton(onClick = { viewModel.closeTab(tab.id) }, modifier = Modifier.height(18.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close tab", tint = TextSecondary, modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
        IconButton(onClick = { viewModel.newTab() }) {
            Icon(Icons.Filled.Add, contentDescription = "New tab", tint = TextSecondary, modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AddressBar(viewModel: BrowserViewModel) {
    var text by remember(viewModel.activeTabId.value) { mutableStateOf(viewModel.activeTab().url) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().background(DesktopSurface).padding(6.dp)
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.bodyMedium
        )
        IconButton(onClick = {
            val tab = viewModel.activeTab()
            tab.url = normalizeUrl(text)
        }) {
            Icon(Icons.Filled.BookmarkBorder, contentDescription = "Go", tint = AccentBlue)
        }
        IconButton(onClick = {
            val tab = viewModel.activeTab()
            viewModel.addBookmark(tab.title, tab.url)
        }) {
            Icon(Icons.Filled.Bookmark, contentDescription = "Bookmark", tint = TextSecondary)
        }
        IconButton(onClick = { viewModel.showHistoryPanel.value = !viewModel.showHistoryPanel.value }) {
            Icon(Icons.Filled.History, contentDescription = "History", tint = TextSecondary)
        }
    }
}

private fun normalizeUrl(input: String): String {
    val trimmed = input.trim()
    return when {
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
        else -> "https://www.google.com/search?q=${trimmed.replace(" ", "+")}"
    }
}

/**
 * The JS-side bridge object AI automation scripts call into (via
 * `window.AndroidAutomation.onResult(callbackId, jsonString)`) to report back
 * what actually happened on the real page — see BrowserController.runAutomation.
 * Read-only from JS's point of view: it can only deliver a result string, it
 * cannot ask Android to do anything else.
 */
private class AutomationBridge(private val controller: BrowserController, private val tabId: String) {
    @JavascriptInterface
    fun onResult(callbackId: String, resultJson: String) {
        controller.onAutomationResult(callbackId, resultJson)
    }
}

/**
 * Renders every open tab's WebView, not just the active one, and keeps all of
 * them alive underneath — only the active tab is visible/on-top/hit-testable.
 *
 * Why: there used to be exactly ONE AndroidView/WebView here, keyed off
 * whatever `viewModel.activeTab()` returned. Switching tabs (e.g. the AI
 * opening a WhatsApp tab while a YouTube tab was still mid-playback) did NOT
 * create a second WebView — it re-pointed that same single WebView at the new
 * tab's URL, `loadUrl`-ing away from whatever was live on the previous tab.
 * That's the exact bug behind "song ke liye browser khula phir dusra task bhi
 * usi me hone laga": with only one real WebView under the hood, two "tabs"
 * were never actually independent, no matter how separate they looked in the
 * tab bar. Each tab now gets its own persistent WebView the same way a
 * minimized window stays composed instead of being torn down (see
 * DesktopScreen) — so a song can keep playing in a background tab while a
 * different tab is used for something else.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebViewContent(viewModel: BrowserViewModel) {
    val activeTabId = viewModel.activeTabId.value
    Box(modifier = Modifier.fillMaxSize()) {
        viewModel.tabs.forEach { tab ->
            key(tab.id) {
                val isActive = tab.id == activeTabId
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(if (isActive) 1f else 0f)
                        // Inactive tabs are visually hidden and excluded from hit
                        // testing, but stay fully composed/alive underneath.
                        .then(if (isActive) Modifier else Modifier.alpha(0f))
                ) {
                    SingleTabWebView(viewModel = viewModel, tabId = tab.id, isActive = isActive)
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SingleTabWebView(viewModel: BrowserViewModel, tabId: String, isActive: Boolean) {
    val context = LocalContext.current
    val controller = viewModel.browserController
    val tab = viewModel.tabs.firstOrNull { it.id == tabId } ?: return

    // Fires only when this specific tab is actually closed (removed from the
    // tabs list) or the window itself is torn down — a background/inactive
    // tab switching visibility does NOT dispose it, so its automation/session
    // survives exactly like a minimized window's does.
    DisposableEffect(tabId) {
        onDispose { controller.unregisterWebView(tabId) }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        // Consumed by hit-testing: an inactive tab sits at alpha 0 behind the
        // active one, so it must not intercept touches meant for it.
        update = { webView ->
            webView.isClickable = isActive
            webView.isFocusable = isActive
            controller.registerWebView(tabId, webView)
            if (webView.url != tab.url) {
                webView.loadUrl(tab.url)
            }
        },
        factory = {
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                // Without this, Android WebView blocks video.play() unless it was
                // triggered by a real finger-tap — a synthetic JS click (what the
                // AI automation does) does not count, so YouTube would load a
                // video and sit paused on it forever. This is what let real
                // playback (and therefore the ad-skip watcher, which only ever
                // sees ads once a video is actually playing) actually start.
                settings.mediaPlaybackRequiresUserGesture = false
                // Force desktop layout/experience across all sites, as required
                settings.userAgentString = DESKTOP_USER_AGENT

                // Persist cookies/localStorage-backed sessions (WhatsApp Web, Gmail, GitHub, etc.)
                // across app restarts — logging in once should stay logged in.
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                addJavascriptInterface(AutomationBridge(controller, tabId), "AndroidAutomation")

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        viewModel.onPageLoaded(tabId, url, view.title ?: url)
                        CookieManager.getInstance().flush()
                    }
                }
                loadUrl(tab.url)
                controller.registerWebView(tabId, this)
            }
        }
    )
}

@Composable
private fun BookmarksPanel(viewModel: BrowserViewModel) {
    val bookmarks by viewModel.bookmarks.collectAsState()
    LazyColumn(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        items(bookmarks) { b ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.activeTab().url = b.url
                        viewModel.showBookmarksPanel.value = false
                    }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(b.title, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    Text(b.url, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = { viewModel.removeBookmark(b) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove", tint = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun HistoryPanel(viewModel: BrowserViewModel) {
    val history by viewModel.history.collectAsState()
    Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("History", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { viewModel.clearHistory() }) {
                Icon(Icons.Filled.Close, contentDescription = "Clear history", tint = TextSecondary)
            }
        }
        LazyColumn {
            items(history) { h ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.activeTab().url = h.url
                            viewModel.showHistoryPanel.value = false
                        }
                        .padding(vertical = 6.dp)
                ) {
                    Text(h.title, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    Text(h.url, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
