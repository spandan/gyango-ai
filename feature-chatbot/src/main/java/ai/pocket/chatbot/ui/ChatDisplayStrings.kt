package ai.pocket.chatbot.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * User-visible chat chrome keyed by [InferenceSettings.speechInputLocaleTag]. Changing language in
 * settings updates these strings for the next composition; assistant **message text** follows the
 * same preference via [ai.pocket.api.PromptBuilder].
 */
data class ChatDisplayStrings(
    val topBarSubtitle: String,
    val emptyVoicePrimaryTitle: String,
    val emptyVoicePrimarySubtitle: String,
    val emptyTextModeTitle: String,
    val emptyTextModeSubtitle: String,
    val loadingModelMessage: String,
    val micMainContentDescription: String,
    val onboardingWelcomeTitle: String,
    val onboardingWelcomeBody: String,
    val onboardingLanguageSection: String,
    val onboardingVoiceSection: String,
    val onboardingContinue: String,
    val settingsTitle: String,
    val settingsConversationLanguage: String,
    val settingsConversationLanguageHint: String,
    val settingsSpeechPickerTitle: String,
    val settingsApply: String,
    val settingsCancel: String,
)

/** Shown next to assistant bubbles and in localized copy; keep in sync with branding. */
const val CHAT_ASSISTANT_DISPLAY_NAME = "Pocket"

private const val ASSISTANT = CHAT_ASSISTANT_DISPLAY_NAME

fun chatDisplayStringsForLocale(localeTag: String): ChatDisplayStrings {
    return when {
        localeTag.startsWith("te", ignoreCase = true) -> ChatDisplayStrings(
            topBarSubtitle = "ప్రైవేట్ · అవసరమైనప్పుడు ఇక్కడే",
            emptyVoicePrimaryTitle = "క్రింద ఉన్న మైక్రోఫోన్‌ను నొక్కండి",
            emptyVoicePrimarySubtitle = "స్వభావంగా మాట్లాడండి — $ASSISTANT మీ ఫోన్‌లోనే వింటుంది, ఆలోచిస్తుంది; ఎంపిక చేసుకుంటే సమాధానాలను బయటకు వినిపిస్తుంది.",
            emptyTextModeTitle = "$ASSISTANT కి హాయ్ చెప్పండి",
            emptyTextModeSubtitle = "అంతా మీ ఫోన్‌లోనే ఉంటుంది.",
            loadingModelMessage = "మోడల్ సిద్ధమవుతోంది — ఒక్క క్షణం.",
            micMainContentDescription = "మాట్లాడండి — మీ భాషలో వింటుంది; సమాధానాలు మీ భాషలో.",
            onboardingWelcomeTitle = "Pocket AI కి స్వాగతం",
            onboardingWelcomeBody = "సమాధానాలు ఎలా వినిపించాలో ఎంచుకోండి. సెట్టింగ్‌లలో ఎప్పుడైనా మార్చుకోవచ్చు.",
            onboardingLanguageSection = "భాష",
            onboardingVoiceSection = "స్వరం",
            onboardingContinue = "కొనసాగించు",
            settingsTitle = "సెట్టింగ్‌లు",
            settingsConversationLanguage = "సంభాషణ భాష",
            settingsConversationLanguageHint = "సహాయక సమాధానాల భాష, ఈ స్క్రీన్‌పై టెక్స్ట్, మరియు వాయిస్ ఇన్‌పుట్. మార్పు తర్వాత కొత్త సందేశాలకు వర్తిస్తుంది.",
            settingsSpeechPickerTitle = "సంభాషణ భాష",
            settingsApply = "వర్తింపజేయి",
            settingsCancel = "రద్దు",
        )
        localeTag.startsWith("hi", ignoreCase = true) -> ChatDisplayStrings(
            topBarSubtitle = "निजी · ज़रूरत पर यहीं मौजूद",
            emptyVoicePrimaryTitle = "नीचे माइक्रोफ़ोन पर टैप करें",
            emptyVoicePrimarySubtitle = "स्वाभाविक बोलें — $ASSISTANT आपके फ़ोन पर ही सुनता है और सोचता है; चालू करने पर जवाब आवाज़ में भी सुन सकते हैं।",
            emptyTextModeTitle = "$ASSISTANT को नमस्ते कहें",
            emptyTextModeSubtitle = "सब कुछ आपके फ़ोन पर ही रहता है।",
            loadingModelMessage = "मॉडल तैयार हो रहा है — एक पल।",
            micMainContentDescription = "बोलें — आपकी भाषा में सुनेगा; जवाब उसी भाषा में।",
            onboardingWelcomeTitle = "Pocket AI में आपका स्वागत है",
            onboardingWelcomeBody = "जवाब कैसे सुनाई दें, चुनें। सेटिंग्स में बाद में बदल सकते हैं।",
            onboardingLanguageSection = "भाषा",
            onboardingVoiceSection = "आवाज़",
            onboardingContinue = "आगे बढ़ें",
            settingsTitle = "सेटिंग्स",
            settingsConversationLanguage = "बातचीत की भाषा",
            settingsConversationLanguageHint = "सहायक के जवाबों की भाषा, इस स्क्रीन का टेक्स्ट, और आवाज़ इनपुट। बदलाव के बाद के नए संदेशों पर लागू होता है।",
            settingsSpeechPickerTitle = "बातचीत की भाषा",
            settingsApply = "लागू करें",
            settingsCancel = "रद्द करें",
        )
        else -> ChatDisplayStrings(
            topBarSubtitle = "Private · here when you need me",
            emptyVoicePrimaryTitle = "Tap the microphone below",
            emptyVoicePrimarySubtitle = "Speak naturally — $ASSISTANT listens and thinks on your phone; when enabled, answers can be read aloud.",
            emptyTextModeTitle = "Say hi to $ASSISTANT",
            emptyTextModeSubtitle = "It's just you and your phone — everything stays on-device.",
            loadingModelMessage = "Getting the model ready — one moment.",
            micMainContentDescription = "Speak — listens in your chosen language; replies in that language.",
            onboardingWelcomeTitle = "Welcome to Pocket AI",
            onboardingWelcomeBody = "Choose your conversation language. You can change this anytime in Settings.",
            onboardingLanguageSection = "Language",
            onboardingVoiceSection = "Voice",
            onboardingContinue = "Continue",
            settingsTitle = "Settings",
            settingsConversationLanguage = "Conversation language",
            settingsConversationLanguageHint = "Language for assistant replies, on-screen chat text, and voice input. Applies to new messages after you change it.",
            settingsSpeechPickerTitle = "Conversation language",
            settingsApply = "Apply",
            settingsCancel = "Cancel",
        )
    }
}

@Composable
fun rememberChatDisplayStrings(localeTag: String): ChatDisplayStrings =
    remember(localeTag) { chatDisplayStringsForLocale(localeTag) }
