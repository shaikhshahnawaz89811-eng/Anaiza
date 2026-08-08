package com.aidesktop.os.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the user's own API credentials that this app needs on-device to
 * call services on their behalf: the Groq key, and a GitHub Personal
 * Access Token used only to push code the user has tested in the Code
 * Runner mini app. Both are encrypted via Android's Keystore-backed
 * EncryptedSharedPreferences.
 *
 * This is NOT a general password vault — it never holds third-party site
 * login passwords (GitHub, Gmail, etc. login credentials). Those are
 * handled by AccountsWindowContent via Android Credential Manager /
 * the platform autofill service instead. A GitHub PAT is the user's own
 * scoped API credential, not a website login, which is why it lives here
 * alongside the Groq key rather than in Credential Manager.
 */
@Singleton
class SecureKeyStore @Inject constructor(@ApplicationContext context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "ai_desktop_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveGroqApiKey(key: String) {
        prefs.edit().putString(KEY_GROQ_API_KEY, key).apply()
    }

    fun getGroqApiKey(): String? = prefs.getString(KEY_GROQ_API_KEY, null)

    fun clearGroqApiKey() {
        prefs.edit().remove(KEY_GROQ_API_KEY).apply()
    }

    fun hasApiKey(): Boolean = !getGroqApiKey().isNullOrBlank()

    fun saveGitHubToken(token: String) {
        prefs.edit().putString(KEY_GITHUB_TOKEN, token).apply()
    }

    fun getGitHubToken(): String? = prefs.getString(KEY_GITHUB_TOKEN, null)

    fun clearGitHubToken() {
        prefs.edit().remove(KEY_GITHUB_TOKEN).apply()
    }

    fun hasGitHubToken(): Boolean = !getGitHubToken().isNullOrBlank()

    companion object {
        private const val KEY_GROQ_API_KEY = "groq_api_key"
        private const val KEY_GITHUB_TOKEN = "github_pat"
    }
}
