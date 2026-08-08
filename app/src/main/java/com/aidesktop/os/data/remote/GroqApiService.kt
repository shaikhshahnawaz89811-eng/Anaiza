package com.aidesktop.os.data.remote

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * role is "system" | "user" | "assistant" | "tool". content is null on an
 * assistant message that only carries tool_calls; tool_call_id/name are set
 * only on the "tool" role message sent back with a real action's result —
 * this mirrors the OpenAI-compatible tool-calling protocol Groq implements.
 */
data class GroqChatMessage(
    val role: String,
    val content: String? = null,
    val tool_calls: List<GroqToolCall>? = null,
    val tool_call_id: String? = null,
    val name: String? = null
)

data class GroqToolCall(
    val id: String,
    val type: String = "function",
    val function: GroqFunctionCall
)

/** [arguments] is a JSON-encoded string (per the tool-calling spec), not a nested object. */
data class GroqFunctionCall(
    val name: String,
    val arguments: String
)

data class GroqToolDefinition(
    val type: String = "function",
    val function: GroqFunctionDefinition
)

data class GroqFunctionDefinition(
    val name: String,
    val description: String,
    val parameters: JsonSchema
)

data class JsonSchema(
    val type: String = "object",
    val properties: Map<String, JsonSchemaProperty>,
    val required: List<String> = emptyList()
)

data class JsonSchemaProperty(
    val type: String,
    val description: String? = null,
    val enum: List<String>? = null
)

data class GroqChatRequest(
    val model: String = "llama-3.3-70b-versatile",
    val messages: List<GroqChatMessage>,
    val temperature: Double = 0.4,
    val max_tokens: Int = 1024,
    val tools: List<GroqToolDefinition>? = null,
    val tool_choice: String? = null
)

data class GroqChoice(val index: Int, val message: GroqChatMessage, val finish_reason: String?)
data class GroqChatResponse(val id: String?, val choices: List<GroqChoice>)

interface GroqApiService {
    @POST("openai/v1/chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") bearerToken: String,
        @Body request: GroqChatRequest
    ): GroqChatResponse
}
