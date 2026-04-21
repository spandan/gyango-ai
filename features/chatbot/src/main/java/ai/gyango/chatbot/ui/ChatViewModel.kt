package ai.gyango.chatbot.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import ai.gyango.chatbot.BuildConfig
import ai.gyango.api.PromptBuilder
import ai.gyango.api.prompts.ChatLessonPromptSpecs
import ai.gyango.assistant.AssistantOutput
import ai.gyango.assistant.AssistantParsingPipeline
import ai.gyango.assistant.GyangoOutputEnvelope
import ai.gyango.assistant.KidsSafetyPolicy
import ai.gyango.chatbot.data.ChatRepository
import ai.gyango.chatbot.data.ImageTextExtractor
import ai.gyango.core.ChatMessage
import ai.gyango.core.InferenceSettings
import ai.gyango.core.InterestCapture
import ai.gyango.core.InterestSignal
import ai.gyango.core.LearnerProfile
import ai.gyango.core.LearningProfile
import ai.gyango.core.hardware.ModelHardwareGate
import ai.gyango.core.LlmDefaults
import ai.gyango.core.LoadingPhase
import ai.gyango.core.Sender
import ai.gyango.core.SkillBand
import ai.gyango.core.SubjectMode
import ai.gyango.orchestration.OrchestrationRequest
import ai.gyango.orchestration.Orchestrator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.util.concurrent.atomic.AtomicLong

/**
 * Chat UI state and streaming inference.
 *
 * **Initialization:** On startup we **warm up** the default LiteRT model on a background dispatcher.
 * The app uses a shared [Orchestrator] → [ai.gyango.models.litert.LiteRtLlm]. Weights come from a bundled
 * asset `models/gemma-4-E2B-it.litertlm` ([ai.gyango.models.litert.LiteRtLlm.DEFAULT_MODEL_ASSET], copied once),
 * or an absolute path override.
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
        /** Defer [Orchestrator.generate] until after IME hide / layout settle (keep small — adds wall latency). */
        private const val INFERENCE_START_DELAY_MS = 80L
        /**
         * How often to push partial assistant text to Compose while streaming.
         * ~30fps is a good tradeoff: smooth enough to read, without flooding the main thread with per-char recompositions.
         */
        private const val STREAM_UI_FLUSH_MS = 33L
        /** Logcat line safety margin (Android effectively caps per call). */
        private const val LLM_LOG_CHUNK_CHARS = 3500

        /** Full prompt / response body, split for logcat (only when [BuildConfig.VERBOSE_DEBUG_LOGS]). */
        private fun logExactLlmPayload(reqId: Long, label: String, text: String) {
            if (!BuildConfig.VERBOSE_DEBUG_LOGS) return
            val n = text.length
            if (n == 0) {
                Log.i(TAG, "$label id=$reqId length=0\n<empty>")
                return
            }
            val totalParts = (n + LLM_LOG_CHUNK_CHARS - 1) / LLM_LOG_CHUNK_CHARS
            Log.i(TAG, "$label id=$reqId length=$n partCount=$totalParts")
            var offset = 0
            var part = 0
            while (offset < n) {
                val end = (offset + LLM_LOG_CHUNK_CHARS).coerceAtMost(n)
                part++
                Log.i(TAG, "$label id=$reqId part=$part/$totalParts\n${text.substring(offset, end)}")
                offset = end
            }
        }
    }

    private val idGenerator = AtomicLong(0L)
    private var pendingImageContextForNextPrompt: String? = null

    /**
     * Subject "lane" for cross-turn `MEMORY:` hints. Cleared when the learner changes topic tiles so
     * hints from math do not leak into science (see [memoryForNextPrompt]). Restored after the next
     * completed assistant message in the new lane.
     */
    private var conversationMemorySubject: SubjectMode? = null

    private fun subjectMemoryKey(mode: SubjectMode?): SubjectMode = when (ChatLessonPromptSpecs.effectiveSubjectMode(mode)) {
        null, SubjectMode.GENERAL, SubjectMode.CURIOSITY -> SubjectMode.GENERAL
        else -> ChatLessonPromptSpecs.effectiveSubjectMode(mode)!!
    }

    private fun interestSubjectKeyForSettings(mode: SubjectMode?): String {
        val eff = ChatLessonPromptSpecs.effectiveSubjectMode(mode)
        return when (eff) {
            null, SubjectMode.GENERAL, SubjectMode.CURIOSITY -> SubjectMode.GENERAL.name
            else -> eff.name
        }
    }

    private fun sanitizeVisibleAssistant(raw: String): String {
        val normalized = AssistantParsingPipeline.normalizeEnvelopeInput(raw)
        if (GyangoOutputEnvelope.isOutputFormat(normalized)) {
            GyangoOutputEnvelope.mergeForDisplayPolished(normalized)?.let { return it }
            GyangoOutputEnvelope.streamingLessonPreview(normalized)?.let {
                return AssistantParsingPipeline.sanitizeAggregateAfterEnvelopeNormalized(it)
            }
            return ""
        }
        return AssistantParsingPipeline.sanitizeAggregateAfterEnvelopeNormalized(normalized)
    }

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    /** LLM warm-up at startup (never gated on [isGenerating]). */
    private val _isLlmBootstrapping = MutableStateFlow(false)

    /** Reloading model mid-stream (e.g. after idle eviction). */
    private val _isPhaseLoadingModel = MutableStateFlow(false)

    /** True while the default LLM is loading for chat (bootstrap or phase). */
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

    private val _modelLoadError = MutableStateFlow<String?>(null)
    val modelLoadError: StateFlow<String?> = _modelLoadError.asStateFlow()

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
    }

    fun warmUpModel() {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main.immediate) {
                _modelLoadError.value = null
                _isLlmBootstrapping.value = true
            }
            try {
                if (!ModelHardwareGate.inspect(appContext).canRunSelectedModel) {
                    withContext(Dispatchers.Main.immediate) {
                        _modelLoadError.value = ModelHardwareGate.UNSUPPORTED_DEVICE_MESSAGE
                    }
                    return@launch
                }
                orchestrator.warmUpDefaultModel()
            } catch (e: Exception) {
                Log.e(TAG, "LiteRT model warm-up failed", e)
                withContext(Dispatchers.Main.immediate) {
                    _modelLoadError.value =
                        e.message?.trim()?.take(220)?.ifBlank { null } ?: "Model load failed"
                }
            } finally {
                withContext(Dispatchers.Main.immediate) { _isLlmBootstrapping.value = false }
            }
        }
    }

    /** Reload model + voice after idle eviction or returning to the app. Does not flip UI loading flags. */
    fun refreshModelsAfterForeground() {
        viewModelScope.launch(Dispatchers.IO) {
            if (!ModelHardwareGate.inspect(appContext).canRunSelectedModel) return@launch
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
        val trimmed = text.trim()
        val safety = KidsSafetyPolicy.screenInput(trimmed, _settings.value.safetyProfile)
        val safeReply = safety.safeReply
        if (safeReply != null) {
            appendSafeExchange(trimmed, safeReply)
            return
        }

        val now = System.currentTimeMillis()
        val userMessage = ChatMessage(
            id = idGenerator.incrementAndGet(),
            sender = Sender.USER,
            text = trimmed,
            timestamp = now
        )

        val assistantMessage = ChatMessage(
            id = idGenerator.incrementAndGet(),
            sender = Sender.ASSISTANT,
            text = "",
            timestamp = now
        )

        _messages.value = _messages.value + listOf(userMessage, assistantMessage)
        maybeUpdateLearnerSignals(trimmed)
        _isGenerating.value = true
        generateAssistantResponse()
    }

    private fun appendSafeExchange(userText: String, safeReply: String) {
        val now = System.currentTimeMillis()
        val userMessage = ChatMessage(
            id = idGenerator.incrementAndGet(),
            sender = Sender.USER,
            text = userText,
            timestamp = now,
        )
        val assistantMessage = ChatMessage(
            id = idGenerator.incrementAndGet(),
            sender = Sender.ASSISTANT,
            text = safeReply,
            timestamp = now,
        )
        _messages.value = _messages.value + listOf(userMessage, assistantMessage)
        viewModelScope.launch {
            repository.saveHistory(_messages.value)
        }
    }

    private fun maybeUpdateLearnerSignals(userText: String) {
        val profile = _settings.value.learnerProfile
        val trimmed = userText.trim()
        val curiosityBoost = if (trimmed.contains('?')) 1 else 0
        val supportBoost = if (Regex("""\b(help|simple|easier|don't understand|confusing)\b""", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)) 1 else 0
        val challengeBoost = if (Regex("""\b(why|how exactly|harder|challenge|deeper|more)\b""", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)) 1 else 0
        if (curiosityBoost == 0 && supportBoost == 0 && challengeBoost == 0) return
        val nextProfile = profile.copy(
            curiositySignals = (profile.curiositySignals + curiosityBoost).coerceAtMost(32),
            supportSignals = (profile.supportSignals + supportBoost).coerceAtMost(32),
            challengeSignals = (profile.challengeSignals + challengeBoost).coerceAtMost(32),
        )
        val inferred = inferLearningProfile(nextProfile)
        val inferredIq = inferIqLevel(nextProfile)
        updateSettings(
            _settings.value.copy(
                learnerProfile = nextProfile.copy(
                    learningProfile = inferred,
                    iqLevel = inferredIq,
                ),
            )
        )
    }

    private fun inferLearningProfile(profile: LearnerProfile): LearningProfile = when {
        profile.challengeSignals >= profile.supportSignals + 2 -> LearningProfile.BUILDER
        profile.curiositySignals >= profile.supportSignals -> LearningProfile.THINKER
        else -> LearningProfile.EXPLORER
    }

    private fun inferIqLevel(profile: LearnerProfile): Int {
        val base = 95
        val curiosityLift = profile.curiositySignals / 2
        val challengeLift = profile.challengeSignals * 2
        val supportOffset = profile.supportSignals
        return (base + curiosityLift + challengeLift - supportOffset).coerceIn(70, 145)
    }

    /**
     * Only the latest user turn is sent to the model: drop an in-flight blank assistant placeholder,
     * then take the last [Sender.USER] row (e.g. image synthetic context when appended last).
     */
    private fun statelessLastUserText(messages: List<ChatMessage>): String {
        val trimmed = if (messages.isNotEmpty() &&
            messages.last().sender == Sender.ASSISTANT &&
            messages.last().text.isBlank()
        ) {
            messages.dropLast(1)
        } else {
            messages
        }
        val idx = trimmed.indexOfLast { it.sender == Sender.USER }
        return if (idx >= 0) trimmed[idx].text.trim() else ""
    }

    /** `[SAVE_CONTEXT]` body from the last completed assistant message for the next `M:` line. */
    private fun memoryForNextPrompt(messages: List<ChatMessage>): String? {
        val currentKey = subjectMemoryKey(_settings.value.subjectMode)
        if (conversationMemorySubject == null || conversationMemorySubject != currentKey) return null
        val trimmed = if (messages.isNotEmpty() &&
            messages.last().sender == Sender.ASSISTANT &&
            messages.last().text.isBlank()
        ) {
            messages.dropLast(1)
        } else {
            messages
        }
        // Prefer the latest lesson-shaped assistant row so short UI hints (e.g. image attachment nudges)
        // do not steal the memory slot from the prior real answer.
        val structured = trimmed.lastOrNull { m ->
            m.sender == Sender.ASSISTANT &&
                m.text.isNotBlank() &&
                (m.rawAssistantEnvelope?.contains(GyangoOutputEnvelope.OUTPUT_MARKER) == true ||
                    m.text.contains(GyangoOutputEnvelope.OUTPUT_MARKER))
        }
        val source = structured
            ?: trimmed.lastOrNull { it.sender == Sender.ASSISTANT && it.text.isNotBlank() }
        return source?.outputMemoryHint?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun generateAssistantResponse() {
        val currentMessages = _messages.value
        val assistantMessageId = currentMessages.last().id
        val promptMessages = currentMessagesWithPendingImageContext(currentMessages)
        val lastUserOnly = statelessLastUserText(promptMessages)
        val memoryHint = memoryForNextPrompt(promptMessages)
        val promptTopic = PromptBuilder.topicLabelForPrompt(_settings.value.subjectMode)
        val prompt = PromptBuilder.buildChatPrompt(
            lastUserContent = lastUserOnly,
            memoryHint = memoryHint,
            maxTokensHeadroom = _settings.value.maxTokens.coerceIn(64, LlmDefaults.MAX_NEW_TOKENS_CAP),
            preferredReplyLocaleTag = _settings.value.speechInputLocaleTag,
            userFirstName = _settings.value.userFirstName,
            userLastName = _settings.value.userLastName,
            birthMonth = _settings.value.birthMonth,
            birthYear = _settings.value.birthYear,
            subjectMode = _settings.value.subjectMode,
            safetyProfile = _settings.value.safetyProfile,
            learnerProfile = _settings.value.learnerProfile,
            subjectSkillBands = _settings.value.subjectSkillBands,
            requestModelThoughtInJson = _settings.value.requestModelThoughtInJson,
        )
        val sanitizer = AssistantOutput.newLlmStreamSanitizer()
        val reqId = assistantMessageId
        val startedAtMs = System.currentTimeMillis()
        if (BuildConfig.VERBOSE_DEBUG_LOGS) {
            Log.i(
                TAG,
                "LLM REQUEST id=$reqId tool=chatbot temp=${_settings.value.temperature} " +
                    "maxTokens=${_settings.value.maxTokens} lowPower=${_settings.value.lowPowerMode} " +
                    "locale=${_settings.value.speechInputLocaleTag} " +
                    "subjectMode=${_settings.value.subjectMode ?: "GENERAL"}"
            )
            Log.i(TAG, "========== TOPIC=$promptTopic (prompt <Topic> line) ==========")
        }

        val capturedUserTextForInterest = lastUserOnly.trim()
        val interestSubjectKeySnapshot = interestSubjectKeyForSettings(_settings.value.subjectMode)

        viewModelScope.launch {
            if (!ModelHardwareGate.inspect(appContext).canRunSelectedModel) {
                val msg = ModelHardwareGate.UNSUPPORTED_DEVICE_MESSAGE
                _messages.value = _messages.value.map {
                    if (it.id == assistantMessageId) it.copy(text = "Error: $msg") else it
                }
                repository.saveHistory(_messages.value)
                _isGenerating.value = false
                return@launch
            }
            var rawAssistantText = ""
            var currentAssistantText = ""
            var rawTokenCount = 0
            var visibleChunkCount = 0

            // Bridge LiteRT token thread to Main:
            // - accumulate sanitized deltas into [rawAssistantText]
            // - throttle Compose updates to ~30fps (avoid per-char StateFlow spam)
            // - never explode multi-char LiteRT deltas into per-character ViewModel updates
            val tokenChannel = Channel<String>(Channel.UNLIMITED)
            var assistantTextDirty = false
            var collectTokensFinished = false
            val rawAppendMonitor = Any()
            var lastEmittedVisible: String? = null
            var lastEmittedSparks: String? = null
            var lastEmittedHint: String? = null
            var lastEmittedRawEnvelope: String? = null

            fun recomputeVisibleAssistantText(): String = sanitizeVisibleAssistant(rawAssistantText)

            fun emitAssistantVisibleText(nextVisible: String) {
                val rawSnap = synchronized(rawAppendMonitor) { rawAssistantText }
                val sparksParsed = AssistantOutput.extractSparksCsv(rawSnap)
                val hintParsed = AssistantOutput.extractOutputMemoryHint(rawSnap)
                if (nextVisible == lastEmittedVisible &&
                    sparksParsed == lastEmittedSparks &&
                    hintParsed == lastEmittedHint &&
                    rawSnap == lastEmittedRawEnvelope
                ) {
                    return
                }
                lastEmittedVisible = nextVisible
                lastEmittedSparks = sparksParsed
                lastEmittedHint = hintParsed
                lastEmittedRawEnvelope = rawSnap
                visibleChunkCount += 1
                currentAssistantText = nextVisible
                _messages.value = _messages.value.map {
                    if (it.id == assistantMessageId) {
                        it.copy(
                            text = nextVisible,
                            outputSparksCsv = sparksParsed ?: it.outputSparksCsv,
                            outputMemoryHint = hintParsed ?: it.outputMemoryHint,
                            rawAssistantEnvelope = rawSnap,
                        )
                    } else {
                        it
                    }
                }
            }

            val tokenCollector = launch(Dispatchers.Default) {
                try {
                    tokenChannel.receiveAsFlow().collect { token ->
                        rawTokenCount += 1
                        val cleanChunk = sanitizer.process(token)
                        if (cleanChunk.isEmpty()) return@collect
                        synchronized(rawAppendMonitor) {
                            rawAssistantText += cleanChunk
                            assistantTextDirty = true
                        }
                    }
                } finally {
                    collectTokensFinished = true
                }
            }

            val flushTicker = launch(Dispatchers.Main) {
                while (isActive && (!collectTokensFinished || assistantTextDirty)) {
                    delay(STREAM_UI_FLUSH_MS)
                    val shouldFlush = synchronized(rawAppendMonitor) {
                        if (!assistantTextDirty) return@synchronized false
                        assistantTextDirty = false
                        true
                    }
                    if (!shouldFlush) continue
                    emitAssistantVisibleText(recomputeVisibleAssistantText())
                    yield()
                }
            }

            try {
                delay(INFERENCE_START_DELAY_MS)
                val inferT0 = if (BuildConfig.VERBOSE_DEBUG_LOGS) SystemClock.elapsedRealtime() else 0L
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
                if (BuildConfig.VERBOSE_DEBUG_LOGS) {
                    Log.d(
                        TAG,
                        "[perf] orchestrator.generate wallMs=${SystemClock.elapsedRealtime() - inferT0} id=$reqId",
                    )
                }
            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - startedAtMs
                Log.e(TAG, "LLM ERROR id=$reqId elapsedMs=$elapsed message=${e.message}", e)
                _messages.value = _messages.value.map {
                    if (it.id == assistantMessageId && it.text.isEmpty()) {
                        it.copy(text = "Error: ${e.message ?: "Unknown error"}")
                    } else it
                }
            } finally {
                tokenChannel.close()
                tokenCollector.join()

                val tail = sanitizer.finish()
                if (tail.isNotEmpty()) {
                    synchronized(rawAppendMonitor) {
                        rawAssistantText += tail
                        assistantTextDirty = true
                    }
                }

                // Ensure the final bubble matches the fully materialized stream (sanitizer tail + last-minute edits).
                synchronized(rawAppendMonitor) {
                    assistantTextDirty = true
                }
                flushTicker.join()
                emitAssistantVisibleText(recomputeVisibleAssistantText())
                yield()

                if (currentAssistantText.isNotEmpty()) {
                    val moderated = KidsSafetyPolicy.moderateOutput(currentAssistantText, _settings.value.safetyProfile)
                    val moderatedReply = moderated.safeReply
                    if (moderatedReply != null && moderatedReply != currentAssistantText) {
                        currentAssistantText = moderatedReply
                        _messages.value = _messages.value.map {
                            if (it.id == assistantMessageId) it.copy(text = currentAssistantText) else it
                        }
                    }
                }
                val rawSnapshot = synchronized(rawAppendMonitor) { rawAssistantText }
                val hintParsed = AssistantOutput.extractOutputMemoryHint(rawSnapshot)
                val sparksParsed = AssistantOutput.extractSparksCsv(rawSnapshot)
                _messages.value = _messages.value.map {
                    if (it.id == assistantMessageId) {
                        // Do not wipe streaming-time parses: extraction can transiently fail on edge
                        // normalisation; null here would drop sparks/hints for the next turn.
                        it.copy(
                            outputMemoryHint = hintParsed ?: it.outputMemoryHint,
                            outputSparksCsv = sparksParsed ?: it.outputSparksCsv,
                            rawAssistantEnvelope = rawSnapshot,
                        )
                    } else {
                        it
                    }
                }
                _isGenerating.value = false
                _isPhaseLoadingModel.value = false
                conversationMemorySubject = subjectMemoryKey(_settings.value.subjectMode)
                AssistantOutput.extractInterestInner(rawSnapshot)?.let { inner ->
                    InterestCapture.sanitizeInterestInner(inner)?.let { area ->
                        val signal = InterestSignal(
                            areaOfInterest = area,
                            subjectKey = interestSubjectKeySnapshot,
                            userQuerySnippet = capturedUserTextForInterest.take(150),
                            recordedAtEpochMs = System.currentTimeMillis(),
                        )
                        updateSettings(
                            _settings.value.copy(
                                interestSignals = (_settings.value.interestSignals + signal).takeLast(48),
                            ),
                        )
                    }
                }
                repository.saveHistory(_messages.value)
                if (BuildConfig.VERBOSE_DEBUG_LOGS) {
                    val elapsed = System.currentTimeMillis() - startedAtMs
                    val finalText = currentAssistantText.ifBlank {
                        _messages.value.lastOrNull { it.id == assistantMessageId }?.text.orEmpty()
                    }
                    Log.i(
                        TAG,
                        "LLM RESPONSE id=$reqId elapsedMs=$elapsed rawTokenCount=$rawTokenCount " +
                            "visibleChunks=$visibleChunkCount finalChars=${finalText.length}",
                    )
                    logExactLlmPayload(reqId, "LLM_RAW_RESPONSE", rawSnapshot)
                    logExactLlmPayload(reqId, "LLM_FINAL_DISPLAY", finalText)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
    }

    fun updateSettings(newSettings: InferenceSettings) {
        val previous = _settings.value
        val coerced = newSettings.copy(
            maxTokens = newSettings.maxTokens.coerceIn(64, LlmDefaults.MAX_NEW_TOKENS_CAP)
        )
        if (subjectMemoryKey(coerced.subjectMode) != subjectMemoryKey(previous.subjectMode)) {
            conversationMemorySubject = null
        }
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
            pendingImageContextForNextPrompt = null
            repository.clearHistory()
        }
    }

    fun onImageAttached(uri: Uri) {
        if (_isGenerating.value) return

        viewModelScope.launch {
            _documentError.value = null
            val result = withContext(Dispatchers.IO) {
                ImageTextExtractor.extractFromUri(appContext, uri, maxChars = 2500)
            }
            when (result) {
                is ImageTextExtractor.Result.Success -> {
                    pendingImageContextForNextPrompt = result.text
                    appendAssistantHint(
                        "Image attached. Ask your question, and I will use details from that image.",
                    )
                }
                is ImageTextExtractor.Result.Error -> {
                    _documentError.value = result.message
                }
            }
        }
    }

    fun onCapturedImage(bitmap: Bitmap) {
        if (_isGenerating.value) return

        viewModelScope.launch {
            _documentError.value = null
            val result = withContext(Dispatchers.IO) {
                ImageTextExtractor.extractFromBitmap(bitmap, maxChars = 2500)
            }
            when (result) {
                is ImageTextExtractor.Result.Success -> {
                    pendingImageContextForNextPrompt = result.text
                    appendAssistantHint(
                        "Photo captured. Ask your question, and I will use details from that photo.",
                    )
                }
                is ImageTextExtractor.Result.Error -> {
                    _documentError.value = result.message
                }
            }
        }
    }

    fun clearDocumentError() {
        _documentError.value = null
    }

    fun completeStarterCheckIn(
        answers: List<Int>,
        subjectPreferences: List<SubjectMode>,
    ) {
        val supportScore = answers.count { it <= 1 }
        val challengeScore = answers.count { it >= 3 }
        val curiosityScore = answers.sum()
        val learningProfile = when {
            challengeScore >= 4 -> LearningProfile.BUILDER
            curiosityScore >= answers.size * 2 -> LearningProfile.THINKER
            else -> LearningProfile.EXPLORER
        }
        val subjectBands = subjectPreferences
            .distinct()
            .associateWith {
                when {
                    challengeScore >= 4 -> SkillBand.CONFIDENT
                    curiosityScore >= answers.size * 2 -> SkillBand.GROWING
                    else -> SkillBand.NEW
                }
            }
        updateSettings(
            _settings.value.copy(
                learnerProfile = LearnerProfile(
                    learningProfile = learningProfile,
                    curiositySignals = (_settings.value.learnerProfile.curiositySignals + curiosityScore).coerceAtMost(32),
                    supportSignals = (_settings.value.learnerProfile.supportSignals + supportScore).coerceAtMost(32),
                    challengeSignals = (_settings.value.learnerProfile.challengeSignals + challengeScore).coerceAtMost(32),
                    iqLevel = null,
                    starterCheckInCompleted = true,
                ),
                subjectSkillBands = _settings.value.subjectSkillBands + subjectBands,
                starterCheckInPromptSeen = true,
            )
        )
    }

    fun skipStarterCheckIn() {
        updateSettings(_settings.value.copy(starterCheckInPromptSeen = true))
    }

    private fun currentMessagesWithPendingImageContext(
        currentMessages: List<ChatMessage>,
    ): List<ChatMessage> {
        val imageContext = pendingImageContextForNextPrompt ?: return currentMessages
        pendingImageContextForNextPrompt = null
        val syntheticContextMessage = ChatMessage(
            id = idGenerator.incrementAndGet(),
            sender = Sender.USER,
            text = "The user attached an image. Use this extracted image text as context:\n$imageContext",
            timestamp = System.currentTimeMillis(),
        )
        return currentMessages + syntheticContextMessage
    }

    private fun appendAssistantHint(text: String) {
        val hint = ChatMessage(
            id = idGenerator.incrementAndGet(),
            sender = Sender.ASSISTANT,
            text = text,
            timestamp = System.currentTimeMillis(),
        )
        _messages.value = _messages.value + hint
        viewModelScope.launch {
            repository.saveHistory(_messages.value)
        }
    }
}
