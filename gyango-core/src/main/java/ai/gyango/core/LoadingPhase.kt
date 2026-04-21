package ai.gyango.core

/**
 * Phase of the generation process, used for UX feedback (e.g. loading vs thinking).
 */
enum class LoadingPhase {
    /** AI model is loading from storage into memory (first-time only). */
    LOADING_MODEL,
    /** Model is ready; generating response. */
    GENERATING
}
