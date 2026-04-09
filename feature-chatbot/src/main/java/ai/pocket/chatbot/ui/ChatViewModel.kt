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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

/**
 * Chat UI state and streaming inference.
 *
 * **Initialization:** On startup we **warm up** the default Gemma model on a background dispatcher
 * (same idea as `lifecycleScope.launch(Dispatchers.IO) { manager.initialize(...) }` in an Activity).
 * The app uses a shared [Orchestrator] → [ai.pocket.models.gemma.GemmaLiteRtLlm], which copies the
 * bundled asset ([ai.pocket.models.gemma.GemmaLiteRtLlm.DEFAULT_MODEL_ASSET]) to app storage before
 * opening LiteRT. For a **standalone** flow with a file you downloaded yourself:
 *
 * ```
 * viewModelScope.launch(Dispatchers.IO) {
 *     val manager = GemmaModelManager(applicationContext)
 *     manager.initialize(File(filesDir, "gemma-4-E2B-it-int4.litertlm").absolutePath)
 *     manager.chatStream("Hello").collect { chunk ->
 *         withContext(Dispatchers.Main) { /* append chunk to UI */ }
 *     }
 * }
 * ```
 *
 * Voice input uses the system speech recognizer.
 */
class ChatViewModel(
    private val orchestrator: Orchestrator,
    private val repository: ChatRepository,
    private val appContext: Context,
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

    /** Gemma warm-up at startup (never gated on [isGenerating]). */
    private val _isLlmBootstrapping = MutableStateFlow(false)

    /** Reloading Gemma mid-stream (e.g. after idle eviction). */
    private val _isPhaseLoadingModel = MutableStateFlow(false)

    /** True while Gemma is loading for chat (bootstrap or phase). */
    val isLoadingModel: StateFlow<Boolean> = combine(
        _isLlmBootstrapping,
        _isPhaseLoadingModel,
    ) { boot, phase -> boot || phase }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _settings = MutableStateFlow(InferenceSettings())
    val settings: StateFlow<InferenceSettings> = _settings.asStateFlow()

    private val _documentError = MutableStateFlow<String?>(null)
    val documentError: StateFlow<String?> = _documentError.asStateFlow()

    private val _isListeningToMic = MutableStateFlow(false)
    val isListeningToMic: StateFlow<Boolean> = _isListeningToMic.asStateFlow()

    private val _voiceError = MutableStateFlow<String?>(null)
    val voiceError: StateFlow<String?> = _voiceError.asStateFlow()

    init {
        viewModelScope.launch {
            repository.settingsFlow.collect { persisted ->
                _settings.value = persisted.copy(
                    maxTokens = persisted.maxTokens.coerceIn(64, LlmDefaults.MAX_NEW_TOKENS_CAP)
                )
            }
        }
        viewModelScope.launch {
            repository.historyFlow.collect { list ->
                if (!_isGenerating.value) {
                    _messages.value = list
                }
                val maxId = list.maxOfOrNull { it.id } ?: 0L
                idGenerator.set(maxId)
            }
        }
        // Eager-load Gemma (LiteRT-LM): copies asset → filesDir on first open inside [GemmaLiteRtLlm].
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main.immediate) { _isLlmBootstrapping.value = true }
            try {
                orchestrator.warmUpDefaultModel()
            } catch (e: Exception) {
                Log.e(TAG, "Gemma model warm-up failed", e)
            } finally {
                withContext(Dispatchers.Main.immediate) { _isLlmBootstrapping.value = false }
            }
        }
    }

    /** Reload Gemma + voice after idle eviction or returning to the app. Does not flip UI loading flags. */
    fun refreshModelsAfterForeground() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { orchestrator.warmUpDefaultModel() }
        }
    }

    /** Handles final text from the microphone and keeps replies in the user's selected language. */
    fun onVoiceTranscript(transcript: String) {
        if (transcript.isBlank() || _isGenerating.value) return
        val tag = _settings.value.speechInputLocaleTag
        val isEnglish = tag.startsWith("en", ignoreCase = true)
        val isTelugu = tag.startsWith("te", ignoreCase = true)
        val message = when {
            isEnglish || isTelugu -> transcript.trim()
            else ->
                "The user spoke in language/locale \"$tag\". Transcription (may be imperfect):\n${transcript.trim()}\n\nUnderstand their intent, and answer helpfully in the same language they used."
        }
        onUserSend(message)
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
            maxTokensHeadroom = _settings.value.maxTokens.coerceIn(64, LlmDefaults.MAX_NEW_TOKENS_CAP),
            preferredReplyLocaleTag = _settings.value.speechInputLocaleTag,
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
                            _isPhaseLoadingModel.value = (phase == LoadingPhase.LOADING_MODEL)
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
                _isPhaseLoadingModel.value = false
                repository.saveHistory(_messages.value)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
    }

    fun updateSettings(newSettings: InferenceSettings) {
        val coerced = newSettings.copy(
            maxTokens = newSettings.maxTokens.coerceIn(64, LlmDefaults.MAX_NEW_TOKENS_CAP)
        )
        _settings.value = coerced
        viewModelScope.launch {
            repository.saveInferenceSettings(coerced)
        }
    }

    fun setListeningToMic(listening: Boolean) {
        _isListeningToMic.value = listening
    }

    fun setVoiceError(message: String?) {
        _voiceError.value = message
    }

    fun clearVoiceError() {
        _voiceError.value = null
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
