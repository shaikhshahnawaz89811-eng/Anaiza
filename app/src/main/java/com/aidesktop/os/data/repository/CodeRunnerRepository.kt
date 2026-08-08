package com.aidesktop.os.data.repository

import android.util.Base64
import com.aidesktop.os.data.local.SecureKeyStore
import com.aidesktop.os.data.remote.GitHubApiService
import com.aidesktop.os.data.remote.GitHubPutFileRequest
import com.aidesktop.os.data.remote.PistonApiService
import com.aidesktop.os.data.remote.PistonExecuteRequest
import com.aidesktop.os.data.remote.PistonExecuteResponse
import com.aidesktop.os.data.remote.PistonFile
import com.aidesktop.os.data.remote.PistonRuntime
import javax.inject.Inject
import javax.inject.Singleton

sealed interface RunResult {
    data class Success(val response: PistonExecuteResponse) : RunResult
    data class Failure(val message: String) : RunResult
}

sealed interface PushResult {
    data class Success(val commitUrl: String?) : PushResult
    data class Failure(val message: String) : PushResult
    object MissingToken : PushResult
}

sealed interface ReadResult {
    data class Success(val content: String, val sha: String) : ReadResult
    data class Failure(val message: String) : ReadResult
    object MissingToken : ReadResult
    object NotFound : ReadResult
}

/**
 * Backs the Code Runner mini app. Running code calls the real Piston
 * execution API and returns its actual stdout/stderr/exit code — nothing
 * here fabricates output. Pushing calls the real GitHub Contents API using
 * the user's own token, so every push is a real, visible commit.
 */
@Singleton
class CodeRunnerRepository @Inject constructor(
    private val pistonApi: PistonApiService,
    private val gitHubApi: GitHubApiService,
    private val keyStore: SecureKeyStore
) {
    suspend fun listRuntimes(): List<PistonRuntime> =
        try {
            pistonApi.listRuntimes()
        } catch (e: Exception) {
            emptyList()
        }

    suspend fun run(language: String, version: String, code: String, stdin: String): RunResult =
        try {
            val response = pistonApi.execute(
                PistonExecuteRequest(
                    language = language,
                    version = version,
                    files = listOf(PistonFile(content = code)),
                    stdin = stdin
                )
            )
            RunResult.Success(response)
        } catch (e: Exception) {
            RunResult.Failure(e.message ?: "Couldn't reach the code execution service.")
        }

    /**
     * Creates or updates [path] in [owner]/[repo] on [branch] with [content],
     * via a real commit to GitHub using the user's own Personal Access Token.
     */
    suspend fun pushToGitHub(
        owner: String,
        repo: String,
        branch: String,
        path: String,
        commitMessage: String,
        content: String
    ): PushResult {
        val token = keyStore.getGitHubToken() ?: return PushResult.MissingToken
        val bearer = "Bearer $token"
        return try {
            // Look up the current sha, if the file already exists, so this is
            // treated as an update rather than accidentally overwriting history.
            val existingSha = try {
                val existing = gitHubApi.getFile(bearer, owner, repo, path, branch)
                if (existing.isSuccessful) existing.body()?.sha else null
            } catch (e: Exception) {
                null
            }

            val encoded = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val response = gitHubApi.putFile(
                bearerToken = bearer,
                owner = owner,
                repo = repo,
                path = path,
                request = GitHubPutFileRequest(
                    message = commitMessage.ifBlank { "Update $path from AI Desktop OS" },
                    content = encoded,
                    sha = existingSha,
                    branch = branch.ifBlank { null }
                )
            )
            PushResult.Success(response.commit?.html_url)
        } catch (e: Exception) {
            PushResult.Failure(e.message ?: "GitHub push failed.")
        }
    }
}
