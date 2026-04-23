package ai.gyango.chatbot.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.android.awaitFrame
import java.util.Locale
import ai.gyango.core.ChatMessage
import ai.gyango.core.InferenceSettings
import ai.gyango.core.ChatPreferenceMappings
import ai.gyango.core.LlmDefaults
import ai.gyango.core.Sender
import ai.gyango.core.SubjectMode
import ai.gyango.chatbot.ui.theme.AssistantMessageBg
import ai.gyango.chatbot.ui.theme.AssistantMessageBgDark
import ai.gyango.chatbot.ui.theme.UserMessageBg
import ai.gyango.chatbot.ui.theme.UserMessageBgDark


private val PresenceGreen = Color(0xFF43A047)

/** Phase 1 tiles: general (includes curiosity), core subjects, writing, exam prep. */
private val SUBJECT_TILES: List<Triple<String, SubjectMode, ImageVector>> = listOf(
    Triple("General", SubjectMode.GENERAL, Icons.Default.Lightbulb),
    Triple("Math", SubjectMode.MATH, Icons.Default.Calculate),
    Triple("Science", SubjectMode.SCIENCE, Icons.Default.Science),
    Triple("Coding", SubjectMode.CODING, Icons.Default.Code),
    Triple("Writing", SubjectMode.WRITING, Icons.Default.Edit),
    Triple("Exam Prep", SubjectMode.EXAM_PREP, Icons.Default.School),
)

private fun subjectTileSelected(settingsMode: SubjectMode?, tileMode: SubjectMode): Boolean {
    if (tileMode == SubjectMode.GENERAL) {
        return settingsMode == null ||
            settingsMode == SubjectMode.GENERAL ||
            settingsMode == SubjectMode.CURIOSITY
    }
    if (tileMode == SubjectMode.SCIENCE) {
        return settingsMode in setOf(
            SubjectMode.SCIENCE,
            SubjectMode.PHYSICS,
            SubjectMode.CHEMISTRY,
            SubjectMode.BIOLOGY,
        )
    }
    return settingsMode == tileMode
}

/** Launch screen emphasizes the microphone; users can switch to typing from the app bar. */
enum class ChatInputMode {
    VoicePrimary,
    TextAndVoice
}

@Composable
private fun rememberGyangoAILogo(): ImageBitmap? {
    val context = LocalContext.current
    return remember(context) {
        try {
            context.assets.open("images/gyangoAI.webp").use { stream ->
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

/** Inline with the GyanGo label — avoids a large side avatar that wastes horizontal space. */
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
    modelLoadError: String? = null,
    onRetryModelLoad: () -> Unit = {},
    settings: InferenceSettings,
    isListeningToMic: Boolean = false,
    chatInputMode: ChatInputMode = ChatInputMode.TextAndVoice,
    onChatInputModeChange: (ChatInputMode) -> Unit = {},
    onSend: (String, fromSparkChip: Boolean) -> Unit,
    onSettingsChanged: (InferenceSettings) -> Unit,
    onClearChat: () -> Unit = {},
    onTakePicture: () -> Unit = {},
    onUploadImage: () -> Unit = {},
    onVoiceInputClick: () -> Unit = {},
    documentError: String? = null,
    onDismissDocumentError: () -> Unit = {},
    voiceError: String? = null,
    onDismissVoiceError: () -> Unit = {},
    onFeedbackClick: () -> Unit = {},
    onNavigateBack: (() -> Unit)? = null,
    /** When the user chooses read-aloud while editing profile settings, host may open Android TTS data install if needed. */
    onReadAloudYesSelected: (speechInputLocaleTag: String) -> Unit = {},
) {
    var inputText by remember { mutableStateOf("") }
    var settingsPage by remember { mutableStateOf<ChatSettingsPage?>(null) }
    val displayStrings = rememberChatDisplayStrings(settings.speechInputLocaleTag)
    val inputBlockedUntilModelReady =
        modelLoadError != null || (isLoadingModel && messages.isEmpty())

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    val logo = rememberGyangoAILogo()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (logo != null) {
                            Image(
                                bitmap = logo,
                                contentDescription = displayStrings.topBarTitle,
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
                                    text = "g",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Column {
                            Text(
                                text = displayStrings.topBarTitle,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Text(
                                text = displayStrings.topBarCaption,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f)
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
                    Box {
                        when (chatInputMode) {
                            ChatInputMode.VoicePrimary -> {
                                IconButton(
                                    onClick = { onChatInputModeChange(ChatInputMode.TextAndVoice) },
                                ) {
                                    Icon(
                                        Icons.Default.Keyboard,
                                        contentDescription = displayStrings.chatToolbarSwitchToTypeLabel,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                    )
                                }
                            }
                            ChatInputMode.TextAndVoice -> {
                                IconButton(
                                    onClick = { onChatInputModeChange(ChatInputMode.VoicePrimary) },
                                ) {
                                    Icon(
                                        Icons.Default.Mic,
                                        contentDescription = displayStrings.chatToolbarSwitchToVoiceLabel,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                    )
                                }
                            }
                        }
                    }
                    IconButton(
                        onClick = {
                            val enable = !settings.assistantSpeechEnabled
                            if (enable) onReadAloudYesSelected(settings.speechInputLocaleTag)
                            onSettingsChanged(settings.copy(assistantSpeechEnabled = enable))
                        },
                    ) {
                        Icon(
                            imageVector = if (settings.assistantSpeechEnabled) {
                                Icons.AutoMirrored.Filled.VolumeUp
                            } else {
                                Icons.AutoMirrored.Filled.VolumeOff
                            },
                            contentDescription = if (settings.assistantSpeechEnabled) {
                                displayStrings.chatToolbarReadAloudTurnOffDescription
                            } else {
                                displayStrings.chatToolbarReadAloudTurnOnDescription
                            },
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        IconButton(
                            onClick = onClearChat,
                            enabled = messages.isNotEmpty() && !isGenerating,
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = displayStrings.chatToolbarClearLabel)
                        }
                        IconButton(onClick = onFeedbackClick) {
                            Icon(Icons.Default.Email, contentDescription = displayStrings.chatToolbarFeedbackLabel)
                        }
                    }
                    IconButton(
                        onClick = { settingsPage = ChatSettingsPage.Hub },
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = displayStrings.chatToolbarSettingsLabel)
                    }
                }
            )
        },
        bottomBar = {
            AiContentDisclaimerBanner()
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Text(
                text = displayStrings.childSafeBanner,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            SubjectModeTileRow(
                settings = settings,
                onSettingsChanged = onSettingsChanged,
                enabled = !isGenerating,
                anchorModifier = Modifier,
            )
            MessagesList(
                messages = messages,
                isGenerating = isGenerating,
                isLoadingModel = isLoadingModel,
                modelLoadError = modelLoadError,
                onRetryModelLoad = onRetryModelLoad,
                chatInputMode = chatInputMode,
                // Streaming placeholder (dots + hints) while the model writes; independent of prompt thought hints.
                showThoughtProcess = true,
                displayStrings = displayStrings,
                onSparkChipSend = { text ->
                    inputText = ""
                    onSend(text, true)
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            Surface(
                modifier = Modifier.imePadding(),
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
                                enabled = !isGenerating && !inputBlockedUntilModelReady,
                                modifier = Modifier
                                    .size(104.dp)
                                    .then(
                                        if (isListeningToMic) Modifier.alpha(0.95f) else Modifier
                                    ),
                                shape = CircleShape,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
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
                        var showAttachmentMenu by remember { mutableStateOf(false) }
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
                                    Box {
                                        IconButton(
                                            onClick = { showAttachmentMenu = true },
                                            enabled = !isGenerating && !inputBlockedUntilModelReady
                                        ) {
                                            Icon(
                                                Icons.Default.Add,
                                                contentDescription = "Add image",
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = showAttachmentMenu,
                                            onDismissRequest = { showAttachmentMenu = false },
                                        ) {
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        "Best for readable text in images.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                },
                                                onClick = {},
                                                enabled = false,
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Take a picture") },
                                                onClick = {
                                                    showAttachmentMenu = false
                                                    onTakePicture()
                                                },
                                                enabled = !isGenerating && !inputBlockedUntilModelReady,
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Upload image") },
                                                onClick = {
                                                    showAttachmentMenu = false
                                                    onUploadImage()
                                                },
                                                enabled = !isGenerating && !inputBlockedUntilModelReady,
                                            )
                                        }
                                    }
                                },
                                enabled = !isGenerating && !inputBlockedUntilModelReady,
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
                            FilledIconButton(
                                onClick = {
                                    val text = inputText.trim()
                                    if (text.isNotEmpty()) {
                                        onSend(text, false)
                                        inputText = ""
                                    }
                                },
                                enabled = inputText.isNotBlank() && !isGenerating && !inputBlockedUntilModelReady,
                                modifier = Modifier.size(52.dp),
                                shape = CircleShape,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
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

        if (settingsPage != null) {
            ChatSettingsOverlay(
                page = settingsPage!!,
                settings = settings,
                onChangePage = { settingsPage = it },
                onDismissRoot = { settingsPage = null },
                onSettingsChanged = onSettingsChanged,
                onReadAloudYesSelected = onReadAloudYesSelected,
            )
        }
    }

    }
}

@Composable
private fun SubjectModeTileRow(
    settings: InferenceSettings,
    onSettingsChanged: (InferenceSettings) -> Unit,
    enabled: Boolean,
    anchorModifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = anchorModifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = SUBJECT_TILES,
            key = { it.second.name },
        ) { (label, mode, icon) ->
            val selected = subjectTileSelected(settings.subjectMode, mode)
            FilterChip(
                selected = selected,
                onClick = {
                    if (!enabled) return@FilterChip
                    val next = if (selected) {
                        null
                    } else {
                        when (mode) {
                            SubjectMode.GENERAL -> SubjectMode.GENERAL
                            SubjectMode.SCIENCE -> SubjectMode.SCIENCE
                            else -> mode
                        }
                    }
                    onSettingsChanged(settings.copy(subjectMode = next))
                },
                label = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(label)
                    }
                },
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun MessagesList(
    messages: List<ChatMessage>,
    isGenerating: Boolean = false,
    isLoadingModel: Boolean = false,
    modelLoadError: String? = null,
    onRetryModelLoad: () -> Unit = {},
    chatInputMode: ChatInputMode = ChatInputMode.TextAndVoice,
    showThoughtProcess: Boolean = true,
    displayStrings: ChatDisplayStrings,
    onSparkChipSend: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (messages.isEmpty()) {
        val logo = rememberGyangoAILogo()
        val (titleLine, subtitleLine) = when (chatInputMode) {
            ChatInputMode.VoicePrimary ->
                displayStrings.emptyVoicePrimaryTitle to displayStrings.emptyVoicePrimarySubtitle
            ChatInputMode.TextAndVoice ->
                displayStrings.emptyTextModeTitle to displayStrings.emptyTextModeSubtitle
        }
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                modelLoadError != null -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (logo != null) {
                            Image(
                                bitmap = logo,
                                contentDescription = displayStrings.topBarTitle,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(18.dp))
                            )
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = EntryScreenCardShape,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    text = displayStrings.modelLoadFailedTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                Text(
                                    text = modelLoadError,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                OutlinedButton(
                                    onClick = onRetryModelLoad,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = EntryScreenFieldShape,
                                ) {
                                    Text(displayStrings.modelLoadRetryButton)
                                }
                            }
                        }
                    }
                }
                isLoadingModel -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (logo != null) {
                            Image(
                                bitmap = logo,
                                contentDescription = displayStrings.topBarTitle,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(18.dp))
                            )
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = EntryScreenCardShape,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Text(
                                    text = displayStrings.loadingDeviceModelTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    text = displayStrings.loadingDeviceModelSubtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                                CircularProgressIndicator(
                                    modifier = Modifier.size(40.dp),
                                    strokeWidth = 3.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
                else -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (logo != null) {
                            Image(
                                bitmap = logo,
                                contentDescription = displayStrings.topBarTitle,
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
                                    text = "g",
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
            }
        }
    } else {
        val lastMessage = messages.lastOrNull()
        val assistantLogo = rememberGyangoAILogo()
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
                MessageBubble(
                    message = message,
                    assistantLogo = assistantLogo,
                    showTypingPlaceholder = false,
                    loadingModelPlaceholder = false,
                    showLivePresence = isGenerating && isLast && message.sender == Sender.ASSISTANT,
                    showThoughtProcess = showThoughtProcess,
                    loadingModelMessage = displayStrings.loadingModelMessage,
                    jsonStreamingPlaceholder = displayStrings.assistantJsonStreamingPlaceholder,
                    awaitingStreamHints = displayStrings.assistantAwaitingStreamHints,
                    isGenerating = isGenerating,
                    onSparkChipSend = onSparkChipSend,
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
    showThoughtProcess: Boolean = true,
    loadingModelMessage: String = "Getting the model ready — one moment.",
    jsonStreamingPlaceholder: String = "Thinking…",
    awaitingStreamHints: List<String> = emptyList(),
    isGenerating: Boolean = false,
    onSparkChipSend: (String) -> Unit = {},
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
    val hasAssistantBubbleContent =
        isUser ||
            showTypingPlaceholder ||
            loadingModelPlaceholder ||
            message.text.isNotBlank() ||
            (!isUser && showLivePresence)

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
                        rawEnvelopeForLessonParsing = message.rawAssistantEnvelope,
                        textColor = textColor,
                        parsedSparksCsv = message.outputSparksCsv,
                        streamInProgress = showLivePresence,
                        showThoughtProcess = showThoughtProcess,
                        jsonStreamingPlaceholder = jsonStreamingPlaceholder,
                        awaitingStreamHints = awaitingStreamHints,
                        onSparkChipClick = onSparkChipSend,
                        sparkChipsEnabled = !isGenerating && !showLivePresence,
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
        if (hasAssistantBubbleContent) {
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
}

private enum class ChatSettingsPage { Hub, Profile, Chat, Interests, SpeechLocale }

private const val LONG_ANSWER_MIN_TOKENS = 400

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatSettingsOverlay(
    page: ChatSettingsPage,
    settings: InferenceSettings,
    onChangePage: (ChatSettingsPage) -> Unit,
    onDismissRoot: () -> Unit,
    onSettingsChanged: (InferenceSettings) -> Unit,
    onReadAloudYesSelected: (speechInputLocaleTag: String) -> Unit = {},
) {
    var speechLocaleTag by remember(settings.speechInputLocaleTag) {
        mutableStateOf(settings.speechInputLocaleTag)
    }
    LaunchedEffect(settings.speechInputLocaleTag) {
        speechLocaleTag = settings.speechInputLocaleTag
    }
    val context = LocalContext.current.applicationContext
    val locStrings = remember(context, speechLocaleTag) { chatDisplayStringsForLocale(context, speechLocaleTag) }
    val appVersion = rememberAppVersionInfo()

    var profileFirst by remember { mutableStateOf(settings.userFirstName) }
    var profileLast by remember { mutableStateOf(settings.userLastName) }
    var profileEmail by remember { mutableStateOf(settings.userEmail) }
    var profileBirthMonth by remember { mutableStateOf(settings.birthMonth) }
    var profileBirthYear by remember { mutableStateOf(settings.birthYear) }
    var profileAssistantSpeech by remember { mutableStateOf(settings.assistantSpeechEnabled) }

    var chatTemp by remember { mutableStateOf(settings.temperature) }
    var chatLongAnswers by remember(settings.maxTokens) {
        mutableStateOf(settings.maxTokens >= LONG_ANSWER_MIN_TOKENS)
    }
    var chatLowPower by remember { mutableStateOf(settings.lowPowerMode) }
    var chatRequestModelThought by remember { mutableStateOf(settings.requestModelThoughtInJson) }

    LaunchedEffect(settings.requestModelThoughtInJson) {
        chatRequestModelThought = settings.requestModelThoughtInJson
    }
    LaunchedEffect(settings.maxTokens) {
        chatLongAnswers = settings.maxTokens >= LONG_ANSWER_MIN_TOKENS
    }

    LaunchedEffect(page) {
        when (page) {
            ChatSettingsPage.Profile -> {
                profileFirst = settings.userFirstName
                profileLast = settings.userLastName
                profileEmail = settings.userEmail
                profileBirthMonth = settings.birthMonth
                profileBirthYear = settings.birthYear
                profileAssistantSpeech = settings.assistantSpeechEnabled
            }
            ChatSettingsPage.Chat -> {
                chatTemp = settings.temperature
                chatLongAnswers = settings.maxTokens >= LONG_ANSWER_MIN_TOKENS
                chatLowPower = settings.lowPowerMode
                chatRequestModelThought = settings.requestModelThoughtInJson
            }
            else -> Unit
        }
    }

    fun navigateBack() {
        when (page) {
            ChatSettingsPage.Profile, ChatSettingsPage.Chat, ChatSettingsPage.Interests ->
                onChangePage(ChatSettingsPage.Hub)
            ChatSettingsPage.SpeechLocale -> onChangePage(ChatSettingsPage.Profile)
            ChatSettingsPage.Hub -> onDismissRoot()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize().zIndex(8f),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = when (page) {
                                ChatSettingsPage.Hub -> locStrings.settingsHubTitle
                                ChatSettingsPage.Profile -> locStrings.settingsProfileTitle
                                ChatSettingsPage.Chat -> locStrings.settingsChatTitle
                                ChatSettingsPage.Interests -> locStrings.settingsInterestsTitle
                                ChatSettingsPage.SpeechLocale -> locStrings.settingsSpeechPickerTitle
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (page == ChatSettingsPage.Hub) onDismissRoot() else navigateBack()
                        }) {
                            Icon(
                                imageVector = if (page == ChatSettingsPage.Hub) {
                                    Icons.Default.Close
                                } else {
                                    Icons.AutoMirrored.Filled.ArrowBack
                                },
                                contentDescription = if (page == ChatSettingsPage.Hub) {
                                    locStrings.settingsClose
                                } else {
                                    locStrings.settingsBack
                                },
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            },
            bottomBar = {
                when (page) {
                    ChatSettingsPage.Profile -> {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedButton(
                                onClick = { onChangePage(ChatSettingsPage.Hub) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(locStrings.settingsCancel)
                            }
                            Button(
                                onClick = {
                                    if (profileAssistantSpeech) {
                                        onReadAloudYesSelected(speechLocaleTag)
                                    }
                                    onSettingsChanged(
                                        settings.copy(
                                            userFirstName = profileFirst.trim(),
                                            userLastName = profileLast.trim(),
                                            userEmail = profileEmail.trim(),
                                            birthMonth = profileBirthMonth,
                                            birthYear = profileBirthYear,
                                            speechInputLocaleTag = speechLocaleTag,
                                            assistantSpeechEnabled = profileAssistantSpeech,
                                        ),
                                    )
                                    onChangePage(ChatSettingsPage.Hub)
                                },
                                enabled = profileFirst.trim().isNotEmpty() && profileLast.trim().isNotEmpty(),
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                ),
                            ) {
                                Text(locStrings.settingsApply)
                            }
                        }
                    }
                    ChatSettingsPage.Chat -> {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedButton(
                                onClick = { onChangePage(ChatSettingsPage.Hub) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(locStrings.settingsCancel)
                            }
                            Button(
                                onClick = {
                                    val maxTokens = ChatPreferenceMappings
                                        .maxTokensForLongAnswersToggle(chatLongAnswers)
                                        .coerceIn(64, LlmDefaults.MAX_NEW_TOKENS_CAP)
                                    onSettingsChanged(
                                        settings.copy(
                                            temperature = chatTemp.coerceIn(
                                                LlmDefaults.LITERT_MIN_TEMPERATURE,
                                                LlmDefaults.LITERT_MAX_TEMPERATURE,
                                            ),
                                            maxTokens = maxTokens,
                                            lowPowerMode = chatLowPower,
                                            requestModelThoughtInJson = chatRequestModelThought,
                                        ),
                                    )
                                    onChangePage(ChatSettingsPage.Hub)
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                ),
                            ) {
                                Text(locStrings.settingsApply)
                            }
                        }
                    }
                    ChatSettingsPage.Interests -> {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onSettingsChanged(settings.copy(interestSignals = emptyList()))
                                },
                                enabled = settings.interestSignals.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(locStrings.settingsInterestsClearButton)
                            }
                            Button(
                                onClick = { onChangePage(ChatSettingsPage.Hub) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                ),
                            ) {
                                Text(locStrings.settingsInterestsDoneButton)
                            }
                        }
                    }
                    else -> Spacer(Modifier.height(0.dp))
                }
            },
        ) { innerPadding ->
            Column(
                Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    when (page) {
                        ChatSettingsPage.Hub -> {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onChangePage(ChatSettingsPage.Profile) },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Column {
                                        Text(
                                            locStrings.settingsHubProfileTitle,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            locStrings.settingsHubProfileSubtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onChangePage(ChatSettingsPage.Chat) },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Default.Tune,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Column {
                                        Text(
                                            locStrings.settingsHubChatTitle,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            locStrings.settingsHubChatSubtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onChangePage(ChatSettingsPage.Interests) },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Column {
                                        Text(
                                            locStrings.settingsHubInterestsTitle,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            locStrings.settingsHubInterestsSubtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Column {
                                        Text(
                                            locStrings.settingsAboutTitle,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        val versionLine = appVersion?.let { info ->
                                            String.format(
                                                Locale.ROOT,
                                                locStrings.settingsVersionFormat,
                                                info.versionName.ifEmpty { "—" },
                                                info.versionCode.toString(),
                                            )
                                        }
                                        if (versionLine != null) {
                                            Text(
                                                text = versionLine,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        ChatSettingsPage.Profile -> {
                            val hintStyle = MaterialTheme.typography.labelSmall
                            val hintColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)

                            OutlinedTextField(
                                value = profileFirst,
                                onValueChange = { profileFirst = it },
                                label = { Text(locStrings.onboardingFirstNameLabel) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                            )
                            OutlinedTextField(
                                value = profileLast,
                                onValueChange = { profileLast = it },
                                label = { Text(locStrings.onboardingLastNameLabel) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                            )
                            OutlinedTextField(
                                value = profileEmail,
                                onValueChange = { profileEmail = it },
                                label = { Text(locStrings.profileEmailLabel) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                            )
                            Text(
                                text = locStrings.onboardingBirthOptionalSection,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                            )
                            BirthMonthYearFields(
                                month = profileBirthMonth,
                                onMonthChange = { profileBirthMonth = it },
                                year = profileBirthYear,
                                onYearChange = { profileBirthYear = it },
                                monthSectionLabel = {},
                                monthDropdownLabel = locStrings.onboardingBirthMonthLabel,
                                yearDropdownLabel = locStrings.onboardingBirthYearLabel,
                                notSetLabel = locStrings.onboardingBirthMonthNotSet,
                            )
                            Text(
                                text = locStrings.settingsBirthAgeHint,
                                style = hintStyle,
                                color = hintColor,
                            )
                            Text(
                                locStrings.settingsConversationLanguage,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            val localeLabel = SpeechInputLocales.OPTIONS.find { it.first == speechLocaleTag }?.second
                                ?: "English"
                            Text(
                                text = localeLabel,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            TextButton(onClick = { onChangePage(ChatSettingsPage.SpeechLocale) }) {
                                Text(locStrings.settingsChangeConversationLanguage)
                            }
                            Text(
                                text = locStrings.settingsConversationLanguageHint,
                                style = hintStyle,
                                color = hintColor,
                            )
                            Text(
                                text = locStrings.onboardingReadAloudSection,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FilterChip(
                                    selected = !profileAssistantSpeech,
                                    onClick = { profileAssistantSpeech = false },
                                    label = { Text(locStrings.onboardingReadAloudNoLabel) },
                                    shape = EntryScreenChipShape,
                                )
                                FilterChip(
                                    selected = profileAssistantSpeech,
                                    onClick = {
                                        profileAssistantSpeech = true
                                        onReadAloudYesSelected(speechLocaleTag)
                                    },
                                    label = { Text(locStrings.onboardingReadAloudYesLabel) },
                                    shape = EntryScreenChipShape,
                                )
                            }
                        }

                        ChatSettingsPage.Chat -> {
                            val hintStyle = MaterialTheme.typography.labelSmall
                            val hintColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = chatLongAnswers,
                                    onCheckedChange = { chatLongAnswers = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                                    ),
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    locStrings.settingsChatLongAnswersTitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            Text(
                                text = locStrings.settingsChatLongAnswersHint,
                                style = hintStyle,
                                color = hintColor,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = chatRequestModelThought,
                                    onCheckedChange = { chatRequestModelThought = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                                    ),
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    locStrings.settingsChatReasoningTitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            Text(
                                text = locStrings.settingsChatReasoningHint,
                                style = hintStyle,
                                color = hintColor,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "${locStrings.settingsChatCreativityTitle} ${"%.2f".format(chatTemp)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                                Slider(
                                    value = chatTemp,
                                    onValueChange = {
                                        chatTemp = it.coerceIn(
                                            LlmDefaults.LITERT_MIN_TEMPERATURE,
                                            LlmDefaults.LITERT_MAX_TEMPERATURE,
                                        )
                                    },
                                    valueRange = LlmDefaults.LITERT_MIN_TEMPERATURE..LlmDefaults.LITERT_MAX_TEMPERATURE,
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                    ),
                                )
                                Text(
                                    text = locStrings.settingsChatCreativityHint,
                                    style = hintStyle,
                                    color = hintColor,
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = chatLowPower,
                                    onCheckedChange = { chatLowPower = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                                    ),
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    locStrings.settingsChatLowPowerTitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            Text(
                                text = locStrings.settingsChatLowPowerHint,
                                style = hintStyle,
                                color = hintColor,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }

                        ChatSettingsPage.Interests -> {
                            Text(
                                text = locStrings.settingsInterestsHint,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (settings.interestSignals.isEmpty()) {
                                Text(
                                    text = locStrings.settingsInterestsEmpty,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            } else {
                                settings.interestSignals.asReversed().forEach { row ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        ),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text(
                                                text = row.areaOfInterest,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            Text(
                                                text = "${locStrings.settingsInterestsTopicPrefix}: ${row.subjectKey}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Text(
                                                text = "${locStrings.settingsInterestsQuestionPrefix}: ${row.userQuerySnippet}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        ChatSettingsPage.SpeechLocale -> {
                            SpeechInputLocales.OPTIONS.forEach { (tag, name) ->
                                TextButton(
                                    onClick = {
                                        speechLocaleTag = tag
                                        onChangePage(ChatSettingsPage.Profile)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = name,
                                        modifier = Modifier.fillMaxWidth(),
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
