package com.aidesktop.os.ui.settings

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aidesktop.os.data.local.dao.AccountDao
import com.aidesktop.os.data.local.entity.AccountEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface CredentialOpResult {
    object Idle : CredentialOpResult
    object Saved : CredentialOpResult
    data class Retrieved(val username: String, val password: String) : CredentialOpResult
    data class Error(val message: String) : CredentialOpResult
}

/**
 * "ID Vault" screen state. This app never stores a password itself: saving
 * an account hands the secret straight to androidx.credentials.CredentialManager,
 * which delegates to the platform's own credential provider (e.g. Google
 * Password Manager, or whichever provider the user has chosen). Only
 * non-secret metadata (label, site, username) is kept in this app's Room DB
 * so the list can be displayed.
 */
@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accountDao: AccountDao
) : ViewModel() {

    val accounts = accountDao.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val opResult = mutableStateOf<CredentialOpResult>(CredentialOpResult.Idle)
    val isBusy = mutableStateOf(false)

    /**
     * Saves [password] into the platform credential store for [siteDomain] via
     * Credential Manager, then records only the label/site/username locally.
     * Requires an Activity-hosting [context] because the system may show UI
     * (e.g. an account picker or a "save to Password Manager?" sheet).
     */
    fun addAccount(context: Context, label: String, siteDomain: String, username: String, password: String) {
        if (label.isBlank() || siteDomain.isBlank() || username.isBlank() || password.isBlank()) {
            opResult.value = CredentialOpResult.Error("All fields are required.")
            return
        }
        isBusy.value = true
        viewModelScope.launch {
            try {
                val credentialManager = CredentialManager.create(context)
                credentialManager.createCredential(
                    context = context,
                    request = CreatePasswordRequest(id = username, password = password)
                )
                accountDao.upsert(
                    AccountEntity(label = label.trim(), siteDomain = siteDomain.trim(), username = username.trim())
                )
                opResult.value = CredentialOpResult.Saved
            } catch (e: CreateCredentialException) {
                opResult.value = CredentialOpResult.Error(e.message ?: "Couldn't save this account to your password manager.")
            } finally {
                isBusy.value = false
            }
        }
    }

    /**
     * Asks Credential Manager for a previously-saved password credential so
     * the user can, e.g., copy it into a login form. Shows the system's own
     * credential picker — this app never sees or stores the result beyond
     * the current in-memory state, and it is cleared on [clearResult].
     */
    fun retrieveCredential(context: Context) {
        isBusy.value = true
        viewModelScope.launch {
            try {
                val credentialManager = CredentialManager.create(context)
                val request = GetCredentialRequest(credentialOptions = listOf(GetPasswordOption()))
                val response = credentialManager.getCredential(context = context, request = request)
                val credential = response.credential as? PasswordCredential
                if (credential != null) {
                    opResult.value = CredentialOpResult.Retrieved(credential.id, credential.password)
                } else {
                    opResult.value = CredentialOpResult.Error("No matching saved credential.")
                }
            } catch (e: GetCredentialException) {
                opResult.value = CredentialOpResult.Error(e.message ?: "No saved credential found.")
            } finally {
                isBusy.value = false
            }
        }
    }

    fun removeAccount(account: AccountEntity) {
        viewModelScope.launch { accountDao.delete(account) }
        // Note: this only removes the local label/username entry. The
        // underlying secret still lives in the platform credential store —
        // deleting it there is the credential provider's own UI, outside
        // this app's control, consistent with never touching stored secrets.
    }

    fun clearResult() {
        opResult.value = CredentialOpResult.Idle
    }
}
