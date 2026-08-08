package com.aidesktop.os.ui.aichat

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aidesktop.os.data.DesktopController
import com.aidesktop.os.data.local.SecureKeyStore
import com.aidesktop.os.data.remote.GroqChatMessage
import com.aidesktop.os.data.repository.GroqRepository
import com.aidesktop.os.data.repository.GroqResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiMessage(val role: String, val content: String)

@HiltViewModel
class AiChatViewModel @Inject constructor(
    private val repository: GroqRepository,
    private val keyStore: SecureKeyStore,
    private val desktopController: DesktopController
) : ViewModel() {

    val messages = mutableStateListOf<ChatUiMessage>()
    val isSending = mutableStateOf(false)
    val needsApiKey = mutableStateOf(!keyStore.hasApiKey())
    val errorMessage = mutableStateOf<String?>(null)

    /** Short line shown in the docked panel's status row (e.g. "Working on it…"
     *  while a request is in flight, or the last real action taken once it's
     *  back) — same real state the full chat view uses, just summarized. */
    val statusText = mutableStateOf<String?>(null)

    fun saveApiKey(key: String) {
        keyStore.saveGroqApiKey(key.trim())
        needsApiKey.value = false
    }

    fun forgetApiKey() {
        keyStore.clearGroqApiKey()
        needsApiKey.value = true
    }

    fun send(userText: String) {
        if (userText.isBlank() || isSending.value) return
        if (needsApiKey.value) return

        messages.add(ChatUiMessage("user", userText))
        isSending.value = true
        errorMessage.value = null
        statusText.value = "Working on it\u2026"
        // Tell the docked panel a real task just started, so its 10-second
        // no-task auto-collapse timer doesn't fire out from under a request
        // that's still in flight.
        desktopController.notifyAiTaskStarted()

        viewModelScope.launch {
            val history = messages.map { GroqChatMessage(it.role, it.content) }
            when (val result = repository.sendMessage(history)) {
                is GroqResult.Success -> {
                    // Show each real action the assistant actually took (e.g. "Opened
                    // Browser and searched YouTube for ...") before its final reply,
                    // so the user can see exactly what happened on the desktop.
                    result.actionsPerformed.forEach { action ->
                        messages.add(ChatUiMessage("assistant", "\u2713 $action"))
                    }
                    messages.add(ChatUiMessage("assistant", result.reply))
                    statusText.value = result.actionsPerformed.lastOrNull() ?: result.reply
                }
                is GroqResult.Failure -> {
                    errorMessage.value = result.message
                    statusText.value = "Something went wrong"
                }
                GroqResult.MissingApiKey -> needsApiKey.value = true
            }
            isSending.value = false
            // Response is back — start the real 10-second "no new task" clock
            // that auto-collapses the panel back to the bubble.
            desktopController.notifyAiTaskFinished()
        }
    }
}
