package ai.pocket.api

import ai.pocket.core.ChatMessage
import ai.pocket.core.LlmDefaults
import ai.pocket.core.Sender

/**
 * Prompts for on-device **Gemma** (LiteRT-LM): short, direct answers; `User:` / `Assistant:` turns
 * for completion-style feeding (compatible with IT chat templates).
 */
object PromptBuilder {

    private const val maxContextMessages = 10

    private val contextLength get() = LlmDefaults.CONTEXT_LENGTH_TOKENS

    private fun estimateTokens(charCount: Int): Int =
        ((charCount * 2 + 4) / 5).coerceAtLeast(0)

    /**
     * Fixed priming. The opening sentence must stay aligned with [ResponseSanitizer] leak detection.
     *
     * Do **not** mention JSON in the base priming; single-turn tool prompts add a JSON hint via
     * [formatPocketSingleTurn] (with [replyLanguageInstruction]).
     */
    private const val gemmaPrimingUserChat =
        "You are Pocket, a helpful on-device assistant (Gemma, LiteRT). " +
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

    private const val gemmaPrimingJsonHint =
        " If they ask for JSON, output only a single valid JSON object, no markdown fences or explanation."

    private const val gemmaPrimingAssistant = "Understood."

    private fun languageNameForLocaleTag(tag: String): String = when {
        tag.startsWith("te", ignoreCase = true) -> "Telugu"
        tag.startsWith("hi", ignoreCase = true) -> "Hindi"
        tag.startsWith("en", ignoreCase = true) -> "English"
        else -> "English"
    }

    /**
     * Binds assistant reply language to the user's app language (e.g. [InferenceSettings.speechInputLocaleTag]).
     */
    fun replyLanguageInstruction(localeTag: String): String {
        val name = languageNameForLocaleTag(localeTag)
        return "The user's preferred language for your replies is $name (locale: $localeTag). " +
            "Write every assistant message in $name unless they explicitly ask for a different language, " +
            "or another language is clearly required for code, quotes, or proper names."
    }

    fun buildChatPrompt(
        messages: List<ChatMessage>,
        maxTokensHeadroom: Int = 256,
        promptBudget: Int? = null,
        preferredReplyLocaleTag: String = "en-US",
    ): String {
        val headroom = maxTokensHeadroom.coerceIn(64, LlmDefaults.MAX_NEW_TOKENS_CAP)
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
            val prompt = buildChatTurnStrings(window, preferredReplyLocaleTag)
            if (estimateTokens(prompt.length) <= budget) break
        }

        return buildChatTurnStrings(window, preferredReplyLocaleTag)
    }

    fun buildChatTurnStrings(
        messages: List<ChatMessage>,
        preferredReplyLocaleTag: String = "en-US",
    ): String {
        val sb = StringBuilder()
        sb.append("User: ")
        sb.append(gemmaPrimingUserChat)
        sb.append(" ")
        sb.append(replyLanguageInstruction(preferredReplyLocaleTag))
        sb.append("\n\n")
        sb.append("Assistant: ")
        sb.append(gemmaPrimingAssistant)
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

    fun formatPocketSingleTurn(
        userTask: String,
        preferredReplyLocaleTag: String = "en-US",
    ): String {
        val body = userTask.trim()
        val sb = StringBuilder()
        sb.append("User: ")
        sb.append(gemmaPrimingUserChat)
        sb.append(" ")
        sb.append(replyLanguageInstruction(preferredReplyLocaleTag))
        sb.append(gemmaPrimingJsonHint)
        sb.append("\n\n")
        sb.append("Assistant: ")
        sb.append(gemmaPrimingAssistant)
        sb.append("\n\n")
        sb.append("User: ")
        sb.append(body)
        sb.append("\n\n")
        sb.append("Assistant:\n")
        return sb.toString()
    }

    fun buildRawPrompt(systemPrompt: String?, userText: String): String {
        return if (systemPrompt != null && systemPrompt.isNotBlank()) {
            "$systemPrompt\n\n$userText"
        } else {
            userText
        }
    }
}
