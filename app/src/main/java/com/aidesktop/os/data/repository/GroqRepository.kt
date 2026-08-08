package com.aidesktop.os.data.repository

import com.aidesktop.os.data.ai.AiToolExecutor
import com.aidesktop.os.data.ai.AiTools
import com.aidesktop.os.data.local.SecureKeyStore
import com.aidesktop.os.data.remote.GroqApiService
import com.aidesktop.os.data.remote.GroqChatMessage
import com.aidesktop.os.data.remote.GroqChatRequest
import javax.inject.Inject
import javax.inject.Singleton

sealed interface GroqResult {
    data class Success(val reply: String, val actionsPerformed: List<String> = emptyList()) : GroqResult
    data class Failure(val message: String) : GroqResult
    object MissingApiKey : GroqResult
}

/**
 * The assistant can now actually act on the in-app desktop: open/close/
 * arrange mini-app windows, and drive the real Browser (navigate a URL,
 * search Google or YouTube, etc.) via Groq's real tool-calling. Every action
 * it can request is listed in AiTools and carried out by AiToolExecutor
 * against the exact same DesktopController/BrowserController the user's own
 * taps use — there is no separate/fake execution path.
 *
 * It still cannot touch anything outside this app's own window: no
 * accessibility service, no typing into other real apps, no reading OTPs,
 * no logging into Gmail/GitHub/WhatsApp/etc on the user's behalf. The
 * system prompt below tells it to say so plainly rather than pretend.
 */
@Singleton
class GroqRepository @Inject constructor(
    private val api: GroqApiService,
    private val keyStore: SecureKeyStore,
    private val toolExecutor: AiToolExecutor,
    private val activityRepository: AiActivityRepository
) {
    private val baseSystemPrompt = """
        You are the AI Assistant inside AI Desktop OS, an Android app that renders
        a Windows-like desktop entirely inside this app's own window.

        You can control that in-app desktop directly using the tools you're given:
        opening, closing, minimizing/restoring and split-screening the mini-app
        windows (browser, ai_chat, projects, file_manager, code_runner, accounts,
        settings), and driving the real in-app Browser (open a URL, search Google,
        search YouTube). When the user asks for something like "play sad songs" or
        "open my GitHub project", use the tools to actually do it — open the
        Browser, search YouTube, etc — instead of just describing the steps. Keep
        your final reply short, since the actions you took are already shown to
        the user separately.

        For a SPECIFIC named movie, show, or anime (not a vague mood like "sad
        songs"), don't guess with youtube_play — call youtube_preview_search
        first, show the user 2-4 of the REAL titles it returns, ask which one
        they mean, and only then call youtube_play_url with the exact url they
        confirmed. Never invent a title or url that wasn't actually returned.

        If a "Real recent activity" section appears below, it is genuine local
        history — actual past actions this app already completed, not a guess
        about the user. You may use it to personalize a reply (e.g. suggesting
        something similar to what was played before) or to skip re-asking
        something already established, but never state it as a new fact you
        weren't told, and never fabricate history beyond what's listed there.

        Hard limits — say these plainly if relevant, never pretend otherwise:
        you cannot click or type inside a real video player, so you cannot
        auto-play a specific video, auto-skip ads, or detect when a song/video
        is about to end. You cannot type into, log in to, or read anything from
        Gmail, GitHub, WhatsApp, or any other real site or app on the user's
        behalf, and you cannot read OTPs. There is no accessibility service or
        device automation — everything you do is limited to this app's own
        in-app desktop and its real Browser tab.
    """.trimIndent()

    suspend fun sendMessage(history: List<GroqChatMessage>): GroqResult {
        val key = keyStore.getGroqApiKey() ?: return GroqResult.MissingApiKey
        val bearer = "Bearer $key"
        val actionsPerformed = mutableListOf<String>()

        val activitySummary = activityRepository.recentActivitySummary()
        val systemPrompt = if (activitySummary != null) {
            "$baseSystemPrompt\n\nReal recent activity (from this device's own local history):\n$activitySummary"
        } else {
            baseSystemPrompt
        }
        var messages = listOf(GroqChatMessage(role = "system", content = systemPrompt)) + history

        return try {
            repeat(MAX_TOOL_ROUNDS) {
                val response = api.chatCompletion(
                    bearerToken = bearer,
                    request = GroqChatRequest(
                        messages = messages,
                        tools = AiTools.definitions,
                        tool_choice = "auto"
                    )
                )
                val choice = response.choices.firstOrNull()
                    ?: return GroqResult.Failure("No response from the model.")
                val message = choice.message
                val toolCalls = message.tool_calls

                if (toolCalls.isNullOrEmpty()) {
                    val reply = message.content
                    return if (reply.isNullOrBlank()) {
                        GroqResult.Failure("Empty response from the model.")
                    } else {
                        GroqResult.Success(reply, actionsPerformed)
                    }
                }

                // Real OpenAI/Groq tool-calling protocol: keep the assistant
                // turn that requested the calls, then append one real "tool"
                // result message per call before asking the model to continue.
                messages = messages + message
                for (call in toolCalls) {
                    val resultText = toolExecutor.execute(call.function.name, call.function.arguments)
                    actionsPerformed.add(resultText)
                    messages = messages + GroqChatMessage(
                        role = "tool",
                        content = resultText,
                        tool_call_id = call.id,
                        name = call.function.name
                    )
                }
            }
            GroqResult.Failure("Took too many steps in a row for that one — try rephrasing.")
        } catch (e: Exception) {
            GroqResult.Failure(e.message ?: "Network error contacting Groq.")
        }
    }

    private companion object {
        const val MAX_TOOL_ROUNDS = 4
    }
}
