package ai.pocket.core

/**
 * LLM that can be explicitly loaded into RAM and released to free memory (e.g. after idle timeout).
 */
interface MemoryManagedLlm : LlmInterface {
    suspend fun loadIntoMemory()
    suspend fun unloadFromMemory()
}
