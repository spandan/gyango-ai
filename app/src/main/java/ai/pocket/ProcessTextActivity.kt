package ai.pocket

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import ai.pocket.chatbot.ui.AssistantReadableText
import ai.pocket.chatbot.ui.MessageTextFormatter
import ai.pocket.chatbot.ui.theme.PocketAITheme
import ai.pocket.api.PromptBuilder
import ai.pocket.core.InferenceSettings
import ai.pocket.core.LlmDefaults
import ai.pocket.core.LoadingPhase
import ai.pocket.orchestration.OrchestrationRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun getActionFromComponent(className: String?): ProcessTextAction = when {
    className?.contains("Summarize") == true -> ProcessTextAction.SUMMARIZE
    className?.contains("Analyze") == true -> ProcessTextAction.ANALYZE
    className?.contains("Draft") == true -> ProcessTextAction.DRAFT_REPLY
    else -> ProcessTextAction.SUMMARIZE
}

private enum class ProcessTextAction {
    SUMMARIZE,
    ANALYZE,
    DRAFT_REPLY
}

class ProcessTextActivity : ComponentActivity() {

    override fun onResume() {
        super.onResume()
        (application as PocketAIApplication).resetModelIdleTimer()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val selectedText = intent?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            ?: intent?.getStringExtra(Intent.EXTRA_TEXT)
        val action = getActionFromComponent(intent?.component?.className)

        if (selectedText.isNullOrBlank()) {
            Toast.makeText(this, "No text selected", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            PocketAITheme {
                ProcessTextScreen(
                    selectedText = selectedText,
                    action = action,
                    orchestrator = (application as PocketAIApplication).orchestrator,
                    onCopy = { text ->
                        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Pocket AI", text))
                        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    onClose = { finish() }
                )
            }
        }
    }
}

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun ProcessTextScreen(
    selectedText: String,
    action: ProcessTextAction,
    orchestrator: ai.pocket.orchestration.Orchestrator,
    onCopy: (String) -> Unit,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(true) }
    var isLoadingModel by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val sanitizedResult = remember(result) { MessageTextFormatter.readableAssistantText(result) }
    val resultScroll = rememberScrollState()

    LaunchedEffect(result.length) {
        if (result.isNotEmpty()) {
            kotlinx.coroutines.delay(32)
            resultScroll.scrollTo(resultScroll.maxValue)
        }
    }

    LaunchedEffect(selectedText, action) {
        val userTask = when (action) {
            ProcessTextAction.SUMMARIZE ->
                "Summarize the following text in a few short paragraphs or bullets. Be concise.\n\n$selectedText"
            ProcessTextAction.ANALYZE ->
                "List key points, themes, and insights from the text below. Use short bullets.\n\n$selectedText"
            ProcessTextAction.DRAFT_REPLY ->
                "Draft a short professional reply to the message below. No subject line.\n\n$selectedText"
        }
        val prompt = PromptBuilder.formatPocketSingleTurn(userTask)
        try {
            orchestrator.generate(
                request = OrchestrationRequest(
                    prompt = prompt,
                    settings = InferenceSettings(temperature = 0.80f, maxTokens = LlmDefaults.MAX_NEW_TOKENS_CAP),
                    toolId = "process_text"
                ),
                onToken = { token ->
                    scope.launch(Dispatchers.Main.immediate) {
                        result += token
                    }
                },
                onPhaseChange = { phase ->
                    scope.launch(Dispatchers.Main.immediate) {
                        isLoadingModel = (phase == LoadingPhase.LOADING_MODEL)
                    }
                }
            )
        } catch (e: Exception) {
            error = e.message ?: "Unknown error"
        }
        isGenerating = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pocket AI") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    if (sanitizedResult.isNotEmpty()) {
                        IconButton(onClick = { onCopy(sanitizedResult) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                        }
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = when {
                    isLoadingModel -> "Preparing AI… (one-time, ~5 sec)"
                    isGenerating && result.isEmpty() -> when (action) {
                        ProcessTextAction.SUMMARIZE -> "Summarizing…"
                        ProcessTextAction.ANALYZE -> "Analyzing…"
                        ProcessTextAction.DRAFT_REPLY -> "Drafting reply…"
                    }
                    else -> when (action) {
                        ProcessTextAction.SUMMARIZE -> "Summarize"
                        ProcessTextAction.ANALYZE -> "Analyze"
                        ProcessTextAction.DRAFT_REPLY -> "Draft reply"
                    }
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Selected text:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 120.dp),
                tonalElevation = 1.dp,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = selectedText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Result:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))

            if (error != null) {
                Text(
                    text = "Error: $error",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else if (isGenerating && result.isEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text(
                        text = if (isLoadingModel) "Loading AI model…" else "Thinking…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    tonalElevation = 1.dp,
                    shape = MaterialTheme.shapes.medium
                ) {
                    if (result.isEmpty()) {
                        Text(
                            text = "Processing...",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        AssistantReadableText(
                            raw = result,
                            textColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(resultScroll)
                                .padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}
