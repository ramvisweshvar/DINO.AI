package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ChatMessage
import com.example.data.ChatSerializer
import com.example.data.ChatSession
import com.example.data.DinoRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// --- Persona Definitions ---

data class DinoPersona(
    val id: String,
    val name: String,
    val title: String,
    val icon: String,
    val description: String,
    val systemInstruction: String,
    val temperature: Float
)

object PersonaRegistry {
    val REX = DinoPersona(
        id = "rex",
        name = "Rex",
        title = "T-Rex (Creative)",
        icon = "🦖",
        description = "Bold, energetic, and highly creative idea generator. Perfect for storytelling, brainstorming, and divergent thinking.",
        systemInstruction = "You are Rex, a roaring, ultra-creative T-Rex AI assistant. You speak with dynamic prehistoric energy! You occasionally use prehistoric metaphors (fossils, volcanic, meteor, Jurassic, magma) and CAPITALISED words of raw excitement, but you are extremely helpful, friendly, and provide highly imaginative, out-of-the-box answers. Respond in character with a modern, high-tech twist.",
        temperature = 0.9f
    )

    val TRIKE = DinoPersona(
        id = "trike",
        name = "Trike",
        title = "Triceratops (Logic & Code)",
        icon = "🦕",
        description = "Methodical, deeply logical, structured code architect. Excellent for programming, math, and STEM reasoning.",
        systemInstruction = "You are Trike, a wise and highly structured Triceratops software engineer and mathematics wizard. You have three defensive horns of pure logic. Your replies are always perfectly organized with clear bullet points, clean code blocks, and rigorous step-by-step reasoning. You are calm, precise, and polite, occasionally mentioning your heavy protective shields of accuracy.",
        temperature = 0.1f
    )

    val PTERA = DinoPersona(
        id = "ptera",
        name = "Ptera",
        title = "Pterodactyl (Summarize)",
        icon = "🦅",
        description = "Synthesizer and research summarizer. Provides a high-altitude, multi-perspective view of complex topics.",
        systemInstruction = "You are Ptera, a wide-winged Pterodactyl research analyst flying high above the Jurassic canopy. Your superpower is providing a high-altitude, panoramic summary of complex ideas, historical contexts, or long documents. You write with soaring elegance, breaking down topics into comprehensive overviews with clear headers and organized sections.",
        temperature = 0.5f
    )

    val RAPTOR = DinoPersona(
        id = "raptor",
        name = "Raptor",
        title = "Velociraptor (Rapid Action)",
        icon = "🦎",
        description = "Lightning-fast, highly focused strategist. Delivers razor-sharp, actionable bullet points with no fluff.",
        systemInstruction = "You are Raptor, a lightning-fast Velociraptor strategist. You value speed and efficiency above all. Your answers are short, razor-sharp, highly actionable, and formatted entirely in clear bullet points. You have no patience for fluff, introductory greetings, or long preambles. Cut straight to the bone of the answer.",
        temperature = 0.7f
    )

    val all = listOf(REX, TRIKE, PTERA, RAPTOR)
    fun getById(id: String) = all.find { it.id == id } ?: REX
}

// --- ViewModel State ---

data class DinoUiState(
    val sessions: List<ChatSession> = emptyList(),
    val currentSession: ChatSession? = null,
    val messages: List<ChatMessage> = emptyList(),
    val inputQuery: String = "",
    val isLoading: Boolean = false,
    val selectedPersonaId: String = PersonaRegistry.REX.id,
    val isDeepThinking: Boolean = false, // Toggle between gemini-3.5-flash and gemini-3.1-pro-preview
    
    // DNA Fusion State
    val dnaConcept1: String = "",
    val dnaConcept2: String = "",
    val dnaResult: String = "",
    val isDnaLoading: Boolean = false,
    
    // General Error / Key warning
    val errorMessage: String? = null
)

class DinoViewModel(private val repository: DinoRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(DinoUiState())
    val uiState: StateFlow<DinoUiState> = _uiState.asStateFlow()

    init {
        // Collect local database sessions in real-time
        viewModelScope.launch {
            repository.allSessions.collect { localSessions ->
                _uiState.update { it.copy(sessions = localSessions) }
                
                // If there's a current session, refresh it from DB updates (e.g. title changes)
                val active = _uiState.value.currentSession
                if (active != null) {
                    val updatedActive = localSessions.find { it.id == active.id }
                    if (updatedActive != null) {
                        _uiState.update {
                            it.copy(
                                currentSession = updatedActive,
                                messages = ChatSerializer.deserialize(updatedActive.messagesJson)
                            )
                        }
                    }
                }
            }
        }
    }

    // --- Chat Session Operations ---

    fun selectSession(session: ChatSession) {
        _uiState.update {
            it.copy(
                currentSession = session,
                messages = ChatSerializer.deserialize(session.messagesJson),
                selectedPersonaId = session.personaId,
                isDeepThinking = session.isDeepThinking,
                errorMessage = null
            )
        }
    }

    fun startNewSession() {
        _uiState.update {
            it.copy(
                currentSession = null,
                messages = emptyList(),
                inputQuery = "",
                errorMessage = null
            )
        }
    }

    fun updateInputQuery(query: String) {
        _uiState.update { it.copy(inputQuery = query) }
    }

    fun selectPersona(personaId: String) {
        _uiState.update { it.copy(selectedPersonaId = personaId) }
    }

    fun toggleDeepThinking(enabled: Boolean) {
        _uiState.update { it.copy(isDeepThinking = enabled) }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            repository.deleteSessionById(sessionId)
            if (_uiState.value.currentSession?.id == sessionId) {
                startNewSession()
            }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.deleteAllSessions()
            startNewSession()
        }
    }

    fun sendMessage() {
        val query = _uiState.value.inputQuery.trim()
        if (query.isEmpty() || _uiState.value.isLoading) return

        _uiState.update { it.copy(inputQuery = "", isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            val persona = PersonaRegistry.getById(_uiState.value.selectedPersonaId)
            val model = if (_uiState.value.isDeepThinking) {
                "gemini-3.1-pro-preview" // Heavy-duty deep reasoning model
            } else {
                "gemini-3.5-flash"       // Speedy core model
            }

            // Construct new message
            val userMessage = ChatMessage(text = query, isUser = true)
            val updatedMessages = _uiState.value.messages + userMessage

            // Local cache of current active session ID
            var activeSession = _uiState.value.currentSession

            if (activeSession == null) {
                // Determine a nice auto-title
                val title = if (query.length > 25) query.take(25) + "..." else query
                val newSession = ChatSession(
                    title = title,
                    personaId = persona.id,
                    isDeepThinking = _uiState.value.isDeepThinking,
                    messagesJson = ChatSerializer.serialize(updatedMessages)
                )
                val newId = repository.insertSession(newSession)
                activeSession = newSession.copy(id = newId)
                _uiState.update { it.copy(currentSession = activeSession, messages = updatedMessages) }
            } else {
                // Update existing session
                val updatedSession = activeSession.copy(
                    messagesJson = ChatSerializer.serialize(updatedMessages),
                    timestamp = System.currentTimeMillis()
                )
                repository.updateSession(updatedSession)
                _uiState.update { it.copy(currentSession = updatedSession, messages = updatedMessages) }
            }

            // Trigger Gemini API call
            val aiResponseText = repository.generateAiResponse(
                model = model,
                apiKey = com.example.BuildConfig.GEMINI_API_KEY,
                systemInstruction = persona.systemInstruction,
                history = updatedMessages
            )

            // Construct response message
            val aiMessage = ChatMessage(text = aiResponseText, isUser = false)
            val finalMessages = updatedMessages + aiMessage

            // Persist response
            val finalSession = activeSession.copy(
                messagesJson = ChatSerializer.serialize(finalMessages),
                timestamp = System.currentTimeMillis()
            )
            repository.updateSession(finalSession)
            
            _uiState.update {
                it.copy(
                    messages = finalMessages,
                    isLoading = false
                )
            }
        }
    }

    // --- DNA Fusion Superpower Operations ---

    fun updateDnaConcept1(concept: String) {
        _uiState.update { it.copy(dnaConcept1 = concept) }
    }

    fun updateDnaConcept2(concept: String) {
        _uiState.update { it.copy(dnaConcept2 = concept) }
    }

    fun fuseDnaConcepts() {
        val c1 = _uiState.value.dnaConcept1.trim()
        val c2 = _uiState.value.dnaConcept2.trim()
        if (c1.isEmpty() || c2.isEmpty() || _uiState.value.isDnaLoading) return

        _uiState.update { it.copy(isDnaLoading = true, dnaResult = "", errorMessage = null) }

        viewModelScope.launch {
            val persona = PersonaRegistry.getById(_uiState.value.selectedPersonaId)
            val model = if (_uiState.value.isDeepThinking) "gemini-3.1-pro-preview" else "gemini-3.5-flash"

            val fusionPrompt = """
                Perform a high-intensity genetic CONCEPTUAL DNA FUSION of two disparate concepts.
                Concept A: $c1
                Concept B: $c2
                
                Please synthesize a completely mind-blowing hybrid idea based on these. Outline:
                1. HYBRID TITILE: A catchy, high-tech prehistoric name.
                2. GENETIC PROFILE: Briefly explain how they merge.
                3. MARKET DISRUPTION / PRACTICAL USES: Why this hybrid is more powerful than anything else.
                4. EXPERIMENT STATUS: A fun Jurassic-style testing log.
                
                Keep your response extremely creative, engaging, and structured, matching your dinosaur persona ($persona).
            """.trimIndent()

            val fusionMessage = ChatMessage(text = fusionPrompt, isUser = true)
            
            val apiResponseText = repository.generateAiResponse(
                model = model,
                apiKey = com.example.BuildConfig.GEMINI_API_KEY,
                systemInstruction = "You are a master evolutionary geneticist AI. Always reply according to the persona constraints of ${persona.name}.",
                history = listOf(fusionMessage),
                temperature = 0.85f // High temperature for high creativity in fusion
            )

            _uiState.update {
                it.copy(
                    dnaResult = apiResponseText,
                    isDnaLoading = false
                )
            }
        }
    }

    fun clearDnaFusion() {
        _uiState.update {
            it.copy(
                dnaConcept1 = "",
                dnaConcept2 = "",
                dnaResult = ""
            )
        }
    }
}

class DinoViewModelFactory(private val repository: DinoRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DinoViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DinoViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
