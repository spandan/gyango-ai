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

    /**
     * Plain-text polish: same as [polishDisplayTextForMarkdown] but converts `$…$` math and LaTeX-lite
     * fragments to Unicode/plain so a non-TeX renderer stays readable.
     */
    fun polishDisplayText(input: String): String = polishDisplayInternal(input, convertLatexToPlain = true)

    /**
     * Polish for a full markdown + LaTeX renderer (e.g. Markwon + JLatexMath): skips TeX-to-plain so
     * `$…$`, `$$…$$`, and `\command{…}` survive for the renderer.
     */
    fun polishDisplayTextForMarkdown(input: String): String = polishDisplayInternal(input, convertLatexToPlain = false)

    private fun polishDisplayInternal(input: String, convertLatexToPlain: Boolean): String {
        var s = input
        s = normalizeMarkdownUnicode(s)
        s = decodeLiteralNewlineEscapes(s)
        s = stripPromptScaffoldEcho(s)
        s = transformOutsideFencedCodeBlocks(s, ::stripNudgingLabelPrefixes)
        s = transformOutsideFencedCodeBlocks(s, ::stripInlineExamQuestionBracketTags)
        // Models often emit `$ ext{...}` when `\text` loses its backslash; repair before `$...$` pairing.
        s = transformOutsideFencedCodeBlocks(s, ::repairMangledDollarExtFragments)
        // Same mangling appears without `$` (e.g. `ext { H } _2`); must run for markdown too — otherwise
        // Markwon shows literal "ext{}" in bubbles.
        s = transformOutsideFencedCodeBlocks(s, ::repairBareMangledTextFragments)
        // `$$…$` (missing final `$`) leaves a visible trailing `$` after JLatex; close the span before orphan stripping.
        s = transformOutsideFencedCodeBlocks(s, ::repairDoubleDollarSingleDollarClose)
        // Remove lone `$` stranded at line end (`...$`) while preserving balanced math/currency spans.
        s = transformOutsideFencedCodeBlocks(s, ::stripSingleTrailingOrphanDollarAtEol)
        s = transformOutsideFencedCodeBlocks(s, ::stripDanglingDollarDelimiters)
        // Adjacent `$a$$b$` (no space) parses as one blob after JLatex `$$` normalization; keep spans separate.
        s = transformOutsideFencedCodeBlocks(s, ::insertSpaceBetweenAdjacentInlineDollarMath)
        // Same for adjacent `$$…$$$$…$$` after normalization (polisher does not run again in the UI).
        s = transformOutsideFencedCodeBlocks(s, ::insertSpaceBetweenAdjacentDoubleDollarMath)
        if (convertLatexToPlain) {
            s = latexLiteToPlain(s)
            // Chemistry / LaTeX-lite outside $…$ (e.g. mangled `\text` → `ext{H}_2`) and stragglers after $ pass.
            s = transformOutsideFencedCodeBlocks(s, ::simplifyLatexChunk)
        }
        s = normalizeVisibleCharacters(s)
        s = dedupeRepeatedParagraphs(s)
        s = dedupeConsecutiveDuplicateLines(s)
        s = collapseDuplicateNextMetaQuestion(s)
        s = normalizeWhitespaceForPlainText(s)
        s = fixTokenizedWordGlue(s)
        s = repairMalformedMarkdownFences(s)
        s = transformOutsideFencedCodeBlocks(s, ::fixMathAdjacentLetterDigitSpacing)
        s = stripStandaloneScaffoldParagraphLabels(s)
        s = trimTrailingUnmatchedBacktick(s)
        return s.trim()
    }

    /**
     * Drops a single trailing `$` at end-of-line when it is not part of a balanced `...$...$` span.
     * Example: `8 + 5 = 13$` -> `8 + 5 = 13`
     */
    private fun stripSingleTrailingOrphanDollarAtEol(chunk: String): String {
        val lines = chunk.replace("\r\n", "\n").split('\n')
        return lines.joinToString("\n") { line ->
            val trimmedEnd = line.trimEnd()
            if (!trimmedEnd.endsWith('$')) return@joinToString line
            if (trimmedEnd.endsWith("$$")) return@joinToString line
            val idx = trimmedEnd.lastIndexOf('$')
            if (idx <= 0) return@joinToString line
            // If there is an unmatched non-currency `$` before the trailing one, keep as math delimiter.
            val prior = trimmedEnd.substring(0, idx)
            val nonCurrencyCountBefore = prior.indices.count { i ->
                prior[i] == '$' && (i == 0 || prior[i - 1] != '\\') && !isCurrencyDollarPrefix(prior, i)
            }
            if (nonCurrencyCountBefore % 2 == 1) {
                line
            } else {
                val prefix = trimmedEnd.dropLast(1).trimEnd()
                line.replaceRange(0, line.length, prefix)
            }
        }
    }

    /**
     * Removes recurring prompt-ish nudging labels that should not be visible in the learner UI.
     * Kept narrow to avoid altering genuine prose.
     */
    private fun stripNudgingLabelPrefixes(chunk: String): String {
        var t = chunk
        t = Regex(
            """(?im)^\s*(?:[-*]\s*)?(?:deeper\s*layer|deep(?:er)?\s*layer)\s*:\s*""",
            RegexOption.IGNORE_CASE,
        ).replace(t, "")
        t = Regex(
            """(?i)\b(?:deeper\s*layer|deep(?:er)?\s*layer)\s*:\s*""",
            RegexOption.IGNORE_CASE,
        ).replace(t, "")
        t = Regex("""\n{3,}""").replace(t, "\n\n")
        return t
    }

    /** Hide inline exam scaffolding tags from user-facing markdown (e.g. `[Question: 4]`). */
    private fun stripInlineExamQuestionBracketTags(chunk: String): String {
        var t = chunk
        t = Regex("""\[\s*Question:\s*(?:\d+|k)\s*]""", RegexOption.IGNORE_CASE).replace(t, "")
        // Keep section headers tight: avoid blank spacer lines immediately under headings.
        t = Regex("""(?m)^(###\s+[^\n]+)\n{2,}""").replace(t, "$1\n")
        t = Regex("""\n{3,}""").replace(t, "\n\n")
        return t
    }

    /**
     * Remove prompt-template scaffolding if the model accidentally echoes it into the visible lesson.
     */
    private fun stripPromptScaffoldEcho(text: String): String {
        val lines = text.replace("\r\n", "\n").split('\n')
        val out = lines.filterNot { line ->
            val t = line.trim()
            if (t.isEmpty()) return@filterNot false
            t.matches(Regex("""^(?:[*_`>#-]+\s*)*(?:#\s*)?(MISSION|SESSION|FORMAT\s*\(STRICT\)|TAIL(?:\s*\(.*\))?)\s*$""", RegexOption.IGNORE_CASE)) ||
                t.matches(Regex("""^\[(?:ACTIVATE|DOMAIN):[^\]]*]$""", RegexOption.IGNORE_CASE)) ||
                t.matches(Regex("""^TOPIC\s*:\s*(GENERAL|MATH|SCIENCE|CODING|WRITING|EXAM_PREP)\s*$""", RegexOption.IGNORE_CASE)) ||
                t.matches(Regex("""^TOPIC_LANE\s*:\s*(GENERAL|SCIENCE|MATH|CODING|WRITING|EXAM_PREP)\s*$""", RegexOption.IGNORE_CASE)) ||
                t.matches(Regex("""^AGE\s*:\s*\d+\s*\|\s*LANG\s*:.*$""", RegexOption.IGNORE_CASE)) ||
                t.matches(Regex("""^MODE\s*:\s*(GENERIC|CHALLENGE|SUPPORTIVE|EASY_PACE|DEEPER|BALANCED).*$""", RegexOption.IGNORE_CASE)) ||
                t.matches(Regex("""^PRIOR\s*:\s*.*$""", RegexOption.IGNORE_CASE)) ||
                t.matches(Regex("""^CURRENT_DIFF\s*:\s*\d+\s*$""", RegexOption.IGNORE_CASE)) ||
                t.matches(Regex("""^Safety:\s*.*Kids-Safe.*$""", RegexOption.IGNORE_CASE)) ||
                t.matches(Regex("""^After lesson prose,\s*output.*$""", RegexOption.IGNORE_CASE)) ||
                t.matches(Regex("""^Topic:\s*.+\|\s*Age:""", RegexOption.IGNORE_CASE)) ||
                t.matches(Regex("""^Reply in\s+""", RegexOption.IGNORE_CASE)) ||
                t.matches(Regex("""^Write the answer""", RegexOption.IGNORE_CASE)) ||
                t.matches(Regex("""^After the answer""", RegexOption.IGNORE_CASE)) ||
                t.matches(Regex("""^No other metadata""", RegexOption.IGNORE_CASE)) ||
                t.matches(Regex("""^CONTEXT\s*>>\s*<one line""", RegexOption.IGNORE_CASE)) ||
                t.matches(Regex("""^CURIOSITY\s*>>\s*<one line""", RegexOption.IGNORE_CASE)) ||
                t.matches(Regex("""^SPARKS\s*>>\s*<""", RegexOption.IGNORE_CASE))
        }
        return out.joinToString("\n").replace(Regex("""\n{3,}"""), "\n\n")
    }

    /**
     * Keep valid `$...$` / `$$...$$` spans but drop obvious orphan delimiters that become visible noise.
     */
    private fun stripDanglingDollarDelimiters(chunk: String): String {
        val chars = chunk.toCharArray()
        val unescapedDollarIdx = mutableListOf<Int>()
        for (i in chars.indices) {
            if (chars[i] != '$') continue
            if (i > 0 && chars[i - 1] == '\\') continue
            unescapedDollarIdx += i
        }
        if (unescapedDollarIdx.isEmpty()) return chunk
        // Protect currency-like `$` prefixes (e.g. `$5`, `$12.99`) so orphan cleanup never removes them.
        val candidateMathDelimiters = unescapedDollarIdx.filterNot { isCurrencyDollarPrefix(chunk, it) }
        // If odd count among math delimiters, trim the last stray one.
        if (candidateMathDelimiters.size % 2 == 1) {
            chars[candidateMathDelimiters.last()] = ' '
        }
        // Remove lines that are only stray delimiters/spaces.
        return String(chars)
            .replace(Regex("""(?m)^\s*\$+\s*$"""), "")
            .replace(Regex("""\n{3,}"""), "\n\n")
    }

    /**
     * True for currency-like forms such as `$5`, `$12.99`, `$1,200`.
     * This avoids stripping valid currency symbols while removing orphan math delimiters.
     */
    private fun isCurrencyDollarPrefix(text: String, dollarIdx: Int): Boolean {
        val prev = text.getOrNull(dollarIdx - 1)
        if (prev != null && (prev.isLetterOrDigit() || prev == '_')) return false
        val rest = text.substring(dollarIdx + 1)
        val amountMatch = Regex("""^([0-9]{1,3}(?:,[0-9]{3})*|[0-9]+)(?:\.[0-9]{1,2})?""").find(rest)
            ?: return false
        val boundary = rest.getOrNull(amountMatch.value.length)
        return boundary == null || boundary.isWhitespace() || boundary in ".,!?;:)]}"
    }

    /**
     * Replace `$ ext{X}_2`-style fragments (mangled `$\text{...}`) with Unicode/plain output even when
     * `$...$` pairing fails. Runs only outside fenced ``` blocks.
     */
    private fun repairMangledDollarExtFragments(chunk: String): String {
        var t = chunk
        var prev: String
        do {
            prev = t
            t = Regex("""\$\s*ext\s*\{([^}]+)\}((?:_\{[^}]+\}|_[0-9A-Za-z+\-=()]+)?)""").replace(t) { m ->
                simplifyLatexChunk("ext{${m.groupValues[1]}}" + m.groupValues[2])
            }
        } while (t != prev)
        return t
    }

    /**
     * Mangled `\text{…}` → `ext{…}` or `ext { … }` (LiteRT / tokenizers drop the backslash).
     * Negative lookbehind avoids matching the `text` inside words like `context{`.
     */
    private val bareMangledTextFragment =
        Regex("""(?<![A-Za-z\\])ext\s*\{([^}]*)\}((?:_\{[^}]+\}|_[0-9A-Za-z+\-=()]+)?)""")

    private fun repairBareMangledTextFragments(chunk: String): String {
        var t = chunk
        var prev: String
        do {
            prev = t
            t = bareMangledTextFragment.replace(t) { m ->
                simplifyLatexChunk("ext{${m.groupValues[1]}}" + m.groupValues[2])
            }
        } while (t != prev)
        return t
    }

    /** Removes lone lines "P1" / "P2" / "P3" (prompt scaffold echo). */
    private fun stripStandaloneScaffoldParagraphLabels(text: String): String {
        val lines = text.replace("\r\n", "\n").split('\n')
        val out = lines.filterNot { line -> line.trim().matches(Regex("""(?i)^P[123]$""")) }
        return out.joinToString("\n").replace(Regex("""\n{3,}"""), "\n\n")
    }

    /** Drop a single stray `` ` `` at EOF when backticks are unbalanced (common stream/model glitch). */
    private fun trimTrailingUnmatchedBacktick(s: String): String {
        var t = s.trimEnd()
        if (t.isEmpty() || t.last() != '`') return t
        val total = t.count { it == '`' }
        if (total % 2 == 1) {
            t = t.dropLast(1).trimEnd()
        }
        return t
    }

    /**
     * Models often glue "```" to prose or equations (e.g. "= 14```") so fences never parse; strip or
     * split so markdown renderers (or parsers) can treat real ``` lines as fenced code.
     */
    private fun repairMalformedMarkdownFences(text: String): String {
        val nl = text.replace("\r\n", "\n")
        return nl.split('\n').joinToString("\n") { line ->
            val tr = line.trim()
            if (tr.matches(Regex("""^```[\w-]*\s*$"""))) {
                return@joinToString line
            }
            var u = line
            // Sometimes thematic separators from prompts leak into the same line as fences (`***```text ...`).
            u = u.replace(Regex("""^\s*(?:\*{3,}\s*)+```([A-Za-z0-9._+-]+)\s*$"""), "```$1")
            u = u.replace(Regex("""^\s*(?:\*{3,}\s*)+```([A-Za-z0-9._+-]+)\s+(.+)$"""), "```$1\n$2")
            u = u.replace(Regex("""^\s*(?:\*{3,}\s*)+```\s*$"""), "```")
            u = u.replace(Regex("""^\s*(?:\*{3,}\s*)+```\s+(.+)$"""), "```\n$1")
            // Normalize inline opening fence with content (` ```text Step 1`) into a valid fenced block opener.
            u = u.replace(
                Regex("""^\s*```([A-Za-z0-9._+-]+)\s+(.+?)\s*```+\s*$"""),
                "```$1\n$2\n```",
            )
            u = u.replace(Regex("""^\s*```([A-Za-z0-9._+-]+)\s+(.+)$"""), "```$1\n$2")
            u = u.replace(Regex("""^\s*```\s+(.+?)\s*```+\s*$"""), "```\n$1\n```")
            u = u.replace(Regex("""^\s*```\s+(.+)$"""), "```\n$1")
            u = u.replace(Regex("""(?<!`)(?:```|`{4,})+\s*$"""), "")
            u = u.replace(Regex("""([^`\s])(?:```|`{4,})+"""), "$1\n")
            u
        }
    }

    /**
     * Converts markdown-tuned assistant bubble text to plain language for TTS or a lightweight
     * streaming preview (avoids reading `#`, `*`, `$…$`, etc. aloud).
     */
    fun plainTextForAssistantSpeech(displayMarkdown: String): String {
        val t = displayMarkdown.trim()
        if (t.isEmpty()) return ""
        val asPlain = polishDisplayText(t)
        return stripInlineMarkdown(asPlain).trim()
    }

    fun stripInlineMarkdown(input: String): String {
        var s = input
        // Markdown links and images: keep visible label / alt text only.
        s = s.replace(Regex("""!?\[([^\]]*)]\([^)]*\)"""), "$1")
        s = s.replace(Regex("""<https?://[^>\s]+>"""), "")
        s = s.replace(Regex("""\*\*([^*]+)\*\*"""), "$1")
        s = s.replace(Regex("""\*([^*]+)\*"""), "$1")
        s = s.replace(Regex("""__([^_]+)__"""), "$1")
        s = s.replace(Regex("""~~(.+?)~~"""), "$1")
        s = s.replace(Regex("""_([^_]+)_"""), "$1")
        s = s.replace(Regex("""`([^`]+)`"""), "$1")
        // Stray fence markers leaked into prose (polisher should already have fixed most).
        s = s.replace(Regex("""`{3,}"""), "")
        s = s.replace("`", "")
        s = s.replace(Regex("""(?m)^#{1,6}\s*"""), "")
        s = s.replace(Regex(""" {2,}"""), " ")
        return s.trim()
    }

    private fun decodeLiteralNewlineEscapes(s: String): String {
        var t = s
        while (t.contains("\\n")) {
            t = t.replace("\\n", "\n")
        }
        // Decode escaped tab only when it is not the start of a LaTeX command (e.g. \times, \text).
        while (Regex("""\\t(?![A-Za-z])""").containsMatchIn(t)) {
            t = Regex("""\\t(?![A-Za-z])""").replace(t, "\t")
        }
        return t
    }

    private fun latexLiteToPlain(s: String): String =
        transformOutsideFencedCodeBlocks(s) { chunk ->
            Regex("""\$(.+?)\$""", RegexOption.DOT_MATCHES_ALL).replace(chunk) { m ->
                simplifyLatexChunk(m.groupValues[1])
            }
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
        var t = inner.trimStart()
        // LiteRT / sanitizers often drop the backslash before `\text{…}`, leaving `ext{H}_2` or `ext { H }`.
        t = t.replace(Regex("""ext\s*\{([^}]*)\}""")) { it.groupValues[1] }
        t = t.replace(Regex("""\\frac\{([^}]*)\}\{([^}]*)\}"""), "($1)/($2)")
        t = t.replace(Regex("""\\rightarrow"""), " → ")
        t = t.replace(Regex("""\\to\b"""), " → ")
        t = t.replace(Regex("""\\implies\b"""), " ⇒ ")
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
        // Plain ASCII reaction arrows after TeX forms are normalized.
        t = t.replace("->", " → ")
        t = t.replace(Regex("""\\"""), "")
        return t.trim()
    }

    private fun normalizeVisibleCharacters(input: String): String {
        var s = input
        s = s.replace("…", "...")
        // Keep Unicode arrows (chemistry / math) readable; ASCII "->" is normalized earlier in LaTeX-lite.
        s = s.replace("•", "- ")
        s = s.replace("“", "\"").replace("”", "\"")
        s = s.replace("‘", "'").replace("’", "'")
        s = s.replace("\r", "\n")
        s = s.replace("\\\"", "\"")
        s = s.replace("\\/", "/")
        s = s.replace(Regex("""\\([{}\[\]()$])"""), "$1")
        s = s.replace(Regex("""[\u0000-\u0008\u000B\u000C\u000E-\u001F]"""), "")
        // Zero-width / bidi controls that leak from models or PDF paste. Omit U+200D (ZWJ) so emoji ZWJ sequences stay intact.
        s = s.replace(Regex("""[\u200B\u200C\u200E\u200F\u202A-\u202E\u2060-\u2064\u2066-\u2069]"""), "")
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
     * Insert a space between consecutive single-dollar inline spans on the same line so each
     * `$…$` stays a distinct region for Markwon/JLatexMath (`$a$$b$` → `$a$ $b$`). Chains need
     * repeated application. Skips spans whose opening `$` is escaped (`\$`).
     */
    private fun insertSpaceBetweenAdjacentInlineDollarMath(chunk: String): String {
        val pattern = Regex("""(?<![\\])(\$[^$\n]+\$)(?=(?<![\\])\$[^$\n]+\$)""")
        var s = chunk
        var prev: String
        do {
            prev = s
            s = pattern.replace(s) { "${it.groupValues[1]} " }
        } while (s != prev)
        return s
    }

    /**
     * Models often close with a single `$` after opening `$$`. JLatex then leaves a stray `$` in the bubble.
     * Only repair when the inner chunk looks like math and the `$` is not followed by a letter (avoids `$$a$b$`).
     */
    private fun repairDoubleDollarSingleDollarClose(chunk: String): String {
        val pattern = Regex("""(?<![\\])\$\$([^$\n]+)\$(?!\$)(?![A-Za-z])""")
        var s = chunk
        var prev: String
        do {
            prev = s
            s = pattern.replace(s) { m ->
                val inner = m.groupValues[1]
                if (doubleDollarInnerLooksLikeMathContent(inner)) {
                    val dd = "${'$'}${'$'}"
                    "$dd$inner$dd"
                } else {
                    m.value
                }
            }
        } while (s != prev)
        return s
    }

    private fun doubleDollarInnerLooksLikeMathContent(inner: String): Boolean {
        val t = inner.trim()
        if (t.isEmpty()) return false
        if (Regex("""[\\^_{}=+\-*/<>0-9]|\\frac|\\text|\\mathrm|\\sqrt|\\sum|\\int""").containsMatchIn(t)) {
            return true
        }
        if (!Regex("""^[A-Za-z]{1,3}$""").matches(t)) return false
        if (t.length == 3 && t.all { it.isUpperCase() }) return false
        if (t in doubleDollarShortEnglishBlocklist) return false
        return true
    }

    private val doubleDollarShortEnglishBlocklist = setOf(
        "it", "or", "an", "if", "is", "to", "of", "in", "on", "at", "as", "by", "be", "we", "he",
        "do", "no", "so", "up", "me", "my", "us", "am",
    )

    /**
     * Insert a space between consecutive `$$…$$` spans on the same line (`$$a$$$$b$$` → `$$a$$ $$b$$`).
     */
    private fun insertSpaceBetweenAdjacentDoubleDollarMath(chunk: String): String {
        val pattern = Regex("""(?<![\\])(\$\$[^$]+\$\$)(?=(?<![\\])\$\$[^$]+\$\$)""")
        var s = chunk
        var prev: String
        do {
            prev = s
            s = pattern.replace(s) { "${it.groupValues[1]} " }
        } while (s != prev)
        return s
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
