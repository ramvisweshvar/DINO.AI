package com.example

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.network.GeminiRetrofitClient
import com.example.ui.*
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Text-To-Speech
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.US
                isTtsInitialized = true
            }
        }

        // Initialize Database, Repository & ViewModel
        val database = DinoDatabase.getDatabase(this)
        val repository = DinoRepository(
            dao = database.chatSessionDao(),
            apiService = GeminiRetrofitClient.service
        )
        val viewModelFactory = DinoViewModelFactory(repository)
        val viewModel = ViewModelProvider(this, viewModelFactory)[DinoViewModel::class.java]

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(innerPadding)
                    ) {
                        DinoMainApp(
                            viewModel = viewModel,
                            onSpeakText = { text -> speakText(text) }
                        )
                    }
                }
            }
        }
    }

    private fun speakText(text: String) {
        if (isTtsInitialized) {
            // Clean up text from markdown symbols for better speech synthesis
            val cleanText = text
                .replace("*", "")
                .replace("#", "")
                .replace("`", "")
            textToSpeech?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "DinoSpeech")
        } else {
            Toast.makeText(this, "Speech engine is initializing...", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        super.onDestroy()
    }
}

@Composable
fun DinoMainApp(
    viewModel: DinoViewModel,
    onSpeakText: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(0) } // 0 = Chat, 1 = DNA Fusion, 2 = Fossil Vault

    // Ensure physical back button closes sliding overlays or tabs
    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // App Header
        DinoHeader(
            isDeepThinking = state.isDeepThinking,
            onToggleDeepThinking = { viewModel.toggleDeepThinking(it) }
        )

        // API Key Security Alert
        val isKeyConfigured = remember {
            com.example.BuildConfig.GEMINI_API_KEY.isNotEmpty() && 
            com.example.BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY"
        }
        if (!isKeyConfigured) {
            ApiKeyWarningCard()
        }

        // Active Content Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (selectedTab) {
                0 -> DinoChatTab(
                    state = state,
                    viewModel = viewModel,
                    onSpeakText = onSpeakText
                )
                1 -> DnaFusionTab(
                    state = state,
                    viewModel = viewModel
                )
                2 -> FossilVaultTab(
                    state = state,
                    viewModel = viewModel
                )
            }
        }

        // Bottom Navigation Bar
        DinoBottomNav(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it }
        )
    }
}

// --- Custom Header ---

@Composable
fun DinoHeader(
    isDeepThinking: Boolean,
    onToggleDeepThinking: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DinoDarkSurface)
            .border(width = 1.dp, color = DinoBorder)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(DinoDarkBg, CircleShape)
                    .border(width = 1.dp, color = DinoNeonGreen, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🦖",
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "DINO.AI",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    color = DinoNeonGreen,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = "Supercharged Prehistoric Brain",
                    fontSize = 10.sp,
                    color = DinoTextMuted
                )
            }
        }

        // Deep Thinking Volcano mode toggle
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(DinoDarkBg, RoundedCornerShape(18.dp))
                .border(width = 1.dp, color = if (isDeepThinking) DinoLavaOrange else DinoBorder, shape = RoundedCornerShape(18.dp))
                .clickable { onToggleDeepThinking(!isDeepThinking) }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Whatshot,
                contentDescription = "Deep Thinking Mode",
                tint = if (isDeepThinking) DinoLavaOrange else DinoTextMuted,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isDeepThinking) "Volcano Mode" else "Quick Strike",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDeepThinking) DinoLavaOrange else DinoTextMuted
            )
        }
    }
}

// --- API Key Warning Card ---

@Composable
fun ApiKeyWarningCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = DinoLavaOrange.copy(alpha = 0.15f)),
        border = BorderStroke(1.dp, DinoLavaOrange),
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning",
                    tint = DinoLavaOrange,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "API Key Warning",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = DinoLavaOrange
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "No active Gemini API key found. Open the Secrets panel in Google AI Studio, set your 'GEMINI_API_KEY', and rebuild the app to unlock the dinosaur's true cognitive power!",
                fontSize = 12.sp,
                color = DinoTextLight,
                lineHeight = 16.sp
            )
        }
    }
}

// --- Dino Chat Tab ---

@Composable
fun DinoChatTab(
    state: DinoUiState,
    viewModel: DinoViewModel,
    onSpeakText: (String) -> Unit
) {
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    // Auto-scroll to bottom on new messages
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Persona Selector Carousel
        PersonaSelectorRow(
            selectedId = state.selectedPersonaId,
            onSelected = { viewModel.selectPersona(it) }
        )

        Divider(color = DinoBorder, thickness = 0.5.dp)

        // Chat Bubble List
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (state.messages.isEmpty()) {
                ChatEmptyState(
                    selectedPersona = PersonaRegistry.getById(state.selectedPersonaId),
                    isDeepThinking = state.isDeepThinking
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.messages) { message ->
                        ChatBubble(
                            message = message,
                            activePersona = PersonaRegistry.getById(state.selectedPersonaId),
                            onSpeakText = onSpeakText
                        )
                    }
                }
            }

            // Inline Loading Spinner
            if (state.isLoading) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .background(DinoCardBg, RoundedCornerShape(20.dp))
                        .border(width = 1.dp, color = DinoNeonGreen, shape = RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = DinoNeonGreen,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Dino neural network synthesizing...",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = DinoNeonGreen
                        )
                    }
                }
            }
        }

        Divider(color = DinoBorder, thickness = 0.5.dp)

        // Chat Input Panel
        ChatInputPanel(
            query = state.inputQuery,
            onQueryChanged = { viewModel.updateInputQuery(it) },
            onSend = {
                viewModel.sendMessage()
                focusManager.clearFocus()
            },
            isLoading = state.isLoading
        )
    }
}

@Composable
fun PersonaSelectorRow(
    selectedId: String,
    onSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DinoDarkSurface)
            .padding(vertical = 10.dp)
    ) {
        Text(
            text = "SELECT PREHISTORIC COGNITIVE ENGINE:",
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            color = DinoTextMuted,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PersonaRegistry.all.forEach { persona ->
                val isSelected = persona.id == selectedId
                val borderCol by animateColorAsState(if (isSelected) DinoNeonGreen else DinoBorder)
                val bgCol by animateColorAsState(if (isSelected) DinoBubbleUser else DinoCardBg)

                Card(
                    modifier = Modifier
                        .weight(1f)
                        .border(width = 1.dp, color = borderCol, shape = RoundedCornerShape(12.dp))
                        .clickable { onSelected(persona.id) },
                    colors = CardDefaults.cardColors(containerColor = bgCol),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = persona.icon, fontSize = 22.sp)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = persona.name,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) DinoNeonGreen else DinoTextLight
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatEmptyState(
    selectedPersona: DinoPersona,
    isDeepThinking: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = selectedPersona.icon,
            fontSize = 60.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Text(
            text = selectedPersona.title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = DinoNeonGreen
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = selectedPersona.description,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            color = DinoTextMuted,
            lineHeight = 16.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .background(DinoCardBg, RoundedCornerShape(8.dp))
                .border(width = 0.5.dp, color = DinoBorder, shape = RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "ACTIVE MODEL: " + if (isDeepThinking) "gemini-3.1-pro" else "gemini-3.5-flash",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isDeepThinking) DinoLavaOrange else DinoNeonGreen,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Dino.AI stores your chats offline safely in Room database epochs.",
                    fontSize = 10.sp,
                    color = DinoTextMuted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    activePersona: DinoPersona,
    onSpeakText: (String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!message.isUser) {
            Text(
                text = activePersona.icon,
                fontSize = 20.sp,
                modifier = Modifier.padding(end = 6.dp, top = 4.dp)
            )
        }

        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
        ) {
            // Role / Header
            Text(
                text = if (message.isUser) "YOU" else activePersona.name.uppercase(),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = DinoTextMuted,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )

            // Message Bubble Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (message.isUser) DinoBubbleUser else DinoBubbleAi
                ),
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (message.isUser) 16.dp else 4.dp,
                    bottomEnd = if (message.isUser) 4.dp else 16.dp
                ),
                border = BorderStroke(
                    0.5.dp,
                    if (message.isUser) DinoNeonGreen.copy(alpha = 0.3f) else DinoBorder
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = message.text,
                        fontSize = 14.sp,
                        color = DinoTextLight,
                        lineHeight = 20.sp
                    )

                    // Action buttons underneath AI answers
                    if (!message.isUser) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Copy button
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(message.text))
                                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy Response",
                                    tint = DinoTextMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            // Read Aloud / Primal Roar button
                            IconButton(
                                onClick = { onSpeakText(message.text) },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = "Read Aloud",
                                    tint = DinoNeonGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatInputPanel(
    query: String,
    onQueryChanged: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DinoDarkSurface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            placeholder = { Text("Roar your command to the Dino...", color = DinoTextMuted) },
            modifier = Modifier
                .weight(1f)
                .testTag("chat_input_field"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DinoDarkBg,
                unfocusedContainerColor = DinoDarkBg,
                focusedBorderColor = DinoNeonGreen,
                unfocusedBorderColor = DinoBorder,
                focusedTextColor = DinoTextLight,
                unfocusedTextColor = DinoTextLight
            ),
            shape = RoundedCornerShape(24.dp),
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            enabled = !isLoading
        )

        Spacer(modifier = Modifier.width(8.dp))

        FloatingActionButton(
            onClick = { onSend() },
            containerColor = DinoNeonGreen,
            contentColor = DinoDarkBg,
            shape = CircleShape,
            modifier = Modifier
                .size(48.dp)
                .testTag("submit_button"),
            elevation = FloatingActionButtonDefaults.elevation(4.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// --- DNA Fusion Tab ---

@Composable
fun DnaFusionTab(
    state: DinoUiState,
    viewModel: DinoViewModel
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "🧬 DNA FUSION LAB",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = DinoNeonGreen,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Synthesize two completely disparate concepts into a mind-blowing prehistoric hybrid solution. More powerful than standard text engines!",
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    color = DinoTextMuted,
                    lineHeight = 15.sp
                )
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DinoDarkSurface),
                border = BorderStroke(1.dp, DinoBorder),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "CONCEPT A (GENE 1):",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = DinoNeonGreen
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = state.dnaConcept1,
                        onValueChange = { viewModel.updateDnaConcept1(it) },
                        placeholder = { Text("e.g. Artificial Intelligence, Blockchain", color = DinoTextMuted) },
                        modifier = Modifier.fillMaxWidth().testTag("dna_concept_1"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = DinoTextLight,
                            unfocusedTextColor = DinoTextLight,
                            focusedBorderColor = DinoNeonGreen,
                            unfocusedBorderColor = DinoBorder
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "CONCEPT B (GENE 2):",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = DinoLavaOrange
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = state.dnaConcept2,
                        onValueChange = { viewModel.updateDnaConcept2(it) },
                        placeholder = { Text("e.g. Pizza Baking, Space Travel", color = DinoTextMuted) },
                        modifier = Modifier.fillMaxWidth().testTag("dna_concept_2"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = DinoTextLight,
                            unfocusedTextColor = DinoTextLight,
                            focusedBorderColor = DinoLavaOrange,
                            unfocusedBorderColor = DinoBorder
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            viewModel.fuseDnaConcepts()
                            focusManager.clearFocus()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DinoNeonGreen,
                            contentColor = DinoDarkBg
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("fuse_button"),
                        shape = RoundedCornerShape(24.dp),
                        enabled = !state.isDnaLoading && state.dnaConcept1.isNotEmpty() && state.dnaConcept2.isNotEmpty()
                    ) {
                        if (state.isDnaLoading) {
                            CircularProgressIndicator(
                                color = DinoDarkBg,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("FUSE COGNITIVE DNA", fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                            }
                        }
                    }
                }
            }
        }

        if (state.dnaResult.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DinoCardBg),
                    border = BorderStroke(1.dp, DinoNeonGreen.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "MUTATION SUCCESSFUL:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = DinoNeonGreen
                            )
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(state.dnaResult))
                                    Toast.makeText(context, "Copied Synthesis", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = DinoNeonGreen, modifier = Modifier.size(14.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = state.dnaResult,
                            fontSize = 13.sp,
                            color = DinoTextLight,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

// --- Fossil Vault Tab ---

@Composable
fun FossilVaultTab(
    state: DinoUiState,
    viewModel: DinoViewModel
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredSessions = remember(state.sessions, searchQuery) {
        if (searchQuery.isBlank()) {
            state.sessions
        } else {
            state.sessions.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "🗄️ FOSSIL VAULT",
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = DinoNeonGreen,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Text(
            text = "Your secure local chat history stored securely in Room database epochs.",
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            color = DinoTextMuted,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp)
        )

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search fossils...", color = DinoTextMuted) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = DinoTextLight,
                unfocusedTextColor = DinoTextLight,
                focusedBorderColor = DinoNeonGreen,
                unfocusedBorderColor = DinoBorder
            ),
            shape = RoundedCornerShape(12.dp),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = DinoTextMuted) },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredSessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🏜️", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (searchQuery.isBlank()) "Fossil vault is empty!" else "No fossils match your search.",
                        fontSize = 13.sp,
                        color = DinoTextMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredSessions) { session ->
                    val formatter = remember { SimpleDateFormat("MMM dd, yyyy - HH:mm", Locale.getDefault()) }
                    val dateString = formatter.format(Date(session.timestamp))
                    val persona = PersonaRegistry.getById(session.personaId)
                    val isCurrent = state.currentSession?.id == session.id

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = if (isCurrent) DinoNeonGreen else DinoBorder,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { viewModel.selectSession(session) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrent) DinoBubbleUser else DinoDarkSurface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = persona.icon, fontSize = 24.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = session.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = DinoTextLight
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = persona.name,
                                            fontSize = 10.sp,
                                            color = DinoNeonGreen,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = dateString,
                                            fontSize = 10.sp,
                                            color = DinoTextMuted
                                        )
                                    }
                                }
                            }

                            // Delete button
                            IconButton(
                                onClick = { viewModel.deleteSession(session.id) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Session",
                                    tint = DinoVolcanicRed,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Clear All button
        if (state.sessions.isNotEmpty()) {
            Button(
                onClick = { viewModel.clearAllHistory() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = DinoVolcanicRed.copy(alpha = 0.15f),
                    contentColor = DinoVolcanicRed
                ),
                border = BorderStroke(1.dp, DinoVolcanicRed),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("PURGE ALL FOSSIL VAULTS", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

// --- Bottom Navigation ---

@Composable
fun DinoBottomNav(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    NavigationBar(
        containerColor = DinoDarkSurface,
        tonalElevation = 8.dp,
        modifier = Modifier.border(width = 1.dp, color = DinoBorder)
    ) {
        NavigationBarItem(
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) },
            icon = { Icon(Icons.Default.Chat, contentDescription = "Dino Chat") },
            label = { Text("Dino Chat") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = DinoDarkBg,
                selectedTextColor = DinoNeonGreen,
                indicatorColor = DinoNeonGreen,
                unselectedIconColor = DinoTextMuted,
                unselectedTextColor = DinoTextMuted
            )
        )

        NavigationBarItem(
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            icon = { Icon(Icons.Default.Science, contentDescription = "DNA Fusion") },
            label = { Text("DNA Fusion") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = DinoDarkBg,
                selectedTextColor = DinoNeonGreen,
                indicatorColor = DinoNeonGreen,
                unselectedIconColor = DinoTextMuted,
                unselectedTextColor = DinoTextMuted
            )
        )

        NavigationBarItem(
            selected = selectedTab == 2,
            onClick = { onTabSelected(2) },
            icon = { Icon(Icons.Default.History, contentDescription = "Fossil Vault") },
            label = { Text("Fossil Vault") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = DinoDarkBg,
                selectedTextColor = DinoNeonGreen,
                indicatorColor = DinoNeonGreen,
                unselectedIconColor = DinoTextMuted,
                unselectedTextColor = DinoTextMuted
            )
        )
    }
}
