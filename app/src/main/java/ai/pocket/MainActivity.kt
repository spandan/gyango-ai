package ai.pocket

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import ai.pocket.chatbot.ui.ChatInputMode
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ai.pocket.chatbot.data.ChatRepository
import ai.pocket.chatbot.ui.ChatScreen
import ai.pocket.chatbot.ui.ChatViewModel
import ai.pocket.chatbot.ui.theme.PocketAITheme
import ai.pocket.chatbot.voice.SpeechTranscriber
import androidx.compose.material3.Surface

class MainActivity : ComponentActivity() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var speechTranscriber: SpeechTranscriber

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceRecognition()
        } else {
            chatViewModel.setVoiceError("Microphone permission is needed for voice input")
        }
    }

    private val documentPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val mimeType = contentResolver.getType(it)
            chatViewModel.onDocumentAttached(it, mimeType)
        }
    }

    private val chatViewModel: ChatViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = application as PocketAIApplication
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

    override fun onResume() {
        super.onResume()
        val app = application as PocketAIApplication
        app.resetModelIdleTimer()
        chatViewModel.refreshModelsAfterForeground()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        speechTranscriber = SpeechTranscriber(this)

        setContent {
            PocketAITheme {
                val messagesState by chatViewModel.messages.collectAsState()
                val generatingState by chatViewModel.isGenerating.collectAsState()
                val loadingModelState by chatViewModel.isLoadingModel.collectAsState()
                val settingsState by chatViewModel.settings.collectAsState()
                val documentError by chatViewModel.documentError.collectAsState()
                val isListeningToMic by chatViewModel.isListeningToMic.collectAsState()
                val voiceError by chatViewModel.voiceError.collectAsState()

                var chatInputMode by rememberSaveable { mutableStateOf(ChatInputMode.VoicePrimary.name) }
                val mode = remember(chatInputMode) {
                    runCatching { ChatInputMode.valueOf(chatInputMode) }
                        .getOrDefault(ChatInputMode.VoicePrimary)
                }

                Surface {
                    ChatScreen(
                        messages = messagesState,
                        isGenerating = generatingState,
                        isLoadingModel = loadingModelState,
                        settings = settingsState,
                        isListeningToMic = isListeningToMic,
                        chatInputMode = mode,
                        onChatInputModeChange = { chatInputMode = it.name },
                        documentError = documentError,
                        onDismissDocumentError = { chatViewModel.clearDocumentError() },
                        onSend = { chatViewModel.onUserSend(it) },
                        onSettingsChanged = { chatViewModel.updateSettings(it) },
                        onClearChat = { chatViewModel.clearChat() },
                        onAttachDocument = {
                            documentPickerLauncher.launch(
                                arrayOf("application/pdf", "text/plain", "text/*")
                            )
                        },
                        onVoiceInputClick = {
                            when {
                                generatingState || isListeningToMic -> Unit
                                ContextCompat.checkSelfPermission(
                                    this@MainActivity,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED -> startVoiceRecognition()
                                else -> micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        voiceError = voiceError,
                        onDismissVoiceError = { chatViewModel.clearVoiceError() },
                        onNavigateBack = null
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        if (isFinishing) {
            (application as PocketAIApplication).unloadAllModelsFromMemoryNow()
        }
        if (::speechTranscriber.isInitialized) {
            speechTranscriber.release()
        }
        super.onDestroy()
    }
}
