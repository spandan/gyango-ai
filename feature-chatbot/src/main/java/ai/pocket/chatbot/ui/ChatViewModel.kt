package ai.pocket.chatbot.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import ai.pocket.api.PromptBuilder
import ai.pocket.api.ResponseSanitizer
import ai.pocket.api.StreamingResponseSanitizer
import ai.pocket.chatbot.data.ChatRepository
import ai.pocket.chatbot.data.DocumentExtractor
import ai.pocket.core.ChatMessage
import ai.pocket.core.InferenceSettings
import ai.pocket.core.LlmDefaults
import ai.pocket.core.LoadingPhase
import ai.pocket.core.Sender
import ai.pocket.orchestration.OrchestrationRequest
import ai.pocket.orchestration.Orchestrator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

class ChatViewModel(
    private val orchestrator: Orchestrator,
    private val repository: ChatRepository,
    private val appContext: Context
) : ViewModel() {

    companion object {
        private const val TAG = "ChatViewModel"
    }

    private val idGenerator = AtomicLong(0L)

    private fun sanitizeVisibleAssistant(raw: String): String =
        ResponseSanitizer.sanitize(raw)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _isLoadingModel = MutableStateFlow(false)
    val isLoadingModel: StateFlow<Boolean> = _isLoadingModel.asStateFlow()

    private val _settings = MutableStateFlow(InferenceSettings())
    val settings: StateFlow<InferenceSettings> = _settings.asStateFlow()

    private val _documentError = MutableStateFlow<String?>(null)
    val documentError: StateFlow<String?> = _documentError.asStateFlow()

    init {
        viewModelScope.launch {
            repository.historyFlow.collect { list ->
                if (!_isGenerating.value) {
                    _messages.value = list
                }
                val maxId = list.maxOfOrNull { it.id } ?: 0L
                idGenerator.set(maxId)
            }
        }
    }

    fun onUserSend(text: String) {
        if (text.isBlank() || _isGenerating.value) return

        val now = System.currentTimeMillis()
        val userMessage = ChatMessage(
            id = idGenerator.incrementAndGet(),
            sender = Sender.USER,
            text = text.trim(),
            timestamp = now
        )

        val assistantMessage = ChatMessage(
            id = idGenerator.incrementAndGet(),
            sender = Sender.ASSISTANT,
            text = "",
            timestamp = now
        )

        _messages.value = _messages.value + listOf(userMessage, assistantMessage)

        generateAssistantResponse()
    }

    private fun generateAssistantResponse() {
        val currentMessages = _messages.value
        val assistantMessageId = currentMessages.last().id
        val prompt = PromptBuilder.buildChatPrompt(
            messages = currentMessages,
            maxTokensHeadroom = _settings.value.maxTokens.coerceIn(64, LlmDefaults.MAX_NEW_TOKENS_CAP)
        )
        val sanitizer = StreamingResponseSanitizer()

        viewModelScope.launch {
            _isGenerating.value = true
            var currentAssistantText = ""
            
            // Use a channel to bridge between the callback and the UI update coroutine
            val tokenChannel = Channel<String>(Channel.UNLIMITED)

            // UI Updater coroutine: collects tokens and batches updates
            val uiUpdater = launch {
                tokenChannel.receiveAsFlow().collect { token ->
                    val cleanChunk = sanitizer.process(token)
                    if (cleanChunk.isNotEmpty()) {
                        currentAssistantText = sanitizeVisibleAssistant(currentAssistantText + cleanChunk)
                        _messages.value = _messages.value.map {
                            if (it.id == assistantMessageId) {
                                it.copy(text = currentAssistantText)
                            } else it
                        }
                    }
                }
            }

            try {
                orchestrator.generate(
                    request = OrchestrationRequest(
                        prompt = prompt,
                        settings = _settings.value,
                        toolId = "chatbot"
                    ),
                    onToken = { token ->
                        val sent = tokenChannel.trySend(token)
                        if (!sent.isSuccess) {
                            Log.e(TAG, "Token not accepted by channel: ${sent.exceptionOrNull()?.message}")
                        }
                    },
                    onPhaseChange = { phase ->
                        viewModelScope.launch(Dispatchers.Main.immediate) {
                            _isLoadingModel.value = (phase == LoadingPhase.LOADING_MODEL)
                        }
                    }
                )
            } catch (e: Exception) {
                _messages.value = _messages.value.map {
                    if (it.id == assistantMessageId && it.text.isEmpty()) {
                        it.copy(text = "Error: ${e.message ?: "Unknown error"}")
                    } else it
                }
            } finally {
                tokenChannel.close()
                uiUpdater.join()
                val tail = sanitizer.finalFlush()
                if (tail.isNotEmpty()) {
                    currentAssistantText = sanitizeVisibleAssistant(currentAssistantText + tail)
                    _messages.value = _messages.value.map {
                        if (it.id == assistantMessageId) it.copy(text = currentAssistantText) else it
                    }
                } else if (currentAssistantText.isNotEmpty()) {
                    val cleaned = sanitizeVisibleAssistant(currentAssistantText)
                    if (cleaned != currentAssistantText) {
                        currentAssistantText = cleaned
                        _messages.value = _messages.value.map {
                            if (it.id == assistantMessageId) it.copy(text = currentAssistantText) else it
                        }
                    }
                }
                _isGenerating.value = false
                _isLoadingModel.value = false
                repository.saveHistory(_messages.value)
            }
        }
    }

    fun updateSettings(newSettings: InferenceSettings) {
        _settings.value = newSettings.copy(
            maxTokens = newSettings.maxTokens.coerceIn(64, LlmDefaults.MAX_NEW_TOKENS_CAP)
        )
    }

    fun clearChat() {
        viewModelScope.launch {
            if (_isGenerating.value) return@launch
            _messages.value = emptyList()
            repository.clearHistory()
        }
    }

    fun onDocumentAttached(uri: Uri, mimeType: String?) {
        if (_isGenerating.value) return

        viewModelScope.launch {
            _documentError.value = null
            val result = withContext(Dispatchers.IO) {
                DocumentExtractor.extract(appContext, uri, mimeType, maxChars = 2500)
            }
            when (result) {
                is DocumentExtractor.Result.Success -> {
                    val fileName = result.fileName ?: "Document"
                    val summaryPrompt = "Please summarize the following document concisely:\n\n---\n$fileName\n---\n\n${result.text}"
                    onUserSend(summaryPrompt)
                }
                is DocumentExtractor.Result.Error -> {
                    _documentError.value = result.message
                }
            }
        }
    }

    fun clearDocumentError() {
        _documentError.value = null
    }
}
