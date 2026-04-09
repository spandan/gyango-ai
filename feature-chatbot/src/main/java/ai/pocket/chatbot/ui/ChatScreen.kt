package ai.pocket.chatbot.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.android.awaitFrame
import ai.pocket.core.ChatMessage
import ai.pocket.core.InferenceSettings
import ai.pocket.core.LlmDefaults
import ai.pocket.core.Sender
import ai.pocket.chatbot.ui.theme.AssistantMessageBg
import ai.pocket.chatbot.ui.theme.AssistantMessageBgDark
import ai.pocket.chatbot.ui.theme.UserMessageBg
import ai.pocket.chatbot.ui.theme.UserMessageBgDark


/** BCP 47 tags shown in settings for the system speech recognizer (Telugu default). */
private val SPEECH_INPUT_LOCALES: List<Pair<String, String>> = listOf(
    "en-US" to "English",
    "hi-IN" to "Hindi",
    "te-IN" to "Telugu",
)

private val PresenceGreen = Color(0xFF43A047)

/** Launch screen emphasizes the microphone; users can switch to typing from the app bar. */
enum class ChatInputMode {
    VoicePrimary,
    TextAndVoice
}

@Composable
private fun rememberPocketAILogo(): ImageBitmap? {
    val context = LocalContext.current
    return remember(context) {
        try {
            context.assets.open("images/pocketAI.webp").use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        } catch (_: Exception) { null }
    }
}

/** Subtle “someone is typing” motion — reads human, not a system spinner. */
@Composable
private fun HumanTypingIndicator(dotColor: Color) {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        repeat(3) { i ->
            val alpha by transition.animateFloat(
                initialValue = 0.28f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(520, delayMillis = i * 140, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$i"
            )
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor.copy(alpha = alpha))
            )
        }
    }
}

/** Inline with the “Pocket” label — avoids a large side avatar that wastes horizontal space. */
@Composable
private fun AssistantInlineIcon(
    logo: ImageBitmap?,
    showLivePresence: Boolean
) {
    val borderColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
    val iconSize = 20.dp
    Box {
        if (logo != null) {
            Image(
                bitmap = logo,
                contentDescription = null,
                modifier = Modifier
                    .size(iconSize)
                    .clip(CircleShape)
                    .border(1.dp, borderColor, CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(iconSize)
                    .clip(CircleShape)
                    .border(1.dp, borderColor, CircleShape)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = CHAT_ASSISTANT_DISPLAY_NAME.take(1),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        if (showLivePresence) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(PresenceGreen)
                    .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    isGenerating: Boolean,
    isLoadingModel: Boolean = false,
    settings: InferenceSettings,
    isListeningToMic: Boolean = false,
    chatInputMode: ChatInputMode = ChatInputMode.TextAndVoice,
    onChatInputModeChange: (ChatInputMode) -> Unit = {},
    onSend: (String) -> Unit,
    onSettingsChanged: (InferenceSettings) -> Unit,
    onClearChat: () -> Unit = {},
    onAttachDocument: () -> Unit = {},
    onVoiceInputClick: () -> Unit = {},
    documentError: String? = null,
    onDismissDocumentError: () -> Unit = {},
    voiceError: String? = null,
    onDismissVoiceError: () -> Unit = {},
    onNavigateBack: (() -> Unit)? = null
) {
    var inputText by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    val displayStrings = rememberChatDisplayStrings(settings.speechInputLocaleTag)

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    val logo = rememberPocketAILogo()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (logo != null) {
                            Image(
                                bitmap = logo,
                                contentDescription = "Pocket AI",
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "p",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Pocket AI",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Text(
                                text = displayStrings.topBarSubtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                },
                actions = {
                    when (chatInputMode) {
                        ChatInputMode.VoicePrimary -> {
                            IconButton(
                                onClick = { onChatInputModeChange(ChatInputMode.TextAndVoice) }
                            ) {
                                Icon(
                                    Icons.Default.Keyboard,
                                    contentDescription = "Type a message",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                        ChatInputMode.TextAndVoice -> {
                            IconButton(
                                onClick = { onChatInputModeChange(ChatInputMode.VoicePrimary) }
                            ) {
                                Icon(
                                    Icons.Default.Mic,
                                    contentDescription = "Voice conversation",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = onClearChat,
                        enabled = messages.isNotEmpty() && !isGenerating
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear chat")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            MessagesList(
                messages = messages,
                isGenerating = isGenerating,
                isLoadingModel = isLoadingModel,
                chatInputMode = chatInputMode,
                displayStrings = displayStrings,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            Surface(
                tonalElevation = 0.dp,
                shadowElevation = 4.dp,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                color = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surface
                       else Color.White
            ) {
                when (chatInputMode) {
                    ChatInputMode.VoicePrimary -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            FilledIconButton(
                                onClick = {
                                    if (!isListeningToMic) onVoiceInputClick()
                                },
                                enabled = !isGenerating,
                                modifier = Modifier
                                    .size(104.dp)
                                    .then(
                                        if (isListeningToMic) Modifier.alpha(0.95f) else Modifier
                                    ),
                                shape = CircleShape,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = if (isListeningToMic) {
                                        MaterialTheme.colorScheme.errorContainer
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                    contentColor = if (isListeningToMic) {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    } else {
                                        MaterialTheme.colorScheme.onPrimary
                                    }
                                )
                            ) {
                                if (isListeningToMic) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(44.dp),
                                        strokeWidth = 3.dp,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Mic,
                                        contentDescription = displayStrings.micMainContentDescription,
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                            }
                        }
                    }
                    ChatInputMode.TextAndVoice -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                modifier = Modifier.weight(1f),
                                value = inputText,
                                onValueChange = { inputText = it },
                                placeholder = {
                                    Text(
                                        "Ask anything...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                },
                                leadingIcon = {
                                    IconButton(
                                        onClick = onAttachDocument,
                                        enabled = !isGenerating
                                    ) {
                                        Icon(
                                            Icons.Default.AttachFile,
                                            contentDescription = "Attach PDF or text",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                },
                                enabled = !isGenerating,
                                maxLines = 4,
                                shape = RoundedCornerShape(20.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    cursorColor = MaterialTheme.colorScheme.primary,
                                    focusedLabelColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            FilledTonalIconButton(
                                onClick = {
                                    if (!isListeningToMic) onVoiceInputClick()
                                },
                                enabled = !isGenerating,
                                modifier = Modifier
                                    .size(52.dp)
                                    .then(
                                        if (isListeningToMic) Modifier.alpha(0.95f) else Modifier
                                    ),
                                shape = CircleShape,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = if (isListeningToMic) {
                                        MaterialTheme.colorScheme.errorContainer
                                    } else {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    },
                                    contentColor = if (isListeningToMic) {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    }
                                )
                            ) {
                                if (isListeningToMic) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.5.dp,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Mic,
                                        contentDescription = "Speak — uses preferred language from settings",
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            FilledIconButton(
                                onClick = {
                                    val text = inputText.trim()
                                    if (text.isNotEmpty()) {
                                        onSend(text)
                                        inputText = ""
                                    }
                                },
                                enabled = inputText.isNotBlank() && !isGenerating,
                                modifier = Modifier.size(52.dp),
                                shape = CircleShape,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(
                                    Icons.Default.Send,
                                    contentDescription = "Send",
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                voiceError?.let { error ->
                    Snackbar(
                        modifier = Modifier.padding(bottom = if (documentError != null) 8.dp else 16.dp),
                        action = {
                            TextButton(onClick = onDismissVoiceError) {
                                Text("OK", color = MaterialTheme.colorScheme.inverseOnSurface)
                            }
                        }
                    ) {
                        Text(error)
                    }
                }
                documentError?.let { error ->
                    Snackbar(
                        modifier = Modifier.padding(bottom = 16.dp),
                        action = {
                            TextButton(onClick = onDismissDocumentError) {
                                Text("OK", color = MaterialTheme.colorScheme.inverseOnSurface)
                            }
                        }
                    ) {
                        Text(error)
                    }
                }
            }
        }

        if (showSettings) {
            SettingsDialog(
                settings = settings,
                onDismiss = { showSettings = false },
                onSettingsChanged = { onSettingsChanged(it) },
            )
        }
    }

        if (!settings.voiceOnboardingComplete) {
            LanguageOnboardingOverlay(settings = settings, strings = displayStrings, onContinue = onSettingsChanged)
        }
    }
}

@Composable
private fun LanguageOnboardingOverlay(
    settings: InferenceSettings,
    strings: ChatDisplayStrings,
    onContinue: (InferenceSettings) -> Unit,
) {
    var speechLocaleTag by remember(settings.speechInputLocaleTag) { mutableStateOf(settings.speechInputLocaleTag) }
    LaunchedEffect(settings.speechInputLocaleTag) {
        speechLocaleTag = settings.speechInputLocaleTag
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = strings.onboardingWelcomeTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = strings.onboardingWelcomeBody,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = strings.onboardingLanguageSection,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SPEECH_INPUT_LOCALES.forEach { (tag, name) ->
                    FilterChip(
                        selected = speechLocaleTag == tag,
                        onClick = { speechLocaleTag = tag },
                        label = { Text(name) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    onContinue(
                        settings.copy(
                            speechInputLocaleTag = speechLocaleTag,
                            assistantSpeechEnabled = false,
                            voiceOnboardingComplete = true,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(strings.onboardingContinue)
            }
        }
    }
}

@Composable
private fun MessagesList(
    messages: List<ChatMessage>,
    isGenerating: Boolean = false,
    isLoadingModel: Boolean = false,
    chatInputMode: ChatInputMode = ChatInputMode.TextAndVoice,
    displayStrings: ChatDisplayStrings,
    modifier: Modifier = Modifier
) {
    if (messages.isEmpty()) {
        val logo = rememberPocketAILogo()
        val (titleLine, subtitleLine) = when (chatInputMode) {
            ChatInputMode.VoicePrimary ->
                displayStrings.emptyVoicePrimaryTitle to displayStrings.emptyVoicePrimarySubtitle
            ChatInputMode.TextAndVoice ->
                displayStrings.emptyTextModeTitle to displayStrings.emptyTextModeSubtitle
        }
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (logo != null) {
                    Image(
                        bitmap = logo,
                        contentDescription = "Pocket AI",
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(20.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "p",
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        )
                    }
                }
                Text(
                    text = titleLine,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = subtitleLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        val lastMessage = messages.lastOrNull()
        val assistantLogo = rememberPocketAILogo()
        val listState = rememberLazyListState()
        // Scroll only when new rows are added (send, clear + reload). Do not tie to streaming
        // text updates — that repeatedly scrolls to the top of the last bubble and breaks reading.
        LaunchedEffect(messages.size) {
            if (messages.isEmpty()) return@LaunchedEffect
            val lastIndex = messages.size - 1
            awaitFrame()
            listState.scrollToItem(lastIndex)
        }
        LazyColumn(
            state = listState,
            modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            reverseLayout = false,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                val isLast = message.id == lastMessage?.id
                val showWaitPlaceholder = isGenerating &&
                    isLast &&
                    message.sender == Sender.ASSISTANT &&
                    message.text.isEmpty()
                MessageBubble(
                    message = message,
                    assistantLogo = assistantLogo,
                    showTypingPlaceholder = showWaitPlaceholder && !isLoadingModel,
                    loadingModelPlaceholder = showWaitPlaceholder && isLoadingModel,
                    showLivePresence = isGenerating && isLast && message.sender == Sender.ASSISTANT,
                    loadingModelMessage = displayStrings.loadingModelMessage,
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    assistantLogo: ImageBitmap? = null,
    showTypingPlaceholder: Boolean = false,
    loadingModelPlaceholder: Boolean = false,
    showLivePresence: Boolean = false,
    loadingModelMessage: String = "Getting the model ready — one moment.",
) {
    val isUser = message.sender == Sender.USER
    val isDark = isSystemInDarkTheme()
    val containerColor = if (isUser) {
        if (isDark) UserMessageBgDark else UserMessageBg
    } else {
        if (isDark) AssistantMessageBgDark else AssistantMessageBg
    }
    val textColor = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface
    val isAssistantIdlePlaceholder =
        !isUser && (showTypingPlaceholder || loadingModelPlaceholder)

    @Composable
    fun bubbleContent() {
        when {
            loadingModelPlaceholder -> {
                Text(
                    text = loadingModelMessage,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor.copy(alpha = 0.82f),
                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight,
                    fontStyle = FontStyle.Italic
                )
            }
            showTypingPlaceholder -> {
                HumanTypingIndicator(
                    dotColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                )
            }
            else -> {
                if (isUser) {
                    Text(
                        text = message.text.trim(),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor,
                        lineHeight = 24.sp,
                        letterSpacing = 0.2.sp
                    )
                } else {
                    AssistantReadableText(
                        raw = message.text,
                        textColor = textColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }

    if (isUser) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                color = containerColor,
                shape = RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomStart = 20.dp,
                    bottomEnd = 6.dp
                ),
                shadowElevation = 2.dp,
                tonalElevation = 0.dp
            ) {
                bubbleContent()
            }
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            AssistantInlineIcon(
                logo = assistantLogo,
                showLivePresence = showLivePresence && !isAssistantIdlePlaceholder
            )
            Text(
                text = CHAT_ASSISTANT_DISPLAY_NAME,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            color = containerColor,
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = 6.dp,
                bottomEnd = 20.dp
            ),
            shadowElevation = 2.dp,
            tonalElevation = 0.dp
        ) {
            bubbleContent()
        }
    }
}

@Composable
private fun SettingsDialog(
    settings: InferenceSettings,
    onDismiss: () -> Unit,
    onSettingsChanged: (InferenceSettings) -> Unit
) {
    var temp by remember(settings.temperature) { mutableStateOf(settings.temperature) }
    val maxTokCap = LlmDefaults.MAX_NEW_TOKENS_CAP.toFloat()
    var maxTokens by remember(settings.maxTokens) {
        mutableStateOf(settings.maxTokens.toFloat().coerceIn(256f, maxTokCap))
    }
    var lowPower by remember { mutableStateOf(settings.lowPowerMode) }
    var speechLocaleTag by remember(settings.speechInputLocaleTag) {
        mutableStateOf(settings.speechInputLocaleTag)
    }
    LaunchedEffect(settings.speechInputLocaleTag) {
        speechLocaleTag = settings.speechInputLocaleTag
    }
    var showSpeechLanguagePicker by remember { mutableStateOf(false) }
    val locStrings = remember(speechLocaleTag) { chatDisplayStringsForLocale(speechLocaleTag) }

    Box {
        AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                locStrings.settingsTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val hintStyle = MaterialTheme.typography.labelSmall
                val hintColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Temperature: ${"%.2f".format(temp)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = temp,
                        onValueChange = { temp = it },
                        valueRange = 0.1f..1.5f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Text(
                        text = "Higher → more varied; lower → steadier answers.",
                        style = hintStyle,
                        color = hintColor
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Max tokens: ${maxTokens.toInt()}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = maxTokens,
                        onValueChange = { maxTokens = it.coerceIn(256f, maxTokCap) },
                        valueRange = 256f..maxTokCap,
                        steps = 11,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Text(
                        text = "Rough cap on reply length; long chats trim older turns.",
                        style = hintStyle,
                        color = hintColor
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        locStrings.settingsConversationLanguage,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    val localeLabel = SPEECH_INPUT_LOCALES.find { it.first == speechLocaleTag }?.second
                        ?: "English"
                    Text(
                        text = localeLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(onClick = { showSpeechLanguagePicker = true }) {
                        Text("Change")
                    }
                    Text(
                        text = locStrings.settingsConversationLanguageHint,
                        style = hintStyle,
                        color = hintColor
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = lowPower,
                            onCheckedChange = { lowPower = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Low power (reserved)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = "Placeholder for future battery tuning; no effect yet.",
                        style = hintStyle,
                        color = hintColor,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSettingsChanged(
                        InferenceSettings(
                            temperature = temp,
                            maxTokens = maxTokens.toInt(),
                            lowPowerMode = lowPower,
                            speechInputLocaleTag = speechLocaleTag,
                            assistantSpeechEnabled = false,
                            voiceOnboardingComplete = settings.voiceOnboardingComplete,
                        )
                    )
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(locStrings.settingsApply)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(locStrings.settingsCancel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface
        )

        if (showSpeechLanguagePicker) {
            AlertDialog(
                onDismissRequest = { showSpeechLanguagePicker = false },
                title = {
                    Text(
                        locStrings.settingsSpeechPickerTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        SPEECH_INPUT_LOCALES.forEach { (tag, name) ->
                            TextButton(
                                onClick = {
                                    speechLocaleTag = tag
                                    showSpeechLanguagePicker = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = name,
                                    modifier = Modifier.fillMaxWidth(),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSpeechLanguagePicker = false }) {
                        Text("Close")
                    }
                },
                shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.surface
            )
        }
    }
}
