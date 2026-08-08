package com.aidesktop.os.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Client for the public Piston code-execution API (https://github.com/engineer-man/piston),
 * the same engine used by several well-known online-compiler tools. This runs code for
 * real in Piston's own sandboxed containers and returns the actual stdout/stderr/exit
 * code — it is not a simulated or hand-written interpreter. No API key is required.
 */
data class PistonRuntime(
    val language: String,
    val version: String,
    val aliases: List<String> = emptyList()
)

data class PistonFile(val name: String? = null, val content: String)

data class PistonExecuteRequest(
    val language: String,
    val version: String,
    val files: List<PistonFile>,
    val stdin: String = "",
    val args: List<String> = emptyList()
)

data class PistonRunResult(
    val stdout: String,
    val stderr: String,
    val code: Int?,
    val signal: String?,
    val output: String
)

data class PistonCompileResult(
    val stdout: String,
    val stderr: String,
    val code: Int?,
    val signal: String?,
    val output: String
)

data class PistonExecuteResponse(
    val language: String,
    val version: String,
    val run: PistonRunResult,
    val compile: PistonCompileResult? = null
)

interface PistonApiService {
    @GET("runtimes")
    suspend fun listRuntimes(): List<PistonRuntime>

    @POST("execute")
    suspend fun execute(@Body request: PistonExecuteRequest): PistonExecuteResponse
}
