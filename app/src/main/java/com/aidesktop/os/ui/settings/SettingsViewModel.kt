package com.aidesktop.os.ui.settings

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aidesktop.os.BuildConfig
import com.aidesktop.os.data.local.SecureKeyStore
import com.aidesktop.os.data.local.dao.BrowserDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val keyStore: SecureKeyStore,
    private val browserDao: BrowserDao
) : ViewModel() {

    val hasApiKey = mutableStateOf(keyStore.hasApiKey())
    val appVersion: String = BuildConfig.VERSION_NAME
    val historyCleared = mutableStateOf(false)

    /** Masked preview only — the real key is never surfaced back to the UI. */
    fun maskedApiKey(): String {
        val key = keyStore.getGroqApiKey() ?: return "Not set"
        return if (key.length <= 8) "••••" else "${key.take(4)}••••${key.takeLast(4)}"
    }

    fun updateApiKey(newKey: String) {
        if (newKey.isBlank()) return
        keyStore.saveGroqApiKey(newKey.trim())
        hasApiKey.value = true
    }

    fun clearApiKey() {
        keyStore.clearGroqApiKey()
        hasApiKey.value = false
    }

    fun clearBrowsingHistory() {
        viewModelScope.launch {
            browserDao.clearHistory()
            historyCleared.value = true
        }
    }
}
