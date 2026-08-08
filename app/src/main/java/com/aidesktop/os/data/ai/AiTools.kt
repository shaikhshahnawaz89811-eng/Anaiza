package com.aidesktop.os.data.ai

import com.aidesktop.os.data.remote.GroqFunctionDefinition
import com.aidesktop.os.data.remote.GroqToolDefinition
import com.aidesktop.os.data.remote.JsonSchema
import com.aidesktop.os.data.remote.JsonSchemaProperty
import com.aidesktop.os.domain.model.AppKind

/**
 * The full, real list of actions the AI Assistant is allowed to take on the
 * in-app desktop. Every entry here has a matching branch in AiToolExecutor —
 * nothing is advertised to the model that isn't actually implemented, and
 * nothing here reaches outside this app's own window (no other real apps,
 * no accessibility service, no reading OTPs, no logging in anywhere on the
 * user's behalf — every action below runs only inside a real page already
 * loaded in this app's own Browser mini-app window, e.g. web.whatsapp.com,
 * the same way a user-script would, and never assumes success it can't verify).
 */
object AiTools {

    private val appKindNames = AppKind.entries.map { it.name.lowercase() }

    val definitions: List<GroqToolDefinition> = listOf(
        GroqToolDefinition(
            function = GroqFunctionDefinition(
                name = "open_app",
                description = "Opens (or focuses, if already open) a mini-app window on the in-app desktop.",
                parameters = JsonSchema(
                    properties = mapOf(
                        "app" to JsonSchemaProperty(
                            type = "string",
                            description = "Which mini-app to open.",
                            enum = appKindNames
                        )
                    ),
                    required = listOf("app")
                )
            )
        ),
        GroqToolDefinition(
            function = GroqFunctionDefinition(
                name = "close_app",
                description = "Closes a mini-app window if it is currently open.",
                parameters = JsonSchema(
                    properties = mapOf("app" to JsonSchemaProperty(type = "string", enum = appKindNames)),
                    required = listOf("app")
                )
            )
        ),
        GroqToolDefinition(
            function = GroqFunctionDefinition(
                name = "minimize_app",
                description = "Minimizes an open mini-app window down to the taskbar.",
                parameters = JsonSchema(
                    properties = mapOf("app" to JsonSchemaProperty(type = "string", enum = appKindNames)),
                    required = listOf("app")
                )
            )
        ),
        GroqToolDefinition(
            function = GroqFunctionDefinition(
                name = "restore_app",
                description = "Restores a minimized mini-app window and brings it to the front.",
                parameters = JsonSchema(
                    properties = mapOf("app" to JsonSchemaProperty(type = "string", enum = appKindNames)),
                    required = listOf("app")
                )
            )
        ),
        GroqToolDefinition(
            function = GroqFunctionDefinition(
                name = "browser_open_url",
                description = "Opens the Browser mini-app (if needed) and navigates its active tab to a specific URL.",
                parameters = JsonSchema(
                    properties = mapOf(
                        "url" to JsonSchemaProperty(type = "string", description = "Full URL, e.g. https://github.com")
                    ),
                    required = listOf("url")
                )
            )
        ),
        GroqToolDefinition(
            function = GroqFunctionDefinition(
                name = "browser_search",
                description = "Opens the Browser mini-app (if needed) and runs a real Google search for the given query.",
                parameters = JsonSchema(
                    properties = mapOf("query" to JsonSchemaProperty(type = "string")),
                    required = listOf("query")
                )
            )
        ),
        GroqToolDefinition(
            function = GroqFunctionDefinition(
                name = "youtube_play",
                description = "Opens the Browser mini-app (if needed), searches YouTube for the given query, " +
                    "clicks the first real (non-ad) result, and starts it playing — a real, watchable video, not " +
                    "just a search page. Also starts an ongoing watcher on that tab that automatically clicks " +
                    "whatever skip/close control appears on any ad, whenever it appears, for as long as the tab " +
                    "stays open, so ads don't need to be handled one at a time. Use this for a vague/mood-based " +
                    "request (e.g. 'sad songs', 'lofi music') where any good matching result is fine. Do NOT use " +
                    "this for a SPECIFIC named movie, show, or anime — use youtube_preview_search first for those, " +
                    "since the first search result for a title can easily be the wrong version/language/episode.",
                parameters = JsonSchema(
                    properties = mapOf(
                        "query" to JsonSchemaProperty(
                            type = "string",
                            description = "What to search for and play, e.g. 'sad songs' or a specific song/movie name."
                        )
                    ),
                    required = listOf("query")
                )
            )
        ),
        GroqToolDefinition(
            function = GroqFunctionDefinition(
                name = "youtube_preview_search",
                description = "Opens the Browser mini-app (if needed), runs a real YouTube search, and returns " +
                    "the top few REAL result titles, channel names, and URLs — without playing anything. Use this " +
                    "whenever the user names a SPECIFIC movie, show, anime, or episode (not just a mood/genre), " +
                    "since guessing the first result risks the wrong language dub, a fan-edit, a trailer instead " +
                    "of the film, or the wrong episode. After calling this, list 2-4 of the real returned titles " +
                    "for the user and ask which one they mean — do NOT invent titles beyond what was returned, " +
                    "and do NOT play anything yet. Once the user confirms one, call youtube_play_url with its " +
                    "exact url from the results.",
                parameters = JsonSchema(
                    properties = mapOf("query" to JsonSchemaProperty(type = "string")),
                    required = listOf("query")
                )
            )
        ),
        GroqToolDefinition(
            function = GroqFunctionDefinition(
                name = "youtube_play_url",
                description = "Plays a specific, already-known YouTube video URL directly (no search step) and " +
                    "starts the same automatic ad-skip watcher as youtube_play. Use this ONLY with a url that " +
                    "came from a youtube_preview_search result the user just confirmed — never with a url you " +
                    "guessed or remembered.",
                parameters = JsonSchema(
                    properties = mapOf(
                        "url" to JsonSchemaProperty(type = "string", description = "Exact video URL from a prior youtube_preview_search result."),
                        "title" to JsonSchemaProperty(type = "string", description = "The title of that result, for logging/confirmation.")
                    ),
                    required = listOf("url")
                )
            )
        ),
        GroqToolDefinition(
            function = GroqFunctionDefinition(
                name = "youtube_search",
                description = "Opens the Browser mini-app (if needed) and runs a real YouTube search for the given " +
                    "query, landing on the results page WITHOUT clicking anything. Use youtube_play instead " +
                    "whenever the user actually wants something to start playing — this tool is only for when " +
                    "they explicitly want to see/browse the results themselves.",
                parameters = JsonSchema(
                    properties = mapOf("query" to JsonSchemaProperty(type = "string")),
                    required = listOf("query")
                )
            )
        ),
        GroqToolDefinition(
            function = GroqFunctionDefinition(
                name = "split_screen",
                description = "Opens two mini-apps side by side, snapped left and right on the desktop.",
                parameters = JsonSchema(
                    properties = mapOf(
                        "left_app" to JsonSchemaProperty(type = "string", enum = appKindNames),
                        "right_app" to JsonSchemaProperty(type = "string", enum = appKindNames)
                    ),
                    required = listOf("left_app", "right_app")
                )
            )
        ),
        GroqToolDefinition(
            function = GroqFunctionDefinition(
                name = "whatsapp_send_message",
                description = "Opens WhatsApp Web in the Browser mini-app (reusing the tab if it's already open, " +
                    "so the existing login session is kept — never asks the user to log in again) and sends a real " +
                    "message to a contact by name, exactly as if the user typed it themselves. If the name matches " +
                    "more than one chat (e.g. 'Ammi' and 'Choti Ammi'), it does NOT guess — it reports back the " +
                    "exact matching names so you can ask the user which one they meant, then call this again with " +
                    "the exact name they confirm. If WhatsApp Web isn't logged in yet, it reports that instead of " +
                    "pretending to send.",
                parameters = JsonSchema(
                    properties = mapOf(
                        "contact_name" to JsonSchemaProperty(
                            type = "string",
                            description = "The exact contact/chat name as it appears in WhatsApp, e.g. 'Ammi'."
                        ),
                        "message" to JsonSchemaProperty(type = "string")
                    ),
                    required = listOf("contact_name", "message")
                )
            )
        ),
        GroqToolDefinition(
            function = GroqFunctionDefinition(
                name = "create_project",
                description = "Creates a new real entry in the Projects mini-app (stored locally) to track a dev project.",
                parameters = JsonSchema(
                    properties = mapOf(
                        "name" to JsonSchemaProperty(type = "string"),
                        "description" to JsonSchemaProperty(type = "string"),
                        "repo_url" to JsonSchemaProperty(type = "string")
                    ),
                    required = listOf("name")
                )
            )
        ),
        GroqToolDefinition(
            function = GroqFunctionDefinition(
                name = "news_search",
                description = "Opens the Browser mini-app (if needed), searches Google News for a topic, and " +
                    "scrapes the actual headline, source name, and timestamp from several real, distinct articles " +
                    "on the results page. Returns the real scraped articles as data — you must summarize ONLY " +
                    "from those returned articles, in your own words, and must NOT invent facts, quotes, or " +
                    "articles that were not in the returned list. If it reports an error, say plainly that the " +
                    "news couldn't be fetched right now rather than guessing at headlines.",
                parameters = JsonSchema(
                    properties = mapOf(
                        "topic" to JsonSchemaProperty(
                            type = "string",
                            description = "What to search news for, e.g. 'India cricket' or 'stock market today'."
                        )
                    ),
                    required = listOf("topic")
                )
            )
        )
    )
}
