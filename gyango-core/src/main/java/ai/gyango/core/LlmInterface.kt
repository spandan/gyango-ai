package ai.gyango.core

import kotlinx.serialization.Serializable

/**
 * Model-agnostic interface for LLM inference.
 */
interface LlmInterface {
    /** Unique identifier for this model, e.g. "gyango-litert". */
    val modelId: String

    suspend fun generate(request: GenerateRequest, onToken: (String) -> Unit)
}

/**
 * Extended interface for models that support loading-phase feedback (e.g. "Loading model…").
 * Orchestrator uses this when available for better UX.
 */
interface PhasedLlm : LlmInterface {
    suspend fun generateWithPhase(
        request: GenerateRequest,
        onToken: (String) -> Unit,
        onPhaseChange: (LoadingPhase) -> Unit
    )
}

/**
 * Request for text generation. Model-agnostic.
 */
data class GenerateRequest(
    val prompt: String,
    val settings: InferenceSettings
)

/**
 * Inference parameters used across all models.
 */
@Serializable
enum class SubjectMode {
    GENERAL,
    CURIOSITY,
    MATH,
    SCIENCE,
    PHYSICS,
    CHEMISTRY,
    BIOLOGY,
    CODING,
    WRITING,
    EXAM_PREP,
}

@Serializable
enum class SafetyProfile {
    STANDARD,
    KIDS_STRICT,
}

@Serializable
enum class LearningProfile {
    EXPLORER,
    THINKER,
    BUILDER,
}

@Serializable
enum class SkillBand {
    NEW,
    GROWING,
    CONFIDENT,
}

@Serializable
data class LearnerProfile(
    val learningProfile: LearningProfile = LearningProfile.EXPLORER,
    val curiositySignals: Int = 0,
    val supportSignals: Int = 0,
    val challengeSignals: Int = 0,
    /**
     * Internal tuning signal only. Used to adjust explanation depth and pace.
     */
    val iqLevel: Int? = null,
    val starterCheckInCompleted: Boolean = false,
)

@Serializable
data class InferenceSettings(
    val temperature: Float = 0.3f,
    /** Max new tokens per reply; must leave room for priming inside [LlmDefaults.CONTEXT_LENGTH_TOKENS]. */
    val maxTokens: Int = LlmDefaults.DEFAULT_MAX_NEW_TOKENS,
    /**
     * When true, model runtime uses a lower-memory profile intended for thermal/battery constraints.
     */
    val lowPowerMode: Boolean = false,
    /**
     * BCP 47 tag (e.g. `en-US`, `es-ES`) passed to the system speech recognizer for voice input.
     */
    val speechInputLocaleTag: String = "en-US",
    /**
     * When true, assistant replies are read aloud with on-device TTS (FastSpeech + vocoder via ONNX Runtime).
     */
    val assistantSpeechEnabled: Boolean = false,
    /**
     * Given name from first-run profile (trimmed in repository); used in prompts when non-blank.
     */
    val userFirstName: String = "",
    /**
     * Family name from first-run profile (trimmed in repository); used in prompts when non-blank.
     */
    val userLastName: String = "",
    /**
     * Optional email for feedback or local profile display; not sent to the model unless wired later.
     */
    val userEmail: String = "",
    /**
     * Read-aloud (TTS) language code. UI currently offers Telugu only (`te`).
     */
    val ttsLanguage: String = "te",
    /**
     * Reserved read-aloud voice selector for future re-enable.
     */
    val ttsVoiceGender: String = "female",
    /**
     * Set to true after the first welcome screen is acknowledged.
     */
    val onboardingWelcomeSeen: Boolean = false,
    /**
     * Set to false until the user finishes the first-run profile sheet (name + conversation language).
     */
    val voiceOnboardingComplete: Boolean = false,
    /**
     * Optional birth month (1..12) used only for age-aware response caution.
     */
    val birthMonth: Int? = null,
    /**
     * Optional birth year (e.g. 2012) used only for age-aware response caution.
     */
    val birthYear: Int? = null,
    /**
     * Optional subject tutor mode selected in chat UI. Null means no tile is selected and the
     * general assistant prompt is used.
     */
    val subjectMode: SubjectMode? = null,
    /**
     * Safety profile applied across prompts and moderation. Kids mode is the default for this app.
     */
    val safetyProfile: SafetyProfile = SafetyProfile.KIDS_STRICT,
    /**
     * Lightweight personalization derived from onboarding + ongoing questions.
     */
    val learnerProfile: LearnerProfile = LearnerProfile(),
    /**
     * Per-subject comfort band used to tune depth without academic labeling.
     */
    val subjectSkillBands: Map<SubjectMode, SkillBand> = emptyMap(),
    /**
     * Whether the optional personalization screen has already been shown after setup.
     */
    val starterCheckInPromptSeen: Boolean = false,
    /**
     * When true, prompts add a short note that private English reasoning should stay minimal
     * relative to the learner's output language (see [ai.gyango.api.PromptBuilder]).
     */
    val requestModelThoughtInJson: Boolean = false,
    /**
     * Optional local-only interest rows from completed replies (see [InterestSignal]).
     * Capped in [ai.gyango.chatbot.data.ChatRepository]; never sent back to the model automatically in Phase 1.
     */
    val interestSignals: List<InterestSignal> = emptyList(),
    /**
     * Indices (0..3) of selected options for each starter check-in question; empty if skipped.
     */
    val starterCheckInAnswerIndices: List<Int> = emptyList(),
    /**
     * Difficulty level (1..10) for the current topic lane; resets to 1 when the subject tile changes.
     * Increments when the learner follows a spark chip (capped at 10).
     */
    val chatDifficultyLevel: Int = 1,
    /**
     * [SubjectMode.name] or GENERAL for the lane [chatDifficultyLevel] applies to; used to reset on topic switch.
     */
    val chatDifficultyLaneKey: String? = null,
    /**
     * When true, heuristic routing picks the prompt topic from the user utterance (no extra LLM call).
     * When false, [subjectMode] from the UI tile is used.
     */
    val autoRouteSubject: Boolean = true,
)
