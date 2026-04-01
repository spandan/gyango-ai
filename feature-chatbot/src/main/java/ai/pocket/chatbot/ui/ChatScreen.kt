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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
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

/** Friendly on-device persona label (feels like a person, not “Chatbot”). */
private const val ASSISTANT_NAME = "Pocket"

private val PresenceGreen = Color(0xFF43A047)

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
                    text = ASSISTANT_NAME.take(1),
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
    onSend: (String) -> Unit,
    onSettingsChanged: (InferenceSettings) -> Unit,
    onClearChat: () -> Unit = {},
    onAttachDocument: () -> Unit = {},
    documentError: String? = null,
    onDismissDocumentError: () -> Unit = {},
    onNavigateBack: (() -> Unit)? = null
) {
    var inputText by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }

    Scaffold(
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
                                text = "Private · here when you need me",
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

            documentError?.let { error ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
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

        if (showSettings) {
            SettingsDialog(
                settings = settings,
                onDismiss = { showSettings = false },
                onSettingsChanged = { onSettingsChanged(it) }
            )
        }
    }
}

@Composable
private fun MessagesList(
    messages: List<ChatMessage>,
    isGenerating: Boolean = false,
    isLoadingModel: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (messages.isEmpty()) {
        val logo = rememberPocketAILogo()
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
                    text = "Say hi to $ASSISTANT_NAME",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "It’s just you and me — everything stays on your phone.",
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
                    showLivePresence = isGenerating && isLast && message.sender == Sender.ASSISTANT
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
    showLivePresence: Boolean = false
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
                    text = "Getting the model ready — one moment.",
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
                text = ASSISTANT_NAME,
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
    var temp by remember { mutableStateOf(settings.temperature) }
    val maxTokCap = LlmDefaults.MAX_NEW_TOKENS_CAP.toFloat()
    var maxTokens by remember(settings.maxTokens) {
        mutableStateOf(settings.maxTokens.toFloat().coerceIn(256f, maxTokCap))
    }
    var lowPower by remember { mutableStateOf(settings.lowPowerMode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        text = "Caps how long each reply can grow. Raise this for fuller explanations; very long chats drop older turns to stay within the model’s context.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
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
                            "Low power mode",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = "Reserved for future power tuning. Inference uses a fixed thread cap for stable replies; toggling does not reload the model.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
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
                            lowPowerMode = lowPower
                        )
                    )
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}
