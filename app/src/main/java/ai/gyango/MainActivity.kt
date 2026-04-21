package ai.gyango

import android.Manifest
import android.graphics.Bitmap
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.content.ComponentCallbacks2
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import ai.gyango.chatbot.ui.BrainNetworkChoiceScreen
import ai.gyango.chatbot.ui.BrainPreparingProgressScreen
import ai.gyango.chatbot.ui.BrainWaitingForWifiScreen
import ai.gyango.chatbot.ui.ChatInputMode
import ai.gyango.chatbot.ui.ModelHardwareUnsupportedScreen
import ai.gyango.chatbot.ui.PadFeatureWalkthroughScreen
import ai.gyango.chatbot.ui.LearningCheckInPromptScreen
import ai.gyango.chatbot.ui.LearningCheckInScreen
import ai.gyango.chatbot.ui.OnboardingWelcomeScreen
import ai.gyango.chatbot.ui.ProfileOnboardingScreen
import ai.gyango.chatbot.ui.TtsLanguageSetupScreen
import ai.gyango.chatbot.ui.brainPackProgressDetail
import ai.gyango.chatbot.ui.rememberChatDisplayStrings
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ai.gyango.chatbot.data.ChatRepository
import ai.gyango.chatbot.ui.ChatScreen
import ai.gyango.chatbot.ui.ChatViewModel
import ai.gyango.chatbot.ui.theme.GyangoAITheme
import ai.gyango.chatbot.voice.SpeechTranscriber
import ai.gyango.core.Sender
import ai.gyango.core.hardware.ModelHardwareGate
import ai.gyango.modeldelivery.GyangoPadDelivery
import ai.gyango.net.isActiveNetworkCellular
import ai.gyango.net.isActiveNetworkWifi
import androidx.compose.material3.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.util.Locale

private enum class PadBrainStep {
    PickNetwork,
    WaitWifi,
    Downloading,
    Done,
}

private enum class PostDownloadStep {
    Prompt,
    CheckIn,
    Done,
}

private enum class TtsLanguageStatus {
    READY,
    MISSING_DATA,
    UNSUPPORTED,
    ERROR,
}

class MainActivity : ComponentActivity() {
    private companion object {
        private const val TAG = "MainActivity"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var speechTranscriber: SpeechTranscriber
    private lateinit var assistantTts: TextToSpeech
    private var isTtsReady: Boolean = false
    private val ttsSetupTick = mutableIntStateOf(0)
    private val padNetworkResumeTick = mutableIntStateOf(0)
    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceRecognition()
        } else {
            chatViewModel.setVoiceError("Microphone permission is needed for voice input")
        }
    }

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            chatViewModel.onImageAttached(it)
        }
    }
    private val cameraPreviewLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let { chatViewModel.onCapturedImage(it) }
    }
    private val ttsSetupLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        ttsSetupTick.intValue += 1
    }

    private val chatViewModel: ChatViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = application as GyangoApplication
                val repo = ChatRepository(applicationContext)
                return ChatViewModel(
                    app.orchestrator,
                    repo,
                    applicationContext,
                ) as T
            }
        }
    }

    private fun startVoiceRecognition() {
        val vm = chatViewModel
        if (vm.isGenerating.value) return
        vm.clearVoiceError()
        vm.setListeningToMic(true)
        speechTranscriber.startListening(
            localeTag = vm.settings.value.speechInputLocaleTag,
            onStarted = {},
            onFinalResult = { text -> vm.onVoiceTranscript(text) },
            onFailed = { msg -> vm.setVoiceError(msg) },
            onFinished = { vm.setListeningToMic(false) }
        )
    }

    private fun localeFromTag(localeTag: String): Locale = Locale.forLanguageTag(localeTag)

    private fun ttsLanguageStatus(localeTag: String): TtsLanguageStatus {
        if (!::assistantTts.isInitialized || !isTtsReady) return TtsLanguageStatus.ERROR
        val locale = localeFromTag(localeTag)
        val availability = assistantTts.isLanguageAvailable(locale)
        if (availability == TextToSpeech.LANG_MISSING_DATA) {
            return TtsLanguageStatus.MISSING_DATA
        }
        if (availability == TextToSpeech.LANG_NOT_SUPPORTED) {
            return TtsLanguageStatus.UNSUPPORTED
        }
        val setResult = assistantTts.setLanguage(locale)
        if (setResult == TextToSpeech.LANG_MISSING_DATA) {
            return TtsLanguageStatus.MISSING_DATA
        }
        if (setResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            return TtsLanguageStatus.UNSUPPORTED
        }
        return TtsLanguageStatus.READY
    }

    private fun speakAssistantReply(reply: String, localeTag: String): Boolean {
        if (reply.isBlank()) return false
        if (ttsLanguageStatus(localeTag) != TtsLanguageStatus.READY) return false
        val utteranceId = "assistant-${System.currentTimeMillis()}"
        assistantTts.speak(reply, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        return true
    }

    private fun stopAssistantSpeech() {
        if (::assistantTts.isInitialized) {
            assistantTts.stop()
        }
    }

    private fun launchAndroidTtsSetup() {
        val installIntent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
        val installResolved = packageManager.resolveActivity(installIntent, PackageManager.MATCH_DEFAULT_ONLY)
        if (installResolved != null) {
            ttsSetupLauncher.launch(installIntent)
            return
        }
        val settingsIntent = Intent("com.android.settings.TTS_SETTINGS")
        val settingsResolved = packageManager.resolveActivity(settingsIntent, PackageManager.MATCH_DEFAULT_ONLY)
        if (settingsResolved != null) {
            ttsSetupLauncher.launch(settingsIntent)
            return
        }
        Toast.makeText(this, "Android speech setup is unavailable on this device", Toast.LENGTH_LONG).show()
    }

    override fun onResume() {
        super.onResume()
        padNetworkResumeTick.intValue += 1
        val app = application as GyangoApplication
        app.resetModelIdleTimer()
        if (!ModelHardwareGate.inspect(this).canRunSelectedModel) return
        if (!chatViewModel.settings.value.voiceOnboardingComplete) return
        if (BuildConfig.USE_PAD && !GyangoPadDelivery.isMergedModelReady(this)) return
        lifecycleScope.launch {
            if (BuildConfig.VERBOSE_DEBUG_LOGS) {
                android.util.Log.d(TAG, "onResume: foreground refresh")
            }
            chatViewModel.refreshModelsAfterForeground()
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            if (BuildConfig.VERBOSE_DEBUG_LOGS) {
                android.util.Log.w(TAG, "onTrimMemory(level=$level): forwarded to Application policy")
            }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (BuildConfig.VERBOSE_DEBUG_LOGS) {
            android.util.Log.w(TAG, "onLowMemory: forwarded to Application policy")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        speechTranscriber = SpeechTranscriber(this)
        assistantTts = TextToSpeech(this) { status ->
            isTtsReady = (status == TextToSpeech.SUCCESS)
            if (isTtsReady) {
                assistantTts.setSpeechRate(1.0f)
            }
            ttsSetupTick.intValue += 1
        }

        setContent {
            GyangoAITheme {
                val activity = this@MainActivity
                val settingsState by chatViewModel.settings.collectAsState()
                val messagesState by chatViewModel.messages.collectAsState()
                val generatingState by chatViewModel.isGenerating.collectAsState()
                val loadingModelState by chatViewModel.isLoadingModel.collectAsState()
                val modelLoadErrorState by chatViewModel.modelLoadError.collectAsState()
                val documentError by chatViewModel.documentError.collectAsState()
                val isListeningToMic by chatViewModel.isListeningToMic.collectAsState()
                val voiceError by chatViewModel.voiceError.collectAsState()
                val entryStrings = rememberChatDisplayStrings(settingsState.speechInputLocaleTag)
                // Re-evaluate each frame so the gate matches the real device (not a stale remember snapshot).
                val hardwareSupport = ModelHardwareGate.inspect(activity)
                val padDownloadMutex = remember { Mutex() }
                var padStep by remember {
                    mutableStateOf(
                        when {
                            !BuildConfig.USE_PAD -> PadBrainStep.Done
                            GyangoPadDelivery.isMergedModelReady(activity) -> PadBrainStep.Done
                            activity.isActiveNetworkCellular() -> PadBrainStep.PickNetwork
                            else -> PadBrainStep.WaitWifi
                        },
                    )
                }
                var padDownloadProgress by remember { mutableStateOf(0f) }
                var padDownloadDetail by remember { mutableStateOf<String?>(null) }
                var padDownloadError by remember { mutableStateOf<String?>(null) }
                var padOnboardingGateResolved by rememberSaveable {
                    mutableStateOf(!BuildConfig.USE_PAD || GyangoPadDelivery.isMergedModelReady(activity))
                }
                val padResumeTick = padNetworkResumeTick.intValue
                val ttsRefreshTick = ttsSetupTick.intValue
                var ttsSetupRequired by rememberSaveable { mutableStateOf(false) }
                var ttsSetupMessage by rememberSaveable { mutableStateOf<String?>(null) }
                var speechCursorInitialized by rememberSaveable { mutableStateOf(false) }
                var lastSpokenAssistantId by rememberSaveable { mutableStateOf(-1L) }
                var postDownloadStep by remember(settingsState.starterCheckInPromptSeen, settingsState.learnerProfile.starterCheckInCompleted) {
                    mutableStateOf(
                        if (settingsState.starterCheckInPromptSeen || settingsState.learnerProfile.starterCheckInCompleted) {
                            PostDownloadStep.Done
                        } else {
                            PostDownloadStep.Prompt
                        }
                    )
                }
                var padWalkthroughDismissed by rememberSaveable { mutableStateOf(false) }
                val scope = rememberCoroutineScope()
                val padBackgroundStatusHint = if (!BuildConfig.USE_PAD || padStep == PadBrainStep.Done) {
                    null
                } else {
                    when (padStep) {
                        PadBrainStep.Downloading -> {
                            val percent = (padDownloadProgress * 100f).toInt().coerceIn(0, 100)
                            val detail = padDownloadDetail?.takeIf { it.isNotBlank() }
                            if (detail != null) {
                                "${entryStrings.onboardingPadDownloadingHint} ($percent%) • $detail"
                            } else {
                                "${entryStrings.onboardingPadDownloadingHint} ($percent%)"
                            }
                        }
                        PadBrainStep.PickNetwork,
                        PadBrainStep.WaitWifi,
                        -> entryStrings.onboardingPadWaitingHint
                        PadBrainStep.Done -> null
                    }
                }
                val showPadFeatureWalkthrough =
                    BuildConfig.USE_PAD &&
                        settingsState.voiceOnboardingComplete &&
                        postDownloadStep == PostDownloadStep.Done &&
                        padStep != PadBrainStep.Done &&
                        !padWalkthroughDismissed

                LaunchedEffect(
                    settingsState.voiceOnboardingComplete,
                    settingsState.assistantSpeechEnabled,
                    settingsState.speechInputLocaleTag,
                    ttsRefreshTick,
                ) {
                    if (!settingsState.voiceOnboardingComplete || !settingsState.assistantSpeechEnabled) {
                        ttsSetupRequired = false
                        ttsSetupMessage = null
                        return@LaunchedEffect
                    }
                    when (ttsLanguageStatus(settingsState.speechInputLocaleTag)) {
                        TtsLanguageStatus.READY -> {
                            ttsSetupRequired = false
                            ttsSetupMessage = null
                        }
                        TtsLanguageStatus.MISSING_DATA -> {
                            ttsSetupRequired = true
                            ttsSetupMessage = "Language pack is missing. Install it in Android speech settings, then return."
                        }
                        TtsLanguageStatus.UNSUPPORTED -> {
                            ttsSetupRequired = true
                            ttsSetupMessage = "Current Android speech engine does not support this language yet."
                        }
                        TtsLanguageStatus.ERROR -> {
                            ttsSetupRequired = true
                            ttsSetupMessage = "Android speech service is not ready. Open setup, then return here."
                        }
                    }
                }

                LaunchedEffect(settingsState.assistantSpeechEnabled) {
                    if (!settingsState.assistantSpeechEnabled) {
                        stopAssistantSpeech()
                    }
                }

                LaunchedEffect(generatingState) {
                    if (generatingState) {
                        stopAssistantSpeech()
                    }
                }

                LaunchedEffect(
                    messagesState,
                    generatingState,
                    settingsState.assistantSpeechEnabled,
                    settingsState.speechInputLocaleTag,
                    ttsSetupRequired,
                ) {
                    if (!speechCursorInitialized) {
                        lastSpokenAssistantId = messagesState.lastOrNull { it.sender == Sender.ASSISTANT }?.id ?: -1L
                        speechCursorInitialized = true
                        return@LaunchedEffect
                    }
                    if (!settingsState.assistantSpeechEnabled || ttsSetupRequired || generatingState) {
                        return@LaunchedEffect
                    }
                    val lastAssistant = messagesState.lastOrNull { it.sender == Sender.ASSISTANT && it.text.isNotBlank() }
                        ?: return@LaunchedEffect
                    if (lastAssistant.id == lastSpokenAssistantId) return@LaunchedEffect
                    if (speakAssistantReply(lastAssistant.text, settingsState.speechInputLocaleTag)) {
                        lastSpokenAssistantId = lastAssistant.id
                    }
                }

                fun enqueuePadModelDownload() {
                    scope.launch {
                        if (!padDownloadMutex.tryLock()) {
                            if (BuildConfig.VERBOSE_DEBUG_LOGS) {
                                android.util.Log.w(TAG, "PAD download already running; ignoring duplicate start")
                            }
                            return@launch
                        }
                        try {
                            padDownloadError = null
                            padStep = PadBrainStep.Downloading
                            padDownloadProgress = 0f
                            runCatching {
                                GyangoPadDelivery.ensureChunksFetchedAndMerged(
                                    activity,
                                    onProgress = { p ->
                                        withContext(Dispatchers.Main.immediate) {
                                            padDownloadProgress = p
                                        }
                                    },
                                    onPackDetail = { oneBased, merging ->
                                        withContext(Dispatchers.Main.immediate) {
                                            padDownloadDetail = if (merging) {
                                                entryStrings.brainMergingDetail
                                            } else if (oneBased > 0) {
                                                brainPackProgressDetail(
                                                    settingsState.speechInputLocaleTag,
                                                    oneBased,
                                                    GyangoPadDelivery.PACK_NAMES.size,
                                                )
                                            } else {
                                                null
                                            }
                                        }
                                    },
                                )
                            }.onSuccess {
                                padDownloadError = null
                                padStep = PadBrainStep.Done
                                padDownloadDetail = null
                            }.onFailure { e ->
                                padDownloadDetail = null
                                val msg = e.message ?: "Model download failed"
                                padDownloadError = msg
                                padStep = PadBrainStep.PickNetwork
                            }
                        } finally {
                            padDownloadMutex.unlock()
                        }
                    }
                }

                LaunchedEffect(
                    padResumeTick,
                    padStep,
                    hardwareSupport.canRunSelectedModel,
                ) {
                    if (!BuildConfig.USE_PAD) return@LaunchedEffect
                    if (!hardwareSupport.canRunSelectedModel) return@LaunchedEffect
                    if (GyangoPadDelivery.isMergedModelReady(activity)) {
                        padStep = PadBrainStep.Done
                        padOnboardingGateResolved = true
                        return@LaunchedEffect
                    }
                    if (padStep == PadBrainStep.Downloading || padStep == PadBrainStep.Done) {
                        padOnboardingGateResolved = true
                        return@LaunchedEffect
                    }
                    when {
                        activity.isActiveNetworkWifi() -> {
                            padOnboardingGateResolved = true
                            // Do not auto-retry while PickNetwork + error: that loops fetch(), Play throttles (-7),
                            // and repeated Toasts hit the system limit (5). User uses Retry / Download on screen.
                            when {
                                padStep == PadBrainStep.WaitWifi -> {
                                    padDownloadError = null
                                    enqueuePadModelDownload()
                                }
                                padStep == PadBrainStep.PickNetwork && padDownloadError == null -> {
                                    padDownloadError = null
                                    enqueuePadModelDownload()
                                }
                                else -> Unit
                            }
                        }
                        // Show cellular choice UI only when active network is cellular.
                        activity.isActiveNetworkCellular() && padStep != PadBrainStep.WaitWifi -> {
                            padStep = PadBrainStep.PickNetwork
                        }
                        // No wifi/cellular: hold in wifi-waiting mode until next app open/resume check.
                        !activity.isActiveNetworkCellular() && padStep == PadBrainStep.PickNetwork && padDownloadError == null -> {
                            padOnboardingGateResolved = true
                            padStep = PadBrainStep.WaitWifi
                        }
                        else -> {
                            // No active network. Do not block onboarding; resume PAD decisions later.
                            padOnboardingGateResolved = true
                        }
                    }
                }

                // GPU/NPU (and NNAPI) gate runs before profile and before any PAD download UI.
                when {
                    !hardwareSupport.canRunSelectedModel -> {
                        if (BuildConfig.VERBOSE_DEBUG_LOGS) {
                            android.util.Log.d(
                                TAG,
                                "Hardware gate: unsupported (gpu=${hardwareSupport.hasGpu}, npu=${hardwareSupport.hasNpu}, " +
                                    "ramMb=${hardwareSupport.totalRamMb}, ramOk=${hardwareSupport.hasEnoughRam}, " +
                                    "freeDiskMb=${hardwareSupport.freeDiskMb}, diskOk=${hardwareSupport.hasEnoughFreeDisk})",
                            )
                        }
                        ModelHardwareUnsupportedScreen(strings = entryStrings, support = hardwareSupport)
                    }
                    !settingsState.voiceOnboardingComplete -> {
                        if (!settingsState.onboardingWelcomeSeen) {
                            OnboardingWelcomeScreen(
                                strings = entryStrings,
                                onContinue = {
                                    chatViewModel.updateSettings(
                                        settingsState.copy(onboardingWelcomeSeen = true),
                                    )
                                },
                            )
                        } else if (
                            BuildConfig.USE_PAD &&
                            !GyangoPadDelivery.isMergedModelReady(activity) &&
                            !padOnboardingGateResolved &&
                            padStep == PadBrainStep.PickNetwork
                        ) {
                            BrainNetworkChoiceScreen(
                                strings = entryStrings,
                                lastDownloadError = padDownloadError,
                                onContinueOnWifi = {
                                    padDownloadError = null
                                    padOnboardingGateResolved = true
                                    padStep = PadBrainStep.WaitWifi
                                },
                                onDownloadNow = {
                                    padDownloadError = null
                                    padOnboardingGateResolved = true
                                    enqueuePadModelDownload()
                                },
                                onRetryAfterFailure = {
                                    padDownloadError = null
                                    padOnboardingGateResolved = true
                                    enqueuePadModelDownload()
                                },
                            )
                        } else {
                            ProfileOnboardingScreen(
                                settings = settingsState,
                                strings = entryStrings,
                                onProfileDraft = { chatViewModel.updateSettings(it) },
                                onComplete = { chatViewModel.updateSettings(it) },
                                statusHint = padBackgroundStatusHint,
                                onReadAloudYesSelected = { tag ->
                                    if (ttsLanguageStatus(tag) != TtsLanguageStatus.READY) {
                                        launchAndroidTtsSetup()
                                    }
                                },
                            )
                        }
                    }
                    settingsState.assistantSpeechEnabled && ttsSetupRequired -> {
                        TtsLanguageSetupScreen(
                            strings = entryStrings,
                            statusMessage = ttsSetupMessage,
                            onInstallLanguagePack = { launchAndroidTtsSetup() },
                            onContinueWithoutReadAloud = {
                                chatViewModel.updateSettings(
                                    settingsState.copy(assistantSpeechEnabled = false),
                                )
                            },
                        )
                    }
                    postDownloadStep == PostDownloadStep.Prompt -> {
                        LearningCheckInPromptScreen(
                            localeTag = settingsState.speechInputLocaleTag,
                            statusHint = padBackgroundStatusHint,
                            onStart = { postDownloadStep = PostDownloadStep.CheckIn },
                            onSkip = {
                                chatViewModel.skipStarterCheckIn()
                                postDownloadStep = PostDownloadStep.Done
                            },
                        )
                        return@GyangoAITheme
                    }
                    postDownloadStep == PostDownloadStep.CheckIn -> {
                        LaunchedEffect(
                            postDownloadStep,
                            settingsState.voiceOnboardingComplete,
                            hardwareSupport.canRunSelectedModel,
                            padStep,
                        ) {
                            if (!settingsState.voiceOnboardingComplete) return@LaunchedEffect
                            if (!hardwareSupport.canRunSelectedModel) return@LaunchedEffect
                            if (BuildConfig.USE_PAD && padStep != PadBrainStep.Done) return@LaunchedEffect
                            chatViewModel.warmUpModel()
                        }
                        LearningCheckInScreen(
                            localeTag = settingsState.speechInputLocaleTag,
                            birthYear = settingsState.birthYear,
                            statusHint = padBackgroundStatusHint,
                            onComplete = { answers, subjects ->
                                chatViewModel.completeStarterCheckIn(answers, subjects)
                                postDownloadStep = PostDownloadStep.Done
                            },
                            onSkip = {
                                chatViewModel.skipStarterCheckIn()
                                postDownloadStep = PostDownloadStep.Done
                            },
                        )
                        return@GyangoAITheme
                    }
                    showPadFeatureWalkthrough -> {
                        PadFeatureWalkthroughScreen(
                            localeTag = settingsState.speechInputLocaleTag,
                            statusHint = padBackgroundStatusHint,
                            onDone = { padWalkthroughDismissed = true },
                            onSkip = { padWalkthroughDismissed = true },
                        )
                        return@GyangoAITheme
                    }
                    !BuildConfig.USE_PAD || padStep == PadBrainStep.Done -> {
                        var chatInputMode by rememberSaveable { mutableStateOf(ChatInputMode.TextAndVoice.name) }
                        val mode = remember(chatInputMode) {
                            runCatching { ChatInputMode.valueOf(chatInputMode) }
                                .getOrDefault(ChatInputMode.TextAndVoice)
                        }

                        LaunchedEffect(settingsState.voiceOnboardingComplete, hardwareSupport.canRunSelectedModel, padStep) {
                            if (!settingsState.voiceOnboardingComplete) return@LaunchedEffect
                            if (!hardwareSupport.canRunSelectedModel) return@LaunchedEffect
                            if (BuildConfig.USE_PAD && padStep != PadBrainStep.Done) return@LaunchedEffect
                            chatViewModel.warmUpModel()
                        }

                        Surface {
                            ChatScreen(
                                messages = messagesState,
                                isGenerating = generatingState,
                                isLoadingModel = loadingModelState,
                                modelLoadError = modelLoadErrorState,
                                onRetryModelLoad = { chatViewModel.warmUpModel() },
                                settings = settingsState,
                                isListeningToMic = isListeningToMic,
                                chatInputMode = mode,
                                onChatInputModeChange = { chatInputMode = it.name },
                                documentError = documentError,
                                onDismissDocumentError = { chatViewModel.clearDocumentError() },
                                onSend = {
                                    stopAssistantSpeech()
                                    chatViewModel.onUserSend(it)
                                },
                                onSettingsChanged = { chatViewModel.updateSettings(it) },
                                onReadAloudYesSelected = { tag ->
                                    if (ttsLanguageStatus(tag) != TtsLanguageStatus.READY) {
                                        launchAndroidTtsSetup()
                                    }
                                },
                                onClearChat = { chatViewModel.clearChat() },
                                onTakePicture = { cameraPreviewLauncher.launch(null) },
                                onUploadImage = {
                                    imagePickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                onVoiceInputClick = {
                                    when {
                                        generatingState || isListeningToMic -> Unit
                                        ContextCompat.checkSelfPermission(
                                            activity,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED -> startVoiceRecognition()
                                        else -> micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                voiceError = voiceError,
                                onDismissVoiceError = { chatViewModel.clearVoiceError() },
                                onFeedbackClick = {
                                    val emailIntent = Intent(
                                        Intent.ACTION_SENDTO,
                                        Uri.parse("mailto:spandan@asterteksolutions.com")
                                    ).apply {
                                        putExtra(Intent.EXTRA_SUBJECT, "GyanGo Feedback")
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "Hi Spandan,\n\nWhat I was doing:\n\nWhat happened:\n\nWhat I expected:\n\nFeature request:\n"
                                        )
                                    }
                                    val resolved = emailIntent.resolveActivity(activity.packageManager)
                                    if (resolved != null) {
                                        activity.startActivity(emailIntent)
                                    } else {
                                        Toast.makeText(activity, "No mail app found on this device", Toast.LENGTH_LONG).show()
                                    }
                                },
                                onNavigateBack = null
                            )
                        }
                    }
                    padStep == PadBrainStep.PickNetwork -> {
                        BrainNetworkChoiceScreen(
                            strings = entryStrings,
                            lastDownloadError = padDownloadError,
                            onContinueOnWifi = {
                                padDownloadError = null
                                padStep = PadBrainStep.WaitWifi
                            },
                            onDownloadNow = {
                                padDownloadError = null
                                enqueuePadModelDownload()
                            },
                            onRetryAfterFailure = {
                                padDownloadError = null
                                enqueuePadModelDownload()
                            },
                        )
                    }
                    padStep == PadBrainStep.WaitWifi -> {
                        BrainWaitingForWifiScreen(
                            strings = entryStrings,
                            onContinueDownloadNow = {
                                padDownloadError = null
                                enqueuePadModelDownload()
                            },
                            onResumeLater = {
                                padDownloadError = null
                                padStep = PadBrainStep.PickNetwork
                            },
                        )
                    }
                    padStep == PadBrainStep.Downloading -> {
                        BrainPreparingProgressScreen(
                            strings = entryStrings,
                            progress = padDownloadProgress,
                            packDetail = padDownloadDetail,
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        if (::speechTranscriber.isInitialized) {
            speechTranscriber.release()
        }
        if (::assistantTts.isInitialized) {
            assistantTts.stop()
            assistantTts.shutdown()
        }
        super.onDestroy()
    }
}
