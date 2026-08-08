package com.aidesktop.os.ui.aichat

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val keyStore: SecureKeyStore
) : ViewModel() {

    val messages = mutableStateListOf<ChatUiMessage>()
    val isSending = mutableStateOf(false)
    val needsApiKey = mutableStateOf(!keyStore.hasApiKey())
    val errorMessage = mutableStateOf<String?>(null)

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
                }
                is GroqResult.Failure -> errorMessage.value = result.message
                GroqResult.MissingApiKey -> needsApiKey.value = true
            }
            isSending.value = false
        }
    }
}
