package ai.pocket.chatbot.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ai.pocket.core.ChatMessage
import ai.pocket.core.InferenceSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.chatDataStore by preferencesDataStore(name = "pocket_ai_chat")

class ChatRepository(private val context: Context) {

    companion object {
        private val KEY_HISTORY = stringPreferencesKey("history")
        private val KEY_INFERENCE_SETTINGS = stringPreferencesKey("inference_settings")
        private val json = Json { ignoreUnknownKeys = true }
        private val historySerializer = ListSerializer(ChatMessage.serializer())
    }

    val historyFlow: Flow<List<ChatMessage>> =
        context.chatDataStore.data.map { prefs ->
            val raw = prefs[KEY_HISTORY] ?: return@map emptyList()
            try {
                json.decodeFromString(historySerializer, raw)
            } catch (_: Throwable) {
                emptyList()
            }
        }

    val settingsFlow: Flow<InferenceSettings> =
        context.chatDataStore.data.map { prefs ->
            val raw = prefs[KEY_INFERENCE_SETTINGS]
            if (raw == null) {
                return@map firstLaunchInferenceSettings()
            }
            val decoded = try {
                json.decodeFromString(InferenceSettings.serializer(), raw)
            } catch (_: Throwable) {
                InferenceSettings()
            }
            normalizeInferenceSettings(decoded)
        }

    private fun firstLaunchInferenceSettings(): InferenceSettings =
        InferenceSettings(
            speechInputLocaleTag = "en-US",
            assistantSpeechEnabled = false,
            voiceOnboardingComplete = false,
        )

    /**
     * Coerce speech locale, TTS language/gender, and keep stored data consistent.
     */
    private fun normalizeInferenceSettings(settings: InferenceSettings): InferenceSettings {
        val allowed = setOf("te-IN", "hi-IN", "en-US")
        val speech = if (settings.speechInputLocaleTag in allowed) settings.speechInputLocaleTag else "en-US"
        return settings.copy(
            speechInputLocaleTag = speech,
            assistantSpeechEnabled = false,
        )
    }

    suspend fun saveHistory(messages: List<ChatMessage>) {
        val encoded = json.encodeToString(historySerializer, messages)
        context.chatDataStore.edit { prefs ->
            prefs[KEY_HISTORY] = encoded
        }
    }

    suspend fun clearHistory() {
        context.chatDataStore.edit { prefs ->
            prefs.remove(KEY_HISTORY)
        }
    }

    suspend fun saveInferenceSettings(settings: InferenceSettings) {
        val encoded = json.encodeToString(InferenceSettings.serializer(), settings)
        context.chatDataStore.edit { prefs ->
            prefs[KEY_INFERENCE_SETTINGS] = encoded
        }
    }
}
