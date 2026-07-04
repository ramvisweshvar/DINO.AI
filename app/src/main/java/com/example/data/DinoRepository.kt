package com.example.data

import com.example.network.GeminiApiService
import com.example.network.GeminiRequest
import com.example.network.Content as NetContent
import com.example.network.Part as NetPart
import com.example.network.GenerationConfig as NetGenConfig
import kotlinx.coroutines.flow.Flow

class DinoRepository(
    private val dao: ChatSessionDao,
    private val apiService: GeminiApiService
) {
    val allSessions: Flow<List<ChatSession>> = dao.getAllSessionsFlow()

    suspend fun getSessionById(id: Long): ChatSession? = dao.getSessionById(id)

    suspend fun insertSession(session: ChatSession): Long = dao.insertSession(session)

    suspend fun updateSession(session: ChatSession) = dao.updateSession(session)

    suspend fun deleteSessionById(id: Long) = dao.deleteSessionById(id)

    suspend fun deleteAllSessions() = dao.deleteAllSessions()

    /**
     * Sends the dialogue history and system instruction to the Gemini model
     */
    suspend fun generateAiResponse(
        model: String,
        apiKey: String,
        systemInstruction: String?,
        history: List<ChatMessage>,
        temperature: Float = 0.7f
    ): String {
        // Map history to Network Contents
        val contents = history.map { message ->
            NetContent(
                parts = listOf(NetPart(text = message.text)),
                role = if (message.isUser) "user" else "model"
            )
        }

        val netSystemInstruction = systemInstruction?.let {
            NetContent(parts = listOf(NetPart(text = it)))
        }

        val config = NetGenConfig(
            temperature = temperature
        )

        val request = GeminiRequest(
            contents = contents,
            generationConfig = config,
            systemInstruction = netSystemInstruction
        )

        return try {
            val response = apiService.generateContent(
                model = model,
                apiKey = apiKey,
                request = request
            )
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "Roar! I couldn't formulate a response. Let me eat some fossil fuel and try again!"
        } catch (e: Exception) {
            "Roar! Failed to communicate with the dino-verse: ${e.localizedMessage ?: e.message ?: "Unknown network error"}. Please check your internet connection."
        }
    }
}
