package ai.gyango.assistant

/**
 * Final plain-text cleanup after envelope extraction.
 *
 * This stage intentionally knows nothing about prompt leaks or token-stream quirks; those belong in
 * the LLM sanitizers. Its job is only to make already-safe assistant text pleasant to read.
 */
object AssistantTextPolisher {

    /**
     * When the model wraps the entire reply in one markdown fence (` ``` … ``` `), strip the outer
     * fences so the bubble shows inner text (optional first-line lang tag after opening fence).
     */
    fun stripOuterMarkdownCodeFence(input: String): String {
        val t = input.trim()
        if (t.length < 7 || !t.startsWith("```") || !t.endsWith("```")) return input
        var inner = t.removePrefix("```")
        val nlIdx = inner.indexOf('\n')
        if (nlIdx >= 0) {
            val firstLine = inner.substring(0, nlIdx).trim()
            val looksLikeFenceInfoString =
                firstLine.isEmpty() || LANG_TAG_ONLY.matches(firstLine)
            if (looksLikeFenceInfoString) {
                inner = inner.substring(nlIdx + 1)
            }
        }
        return if (inner.endsWith("```")) {
            inner.removeSuffix("```").trimEnd().trim()
        } else {
            input
        }
    }

    private val LANG_TAG_ONLY = Regex("^[A-Za-z0-9._+-]+$")

    fun polishDisplayText(input: String): String {
        var s = input
        s = normalizeMarkdownUnicode(s)
        s = decodeLiteralNewlineEscapes(s)
        s = latexLiteToPlain(s)
        s = normalizeVisibleCharacters(s)
        s = dedupeRepeatedParagraphs(s)
        s = dedupeConsecutiveDuplicateLines(s)
        s = collapseDuplicateNextMetaQuestion(s)
        s = normalizeWhitespaceForPlainText(s)
        s = fixTokenizedWordGlue(s)
        s = repairMalformedMarkdownFences(s)
        s = transformOutsideFencedCodeBlocks(s, ::fixMathAdjacentLetterDigitSpacing)
        return s.trim()
    }

    /**
     * Models often glue "```" to prose or equations (e.g. "= 14```") so fences never parse; strip or
     * split so [MessageTextFormatter] can treat real ``` lines as code blocks.
     */
    private fun repairMalformedMarkdownFences(text: String): String {
        val nl = text.replace("\r\n", "\n")
        return nl.split('\n').joinToString("\n") { line ->
            val tr = line.trim()
            if (tr.matches(Regex("""^```[\w-]*\s*$"""))) {
                return@joinToString line
            }
            var u = line
            u = u.replace(Regex("""(?<!`)(?:```|`{4,})+\s*$"""), "")
            u = u.replace(Regex("""([^`\s])(?:```|`{4,})+"""), "$1\n")
            u
        }
    }

    fun stripInlineMarkdown(input: String): String {
        var s = input
        s = s.replace(Regex("""\*\*([^*]+)\*\*"""), "$1")
        s = s.replace(Regex("""\*([^*]+)\*"""), "$1")
        s = s.replace(Regex("""__([^_]+)__"""), "$1")
        s = s.replace(Regex("""_([^_]+)_"""), "$1")
        s = s.replace(Regex("""`([^`]+)`"""), "$1")
        // Stray fence markers leaked into prose (polisher should already have fixed most).
        s = s.replace(Regex("""`{3,}"""), "")
        s = s.replace("`", "")
        s = s.replace(Regex("""(?m)^#{1,6}\s*"""), "")
        return s.trim()
    }

    private fun decodeLiteralNewlineEscapes(s: String): String {
        var t = s
        while (t.contains("\\n")) {
            t = t.replace("\\n", "\n")
        }
        while (t.contains("\\t")) {
            t = t.replace("\\t", "\t")
        }
        return t
    }

    private fun latexLiteToPlain(s: String): String =
        Regex("""\$(.+?)\$""", RegexOption.DOT_MATCHES_ALL).replace(s) { m ->
            simplifyLatexChunk(m.groupValues[1])
        }

    private val digitToSubscript = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
        '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
        '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
    )
    private val digitToSuperscript = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾', 'n' to 'ⁿ',
    )

    private fun mapToSubscript(s: String): String =
        s.map { ch -> digitToSubscript[ch] ?: ch }.joinToString("")

    private fun mapToSuperscript(s: String): String =
        s.map { ch -> digitToSuperscript[ch] ?: ch }.joinToString("")

    private fun simplifyLatexChunk(inner: String): String {
        var t = inner
        t = t.replace(Regex("""\\frac\{([^}]*)\}\{([^}]*)\}"""), "($1)/($2)")
        t = t.replace(Regex("""\\rightarrow"""), " -> ")
        t = t.replace(Regex("""\\to\b"""), " -> ")
        t = t.replace(Regex("""\\text\{([^}]*)\}"""), "$1")
        t = t.replace(Regex("""\\mathrm\{([^}]*)\}"""), "$1")
        t = t.replace(Regex("""\\,"""), " ")
        // Subscripts / superscripts (native-readable, no WebView).
        t = Regex("""_\{([^}]+)\}""").replace(t) { m -> mapToSubscript(m.groupValues[1]) }
        t = Regex("""(?<=[A-Za-z0-9\)])(?:_([0-9]+)|_([+\-=()]+))""").replace(t) { m ->
            val innerDigits = m.groupValues[1].ifEmpty { m.groupValues[2] }
            if (innerDigits.isNotEmpty()) mapToSubscript(innerDigits) else m.value
        }
        t = Regex("""\^\{([^}]+)\}""").replace(t) { m -> mapToSuperscript(m.groupValues[1]) }
        t = Regex("""(?:^|(?<=[A-Za-z0-9\)]))\^([0-9]+)""").replace(t) { m -> mapToSuperscript(m.groupValues[1]) }
        t = t.replace(Regex("""\\"""), "")
        return t.trim()
    }

    private fun normalizeVisibleCharacters(input: String): String {
        var s = input
        s = s.replace("…", "...")
        s = s.replace("→", " -> ")
        s = s.replace("•", "- ")
        s = s.replace("“", "\"").replace("”", "\"")
        s = s.replace("‘", "'").replace("’", "'")
        s = s.replace("\r", "\n")
        s = s.replace("\\\"", "\"")
        s = s.replace("\\/", "/")
        s = s.replace(Regex("""\\([{}\[\]()$])"""), "$1")
        s = s.replace(Regex("""[\u0000-\u0008\u000B\u000C\u000E-\u001F]"""), "")
        return s
    }

    private fun normalizeMarkdownUnicode(s: String): String =
        s.replace('\uFF0A', '*')
            .replace('\u2217', '*')
            .replace("\uFEFF", "")

    private fun dedupeRepeatedParagraphs(text: String): String {
        val paras = text.split(Regex("\n\n+"))
        if (paras.size < 2) return text
        fun norm(p: String) = p.trim().lowercase().replace(Regex("\\s+"), " ")
        val out = mutableListOf<String>()
        var prevKey: String? = null
        for (p in paras) {
            val key = norm(p)
            if (key.isNotEmpty() && key == prevKey) continue
            out.add(p)
            prevKey = if (key.isNotEmpty()) key else null
        }
        return out.joinToString("\n\n")
    }

    private fun dedupeConsecutiveDuplicateLines(text: String): String {
        val lines = text.lines()
        val out = mutableListOf<String>()
        var prevNonBlank: String? = null
        for (line in lines) {
            val t = line.trim()
            if (t.isNotEmpty() && t == prevNonBlank) continue
            out.add(line)
            prevNonBlank = if (t.isNotEmpty()) t else prevNonBlank
        }
        return out.joinToString("\n")
    }

    /**
     * Plain-text models sometimes end a paragraph with `next: …` and repeat the same question on the
     * following line. Keep a single copy.
     */
    private fun collapseDuplicateNextMetaQuestion(text: String): String {
        val nl = text.replace("\r\n", "\n")
        val lines = nl.split('\n')
        val lastIdx = lines.indexOfLast { it.isNotBlank() }
        if (lastIdx < 1) return text
        val lastLine = lines[lastIdx].trim()
        if (lastLine.isEmpty()) return text
        val prefix = lines.subList(0, lastIdx).joinToString("\n").trimEnd()
        if (!prefix.contains("next:", ignoreCase = true)) return text
        val afterNext = prefix.substringAfterLast("next:", "").substringAfterLast("Next:", "").trim()
        if (afterNext.isEmpty()) return text

        fun norm(s: String) =
            s.lowercase()
                .replace(Regex("\\s+"), " ")
                .trim()
                .trimEnd('.', '?', '!', '…')

        if (norm(afterNext) != norm(lastLine)) return text

        val stripped = prefix.replace(
            Regex("""(?is)\s*next:\s*""" + Regex.escape(lastLine) + """\s*$"""),
            "",
        ).trimEnd()
        return if (stripped.isEmpty()) prefix else stripped
    }

    private fun normalizeWhitespaceForPlainText(input: String): String {
        var s = input.trim().replace("\r\n", "\n")
        s = s.replace('\t', ' ')
        s = s.replace(Regex("""[ ]{2,}"""), " ")
        s = s.replace(Regex(""" *\n *"""), "\n")
        s = s.replace(Regex("\n{4,}"), "\n\n\n")
        s = s.replace('\u00A0', ' ')
        // Fix common tokenizer glue where tiny words lose one space (e.g., "ora" -> "or a").
        s = Regex(
            """\b(or|in|to|for|of|as|on|at|by|is|are|was|were)(a|an)\b""",
            RegexOption.IGNORE_CASE
        ).replace(s) { m ->
            "${m.groupValues[1]} ${m.groupValues[2]}"
        }
        return s
    }

    /**
     * Each rule is one regex + spacing expansion. Order matters: longer / more specific matches first.
     * (We deliberately avoid a separate "literal dictionary" layer — same shapes, one pipeline.)
     */
    /** Lazy so class load does not compile a large batch of [Regex] in `<clinit>` (ART-sensitive). */
    private val tokenGlueRules: List<Pair<Regex, (MatchResult) -> CharSequence>> by lazy {
        listOf(
            Regex("""(?i)\b(let's)([a-z]{2,})\b""") to { m ->
                "${m.groupValues[1]} ${m.groupValues[2]}"
            },
            // Missing space before article "a" after a word (e.g. "fundamentallya representative", "usesa mixture").
            Regex(
                """(?i)\b([a-z]{3,}(?:ally|ologies|pathies|ences?|ances?|tions?|sions?|ments?|fully?|ously|ively|bles?|aries|ories|ities|ings?|isms?|ness|less|wards?|ships?|ies|ous|ive|ful|ble|ary|ory|ity|ism|ence|ance|ward|ship|tion|sion|ment|ics|ing|ion|ed|es|ly))a(\s+[a-z][a-z]*)""",
            ) to { m -> "${m.groupValues[1]} a${m.groupValues[2]}" },
            // willusea, willusean
            Regex("""(?i)\b(will)(use)(a|an)\b""") to { m ->
                "${m.groupValues[1]} ${m.groupValues[2]} ${m.groupValues[3]}"
            },
            // needtostart, goingtobe, … (never bare "goingto" — would split "together")
            Regex(
                """(?i)\b(need|want|try|learn|forget|remember|begin|continue|hope|plan|wish|refuse|stop|love|hate|""" +
                    """fail|going)(to)(start|end|make|do|get|go|be|have|begin|help|give|take|see|know|say|tell|""" +
                    """use|keep|show|play|come|turn|form|grow|work|stay|leave|find|ask|explain|release|absorb|""" +
                    """produce|contain|include|happen|change|become)\b""",
            ) to { m ->
                "${m.groupValues[1]} ${m.groupValues[2]} ${m.groupValues[3]}"
            },
            Regex(
                """(?i)\b(need|want|try|learn|forget|remember|begin|continue|hope|plan|wish|refuse|stop|love|hate|""" +
                    """fail)(to)\b""",
            ) to { m -> "${m.groupValues[1]} ${m.groupValues[2]}" },
            Regex(
                """(?i)\b(will|can)(be|do|make|have|get|use|not|see|help)\b""",
            ) to { m -> "${m.groupValues[1]} ${m.groupValues[2]}" },
            Regex("""(?i)\b(it)(is|was|will|has)\b""") to { m ->
                "${m.groupValues[1]} ${m.groupValues[2]}"
            },
            Regex("""(?i)\b(what|why|how|when|where|who)(is|was|are|will)\b""") to { m ->
                "${m.groupValues[1]} ${m.groupValues[2]}"
            },
            Regex("""(?i)\b(there)(is|are)\b""") to { m ->
                "${m.groupValues[1]} ${m.groupValues[2]}"
            },
            Regex("""(?i)\b(to)(start|end|make|do|get|go|be|have|begin|help|give|take|see|know)\b""") to { m ->
                "${m.groupValues[1]} ${m.groupValues[2]}"
            },
            // Merged subject token + predicate / infinitive marker (theyuse, plantsuse, plantsto, …)
            Regex(
                """(?i)\b(they|we|you|it|plants|cells|people|kids|students|animals|humans|trees|foods|leaves|""" +
                    """roots|seeds|birds|fish|words|numbers|books|hands|days|years|things|gases|molecules)""" +
                    """(use|make|need|want|get|grow|take|eat|see|go|do|are|is|was|were|have|has|had|will|can|could|""" +
                    """should|would|must|may|might|to)\b""",
            ) to { m -> "${m.groupValues[1]} ${m.groupValues[2]}" },
            Regex(
                """(?i)\b(like)(a|an|the|this|that|your|my|our|their|some|one|each|every)\b""",
            ) to { m -> "${m.groupValues[1]} ${m.groupValues[2]}" },
            Regex(
                """(?i)\b(the)(user|same|other|idea|way|time|sun|air|water|plant|plants|world|day|year|moon|""" +
                    """earth|food|gas|rest|reason|answer|question|problem|result|process|system|body|cell|""" +
                    """leaf|root|seed|tree|animal|child|person|place|thing|part|end|start|top|bottom|side)\b""",
            ) to { m -> "${m.groupValues[1]} ${m.groupValues[2]}" },
        )
    }

    /**
     * LiteRT / small models often drop a space between tokens ("Theyuse", "likea", "needtostart").
     * Heuristic insertions only — see [tokenGlueRules].
     */
    private fun fixTokenizedWordGlue(s: String): String {
        var t = s
        for ((pattern, expand) in tokenGlueRules) {
            t = pattern.replace(t, expand)
        }
        t = t.replace(Regex(""" {2,}"""), " ")
        return t
    }

    private val fenceDelimiterLine: Regex by lazy { Regex("""^```(?:\w+)?\s*$""") }

    /**
     * Runs [transform] only on text outside ``` … ``` fences so equation/code lines stay untouched.
     */
    private fun transformOutsideFencedCodeBlocks(text: String, transform: (String) -> String): String {
        val nl = text.replace("\r\n", "\n")
        val lines = nl.split('\n')
        val outside = StringBuilder()
        val result = StringBuilder()
        var inFence = false
        fun flushOutside() {
            if (outside.isEmpty()) return
            val chunk = outside.toString().removeSuffix("\n")
            outside.clear()
            if (chunk.isNotEmpty()) {
                result.append(transform(chunk))
            }
        }
        for (line in lines) {
            val t = line.trim()
            if (fenceDelimiterLine.matches(t)) {
                if (!inFence) {
                    flushOutside()
                    inFence = true
                    result.append(line).append('\n')
                } else {
                    result.append(line).append('\n')
                    inFence = false
                }
            } else {
                if (inFence) {
                    result.append(line).append('\n')
                } else {
                    outside.append(line).append('\n')
                }
            }
        }
        if (!inFence) {
            flushOutside()
        }
        return result.toString().trimEnd()
    }

    /**
     * LiteRT often glues digits to words ("10.Clue", "is2x", "equals10"). Heuristic spacing only.
     */
    private fun fixMathAdjacentLetterDigitSpacing(chunk: String): String {
        var s = chunk
        // "10.Clue" / "16.That" (not decimals like 3.14 — letter after dot must start a capitalized word).
        s = Regex("""(\d+\.)([A-Z][a-z]{1,})\b""").replace(s) { m ->
            "${m.groupValues[1]} ${m.groupValues[2]}"
        }
        // "So,2x" — comma/semicolon after a letter, then a digit.
        s = Regex("""(?<=[a-zA-Z])([,;])(\d)""").replace(s) { m ->
            "${m.groupValues[1]} ${m.groupValues[2]}"
        }
        // "x:10" / "y:6" — colon after a letter, then a digit (skip times like 12:30).
        s = Regex("""(?<=[a-zA-Z])(:)(\d)""").replace(s) { m ->
            "${m.groupValues[1]} ${m.groupValues[2]}"
        }
        s = Regex(
            """(?i)\b(is|are|was|were|then|if|when|so|plus|minus|equals|than|about)(\d+)([xyz])?([!?.,]*)\b""",
        ).replace(s) { m ->
            val tail = (m.groupValues[3] + m.groupValues[4])
            "${m.groupValues[1]} ${m.groupValues[2]}$tail"
        }
        s = s.replace(Regex(""" {2,}"""), " ")
        return s
    }
}
