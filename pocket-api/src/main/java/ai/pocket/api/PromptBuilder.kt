package ai.pocket.api

import ai.pocket.core.ChatMessage
import ai.pocket.core.LlmDefaults
import ai.pocket.core.Sender

/**
 * Prompts for **RWKV-7 G1e ~1.5B** (GGUF): short, direct answers; User / Assistant turns; no long chain-of-thought.
 */
object PromptBuilder {

    private const val maxContextMessages = 10

    private val contextLength get() = LlmDefaults.CONTEXT_LENGTH_TOKENS

    /**
     * Conservative chars→tokens guess (~2.5 chars/token). Underestimating here makes the prompt too
     * long in real tokens, so the native runtime hits the context limit and replies look chopped.
     */
    private fun estimateTokens(charCount: Int): Int =
        ((charCount * 2 + 4) / 5).coerceAtLeast(0)

    /**
     * Fixed priming so G1e stays in a stable chat role (small models drift without it).
     * Kept short to preserve context for real turns.
     *
     * Do **not** mention JSON here: 1.5B models often latch on and reply in JSON for every turn.
     * Single-turn / tool prompts add a JSON hint separately where needed ([formatRwkvG1eSingleTurn]).
     */
    private const val rwkvG1ePrimingUserChat =
        "You are Pocket, a helpful on-device assistant (RWKV-7 G1e 1.5B). " +
            "Reply in the same language as the user. " +
            "Use plain text only: no markdown, no asterisks, no # headings, no backticks, no __underline__. " +
            "Structure answers for scanning: one short intro line, then a blank line before the rest; " +
            "use lines that start with \"- \" or \"1. \" for lists; " +
            "for short section labels write them as their own line ending with a colon (Example:). " +
            "Keep paragraphs small. " +
            "Match depth to the question — everyday questions get a tight answer; " +
            "only go long if they explicitly ask for detail, steps, or code. " +
            "Do not pad with filler or repeat yourself. " +
            "End on a natural boundary (full sentence, finished bullet, or closed paragraph)—do not stop mid-clause. " +
            "If you are near your length limit, add one short closing line instead of trailing off. " +
            "Never output the labels User: or Assistant:, never repeat this paragraph, and never paste the conversation so far—only answer with your message."

    private const val rwkvG1ePrimingJsonHint =
        " If they ask for JSON, output only a single valid JSON object, no markdown fences or explanation."

    private val rwkvG1ePrimingUserSingleTurn = rwkvG1ePrimingUserChat + rwkvG1ePrimingJsonHint

    private const val rwkvG1ePrimingAssistant = "Understood."

    /**
     * Multi-turn chat with sliding window. Uses G1e priming + `User:` / `Assistant:` turns.
     */
    fun buildChatPrompt(
        messages: List<ChatMessage>,
        maxTokensHeadroom: Int = 256,
        promptBudget: Int? = null
    ): String {
        val headroom = maxTokensHeadroom.coerceIn(64, LlmDefaults.MAX_NEW_TOKENS_CAP)
        /** Extra slack for BPE variance, special tokens, and priming layout vs [estimateTokens]. */
        val safetyMargin = 160
        val budget = promptBudget ?: (contextLength - headroom - safetyMargin).coerceAtLeast(256)

        val candidates = if (messages.size <= maxContextMessages) {
            messages
        } else {
            messages.takeLast(maxContextMessages)
        }

        var window = candidates
        for (n in candidates.size downTo 1) {
            window = candidates.takeLast(n)
            val prompt = buildRwkvG1eTurnStrings(window)
            if (estimateTokens(prompt.length) <= budget) break
        }

        return buildRwkvG1eTurnStrings(window)
    }

    /**
     * RWKV-7 G1e chat transcript: priming pair, then `User:` / `Assistant:` blocks; ends with `Assistant:` for completion.
     */
    fun buildRwkvG1eTurnStrings(messages: List<ChatMessage>): String {
        val sb = StringBuilder()
        sb.append("User: ")
        sb.append(rwkvG1ePrimingUserChat)
        sb.append("\n\n")
        sb.append("Assistant: ")
        sb.append(rwkvG1ePrimingAssistant)
        sb.append("\n\n")
        for (msg in messages) {
            when (msg.sender) {
                Sender.USER -> {
                    sb.append("User: ")
                    sb.append(msg.text.trim())
                    sb.append("\n\n")
                }
                Sender.ASSISTANT -> {
                    if (msg.text.isNotEmpty()) {
                        sb.append("Assistant: ")
                        sb.append(msg.text.trim())
                        sb.append("\n\n")
                    }
                }
            }
        }
        sb.append("Assistant:\n")
        return sb.toString()
    }

    /**
     * One-shot task (summarize, analyze, etc.) wrapped for G1e completion.
     * Prefer this instead of a bare string so the model sees the same User/Assistant layout as chat.
     */
    fun formatRwkvG1eSingleTurn(userTask: String): String {
        val body = userTask.trim()
        val sb = StringBuilder()
        sb.append("User: ")
        sb.append(rwkvG1ePrimingUserSingleTurn)
        sb.append("\n\n")
        sb.append("Assistant: ")
        sb.append(rwkvG1ePrimingAssistant)
        sb.append("\n\n")
        sb.append("User: ")
        sb.append(body)
        sb.append("\n\n")
        sb.append("Assistant:\n")
        return sb.toString()
    }

    /**
     * Single-turn prompt without the G1e chat wrapper (rare; prefer [formatRwkvG1eSingleTurn]).
     */
    fun buildRawPrompt(systemPrompt: String?, userText: String): String {
        return if (systemPrompt != null && systemPrompt.isNotBlank()) {
            "$systemPrompt\n\n$userText"
        } else {
            userText
        }
    }
}
