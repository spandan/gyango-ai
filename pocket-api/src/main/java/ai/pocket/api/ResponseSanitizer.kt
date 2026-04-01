package ai.pocket.api

object ResponseSanitizer {

    private const val pocketPrimingFingerprint = "You are Pocket, a helpful on-device assistant"
    /** Matches tail of current G1e user priming; used to detect verbatim echo. */
    private const val pocketPrimingUserChatTail =
        "Never output the labels User: or Assistant:, never repeat this paragraph, and never paste the conversation so far—only answer with your message."
    /** Earlier priming versions ended here; still strip if the model echoes an old prompt. */
    private const val pocketPrimingUserChatLegacyEnd = "Do not pad with filler or repeat yourself."

    private val assistantLeadRoleLabels = Regex("""(?i)^(?:Assistant:|User:)\s*""")

    /**
     * Removes verbatim leaked G1e priming and stray `User:` / `Assistant:` prefixes at the start
     * of the assistant message (safe on streaming aggregates after each chunk).
     */
    private fun stripAssistantEchoAndRolePrefixes(raw: String): String {
        var s = raw.trimStart()
        if (s.isEmpty()) return s

        while (s.length >= pocketPrimingFingerprint.length) {
            val hit = s.indexOf(pocketPrimingFingerprint, ignoreCase = true)
            if (hit < 0 || hit > 24) break
            val endNew = s.indexOf(pocketPrimingUserChatTail, hit, ignoreCase = true).let { i ->
                if (i < 0) -1 else i + pocketPrimingUserChatTail.length
            }
            val endLegacy = s.indexOf(pocketPrimingUserChatLegacyEnd, hit, ignoreCase = true).let { i ->
                if (i < 0) -1 else i + pocketPrimingUserChatLegacyEnd.length
            }
            val end = maxOf(endNew, endLegacy)
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
     * Full LLM output cleanup: strip G1e priming/role-label leaks, remove model-specific tags and
     * thinking blocks, and fix smashed list formatting. Single entry point for raw assistant text.
     */
    fun sanitize(input: String): String {
        var result = stripAssistantEchoAndRolePrefixes(input)
        
        // 1. Remove <think>...</think> or <thought>...</thought> blocks entirely.
        // Handles unclosed blocks as well.
        result = result.replace(Regex("<think>.*?(?:</think>|$)", RegexOption.DOT_MATCHES_ALL), "")
        result = result.replace(Regex("<thought>.*?(?:</thought>|$)", RegexOption.DOT_MATCHES_ALL), "")
        result = result.replace("<think>", "").replace("</think>", "")
        result = result.replace("<thought>", "").replace("</thought>", "")

        // 2. Fix smashed numbered lists (e.g., "1. First 2. Second" -> "1. First\n2. Second")
        // Look for: non-newline char + optional space + digit(s) + dot + optional space
        // We use a positive lookahead to ensure we don't consume the next item's content.
        result = result.replace(Regex("([^\\n])\\s*(\\d+\\.\\s*)"), "$1\n$2")

        // 3. Fix smashed bullet points (e.g., "Item 1 - Item 2")
        // Look for: end of a sentence/word + optional space + bullet character + space
        result = result.replace(Regex("([^\\n])\\s+([-*•])\\s+"), "$1\n$2 ")

        // 4. Remove explicit forbidden tokens
        for (tag in forbiddenTokens) {
            result = result.replace(tag, "", ignoreCase = true)
        }
        
        // 5. Remove common model artifacts using regex
        result = result.replace(Regex("<[^>]*(?:turn|end|start|eos|bos|eot|assistant|user|system)[^>]*>?", RegexOption.IGNORE_CASE), "")
        result = result.replace(Regex("<(?:end_of_turn|start_of_turn|eot_id|end_header_id|assistant|user|system)[^>]*>?"), "")
        result = result.replace(Regex("<\\|[^|]*\\|>"), "")
        
        // 6. Strip residual leading/trailing tag characters
        result = result.replace(Regex("^<+"), "").replace(Regex(">+$"), "")
        
        // 7. Normalize newlines (max 2 consecutive)
        result = result.replace(Regex("\n{3,}"), "\n\n")
        
        // 8. Remove common model "thinking" or "preamble" if they are clearly artifacts
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
}

/**
 * Stateful sanitizer for streaming responses to handle partial tokens.
 * It waits to flush text if it looks like it might be starting a forbidden token.
 */
class StreamingResponseSanitizer {
    private var buffer = ""
    private var isThinking = false
    
    // Tokens we absolutely don't want to show even partially during streaming
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
            // 1. Handle Thinking State
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
                    // Check if end of buffer might be start of a closing tag
                    val potentialEnd = (1..10).any { i -> 
                        buffer.endsWith("</think>".take(i), ignoreCase = true) ||
                        buffer.endsWith("</thought>".take(i), ignoreCase = true)
                    }
                    if (potentialEnd) {
                        // Keep the tail, discard the rest
                        val keep = buffer.takeLast(10)
                        buffer = keep
                        break
                    } else {
                        buffer = ""
                        break
                    }
                }
            }

            // 2. Check for forbidden tokens
            val fullMatch = forbiddenTokens.find { buffer.startsWith(it, ignoreCase = true) }

            if (fullMatch != null) {
                buffer = buffer.substring(fullMatch.length)
            } else {
                // 3. Check for potential start of forbidden tokens
                val potentialMatch = forbiddenTokens.any { it.startsWith(buffer, ignoreCase = true) }
                
                if (potentialMatch) {
                    break
                } else {
                    // 4. Safe to flush first char
                    output += buffer[0]
                    buffer = buffer.substring(1)
                }
            }
        }
        
        return output
    }
    
    /**
     * Call this when generation is finished to flush any remaining safe text.
     */
    fun finalFlush(): String {
        val remaining = buffer
        buffer = ""
        // Final pass through the main sanitizer handles unclosed thinking blocks and list splitting
        return ResponseSanitizer.sanitize(remaining)
    }
}
