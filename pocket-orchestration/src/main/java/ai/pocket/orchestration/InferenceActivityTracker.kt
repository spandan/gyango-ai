package ai.pocket.orchestration

/**
 * Tracks overlapping inference (LLM, audio pipeline, etc.) so idle eviction does not run mid-flight.
 */
interface InferenceActivityTracker {
    fun beginInference()
    fun endInference()
}
