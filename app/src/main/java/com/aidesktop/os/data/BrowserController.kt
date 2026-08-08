package com.aidesktop.os.data

import android.webkit.WebView
import org.json.JSONObject
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.aidesktop.os.domain.model.BrowserTab
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for the Browser mini-app's open tabs. Moved out of
 * BrowserViewModel (which is scoped to the Browser window's own composable)
 * so the AI tool executor — running from the AI Chat window's ViewModel —
 * can navigate the exact same real WebView tabs the user sees, instead of a
 * separate/fake copy of browser state.
 *
 * Also holds a live reference to each tab's actual WebView (registered by
 * BrowserWindowContent when it creates one) so the AI can run real, scoped
 * automation scripts inside a page the user already sees on screen — e.g.
 * clicking WhatsApp Web's own search box, typing into its own compose box.
 * This never leaves this app's own WebView: no accessibility service, no
 * overlay, no interaction with any other real app on the phone.
 */
@Singleton
class BrowserController @Inject constructor() {

    val tabs = mutableStateListOf(BrowserTab())
    val activeTabId = mutableStateOf(tabs.first().id)

    private val webViews = mutableMapOf<String, WebView>()
    private val pendingCallbacks = mutableMapOf<String, CompletableDeferred<String>>()

    fun activeTab(): BrowserTab = tabs.firstOrNull { it.id == activeTabId.value } ?: tabs.first()

    fun newTab(url: String = "https://www.google.com"): BrowserTab {
        val tab = BrowserTab(url = url)
        tabs.add(tab)
        activeTabId.value = tab.id
        return tab
    }

    fun closeTab(id: String) {
        if (tabs.size == 1) return
        tabs.removeAll { it.id == id }
        webViews.remove(id)
        if (activeTabId.value == id) activeTabId.value = tabs.first().id
    }

    fun selectTab(id: String) {
        activeTabId.value = id
    }

    /** Navigates the active tab to [url] in place, reusing it rather than opening a new one. */
    fun navigateActiveTab(url: String) {
        activeTab().url = url
    }

    /** Finds an already-open tab whose URL is on [host], if any (so we reuse a logged-in session tab instead of opening a duplicate). */
    fun findTabOnHost(host: String): BrowserTab? = tabs.firstOrNull { it.url.contains(host, ignoreCase = true) }

    /**
     * Suspends until the WebViewClient has actually reported page-finished for [tabId]
     * landing on [host] (tab.url gets updated only in onPageLoaded), or times out.
     * Needed before running an automation script — evaluateJavascript against a page
     * that's still mid-navigation runs in a document that's about to be torn down.
     */
    suspend fun awaitHostLoaded(tabId: String, host: String, timeoutMs: Long = 15_000): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            val tab = tabs.firstOrNull { it.id == tabId }
            if (tab != null && tab.url.contains(host, ignoreCase = true) && webViews.containsKey(tabId)) {
                return true
            }
            kotlinx.coroutines.delay(250)
        }
        return false
    }

    // --- Live WebView registry, used only by BrowserWindowContent -----------------

    fun registerWebView(tabId: String, webView: WebView) {
        webViews[tabId] = webView
    }

    fun unregisterWebView(tabId: String) {
        webViews.remove(tabId)
    }

    fun isTabReady(tabId: String): Boolean = webViews.containsKey(tabId)

    /**
     * Runs [script] (a JS statement block, not a function) inside the real WebView
     * for [tabId]. The script MUST eventually call the injected `finish(obj)` helper
     * exactly once with a plain JS object describing what happened — this function
     * suspends until that happens (or times out) and returns the JSON string.
     * Never assume success: if `finish` is never called, this returns a timeout error.
     */
    suspend fun runAutomation(tabId: String, script: String, timeoutMs: Long = 20_000): String {
        val webView = webViews[tabId] ?: return """{"status":"error","reason":"tab_not_ready"}"""
        val callbackId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<String>()
        pendingCallbacks[callbackId] = deferred

        val callbackIdLiteral = JSONObject.quote(callbackId)
        val wrapped = """
            (function() {
                var __cbId = $callbackIdLiteral;
                function finish(obj) {
                    try {
                        window.AndroidAutomation.onResult(__cbId, JSON.stringify(obj));
                    } catch (bridgeErr) {}
                }
                try {
                    $script
                } catch (e) {
                    finish({status: "error", reason: "js_exception", message: String(e && e.message ? e.message : e)});
                }
            })();
        """.trimIndent()

        return try {
            withContext(Dispatchers.Main) {
                webView.evaluateJavascript(wrapped, null)
            }
            withTimeout(timeoutMs) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            """{"status":"error","reason":"timeout"}"""
        } finally {
            pendingCallbacks.remove(callbackId)
        }
    }

    /** Called by the JS bridge (AndroidAutomation.onResult) when a running script finishes. */
    fun onAutomationResult(callbackId: String, resultJson: String) {
        pendingCallbacks[callbackId]?.complete(resultJson)
    }
}
