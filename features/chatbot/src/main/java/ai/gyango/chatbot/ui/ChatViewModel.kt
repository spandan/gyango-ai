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
import ai.gyango.core.SubjectModeAutoRouter
import ai.gyango.core.SubjectModeRouting
import ai.gyango.assistant.AssistantLlmSanitizer
import ai.gyango.assistant.AssistantOutput
import ai.gyango.assistant.ExamPrepCoachState
import ai.gyango.assistant.GyangoOutputEnvelope
import ai.gyango.assistant.KidsSafetyPolicy
import ai.gyango.assistant.SafetyDecision
import ai.gyango.chatbot.data.ChatRepository
import ai.gyango.chatbot.data.ImageTextExtractor
import ai.gyango.core.ChatMessage
import ai.gyango.core.InferenceSettings
import ai.gyango.core.InterestCapture
import ai.gyango.core.InterestSignal
import ai.gyango.core.TutorUserPreference
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
        private const val IMAGE_OCR_MAX_CHARS = 5000
        private const val IMAGE_OCR_CONTEXT_TURNS = 3
        private const val PROMPT_MODEL_FAMILY = PromptBuilder.DEFAULT_PROMPT_MODEL_FAMILY
        private val PROMPT_TEMPLATE_VERSION = PromptBuilder.ACTIVE_PROMPT_TEMPLATE_VERSION
        private val IMAGE_UPLOAD_SPARKS_CSV = listOf(
            "Solve the problem from the attached image",
            "Interpret the text from attached image and summarize it for me",
            "Answer the question based on attached image",
        ).joinToString(GyangoOutputEnvelope.SPARK_CHIPS_DELIMITER)
        /** Defer [Orchestrator.generate] until after IME hide / layout settle (keep small — adds wall latency). */
        private const val INFERENCE_START_DELAY_MS = 80L
        /**
         * How often the stream flush loop wakes while tokens arrive (keeps shutdown responsive).
         */
        private const val STREAM_UI_FLUSH_MS = 33L

        private val EXAM_PREP_QUESTION_OPTIONS = setOf(10, 20, 30)

        /**
         * When true, skip [parseForDisplay] on each tick while tokens arrive; run once after the stream ends
         * (matches UI: Markwon runs only when streaming finishes).
         */
        private const val DEFER_ASSISTANT_DISPLAY_TEXT_UNTIL_STREAM_END = false
        /** Logcat line safety margin (Android effectively caps per call). */
        private const val LLM_LOG_CHUNK_CHARS = 3500

        /** Full prompt / response body, split for logcat (only when [BuildConfig.LLM_IO_LOGS]). */
        private fun logExactLlmPayload(reqId: Long, label: String, text: String) {
            if (!BuildConfig.LLM_IO_LOGS) return
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
    private var pendingImageContextTurnsRemaining: Int = 0

    /**
     * Subject "lane" for cross-turn `MEMORY:` hints. Cleared when the learner changes topic tiles so
     * hints from math do not leak into science (see [memoryForNextPrompt]). Restored after the next
     * completed assistant message in the new lane.
     */
    private var conversationMemorySubject: SubjectMode? = null

    /**
     * After a topic-tile change we keep prior turns on disk as [topicSessionHistoryPrefix] and show
     * only [_messages] in the UI; persistence uses prefix + session so we never call
     * [ChatRepository.clearHistory] for a topic switch.
     */
    private var useTopicSessionHistoryMerge: Boolean = false
    private var topicSessionHistoryPrefix: List<ChatMessage> = emptyList()

    /** Topic changed mid-generation; run [evictTopicSwitchSessionUiAndKvCache] after the stream ends. */
    private var deferredTopicSwitchEviction: Boolean = false

    private fun snapshotMessagesForPersistence(): List<ChatMessage> =
        if (useTopicSessionHistoryMerge) topicSessionHistoryPrefix + _messages.value
        else _messages.value

    private suspend fun persistChatHistorySnapshot() {
        repository.saveHistory(snapshotMessagesForPersistence())
    }

    private fun applyMaxMessageIdsFromFlow(list: List<ChatMessage>) {
        val fromFlow = list.maxOfOrNull { it.id } ?: 0L
        val fromPrefix = topicSessionHistoryPrefix.maxOfOrNull { it.id } ?: 0L
        val fromUi = _messages.value.maxOfOrNull { it.id } ?: 0L
        val cur = idGenerator.get()
        idGenerator.set(listOf(fromFlow, fromPrefix, fromUi, cur).max())
    }

    /**
     * Clears on-screen chat for a new topic and drops the LiteRT engine so runtime KV is not carried
     * across topics. Does not remove persisted history (see [useTopicSessionHistoryMerge]).
     */
    private fun evictTopicSwitchSessionUiAndKvCache() {
        if (useTopicSessionHistoryMerge) {
            topicSessionHistoryPrefix = topicSessionHistoryPrefix + _messages.value
        } else {
            useTopicSessionHistoryMerge = true
            topicSessionHistoryPrefix = _messages.value.toList()
        }
        _messages.value = emptyList()
        pendingImageContextForNextPrompt = null
        pendingImageContextTurnsRemaining = 0
        viewModelScope.launch {
            runCatching { orchestrator.unloadDefaultModelFromMemory() }
                .onFailure { e ->
                    Log.w(TAG, "unload default model after topic change: ${e.message}")
                }
        }
    }

    private fun subjectMemoryKey(mode: SubjectMode?): SubjectMode = when (SubjectModeRouting.effectiveSubjectMode(mode)) {
        null, SubjectMode.GENERAL -> SubjectMode.GENERAL
        else -> SubjectModeRouting.effectiveSubjectMode(mode)!!
    }

    private fun interestSubjectKeyForSettings(mode: SubjectMode?): String {
        val eff = SubjectModeRouting.effectiveSubjectMode(mode)
        return when (eff) {
            null, SubjectMode.GENERAL -> SubjectMode.GENERAL.name
            else -> eff.name
        }
    }

    /** Subject used for this LLM call: explicit topic wins, otherwise heuristic route or GENERAL. */
    private fun effectivePromptSubject(lastUserText: String): SubjectMode {
        val selected = _settings.value.subjectMode
        if (selected != null && selected != SubjectMode.GENERAL) {
            return selected
        }
        return if (_settings.value.autoRouteSubject) {
            SubjectModeAutoRouter.route(lastUserText)
        } else {
            selected ?: SubjectMode.GENERAL
        }
    }

    private fun sanitizeVisibleAssistant(raw: String): String {
        return AssistantOutput.parseForDisplay(raw, null).displayText
    }

    private fun parseAssistant(raw: String) =
        AssistantOutput.parseForDisplay(raw, null)

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

    private val _examPrepWizardDraft = MutableStateFlow(ExamPrepWizardDraft())
    val examPrepWizardDraft: StateFlow<ExamPrepWizardDraft> = _examPrepWizardDraft.asStateFlow()

    private val _examPrepSessionConfig = MutableStateFlow<ExamPrepSessionConfig?>(null)

    private val _examPrepSetupError = MutableStateFlow<String?>(null)
    val examPrepSetupError: StateFlow<String?> = _examPrepSetupError.asStateFlow()

    private fun resetExamPrepLocalState() {
        _examPrepWizardDraft.value = ExamPrepWizardDraft()
        _examPrepSessionConfig.value = null
        _examPrepSetupError.value = null
    }

    /** Clears the inline error shown on the Exam Prep setup card. */
    fun dismissExamPrepSetupError() {
        _examPrepSetupError.value = null
    }

    fun setExamPrepLane(lane: ExamPrepContentLane) {
        _examPrepWizardDraft.value = ExamPrepWizardDraft(lane = lane, subtopic = null)
        _examPrepSetupError.value = null
    }

    fun setExamPrepSubtopic(text: String) {
        val lane = _examPrepWizardDraft.value.lane ?: return
        _examPrepWizardDraft.value = ExamPrepWizardDraft(lane = lane, subtopic = text.trim())
        _examPrepSetupError.value = null
    }

    /**
     * Final wizard step: validates sub-topic (kids safety), stores session config for prompts, then
     * sends the usual exam bootstrap user line to start the LLM.
     */
    fun submitExamPrepQuestionCount(count: Int) {
        if (_isGenerating.value) return
        if (_settings.value.subjectMode != SubjectMode.EXAM_PREP) return
        if (_messages.value.isNotEmpty()) return
        if (count !in EXAM_PREP_QUESTION_OPTIONS) return
        val draft = _examPrepWizardDraft.value
        val lane = draft.lane ?: return
        val sub = draft.subtopic?.trim().orEmpty()
        if (sub.isBlank()) return
        val safety = KidsSafetyPolicy.screenInput(sub, _settings.value.safetyProfile)
        if (safety.decision != SafetyDecision.ALLOW) {
            _examPrepSetupError.value = safety.safeReply?.trim()?.takeIf { it.isNotEmpty() }
                ?: "That focus could not be used. Try different wording."
            return
        }
        _examPrepSessionConfig.value = ExamPrepSessionConfig(
            lane = lane,
            subtopic = sub,
            questionCount = count,
        )
        onUserSend(ExamPrepCoachState.bootstrapUserText(), fromSparkChip = false)
    }

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
                    if (!useTopicSessionHistoryMerge) {
                        _messages.value = list
                    }
                }
                applyMaxMessageIdsFromFlow(list)
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
        val isKnownLocale = SpeechInputLocales.OPTIONS.any { (localeTag, _) ->
            localeTag.equals(tag, ignoreCase = true)
        }
        val message = when {
            isKnownLocale -> transcript.trim()
            else ->
                "The user spoke in language/locale \"$tag\". Transcription (may be imperfect):\n${transcript.trim()}\n\nUnderstand their intent, and answer helpfully in the same language they used."
        }
        onUserSend(message, fromSparkChip = false)
    }

    fun onUserSend(text: String, fromSparkChip: Boolean = false) {
        if (text.isBlank() || _isGenerating.value) return
        val trimmed = text.trim()
        if (fromSparkChip) {
            val lane = interestSubjectKeyForSettings(_settings.value.subjectMode)
            val cur = _settings.value.chatDifficultyLevel.coerceIn(1, 10)
            if (cur < 10) {
                updateSettings(
                    _settings.value.copy(
                        chatDifficultyLevel = cur + 1,
                        chatDifficultyLaneKey = lane,
                    ),
                )
            }
        }
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
            persistChatHistorySnapshot()
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
     * then take the last [Sender.USER] row.
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

    /**
     * Single-turn `M:` hint: last completed assistant’s parsed CONTEXT line only (no sliding history).
     * Exam prep always supplies a line—bootstrap on first turn, then the prior assistant CONTEXT string.
     */
    private fun memoryForNextPrompt(messages: List<ChatMessage>, routedSubject: SubjectMode): String? {
        val trimmed = if (messages.isNotEmpty() &&
            messages.last().sender == Sender.ASSISTANT &&
            messages.last().text.isBlank()
        ) {
            messages.dropLast(1)
        } else {
            messages
        }
        val structured = trimmed.lastOrNull { m ->
            m.sender == Sender.ASSISTANT &&
                m.text.isNotBlank() &&
                (m.rawAssistantEnvelope?.let { GyangoOutputEnvelope.isLessonEnvelope(it) } == true ||
                    GyangoOutputEnvelope.isLessonEnvelope(m.text))
        }
        val source = structured
            ?: trimmed.lastOrNull { it.sender == Sender.ASSISTANT && it.text.isNotBlank() }
        val fromField = source?.outputContext?.trim()?.takeIf { it.isNotBlank() }
        val fromRaw = source?.let { msg ->
            val raw = msg.rawAssistantEnvelope?.takeIf { it.isNotBlank() } ?: msg.text
            GyangoOutputEnvelope.parseContextForNextTurn(raw)?.trim()?.takeIf { it.isNotBlank() }
        }
        val merged = fromField ?: fromRaw
        if (routedSubject == SubjectMode.EXAM_PREP) {
            return merged ?: ExamPrepCoachState.BOOTSTRAP_MEMORY
        }
        val currentKey = subjectMemoryKey(routedSubject)
        if (conversationMemorySubject == null || conversationMemorySubject != currentKey) return null
        return merged
    }

    /** Last visible "next question" text from the previous assistant exam turn (if present). */
    private fun priorExamQuestionForPrompt(messages: List<ChatMessage>, routedSubject: SubjectMode): String? {
        if (routedSubject != SubjectMode.EXAM_PREP) return null
        val trimmed = if (
            messages.isNotEmpty() &&
            messages.last().sender == Sender.ASSISTANT &&
            messages.last().text.isBlank()
        ) {
            messages.dropLast(1)
        } else {
            messages
        }
        val lastAssistant = trimmed.lastOrNull { it.sender == Sender.ASSISTANT && it.text.isNotBlank() } ?: return null
        val displayMarkdown = lastAssistant.text.trim().takeIf { it.isNotBlank() } ?: return null
        return ExamPrepCoachState.extractLatestNextQuestion(displayMarkdown)
    }

    private fun generateAssistantResponse() {
        val currentMessages = _messages.value
        val assistantMessageId = currentMessages.last().id
        val lastUserOnly = statelessLastUserText(currentMessages)
        val imageOcrForPrompt = pendingImageContextForNextPrompt
            ?.trim()
            ?.takeIf { it.isNotBlank() && pendingImageContextTurnsRemaining > 0 }
        if (imageOcrForPrompt != null) {
            pendingImageContextTurnsRemaining = (pendingImageContextTurnsRemaining - 1).coerceAtLeast(0)
            if (pendingImageContextTurnsRemaining == 0) {
                pendingImageContextForNextPrompt = null
            }
        }
        val routedSubject = effectivePromptSubject(lastUserOnly)
        val laneKeySync = interestSubjectKeyForSettings(routedSubject)
        if (_settings.value.chatDifficultyLaneKey != laneKeySync) {
            updateSettings(
                _settings.value.copy(
                    chatDifficultyLevel = 1,
                    chatDifficultyLaneKey = laneKeySync,
                ),
            )
        }
        val difficultyForTelemetry = _settings.value.chatDifficultyLevel.coerceIn(1, 10)
        val preferenceLine = TutorUserPreference.modeLineFromCheckIn(
            _settings.value.starterCheckInAnswerIndices,
            _settings.value.learnerProfile.starterCheckInCompleted,
        )
        val memoryHint = memoryForNextPrompt(currentMessages, routedSubject)
        val priorExamQuestion = priorExamQuestionForPrompt(currentMessages, routedSubject)
        val promptTopic = PromptBuilder.topicLabelForPrompt(routedSubject)
        val examCfg = _examPrepSessionConfig.value
        val examLaneStr: String? =
            if (routedSubject == SubjectMode.EXAM_PREP && examCfg != null) examCfg.lane.promptLabel else null
        val examSubtopicStr: String? =
            if (routedSubject == SubjectMode.EXAM_PREP && examCfg != null) examCfg.subtopic else null
        val examCount: Int? =
            if (routedSubject == SubjectMode.EXAM_PREP && examCfg != null) examCfg.questionCount else null
        val promptTemplateCandidates = PromptBuilder.promptTemplateAssetPathCandidates(
            mode = routedSubject,
            promptModelFamily = PROMPT_MODEL_FAMILY,
            promptTemplateVersion = PROMPT_TEMPLATE_VERSION,
        )
        var resolvedPromptTemplateAssetPath: String? = null
        val prompt = PromptBuilder.buildChatPrompt(
            lastUserContent = lastUserOnly,
            memoryHint = memoryHint,
            imageOcrContext = imageOcrForPrompt,
            preferredReplyLocaleTag = _settings.value.speechInputLocaleTag,
            birthMonth = _settings.value.birthMonth,
            birthYear = _settings.value.birthYear,
            subjectMode = routedSubject,
            safetyProfile = _settings.value.safetyProfile,
            userPreferenceModeLine = preferenceLine,
            difficultyLevel = difficultyForTelemetry,
            requestThoughtHints = _settings.value.requestModelThoughtInJson,
            promptModelFamily = PROMPT_MODEL_FAMILY,
            promptTemplateVersion = PROMPT_TEMPLATE_VERSION,
            loadPromptTemplate = { assetPath ->
                runCatching {
                    appContext.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
                }.getOrNull()?.also {
                    resolvedPromptTemplateAssetPath = assetPath
                }
            },
            examPrepTopicLane = examLaneStr,
            examPrepSubtopic = examSubtopicStr,
            examPrepQuestionTarget = examCount,
            examPrepPriorQuestion = priorExamQuestion,
        )
        val reqId = assistantMessageId
        val startedAtMs = System.currentTimeMillis()
        if (BuildConfig.LLM_IO_LOGS) {
            val sample = LlmDefaults.samplingForSubject(routedSubject)
            Log.i(
                TAG,
                "LLM REQUEST id=$reqId tool=chatbot temp=${sample.temperature} " +
                    "topP=${sample.topP} topK=${sample.topK} " +
                    "maxTokens=${_settings.value.maxTokens} lowPower=${_settings.value.lowPowerMode} " +
                    "locale=${_settings.value.speechInputLocaleTag} " +
                    "subjectMode=${routedSubject} autoRoute=${_settings.value.autoRouteSubject}"
            )
            Log.i(
                TAG,
                "========== TOPIC=$promptTopic routed=$routedSubject " +
                    "activePromptVersion=${PROMPT_TEMPLATE_VERSION.token} " +
                    "resolvedTemplate=${resolvedPromptTemplateAssetPath ?: "INLINE_FALLBACK"} " +
                    "candidates=$promptTemplateCandidates ==========",
            )
        }

        val capturedUserTextForInterest = lastUserOnly.trim()
        val interestSubjectKeySnapshot = laneKeySync

        viewModelScope.launch {
            if (!ModelHardwareGate.inspect(appContext).canRunSelectedModel) {
                val msg = ModelHardwareGate.UNSUPPORTED_DEVICE_MESSAGE
                _messages.value = _messages.value.map {
                    if (it.id == assistantMessageId) it.copy(text = "Error: $msg") else it
                }
                persistChatHistorySnapshot()
                _isGenerating.value = false
                return@launch
            }
            var rawAssistantText = ""
            var currentAssistantText = ""
            var rawTokenCount = 0
            var visibleChunkCount = 0
            var outputSafetyBlocked = false

            // Bridge LiteRT token thread to Main:
            // - accumulate raw model deltas into [rawAssistantText] (no per-chunk stripping; preserves markdown)
            // - run [AssistantLlmSanitizer.sanitize] once after the stream completes
            // - throttle optional partial UI flushes (or defer until done when [DEFER_ASSISTANT_DISPLAY_TEXT_UNTIL_STREAM_END])
            val tokenChannel = Channel<String>(Channel.UNLIMITED)
            var assistantTextDirty = false
            var collectTokensFinished = false
            val rawAppendMonitor = Any()
            var lastEmittedVisible: String? = null
            var lastEmittedSparks: String? = null
            var lastEmittedContext: String? = null
            var lastEmittedCuriosity: String? = null
            var lastEmittedRawEnvelope: String? = null

            fun hideTrailingUnclosedFencedBlock(text: String): String {
                val normalized = text.replace("\r\n", "\n")
                val fenceLine = Regex("""^\s*```[\w-]*\s*$""")
                val lines = normalized.split('\n')
                var inFence = false
                var openFenceStart = -1
                var cursor = 0
                lines.forEach { line ->
                    if (fenceLine.matches(line.trim())) {
                        if (!inFence) {
                            inFence = true
                            openFenceStart = cursor
                        } else {
                            inFence = false
                            openFenceStart = -1
                        }
                    }
                    cursor += line.length + 1
                }
                return if (inFence && openFenceStart >= 0) {
                    normalized.substring(0, openFenceStart).trimEnd()
                } else {
                    normalized
                }
            }

            fun recomputeVisibleAssistantText(streamInProgress: Boolean): String {
                val visible = sanitizeVisibleAssistant(rawAssistantText)
                return if (streamInProgress) hideTrailingUnclosedFencedBlock(visible) else visible
            }

            fun emitAssistantVisibleText(nextVisible: String) {
                val rawSnap = synchronized(rawAppendMonitor) { rawAssistantText }
                val moderated = KidsSafetyPolicy.moderateOutput(nextVisible, _settings.value.safetyProfile)
                val safeVisible = moderated.safeReply?.takeIf { it.isNotBlank() } ?: nextVisible
                if (moderated.safeReply != null) {
                    outputSafetyBlocked = true
                }
                val sparksParsed = if (outputSafetyBlocked) null else AssistantOutput.extractSparksCsv(rawSnap)
                val contextParsed = if (outputSafetyBlocked) null else AssistantOutput.extractOutputContext(rawSnap)
                val curiosityParsed = if (outputSafetyBlocked) null else AssistantOutput.extractCuriosity(rawSnap)
                val rawForCompare = if (outputSafetyBlocked) "__BLOCKED__" else rawSnap
                if (safeVisible == lastEmittedVisible &&
                    sparksParsed == lastEmittedSparks &&
                    contextParsed == lastEmittedContext &&
                    curiosityParsed == lastEmittedCuriosity &&
                    rawForCompare == lastEmittedRawEnvelope
                ) {
                    return
                }
                lastEmittedVisible = safeVisible
                lastEmittedSparks = sparksParsed
                lastEmittedContext = contextParsed
                lastEmittedCuriosity = curiosityParsed
                lastEmittedRawEnvelope = rawForCompare
                visibleChunkCount += 1
                currentAssistantText = safeVisible
                _messages.value = _messages.value.map {
                    if (it.id == assistantMessageId) {
                        it.copy(
                            text = safeVisible,
                            outputSparksCsv = if (outputSafetyBlocked) null else sparksParsed ?: it.outputSparksCsv,
                            outputContext = if (outputSafetyBlocked) null else contextParsed ?: it.outputContext,
                            outputCuriosity = if (outputSafetyBlocked) null else curiosityParsed ?: it.outputCuriosity,
                            rawAssistantEnvelope = if (outputSafetyBlocked) null else rawSnap,
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
                        if (token.isEmpty()) return@collect
                        synchronized(rawAppendMonitor) {
                            rawAssistantText += token
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
                    if (!DEFER_ASSISTANT_DISPLAY_TEXT_UNTIL_STREAM_END) {
                        emitAssistantVisibleText(recomputeVisibleAssistantText(streamInProgress = true))
                    }
                    yield()
                }
            }

            try {
                delay(INFERENCE_START_DELAY_MS)
                val inferT0 = if (BuildConfig.VERBOSE_DEBUG_LOGS) SystemClock.elapsedRealtime() else 0L
                orchestrator.generate(
                    request = OrchestrationRequest(
                        prompt = prompt,
                        settings = _settings.value.copy(subjectMode = routedSubject),
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

                synchronized(rawAppendMonitor) {
                    rawAssistantText = AssistantLlmSanitizer.sanitize(rawAssistantText)
                    assistantTextDirty = true
                }

                // Ensure the final bubble matches the fully materialized stream after one-shot LLM sanitize.
                synchronized(rawAppendMonitor) {
                    assistantTextDirty = true
                }
                flushTicker.join()
                emitAssistantVisibleText(recomputeVisibleAssistantText(streamInProgress = false))
                yield()

                if (!outputSafetyBlocked && currentAssistantText.isNotEmpty()) {
                    val moderated = KidsSafetyPolicy.moderateOutput(currentAssistantText, _settings.value.safetyProfile)
                    val moderatedReply = moderated.safeReply
                    if (moderatedReply != null && moderatedReply != currentAssistantText) {
                        currentAssistantText = moderatedReply
                        outputSafetyBlocked = true
                        _messages.value = _messages.value.map {
                            if (it.id == assistantMessageId) {
                                it.copy(
                                    text = currentAssistantText,
                                    outputSparksCsv = null,
                                    outputContext = null,
                                    outputCuriosity = null,
                                    rawAssistantEnvelope = null,
                                )
                            } else {
                                it
                            }
                        }
                    }
                }
                val rawSnapshot = synchronized(rawAppendMonitor) { rawAssistantText }
                val finalParse = parseAssistant(rawSnapshot)
                if (!outputSafetyBlocked) {
                    val contextParsed = if (finalParse.tailComplete && finalParse.topicContractValid) {
                        val parsed = AssistantOutput.extractOutputContext(rawSnapshot)
                        if (routedSubject == SubjectMode.EXAM_PREP) {
                            ExamPrepCoachState.coerceQuestionProgression(parsed, memoryHint)
                        } else {
                            parsed
                        }
                    } else {
                        null
                    }
                    val curiosityParsed = if (finalParse.tailComplete) {
                        AssistantOutput.extractCuriosity(rawSnapshot)
                    } else {
                        null
                    }
                    val sparksParsed = if (finalParse.tailComplete) {
                        AssistantOutput.extractSparksCsv(rawSnapshot)
                    } else {
                        null
                    }
                    _messages.value = _messages.value.map {
                        if (it.id == assistantMessageId) {
                            it.copy(
                                outputContext = contextParsed,
                                outputCuriosity = curiosityParsed,
                                outputSparksCsv = sparksParsed ?: it.outputSparksCsv,
                                rawAssistantEnvelope = rawSnapshot,
                            )
                        } else {
                            it
                        }
                    }
                }
                _isGenerating.value = false
                _isPhaseLoadingModel.value = false
                conversationMemorySubject = subjectMemoryKey(routedSubject)
                if (!outputSafetyBlocked) {
                    AssistantOutput.extractCuriosity(rawSnapshot)?.let { inner ->
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
                }
                persistChatHistorySnapshot()
                if (deferredTopicSwitchEviction) {
                    deferredTopicSwitchEviction = false
                    evictTopicSwitchSessionUiAndKvCache()
                }
                val curiosityTelemetry = if (outputSafetyBlocked) {
                    null
                } else {
                    AssistantOutput.extractCuriosity(rawSnapshot)
                        ?.let { InterestCapture.sanitizeInterestInner(it) }
                }
                repository.recordTurnTelemetry(
                    subjectKey = interestSubjectKeySnapshot,
                    userQuestion = capturedUserTextForInterest,
                    curiosity = curiosityTelemetry,
                    difficultyLevel = difficultyForTelemetry,
                    parseStatus = if (outputSafetyBlocked) null else finalParse.status.name,
                    parseReason = if (outputSafetyBlocked) null else finalParse.invalidReason,
                    topicContractValid = if (outputSafetyBlocked) null else finalParse.topicContractValid,
                )
                if (BuildConfig.LLM_IO_LOGS) {
                    val elapsed = System.currentTimeMillis() - startedAtMs
                    val finalText = currentAssistantText.ifBlank {
                        _messages.value.lastOrNull { it.id == assistantMessageId }?.text.orEmpty()
                    }
                    Log.i(
                        TAG,
                        "LLM RESPONSE id=$reqId elapsedMs=$elapsed rawTokenCount=$rawTokenCount " +
                            "visibleChunks=$visibleChunkCount finalChars=${finalText.length}",
                    )
                    if (outputSafetyBlocked) {
                        Log.i(TAG, "LLM RAW RESPONSE id=$reqId hidden_by_output_safety=true")
                    } else {
                        logExactLlmPayload(reqId, "LLM_RAW_RESPONSE", rawSnapshot)
                    }
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
        val topicSampling = LlmDefaults.samplingForSubject(newSettings.subjectMode)
        val coerced = newSettings.copy(
            maxTokens = newSettings.maxTokens.coerceIn(64, LlmDefaults.MAX_NEW_TOKENS_CAP),
            temperature = topicSampling.temperature,
            topP = topicSampling.topP,
            topK = topicSampling.topK,
        )
        val prevLane = interestSubjectKeyForSettings(previous.subjectMode)
        val newLane = interestSubjectKeyForSettings(coerced.subjectMode)
        val topicLaneChanged = prevLane != newLane
        if (subjectMemoryKey(coerced.subjectMode) != subjectMemoryKey(previous.subjectMode)) {
            conversationMemorySubject = null
        }
        val merged = coerced.copy(
            chatDifficultyLevel = if (topicLaneChanged) 1 else coerced.chatDifficultyLevel.coerceIn(1, 10),
            chatDifficultyLaneKey = if (topicLaneChanged) newLane else (coerced.chatDifficultyLaneKey ?: newLane),
        )
        _settings.value = merged
        if (merged.subjectMode != SubjectMode.EXAM_PREP) {
            resetExamPrepLocalState()
        } else if (topicLaneChanged) {
            resetExamPrepLocalState()
        }
        if (topicLaneChanged) {
            if (_isGenerating.value) {
                deferredTopicSwitchEviction = true
            } else {
                deferredTopicSwitchEviction = false
                evictTopicSwitchSessionUiAndKvCache()
            }
        }
        viewModelScope.launch {
            repository.saveInferenceSettings(merged)
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
            useTopicSessionHistoryMerge = false
            topicSessionHistoryPrefix = emptyList()
            deferredTopicSwitchEviction = false
            _messages.value = emptyList()
            pendingImageContextForNextPrompt = null
            pendingImageContextTurnsRemaining = 0
            resetExamPrepLocalState()
            repository.clearHistory()
        }
    }

    fun onImageAttached(uri: Uri) {
        if (_isGenerating.value) return

        viewModelScope.launch {
            _documentError.value = null
            val result = withContext(Dispatchers.IO) {
                ImageTextExtractor.extractFromUri(appContext, uri, maxChars = IMAGE_OCR_MAX_CHARS)
            }
            when (result) {
                is ImageTextExtractor.Result.Success -> {
                    pendingImageContextForNextPrompt = result.text
                    pendingImageContextTurnsRemaining = IMAGE_OCR_CONTEXT_TURNS
                    appendAssistantHint(
                        "Image attached. Ask your question, and I will use details from that image for the next $IMAGE_OCR_CONTEXT_TURNS turns.",
                        sparksCsv = IMAGE_UPLOAD_SPARKS_CSV,
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
                ImageTextExtractor.extractFromBitmap(bitmap, maxChars = IMAGE_OCR_MAX_CHARS)
            }
            when (result) {
                is ImageTextExtractor.Result.Success -> {
                    pendingImageContextForNextPrompt = result.text
                    pendingImageContextTurnsRemaining = IMAGE_OCR_CONTEXT_TURNS
                    appendAssistantHint(
                        "Photo captured. Ask your question, and I will use details from that photo for the next $IMAGE_OCR_CONTEXT_TURNS turns.",
                        sparksCsv = IMAGE_UPLOAD_SPARKS_CSV,
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
                starterCheckInAnswerIndices = answers.map { it.coerceIn(0, 3) }.take(16),
            )
        )
    }

    fun skipStarterCheckIn() {
        updateSettings(_settings.value.copy(starterCheckInPromptSeen = true))
    }

    private fun appendAssistantHint(text: String, sparksCsv: String? = null) {
        val hint = ChatMessage(
            id = idGenerator.incrementAndGet(),
            sender = Sender.ASSISTANT,
            text = text,
            outputSparksCsv = sparksCsv?.trim()?.takeIf { it.isNotBlank() },
            timestamp = System.currentTimeMillis(),
        )
        _messages.value = _messages.value + hint
        viewModelScope.launch {
            persistChatHistorySnapshot()
        }
    }
}
