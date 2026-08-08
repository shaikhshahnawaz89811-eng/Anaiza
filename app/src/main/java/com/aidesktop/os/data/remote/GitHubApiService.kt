package com.aidesktop.os.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Client for GitHub's real REST API (https://docs.github.com/en/rest/repos/contents),
 * scoped to exactly what "test code, then push it" needs: reading a file's current
 * sha (so an update doesn't clobber history) and creating/updating a single file
 * via a commit. The user supplies their own Personal Access Token — this app never
 * has GitHub credentials of its own, and every push is a real commit the user can
 * see in their repository's history, not a simulated or queued action.
 */
data class GitHubFileContentResponse(
    val sha: String,
    val content: String? = null,
    val encoding: String? = null
)

data class GitHubCommitAuthor(val name: String, val email: String)

data class GitHubPutFileRequest(
    val message: String,
    val content: String, // Base64-encoded file content
    val sha: String? = null, // required when updating an existing file
    val branch: String? = null
)

data class GitHubPutFileResponse(
    val content: GitHubFileContentResponseSummary?,
    val commit: GitHubCommitSummary?
)

data class GitHubFileContentResponseSummary(val sha: String, val path: String, val html_url: String?)
data class GitHubCommitSummary(val sha: String, val html_url: String?)

interface GitHubApiService {
    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getFile(
        @Header("Authorization") bearerToken: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Query("ref") branch: String?
    ): Response<GitHubFileContentResponse>

    @PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun putFile(
        @Header("Authorization") bearerToken: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Body request: GitHubPutFileRequest
    ): GitHubPutFileResponse
}
