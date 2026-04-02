package ai.pocket

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ai.pocket.chatbot.data.ChatRepository
import ai.pocket.chatbot.ui.ChatScreen
import ai.pocket.chatbot.ui.ChatViewModel
import ai.pocket.chatbot.ui.theme.PocketAITheme
import androidx.compose.material3.Surface

class MainActivity : ComponentActivity() {

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
                return ChatViewModel(app.orchestrator, repo, applicationContext) as T
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
                val messagesState by chatViewModel.messages.collectAsState()
                val generatingState by chatViewModel.isGenerating.collectAsState()
                val loadingModelState by chatViewModel.isLoadingModel.collectAsState()
                val settingsState by chatViewModel.settings.collectAsState()
                val documentError by chatViewModel.documentError.collectAsState()

                Surface {
                    ChatScreen(
                        messages = messagesState,
                        isGenerating = generatingState,
                        isLoadingModel = loadingModelState,
                        settings = settingsState,
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
                        onNavigateBack = null
                    )
                }
            }
        }
    }
}
