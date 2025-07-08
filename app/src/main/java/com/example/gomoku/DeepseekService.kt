package com.example.gomoku

import android.util.Log
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class DeepseekService {

    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun generateCompletion(prompt: String, model: String = "deepseek-chat") : String? {
        return try {
            val response: DeepseekResponse = client.post("$BASE_URL/chat/completions") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $API_KEY")
                setBody(DeepseekRequest(
                    model = model,
                    messages = listOf(Message(role = "user", content = prompt)),
                    stream = false
                ))
            }.body()
            response.choices.firstOrNull()?.message?.content
        } catch (e: Exception) {
            Log.e("DeepseekService", "Error generating completion: ${e.message}", e)
            null
        }
    }

    companion object {
        private const val BASE_URL = "https://api.deepseek.com/v1"
        // API key disabled for privacy
    }
}

@Serializable
data class DeepseekRequest(
    val model: String,
    val messages: List<Message>,
    val stream: Boolean = false
)

@Serializable
data class Message(
    val role: String,
    val content: String
)

@Serializable
data class DeepseekResponse(
    val id: String,
    val `object`: String,
    val created: Long,
    val model: String,
    val choices: List<Choice>,
    val usage: Usage
)

@Serializable
data class Choice(
    val index: Int,
    val message: Message,
    val finish_reason: String
)

@Serializable
data class Usage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int
)
