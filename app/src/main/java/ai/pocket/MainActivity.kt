package ai.pocket

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ai.pocket.audio.AudioAnalysisViewModel
import ai.pocket.chatbot.data.ChatRepository
import ai.pocket.chatbot.ui.ChatScreen
import ai.pocket.chatbot.ui.ChatViewModel
import ai.pocket.chatbot.ui.theme.PocketAITheme
import ai.pocket.ui.AnalyzeAudioScreen
import ai.pocket.ui.HomeScreen
import androidx.compose.material3.Surface
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {

    private val documentPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val mimeType = contentResolver.getType(it)
            chatViewModel.onDocumentAttached(it, mimeType)
        }
    }

    private val audioPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            audioAnalysisViewModel.onAudioPicked(it, queryDisplayName(it))
        }
    }

    private val recordAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            audioAnalysisViewModel.startRecording()
        }
    }

    private fun startRecordingWithPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED -> audioAnalysisViewModel.startRecording()
            else -> recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private val chatViewModel: ChatViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = application as PocketAIApplication
                val repo = ChatRepository(applicationContext)
                return ChatViewModel(app.orchestrator, repo, applicationContext) as T
            }
        }
    }

    private val audioAnalysisViewModel: AudioAnalysisViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = application as PocketAIApplication
                return AudioAnalysisViewModel(application, app.orchestrator) as T
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (application as PocketAIApplication).resetModelIdleTimer()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PocketAITheme {
                val navController = rememberNavController()
                val messagesState by chatViewModel.messages.collectAsState()
                val generatingState by chatViewModel.isGenerating.collectAsState()
                val loadingModelState by chatViewModel.isLoadingModel.collectAsState()
                val settingsState by chatViewModel.settings.collectAsState()
                val documentError by chatViewModel.documentError.collectAsState()
                val audioUi by audioAnalysisViewModel.ui.collectAsState()

                Surface {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_HOME
                    ) {
                        composable(ROUTE_HOME) {
                            HomeScreen(
                                onOpenChat = { navController.navigate(ROUTE_CHAT) },
                                onOpenAnalyzeAudio = { navController.navigate(ROUTE_ANALYZE) }
                            )
                        }
                        composable(ROUTE_CHAT) {
                            ChatScreen(
                                messages = messagesState,
                                isGenerating = generatingState,
                                isLoadingModel = loadingModelState,
                                settings = settingsState,
                                documentError = documentError,
                                onDismissDocumentError = { chatViewModel.clearDocumentError() },
                                onSend = { text -> chatViewModel.onUserSend(text) },
                                onSettingsChanged = { chatViewModel.updateSettings(it) },
                                onClearChat = { chatViewModel.clearChat() },
                                onAttachDocument = {
                                    documentPickerLauncher.launch(
                                        arrayOf("application/pdf", "text/plain", "text/*")
                                    )
                                },
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable(ROUTE_ANALYZE) {
                            AnalyzeAudioScreen(
                                state = audioUi,
                                onBack = { navController.popBackStack() },
                                onPickAudio = {
                                    audioPickerLauncher.launch(
                                        arrayOf("audio/*", "application/ogg", "application/x-wav")
                                    )
                                },
                                onStartRecording = { startRecordingWithPermission() },
                                onStopRecording = { audioAnalysisViewModel.stopRecording() },
                                onAnalyze = { audioAnalysisViewModel.runPipeline() },
                                onClear = { audioAnalysisViewModel.clearSelection() }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { c ->
            val ix = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (ix < 0 || !c.moveToFirst()) null else c.getString(ix)
        }
    }

    companion object {
        private const val ROUTE_HOME = "home"
        private const val ROUTE_CHAT = "chat"
        private const val ROUTE_ANALYZE = "analyze"
    }
}
