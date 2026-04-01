package ai.pocket.chatbot.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ai.pocket.core.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.chatDataStore by preferencesDataStore(name = "pocket_ai_chat")

class ChatRepository(private val context: Context) {

    companion object {
        private val KEY_HISTORY = stringPreferencesKey("history")
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
}
