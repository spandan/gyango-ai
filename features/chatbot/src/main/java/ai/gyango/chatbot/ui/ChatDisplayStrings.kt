package ai.gyango.chatbot.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * User-visible chat chrome keyed by [InferenceSettings.speechInputLocaleTag]. Strings are loaded
 * from locale JSON assets (`chatbot_locales/<tag>.json`) to keep translations outside code.
 */
@Serializable
data class ChatDisplayStrings(
    val topBarTitle: String = "",
    val topBarCaption: String = "",
    val emptyVoicePrimaryTitle: String = "",
    val emptyVoicePrimarySubtitle: String = "",
    val emptyTextModeTitle: String = "",
    val emptyTextModeSubtitle: String = "",
    val loadingModelMessage: String = "",
    val assistantJsonStreamingPlaceholder: String = "",
    val assistantAwaitingStreamHints: List<String> = emptyList(),
    val loadingDeviceModelTitle: String = "",
    val loadingDeviceModelSubtitle: String = "",
    val modelLoadFailedTitle: String = "",
    val modelLoadRetryButton: String = "",
    val onboardingProgressSavedHint: String = "",
    val onboardingPadDownloadingHint: String = "",
    val onboardingPadWaitingHint: String = "",
    val micMainContentDescription: String = "",
    val chatToolbarSwitchToTypeLabel: String = "",
    val chatToolbarSwitchToVoiceLabel: String = "",
    val chatToolbarClearLabel: String = "",
    val chatToolbarSettingsLabel: String = "",
    val chatToolbarFeedbackLabel: String = "",
    val chatToolbarReadAloudTurnOnDescription: String = "",
    val chatToolbarReadAloudTurnOffDescription: String = "",
    val brainNetworkHeadline: String = "",
    val brainNetworkSubtext: String = "",
    val brainNetworkWifiOnly: String = "",
    val brainNetworkProceedNow: String = "",
    val brainWaitingWifiHeadline: String = "",
    val brainWaitingWifiSubtext: String = "",
    val brainWaitingWifiOptionsTitle: String = "",
    val brainWaitingWifiContinueDownloadButton: String = "",
    val brainWaitingWifiResumeLaterButton: String = "",
    val brainPreparingHeadline: String = "",
    val brainPreparingSubtext: String = "",
    val brainMergingDetail: String = "",
    val brainRetryDownloadButton: String = "",
    val brainDownloadFailedHeadline: String = "",
    val brainPackProgressFormat: String = "Downloading pack %1\$d of %2\$d",
    val onboardingIntroTitle: String = "",
    val onboardingIntroBody: String = "",
    val onboardingIntroContinue: String = "",
    val onboardingWelcomeTitle: String = "",
    val onboardingWelcomeBody: String = "",
    val onboardingFirstNameLabel: String = "",
    val onboardingLastNameLabel: String = "",
    val onboardingBirthOptionalSection: String = "",
    val onboardingBirthMonthLabel: String = "",
    val onboardingBirthMonthNotSet: String = "",
    val onboardingBirthYearLabel: String = "",
    val onboardingLanguageSection: String = "",
    val onboardingVoiceSection: String = "",
    val onboardingReadAloudSection: String = "",
    val onboardingReadAloudNoLabel: String = "",
    val onboardingReadAloudYesLabel: String = "",
    val onboardingContinue: String = "",
    val ttsSetupTitle: String = "",
    val ttsSetupBody: String = "",
    val ttsSetupInstallButton: String = "",
    val ttsSetupSkipButton: String = "",
    val onboardingHardwareUnsupportedTitle: String = "",
    val onboardingHardwareUnsupportedBody: String = "",
    val onboardingHardwareUnsupportedPerformanceNote: String = "",
    val settingsTitle: String = "",
    val settingsConversationLanguage: String = "",
    val settingsConversationLanguageHint: String = "",
    val settingsSpeechPickerTitle: String = "",
    val settingsApply: String = "",
    val settingsCancel: String = "",
    val settingsHubTitle: String = "",
    val settingsHubProfileTitle: String = "",
    val settingsHubProfileSubtitle: String = "",
    val settingsHubChatTitle: String = "",
    val settingsHubChatSubtitle: String = "",
    val settingsProfileTitle: String = "",
    val settingsChatTitle: String = "",
    val settingsBack: String = "",
    val settingsClose: String = "",
    val profileEmailLabel: String = "",
    val settingsBirthAgeHint: String = "",
    val settingsChatLongAnswersTitle: String = "",
    val settingsChatLongAnswersHint: String = "",
    val settingsChatReasoningTitle: String = "",
    val settingsChatReasoningHint: String = "",
    val settingsChatCreativityTitle: String = "",
    val settingsChatCreativityHint: String = "",
    val settingsChatLowPowerTitle: String = "",
    val settingsChatLowPowerHint: String = "",
    val settingsChangeConversationLanguage: String = "",
    val settingsHubInterestsTitle: String = "",
    val settingsHubInterestsSubtitle: String = "",
    val settingsAboutTitle: String = "",
    val settingsVersionFormat: String = "",
    val settingsInterestsTitle: String = "",
    val settingsInterestsEmpty: String = "",
    val settingsInterestsHint: String = "",
    val settingsInterestsClearButton: String = "",
    val settingsInterestsDoneButton: String = "",
    val settingsInterestsTopicPrefix: String = "",
    val settingsInterestsQuestionPrefix: String = "",
    val onboardingHardwareChecklistTitle: String = "",
    val onboardingHardwareNoAccelCard: String = "",
    val onboardingHardwareLowRamCard: String = "",
    val onboardingHardwareLowDiskCard: String = "",
    val childSafeBanner: String = "",
)

/** Shown next to assistant bubbles and in localized copy; keep in sync with branding. */
const val CHAT_ASSISTANT_DISPLAY_NAME = "GyanGo"

/** Global product tagline (English) for captions. */
const val CHAT_BRAND_TAGLINE = "Gyan on the Go"

private object ChatDisplayStringsStore {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = mutableMapOf<String, ChatDisplayStrings>()

    fun load(context: Context, localeTag: String): ChatDisplayStrings {
        val key = canonicalTag(localeTag)
        cache[key]?.let { return it }

        val loaded = decodeAssetOrNull(context, "chatbot_locales/$key.json")
            ?: decodeAssetOrNull(context, "chatbot_locales/en-US.json")
            ?: ChatDisplayStrings(
                topBarTitle = CHAT_ASSISTANT_DISPLAY_NAME,
                topBarCaption = CHAT_BRAND_TAGLINE,
            )
        cache[key] = loaded
        return loaded
    }

    private fun decodeAssetOrNull(context: Context, assetPath: String): ChatDisplayStrings? =
        runCatching {
            context.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { reader ->
                json.decodeFromString(ChatDisplayStrings.serializer(), reader.readText())
            }
        }.getOrNull()

    private fun canonicalTag(localeTag: String): String = when {
        localeTag.startsWith("te", ignoreCase = true) -> "te-IN"
        localeTag.startsWith("hi", ignoreCase = true) -> "hi-IN"
        else -> "en-US"
    }
}

/** Localized “pack x of y” line during PAD download (1-based index). */
fun brainPackProgressDetail(context: Context, localeTag: String, oneBased: Int, total: Int): String {
    val strings = ChatDisplayStringsStore.load(context, localeTag)
    val locale = Locale.forLanguageTag(localeTag)
    return runCatching {
        String.format(locale, strings.brainPackProgressFormat, oneBased, total)
    }.getOrElse {
        "Downloading pack $oneBased of $total"
    }
}

fun chatDisplayStringsForLocale(context: Context, localeTag: String): ChatDisplayStrings =
    ChatDisplayStringsStore.load(context, localeTag)

@Composable
fun rememberChatDisplayStrings(localeTag: String): ChatDisplayStrings {
    val context = LocalContext.current.applicationContext
    return remember(context, localeTag) { chatDisplayStringsForLocale(context, localeTag) }
}
