package ai.gyango.assistant

/**
 * LLM-layer cleanup only: leaked priming, control tags, thinking blocks, and light structural fixes
 * (lists, bullets, glued math). Does not interpret JSON or markdown for UI.
 *
 * For the full string shown in chat bubbles, use [AssistantOutput.formatForDisplay] (markdown-capable polish).
 */
object AssistantLlmSanitizer {

    internal const val gyangoPrimingFingerprint = "You are GyanGo, a helpful on-device assistant"
    /** Plain-text chat / tool priming tail; used to detect verbatim echo. */
    internal const val gyangoPrimingUserChatPlainTail =
        "Never output the labels User: or Assistant:, never repeat this paragraph, and never paste the conversation so far—only answer with your message."
    /** Indian K-12 JSON-only chat priming tail. */
    internal const val gyangoPrimingUserChatJsonTail =
        "Output only that single JSON object. Do not output any characters before or after it. " +
            "Never repeat this instruction block and never paste the conversation or User/Assistant labels."
    /** Earlier priming versions ended here; still strip if the model echoes an old prompt. */
    internal const val gyangoPrimingUserChatLegacyEnd = "Do not pad with filler or repeat yourself."

    private val assistantLeadRoleLabels = Regex("""(?i)^(?:Assistant:|User:)\s*""")

    private fun stripAssistantEchoAndRolePrefixes(raw: String): String {
        var s = raw.trimStart()
        if (s.isEmpty()) return s

        while (s.length >= gyangoPrimingFingerprint.length) {
            val hit = s.indexOf(gyangoPrimingFingerprint, ignoreCase = true)
            if (hit < 0 || hit > 24) break
            val endPlain = s.indexOf(gyangoPrimingUserChatPlainTail, hit, ignoreCase = true).let { i ->
                if (i < 0) -1 else i + gyangoPrimingUserChatPlainTail.length
            }
            val endJson = s.indexOf(gyangoPrimingUserChatJsonTail, hit, ignoreCase = true).let { i ->
                if (i < 0) -1 else i + gyangoPrimingUserChatJsonTail.length
            }
            val endLegacy = s.indexOf(gyangoPrimingUserChatLegacyEnd, hit, ignoreCase = true).let { i ->
                if (i < 0) -1 else i + gyangoPrimingUserChatLegacyEnd.length
            }
            val end = maxOf(endPlain, endJson, endLegacy)
            if (end <= hit) break
            var cutStart = hit
            val userLead = s.lastIndexOf("User:", hit, ignoreCase = true)
            if (userLead >= 0 && hit - userLead <= 12) cutStart = userLead
            s = s.removeRange(cutStart, end).trimStart()
        }

        while (s.isNotEmpty()) {
            val m = assistantLeadRoleLabels.find(s) ?: break
            if (m.range.first != 0) break
            s = s.removeRange(m.range).trimStart()
        }
        return s
    }

    private val forbiddenTokens = listOf(
        "!end_of_the_turn",
        "!start_of_the_turn",
        "<|endoftext|>",
        "<|im_end|>",
        "<|im_start|>",
        "<start_of_turn>",
        "<end_of_turn>",
        "<start_of_turn>user",
        "<start_of_turn>model",
        "<eos>",
        "<bos>",
        "<|end_of_turn|>",
        "<|start_of_turn|>",
        "<|eot_id|>",
        "<|end_header_id|>",
        "<|start_header_id|>",
        "<|assistant|>",
        "<|user|>",
        "<|system|>",
        "<|think|>",
        "<|turn|>",
        "<turn|>",
        "[/INST]",
        "[INST]",
        "<<SYS>>",
        "<</SYS>>",
        "<s>",
        "</s>",
        "model\n",
        "user\n",
        "Assistant:",
        "User:",
        "assistant\n",
        "user\n",
        "end_of_turn",
        "start_of_turn"
    )

    /**
     * Strips leaks, thinking blocks, and chat control tokens **without** running list / bullet /
     * math-spacing heuristics.
     */
    private fun sanitizeLeaksAndThinking(input: String): String {
        var result = stripAssistantEchoAndRolePrefixes(input)

        result = result.replace(Regex("<think>.*?(?:</think>|$)", RegexOption.DOT_MATCHES_ALL), "")
        result = result.replace(Regex("<thought>.*?(?:</thought>|$)", RegexOption.DOT_MATCHES_ALL), "")
        result = result.replace("<think>", "").replace("</think>", "")
        result = result.replace("<thought>", "").replace("</thought>", "")

        result = fixGluedTokenizerArtifacts(result)

        for (tag in forbiddenTokens) {
            result = result.replace(tag, "", ignoreCase = true)
        }

        result = result.replace(Regex("<[^>]*(?:turn|end|start|eos|bos|eot|assistant|user|system)[^>]*>?", RegexOption.IGNORE_CASE), "")
        result = result.replace(Regex("<(?:end_of_turn|start_of_turn|eot_id|end_header_id|assistant|user|system)[^>]*>?"), "")
        result = result.replace(Regex("<\\|[^|]*\\|>"), "")

        result = result.replace(Regex("^<+"), "").replace(Regex(">+$"), "")

        result = result.replace(Regex("\n{3,}"), "\n\n")

        val preambles = listOf(
            "Here is the result:",
            "Sure, I can help with that:",
            "Based on the text provided:",
            "According to the information:",
            "I've analyzed the text:"
        )
        for (p in preambles) {
            if (result.startsWith(p, ignoreCase = true)) {
                val potential = result.substring(p.length).trimStart()
                if (potential.startsWith("\n")) {
                    result = potential.trimStart()
                }
            }
        }

        return result.trim()
    }

    /** Full plain-text cleanup including list / math heuristics. */
    fun sanitize(input: String): String {
        var result = sanitizeLeaksAndThinking(input)

        result = Regex("([^\\n])\\s*(\\d+\\.\\s*)").replace(result) { m ->
            val beforeList = m.groupValues[1]
            val listHead = m.groupValues[2]
            val dotIdx = m.range.first
            if (beforeList == "." && isLetterDotAbbreviationClose(result, dotIdx)) {
                m.value
            } else {
                "$beforeList\n$listHead"
            }
        }

        result = result.replace(Regex("""(?m)^([2-9])([2-9]\.\s*[A-Za-z])"""), "$1\n$2")
        // Do not insert a space after `3.` when the rest is digits (e.g. 3.14); only fix glued list markers like `3.Word`.
        result = Regex("""(?m)^(\d+)\.(\S)""").replace(result) { m ->
            val rest = m.groupValues[2]
            if (rest.isNotEmpty() && rest[0].isDigit()) m.value else "${m.groupValues[1]}. $rest"
        }

        result = loosenGluedMathOperators(result)

        result = result.replace(Regex("""([.!?;:])\s+([*•])\s+"""), "$1\n$2 ")

        return result.trim()
    }

    private fun isLetterDotAbbreviationClose(text: String, dotIndex: Int): Boolean {
        if (dotIndex < 0 || dotIndex > text.lastIndex || text[dotIndex] != '.') return false
        var i = dotIndex
        while (i >= 0 && text[i] == '.') i--
        if (i < 0 || !text[i].isLetter()) return false
        var pairs = 0
        while (true) {
            if (i + 1 > text.lastIndex || text[i + 1] != '.') return false
            pairs++
            if (i < 2 || text[i - 1] != '.') return pairs >= 2
            i -= 2
        }
    }

    private fun fixGluedTokenizerArtifacts(s: String): String {
        var t = s.replace(Regex("""(?<=[a-z])U\.S\.A\."""), " U.S.A.")
        t = t.replace(Regex("""(?<=[a-z])U\.S\."""), " U.S.")
        t = Regex("""([a-z]{8,})sa(\s+[A-Z]|\s*[\u201C"\u2018'])""").replace(t) { m ->
            "${m.groupValues[1]}s a${m.groupValues[2]}"
        }
        return t
    }

    private fun loosenGluedMathOperators(s: String): String {
        var t = s.replace(Regex("""(\d)=(\d)"""), "$1 = $2")
        t = t.replace(Regex("""(\d)\+(\d)"""), "$1 + $2")
        t = t.replace(Regex("""(\d)-(\d)"""), "$1 - $2")
        t = t.replace(Regex("""([a-zA-Z])=([0-9])"""), "$1 = $2")
        t = t.replace(Regex("""(\d)([a-zA-Z])="""), "$1 $2=")
        return t
    }
}

/**
 * Stateful filter for **streaming** LLM chunks: suppresses partial forbidden tags and thinking blocks.
 * Finish with [finish] so the tail is run through [AssistantLlmSanitizer.sanitize].
 */
class AssistantLlmStreamSanitizer {
    private var buffer = ""
    private var isThinking = false

    private val forbiddenTokens = listOf(
        "<think>",
        "</think>",
        "<thought>",
        "</thought>",
        "<|end_of_turn|>",
        "<|start_of_turn|>",
        "<start_of_turn>",
        "<end_of_turn>",
        "!end_of_the_turn",
        "!start_of_the_turn",
        "<|endoftext|>",
        "<|im_end|>",
        "<|im_start|>",
        "<|eot_id|>",
        "<|end_header_id|>",
        "<|start_header_id|>",
        "<|assistant|>",
        "<|user|>",
        "<eos>",
        "<bos>",
        "Assistant:",
        "User:",
        "assistant\n",
        "user\n",
        "end_of_turn",
        "start_of_turn"
    ).sortedByDescending { it.length }

    fun process(chunk: String): String {
        buffer += chunk
        var output = ""

        while (buffer.isNotEmpty()) {
            if (buffer.startsWith("<think>", ignoreCase = true)) {
                isThinking = true
                buffer = buffer.substring("<think>".length)
                continue
            }
            if (buffer.startsWith("<thought>", ignoreCase = true)) {
                isThinking = true
                buffer = buffer.substring("<thought>".length)
                continue
            }

            if (isThinking) {
                val endThinkIdx = buffer.indexOf("</think>", ignoreCase = true)
                val endThoughtIdx = buffer.indexOf("</thought>", ignoreCase = true)

                val endIdx = when {
                    endThinkIdx != -1 && endThoughtIdx != -1 -> minOf(endThinkIdx, endThoughtIdx)
                    endThinkIdx != -1 -> endThinkIdx
                    endThoughtIdx != -1 -> endThoughtIdx
                    else -> -1
                }

                if (endIdx != -1) {
                    isThinking = false
                    val tagLen = if (buffer.startsWith("</think", ignoreCase = true, startIndex = endIdx)) 8 else 10
                    buffer = buffer.substring(endIdx + tagLen)
                    continue
                } else {
                    val potentialEnd = (1..10).any { i ->
                        buffer.endsWith("</think>".take(i), ignoreCase = true) ||
                            buffer.endsWith("</thought>".take(i), ignoreCase = true)
                    }
                    if (potentialEnd) {
                        val keep = buffer.takeLast(10)
                        buffer = keep
                        break
                    } else {
                        buffer = ""
                        break
                    }
                }
            }

            val fullMatch = forbiddenTokens.find { buffer.startsWith(it, ignoreCase = true) }

            if (fullMatch != null) {
                buffer = buffer.substring(fullMatch.length)
            } else {
                val potentialMatch = forbiddenTokens.any { it.startsWith(buffer, ignoreCase = true) }

                if (potentialMatch) {
                    break
                } else {
                    output += buffer[0]
                    buffer = buffer.substring(1)
                }
            }
        }

        return output
    }

    fun finish(): String {
        val remaining = buffer
        buffer = ""
        return AssistantLlmSanitizer.sanitize(remaining)
    }
}
