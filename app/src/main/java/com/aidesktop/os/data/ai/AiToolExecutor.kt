package com.aidesktop.os.data.ai

import com.aidesktop.os.data.BrowserController
import com.aidesktop.os.data.DesktopController
import com.aidesktop.os.data.local.entity.ProjectEntity
import com.aidesktop.os.data.repository.AiActivityRepository
import com.aidesktop.os.data.repository.ProjectRepository
import com.aidesktop.os.domain.model.AppKind
import com.google.gson.Gson
import org.json.JSONObject
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs a tool call the model asked for and returns a short, honest,
 * human-readable result string — fed straight back to the model as the real
 * "tool" message, and also shown to the user in chat. It never reports
 * success unless the underlying action actually happened. Executes against
 * the same DesktopController / BrowserController / ProjectRepository the
 * user's own taps use, so there is no separate fake state for the AI.
 */
@Singleton
class AiToolExecutor @Inject constructor(
    private val desktopController: DesktopController,
    private val browserController: BrowserController,
    private val projectRepository: ProjectRepository,
    private val activityRepository: AiActivityRepository
) {
    private val gson = Gson()

    suspend fun execute(toolName: String, argumentsJson: String): String = try {
        when (toolName) {
            "open_app" -> withApp(argumentsJson, "app") { app ->
                desktopController.openApp(app)
                "Opened ${app.displayName()}."
            }
            "close_app" -> withApp(argumentsJson, "app") { app ->
                desktopController.closeByKind(app)
                "Closed ${app.displayName()}."
            }
            "minimize_app" -> withApp(argumentsJson, "app") { app ->
                desktopController.minimizeByKind(app)
                "Minimized ${app.displayName()}."
            }
            "restore_app" -> withApp(argumentsJson, "app") { app ->
                desktopController.restoreByKind(app)
                "Restored ${app.displayName()}."
            }
            "browser_open_url" -> {
                val url = argField(argumentsJson, "url")
                if (url.isNullOrBlank()) return@try "No URL was given."
                openBrowserAndNavigate(normalizeUrl(url))
                "Opened Browser and navigated to $url."
            }
            "browser_search" -> {
                val query = argField(argumentsJson, "query")
                if (query.isNullOrBlank()) return@try "No search query was given."
                openBrowserAndNavigate("https://www.google.com/search?q=${encode(query)}")
                "Opened Browser and searched Google for \"$query\"."
            }
            "youtube_play" -> {
                val query = argField(argumentsJson, "query")
                if (query.isNullOrBlank()) return@try "No search query was given."
                playOnYouTube(query)
            }
            "youtube_preview_search" -> {
                val query = argField(argumentsJson, "query")
                if (query.isNullOrBlank()) return@try "No search query was given."
                previewYouTubeSearch(query)
            }
            "youtube_play_url" -> {
                val url = argField(argumentsJson, "url")
                val title = argField(argumentsJson, "title").orEmpty()
                if (url.isNullOrBlank()) return@try "No video URL was given."
                playYouTubeUrl(url, title)
            }
            "youtube_search" -> {
                val query = argField(argumentsJson, "query")
                if (query.isNullOrBlank()) return@try "No search query was given."
                openBrowserAndNavigate("https://www.youtube.com/results?search_query=${encode(query)}")
                "Opened Browser and searched YouTube for \"$query\". You'll need to tap a video to play it — " +
                    "I can't auto-play, auto-skip ads, or tell when a video is about to end."
            }
            "whatsapp_send_message" -> {
                val contactName = argField(argumentsJson, "contact_name")
                val message = argField(argumentsJson, "message")
                if (contactName.isNullOrBlank()) return@try "No contact name was given."
                if (message.isNullOrBlank()) return@try "No message text was given."
                sendWhatsAppMessage(contactName, message)
            }
            "split_screen" -> {
                val leftApp = parseAppKind(argField(argumentsJson, "left_app"))
                val rightApp = parseAppKind(argField(argumentsJson, "right_app"))
                if (leftApp == null || rightApp == null) return@try "Couldn't recognize one of those app names."
                desktopController.splitTwoApps(leftApp, rightApp)
                "Split the screen: ${leftApp.displayName()} on the left, ${rightApp.displayName()} on the right."
            }
            "create_project" -> {
                val name = argField(argumentsJson, "name")
                if (name.isNullOrBlank()) return@try "A project needs a name."
                projectRepository.saveProject(
                    ProjectEntity(
                        name = name,
                        description = argField(argumentsJson, "description").orEmpty(),
                        repositoryUrl = argField(argumentsJson, "repo_url").orEmpty(),
                        progressPercent = 0,
                        buildStatus = "Not built",
                        notes = ""
                    )
                )
                desktopController.openApp(AppKind.PROJECTS)
                "Created project \"$name\" and opened Projects."
            }
            "news_search" -> {
                val topic = argField(argumentsJson, "topic")
                if (topic.isNullOrBlank()) return@try "No news topic was given."
                searchNews(topic)
            }
            else -> "Unknown action: $toolName."
        }
    }
    } catch (e: Exception) {
        "Couldn't complete that action (${e.message ?: "unknown error"})."
    }

    private inline fun withApp(argumentsJson: String, field: String, block: (AppKind) -> String): String {
        val app = parseAppKind(argField(argumentsJson, field)) ?: return "Didn't recognize which app that was."
        return block(app)
    }

    private fun openBrowserAndNavigate(url: String) {
        desktopController.openApp(AppKind.BROWSER)
        browserController.navigateActiveTab(url)
    }

    /**
     * Opens (or reuses) the YouTube tab, waits for it to actually finish loading,
     * runs the real automation script inside that live page (search → click first
     * real result → confirm the video element exists → start the ad watcher), and
     * turns the honest JSON result back into a plain-language reply.
     */
    private suspend fun playOnYouTube(query: String): String {
        desktopController.openApp(AppKind.BROWSER)
        openBrowserAndNavigate("https://www.youtube.com/results?search_query=${encode(query)}")

        val tab = browserController.activeTab()
        val loaded = browserController.awaitHostLoaded(tab.id, "youtube.com")
        if (!loaded) {
            return "Couldn't get YouTube to load in time. Check the connection and try again."
        }

        val script = YouTubeAutomation.buildPlayScript(query)
        val resultJson = browserController.runAutomation(tab.id, script)

        return try {
            val result = JSONObject(resultJson)
            when (result.optString("status")) {
                "playing" -> {
                    val title = result.optString("title", query)
                    activityRepository.log("youtube_play", query, title)
                    "Playing \"$title\" on YouTube. Watching for ads and will skip them automatically."
                }
                "error" -> "Couldn't get a video playing for \"$query\" (${result.optString("reason", "unknown")})."
                else -> "Unexpected response starting playback for \"$query\"."
            }
        } catch (e: Exception) {
            "Couldn't confirm whether \"$query\" started playing on YouTube."
        }
    }

    /**
     * Real, no-click YouTube search used before playing a specifically-named
     * movie/show/anime — returns actual titles/channels/urls from the results
     * page so the model can ask the user to confirm the right one instead of
     * guessing via the first result.
     */
    private suspend fun previewYouTubeSearch(query: String): String {
        desktopController.openApp(AppKind.BROWSER)
        openBrowserAndNavigate("https://www.youtube.com/results?search_query=${encode(query)}")

        val tab = browserController.activeTab()
        val loaded = browserController.awaitHostLoaded(tab.id, "youtube.com")
        if (!loaded) {
            return "Couldn't get YouTube to load in time for \"$query\". Check the connection and try again."
        }

        val script = YouTubeAutomation.buildPreviewSearchScript(query)
        val resultJson = browserController.runAutomation(tab.id, script)

        return try {
            val result = JSONObject(resultJson)
            when (result.optString("status")) {
                "ok" -> {
                    val results = result.optJSONArray("results")
                    if (results == null || results.length() == 0) {
                        "No real results came back for \"$query\"."
                    } else {
                        val lines = buildList {
                            for (i in 0 until results.length()) {
                                val r = results.getJSONObject(i)
                                val title = r.optString("title")
                                val channel = r.optString("channel").ifBlank { "unknown channel" }
                                val url = r.optString("url")
                                if (title.isNotBlank() && url.isNotBlank()) add("${i + 1}. [$channel] $title — $url")
                            }
                        }
                        "Real YouTube search results for \"$query\" (show these to the user, ask which one they " +
                            "mean, then call youtube_play_url with the exact matching url — do not invent other titles):\n" +
                            lines.joinToString("\n")
                    }
                }
                "error" -> "Couldn't search YouTube for \"$query\" right now (${result.optString("reason", "unknown")})."
                else -> "Unexpected response searching YouTube for \"$query\"."
            }
        } catch (e: Exception) {
            "Couldn't confirm what came back from the YouTube search for \"$query\"."
        }
    }

    /**
     * Plays an exact, already-confirmed video URL (from youtube_preview_search).
     * Navigates from Kotlin first and waits for the real page load — same
     * pattern as every other automation here — rather than navigating inside
     * the injected script, so the confirm script never runs against a
     * mid-navigation/torn-down document.
     */
    private suspend fun playYouTubeUrl(url: String, knownTitle: String): String {
        desktopController.openApp(AppKind.BROWSER)
        openBrowserAndNavigate(url)

        val tab = browserController.activeTab()
        val loaded = browserController.awaitHostLoaded(tab.id, "youtube.com")
        if (!loaded) {
            return "Couldn't get that YouTube video to load in time. Check the connection and try again."
        }

        val script = YouTubeAutomation.buildPlayUrlScript(url)
        val resultJson = browserController.runAutomation(tab.id, script)

        return try {
            val result = JSONObject(resultJson)
            when (result.optString("status")) {
                "playing" -> {
                    val title = result.optString("title").ifBlank { knownTitle }.ifBlank { url }
                    activityRepository.log("youtube_play", knownTitle.ifBlank { title }, title)
                    "Playing \"$title\" on YouTube. Watching for ads and will skip them automatically."
                }
                "error" -> "Couldn't confirm that video started playing (${result.optString("reason", "unknown")})."
                else -> "Unexpected response starting playback for that video."
            }
        } catch (e: Exception) {
            "Couldn't confirm whether that video started playing on YouTube."
        }
    }

    private suspend fun sendWhatsAppMessage(contactName: String, message: String): String {
        desktopController.openApp(AppKind.BROWSER)

        val host = "web.whatsapp.com"
        val existing = browserController.findTabOnHost(host)
        val tab = existing ?: browserController.newTab("https://$host")
        browserController.selectTab(tab.id)
        if (existing == null) {
            // fresh tab: nudge it to load in case newTab() alone didn't trigger it yet
            browserController.navigateActiveTab("https://$host")
        }

        val loaded = browserController.awaitHostLoaded(tab.id, host)
        if (!loaded) {
            return "Couldn't get WhatsApp Web to load in time. Check the connection and try again."
        }

        val script = WhatsAppAutomation.buildSendMessageScript(contactName, message)
        val resultJson = browserController.runAutomation(tab.id, script)

        return try {
            val result = JSONObject(resultJson)
            when (result.optString("status")) {
                "sent" -> {
                    val matched = result.optString("matchedContact", contactName)
                    activityRepository.log("whatsapp_send_message", contactName, "To $matched: \"$message\"")
                    "Sent to $matched on WhatsApp: \"$message\""
                }
                "ambiguous" -> {
                    val matches = result.optJSONArray("matches")
                    val names = buildList {
                        if (matches != null) for (i in 0 until matches.length()) add(matches.getString(i))
                    }
                    "Found more than one chat matching \"$contactName\": ${names.joinToString(", ")}. " +
                        "Ask the user which exact one they mean, then call whatsapp_send_message again with that exact name."
                }
                "not_found" -> "No WhatsApp chat matching \"$contactName\" was found. Ask the user to check the name."
                "not_logged_in" -> "WhatsApp Web isn't logged in yet — the QR code is showing. Ask the user to scan it with their phone's WhatsApp first."
                "send_failed" -> "Opened the chat with ${result.optString("matchedContact", contactName)} but couldn't send — WhatsApp Web's layout may have changed. Nothing was sent."
                "error" -> "Something went wrong talking to WhatsApp Web (${result.optString("reason", "unknown")}). Nothing was sent."
                else -> "Unexpected response from WhatsApp Web automation. Nothing confirmed sent."
            }
        } catch (e: Exception) {
            "Couldn't confirm what happened on WhatsApp Web — treat the message as NOT sent."
        }
    }

    /**
     * Opens (or reuses) a Google News tab, waits for it to load, runs the real
     * scrape script, and turns the honestly-scraped articles into a plain-text
     * block the model is told (via news_search's tool description) to summarize
     * from — never inventing headlines the scrape didn't actually find.
     */
    private suspend fun searchNews(topic: String): String {
        desktopController.openApp(AppKind.BROWSER)
        openBrowserAndNavigate("https://news.google.com/search?q=${encode(topic)}&hl=en-IN")

        val tab = browserController.activeTab()
        val loaded = browserController.awaitHostLoaded(tab.id, "news.google.com")
        if (!loaded) {
            return "Couldn't get Google News to load in time for \"$topic\". Check the connection and try again."
        }

        val script = NewsAutomation.buildScrapeScript(topic)
        val resultJson = browserController.runAutomation(tab.id, script)

        return try {
            val result = JSONObject(resultJson)
            when (result.optString("status")) {
                "ok" -> {
                    val articles = result.optJSONArray("articles")
                    if (articles == null || articles.length() == 0) {
                        "No readable articles came back for \"$topic\" — nothing to summarize."
                    } else {
                        val lines = buildList {
                            for (i in 0 until articles.length()) {
                                val a = articles.getJSONObject(i)
                                val title = a.optString("title")
                                val source = a.optString("source").ifBlank { "unknown source" }
                                if (title.isNotBlank()) add("- [$source] $title")
                            }
                        }
                        activityRepository.log("news_search", topic, "${lines.size} articles found")
                        "Real headlines scraped just now from Google News for \"$topic\" (summarize only from " +
                            "these, in your own words, and name the sources):\n" + lines.joinToString("\n")
                    }
                }
                "error" -> "Couldn't fetch news for \"$topic\" right now (${result.optString("reason", "unknown")}). " +
                    "Tell the user plainly rather than guessing headlines."
                else -> "Unexpected response fetching news for \"$topic\"."
            }
        } catch (e: Exception) {
            "Couldn't confirm what came back from the news search for \"$topic\" — don't summarize anything for this."
        }
    }

    private fun parseAppKind(raw: String?): AppKind? =
        raw?.let { r -> AppKind.entries.firstOrNull { it.name.equals(r, ignoreCase = true) } }

    private fun argField(json: String, field: String): String? = try {
        @Suppress("UNCHECKED_CAST")
        val map = gson.fromJson(json, Map::class.java) as? Map<String, Any?>
        map?.get(field)?.toString()
    } catch (e: Exception) {
        null
    }

    private fun encode(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun normalizeUrl(input: String): String {
        val trimmed = input.trim()
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
            else -> "https://www.google.com/search?q=${encode(trimmed)}"
        }
    }

    private fun AppKind.displayName(): String = when (this) {
        AppKind.BROWSER -> "Browser"
        AppKind.AI_CHAT -> "AI Assistant"
        AppKind.PROJECTS -> "Projects"
        AppKind.FILE_MANAGER -> "File Manager"
        AppKind.CODE_RUNNER -> "Code Runner"
        AppKind.SETTINGS -> "Settings"
        AppKind.ACCOUNTS -> "Accounts"
    }
}
