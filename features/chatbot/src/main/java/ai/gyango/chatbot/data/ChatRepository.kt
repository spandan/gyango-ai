package ai.gyango.chatbot.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ai.gyango.core.ChatMessage
import ai.gyango.core.InferenceSettings
import ai.gyango.core.InterestSignal
import ai.gyango.core.ChatPreferenceMappings
import ai.gyango.core.LlmDefaults
import ai.gyango.core.SafetyProfile
import ai.gyango.core.SkillBand
import ai.gyango.core.SubjectMode
import java.time.Year
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.chatDataStore by preferencesDataStore(name = "gyango_ai_chat")

class ChatRepository(private val context: Context) {

    private val turnTelemetryStore = TurnTelemetryStore(context)

    companion object {
        private val KEY_HISTORY = stringPreferencesKey("history")
        private val KEY_INFERENCE_SETTINGS = stringPreferencesKey("inference_settings")
        private val json = Json { ignoreUnknownKeys = true }
        private val historySerializer = ListSerializer(ChatMessage.serializer())
        private const val INTEREST_SIGNALS_CAP = 36

        /** Legacy installs stored `CURIOSITY` as a subject enum name; map it to `GENERAL` before decode. */
        private fun migrateLegacySubjectModeInInferenceJson(raw: String): String =
            raw.replace("\"CURIOSITY\"", "\"GENERAL\"")
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
                json.decodeFromString(
                    InferenceSettings.serializer(),
                    migrateLegacySubjectModeInInferenceJson(raw),
                )
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
            pinSetupComplete = false,
            subjectMode = SubjectMode.GENERAL,
            maxTokens = ChatPreferenceMappings.MAX_TOKENS_SHORT_ANSWERS,
        )

    /**
     * Coerce speech locale, TTS language/gender, and keep stored data consistent.
     */
    private fun normalizeInferenceSettings(settings: InferenceSettings): InferenceSettings {
        val allowed = setOf(
            "en-US",
            "hi-IN",
            "te-IN",
            "mr-IN",
            "ta-IN",
            "bn-IN",
            "gu-IN",
            "kn-IN",
            "ml-IN",
            "pa-IN",
            "fr-FR",
            "es-ES",
        )
        val speech = if (settings.speechInputLocaleTag in allowed) settings.speechInputLocaleTag else "en-US"
        val thisYear = Year.now().value
        val normalizedBirthMonth = settings.birthMonth?.takeIf { it in 1..12 }
        val normalizedBirthYear = settings.birthYear?.takeIf { it in 1900..thisYear }
        val maxTok = settings.maxTokens.coerceIn(64, LlmDefaults.MAX_NEW_TOKENS_CAP)
        val topicSampling = LlmDefaults.samplingForSubject(settings.subjectMode)
        val temp = topicSampling.temperature.coerceIn(
            LlmDefaults.LITERT_MIN_TEMPERATURE,
            LlmDefaults.LITERT_MAX_TEMPERATURE,
        )
        val topP = topicSampling.topP.coerceIn(LlmDefaults.LITERT_MIN_TOP_P, LlmDefaults.LITERT_MAX_TOP_P)
        val topK = topicSampling.topK.coerceIn(LlmDefaults.LITERT_MIN_TOP_K, LlmDefaults.LITERT_MAX_TOP_K)
        val profileGateComplete = settings.profileOnboardingSubmitted ||
            settings.voiceOnboardingComplete ||
            (
                settings.pinSetupComplete &&
                    settings.userProfileName.isNotBlank()
                )
        return settings.copy(
            temperature = temp,
            topP = topP,
            topK = topK,
            profileOnboardingSubmitted = profileGateComplete,
            speechInputLocaleTag = speech,
            userProfileName = settings.userProfileName.trim().take(80),
            userFirstName = "",
            userLastName = "",
            userEmail = "",
            birthMonth = normalizedBirthMonth,
            birthYear = normalizedBirthYear,
            maxTokens = maxTok,
            subjectMode = settings.subjectMode,
            safetyProfile = settings.safetyProfile.takeIf { it in SafetyProfile.entries } ?: SafetyProfile.KIDS_STRICT,
            subjectSkillBands = settings.subjectSkillBands.filterKeys { it in SubjectMode.entries }
                .mapValues { (_, band) -> band.takeIf { it in SkillBand.entries } ?: SkillBand.NEW },
            learnerProfile = settings.learnerProfile.copy(
                curiositySignals = settings.learnerProfile.curiositySignals.coerceIn(0, 32),
                supportSignals = settings.learnerProfile.supportSignals.coerceIn(0, 32),
                challengeSignals = settings.learnerProfile.challengeSignals.coerceIn(0, 32),
                iqLevel = settings.learnerProfile.iqLevel?.coerceIn(70, 145),
            ),
            interestSignals = settings.interestSignals
                .asSequence()
                .map { row ->
                    InterestSignal(
                        areaOfInterest = row.areaOfInterest.trim().take(120),
                        subjectKey = row.subjectKey.trim().take(24),
                        userQuerySnippet = row.userQuerySnippet.trim().take(150),
                        recordedAtEpochMs = row.recordedAtEpochMs,
                    )
                }
                .filter { it.areaOfInterest.isNotBlank() }
                .toList()
                .takeLast(INTEREST_SIGNALS_CAP),
            starterCheckInAnswerIndices = settings.starterCheckInAnswerIndices
                .map { it.coerceIn(0, 3) }
                .take(16),
            chatDifficultyLevel = settings.chatDifficultyLevel.coerceIn(1, 10),
            chatDifficultyLaneKey = settings.chatDifficultyLaneKey?.trim()?.take(32),
            autoRouteSubject = settings.autoRouteSubject,
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

    suspend fun recordTurnTelemetry(
        subjectKey: String,
        userQuestion: String,
        curiosity: String?,
        difficultyLevel: Int,
        parseStatus: String?,
        parseReason: String?,
        topicContractValid: Boolean?,
    ) {
        runCatching {
            turnTelemetryStore.insertTurn(
                subjectKey = subjectKey,
                userQuestion = userQuestion,
                curiosity = curiosity,
                difficultyLevel = difficultyLevel,
                parseStatus = parseStatus,
                parseReason = parseReason,
                topicContractValid = topicContractValid,
            )
        }
    }
}
