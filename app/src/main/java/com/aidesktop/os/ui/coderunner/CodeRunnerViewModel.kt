package com.aidesktop.os.ui.coderunner

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aidesktop.os.data.local.SecureKeyStore
import com.aidesktop.os.data.remote.PistonRuntime
import com.aidesktop.os.data.repository.CodeRunnerRepository
import com.aidesktop.os.data.repository.PushResult
import com.aidesktop.os.data.repository.RunResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RunOutput(val stdout: String, val stderr: String, val exitCode: Int?)

@HiltViewModel
class CodeRunnerViewModel @Inject constructor(
    private val repository: CodeRunnerRepository,
    private val keyStore: SecureKeyStore
) : ViewModel() {

    val code = mutableStateOf("")
    val stdin = mutableStateOf("")
    val runtimes = mutableStateOf<List<PistonRuntime>>(emptyList())
    val selectedRuntime = mutableStateOf<PistonRuntime?>(null)

    val isRunning = mutableStateOf(false)
    val output = mutableStateOf<RunOutput?>(null)
    val runError = mutableStateOf<String?>(null)

    val hasGitHubToken = mutableStateOf(keyStore.hasGitHubToken())
    val isPushing = mutableStateOf(false)
    val pushMessage = mutableStateOf<String?>(null)

    init {
        loadRuntimes()
    }

    private fun loadRuntimes() {
        viewModelScope.launch {
            val list = repository.listRuntimes()
                // one entry per language, the latest version each — keeps the picker short
                .groupBy { it.language }
                .map { (_, versions) -> versions.maxByOrNull { it.version } ?: versions.first() }
                .sortedBy { it.language }
            runtimes.value = list
            if (selectedRuntime.value == null) {
                selectedRuntime.value = list.firstOrNull { it.language == "python" } ?: list.firstOrNull()
            }
        }
    }

    fun selectRuntime(runtime: PistonRuntime) {
        selectedRuntime.value = runtime
    }

    fun run() {
        val runtime = selectedRuntime.value ?: return
        if (code.value.isBlank() || isRunning.value) return

        isRunning.value = true
        runError.value = null
        output.value = null

        viewModelScope.launch {
            when (val result = repository.run(runtime.language, runtime.version, code.value, stdin.value)) {
                is RunResult.Success -> {
                    val run = result.response.run
                    output.value = RunOutput(stdout = run.stdout, stderr = run.stderr, exitCode = run.code)
                }
                is RunResult.Failure -> runError.value = result.message
            }
            isRunning.value = false
        }
    }

    fun saveGitHubToken(token: String) {
        keyStore.saveGitHubToken(token.trim())
        hasGitHubToken.value = true
    }

    fun forgetGitHubToken() {
        keyStore.clearGitHubToken()
        hasGitHubToken.value = false
    }

    fun pushToGitHub(owner: String, repo: String, branch: String, path: String, commitMessage: String) {
        if (isPushing.value) return
        isPushing.value = true
        pushMessage.value = null

        viewModelScope.launch {
            when (val result = repository.pushToGitHub(owner, repo, branch, path, commitMessage, code.value)) {
                is PushResult.Success -> pushMessage.value = "Pushed. ${result.commitUrl ?: ""}".trim()
                is PushResult.Failure -> pushMessage.value = "Push failed: ${result.message}"
                PushResult.MissingToken -> pushMessage.value = "Add a GitHub token first."
            }
            isPushing.value = false
        }
    }

    fun clearPushMessage() {
        pushMessage.value = null
    }
}
