package ai.gyango.assistant

/**
 * Splits the lesson body (after `---OUTPUT---`, before `STATE >>` / envelope markers) into display sections.
 *
 * When **Definition:**, **Analogy:**, and **Application:** headers are all found (any order), each
 * section is captured by kind for UI (light semantic backgrounds in chat when all three exist).
 *
 * Otherwise the lesson is split on blank-line runs into up to three paragraphs without semantic tinting;
 * a paragraph that explicitly opens with an **Analogy:** header may use a light analogy tint only.
 */
object AssistantLessonStructureParser {
    private val defaultSectionPatterns = listOf(
        AssistantLessonSectionExtractor.SectionPattern(
            key = "definition",
            caption = "Definition",
            headerRegex = Regex("""(?mi)(^\*\*Definition:\*\*\s*|^\*\*Definition\*\*:\s*|^\*\*Def:\*\*\s*|^Definition:\s*)"""),
        ),
        AssistantLessonSectionExtractor.SectionPattern(
            key = "analogy",
            caption = "Analogy",
            headerRegex = Regex("""(?mi)(^\*\*Analogy:\*\*\s*|^\*\*Analogy\*\*:\s*|^\*\*Analogy\*\*\s*:\s*|^Analogy:\s*)"""),
        ),
        AssistantLessonSectionExtractor.SectionPattern(
            key = "application",
            caption = "Application",
            headerRegex = Regex("""(?mi)(^\*\*Application:\*\*\s*|^\*\*Application\*\*:\s*|^\*\*App:\*\*\s*|^Application:\s*)"""),
        ),
    )

    /**
     * When the model emits all three labeled sections (any order, line-start headers), returns tinted
     * section presentation; otherwise null (caller keeps a single plain bubble).
     */
    fun explicitLabeledTripleOrNull(lessonSanitized: String): AssistantLessonBubblePresentation? =
        tryExplicitTriple(lessonSanitized.trim().replace("\r\n", "\n"))

    fun fromSanitizedLesson(lessonSanitized: String): AssistantLessonBubblePresentation {
        val text = lessonSanitized.trim().replace("\r\n", "\n")
        if (text.isBlank()) {
            return AssistantLessonBubblePresentation(
                explicitLabeledTriple = false,
                sections = emptyList(),
                applyFallbackTintSurfaces = false,
            )
        }
        tryExplicitTriple(text)?.let { return it }
        return fallbackParagraphs(text)
    }

    private fun tryExplicitTriple(text: String): AssistantLessonBubblePresentation? {
        val extractedSections = AssistantLessonSectionExtractor.extractAllOrNull(
            text = text,
            patterns = defaultSectionPatterns,
        ) ?: return null

        fun polishSlice(s: String): String =
            AssistantTextPolisher.polishDisplayText(stripLeadingSectionLabelEcho(s))

        val sectionsByKey = extractedSections.associateBy { it.key }
        val definitionBody = sectionsByKey["definition"]?.body ?: return null
        val analogyBody = sectionsByKey["analogy"]?.body ?: return null
        val applicationBody = sectionsByKey["application"]?.body ?: return null

        return AssistantLessonBubblePresentation(
            explicitLabeledTriple = true,
            applyFallbackTintSurfaces = false,
            sections = listOf(
                AssistantLessonBubbleSection(
                    polishedBody = polishSlice(definitionBody),
                    caption = sectionsByKey["definition"]?.caption,
                    useAnalogyCallout = false,
                    fallbackTintIndex = 0,
                ),
                AssistantLessonBubbleSection(
                    polishedBody = polishSlice(analogyBody),
                    caption = sectionsByKey["analogy"]?.caption,
                    useAnalogyCallout = false,
                    fallbackTintIndex = 1,
                ),
                AssistantLessonBubbleSection(
                    polishedBody = polishSlice(applicationBody),
                    caption = sectionsByKey["application"]?.caption,
                    useAnalogyCallout = false,
                    fallbackTintIndex = 2,
                ),
            ),
        )
    }

    private fun fallbackParagraphs(text: String): AssistantLessonBubblePresentation {
        val parts = text.split(Regex("""\n\s*\n+"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(3)
        if (parts.isEmpty()) {
            return AssistantLessonBubblePresentation(
                explicitLabeledTriple = false,
                sections = emptyList(),
                applyFallbackTintSurfaces = false,
            )
        }
        val sections = parts.mapIndexed { index, part ->
            val analogyCallout = paragraphLeadsWithExplicitAnalogyHeader(part)
            val stripped = stripLeadingSectionLabelEcho(part)
            val polished = AssistantTextPolisher.polishDisplayText(stripped)
            AssistantLessonBubbleSection(
                polishedBody = polished,
                caption = null,
                useAnalogyCallout = analogyCallout,
                fallbackTintIndex = index.coerceIn(0, 2),
            )
        }
        return AssistantLessonBubblePresentation(
            explicitLabeledTriple = false,
            applyFallbackTintSurfaces = false,
            sections = sections,
        )
    }

    private fun paragraphLeadsWithExplicitAnalogyHeader(text: String): Boolean {
        val firstLine = text.trim().lineSequence().firstOrNull()?.trim() ?: return false
        if (firstLine.startsWith("**Analogy:**", ignoreCase = true)) return true
        if (firstLine.startsWith("**Analogy**:", ignoreCase = true)) return true
        if (Regex("""^\*\*Analogy\*\*:\s*""", RegexOption.IGNORE_CASE).containsMatchIn(firstLine)) return true
        if (firstLine.startsWith("Analogy:", ignoreCase = true)) return true
        return false
    }

    /** Same idea as chat UI: strip echoed section labels and leading blockquote markers. */
    private fun stripLeadingSectionLabelEcho(section: String): String {
        var s = section.trim().lines().joinToString("\n") { line ->
            line.replaceFirst(Regex("""^\s*>\s?"""), "")
        }.trim()
        val leadingEcho = listOf(
            Regex("""(?is)^\*\*Definition:\*\*\s*"""),
            Regex("""(?is)^\*\*Definition\*\*:\s*"""),
            Regex("""(?is)^\*\*Def:\*\*\s*"""),
            Regex("""(?is)^\*\*Analogy:\*\*\s*"""),
            Regex("""(?is)^\*\*Analogy\*\*:\s*"""),
            Regex("""(?is)^\*\*Analogy\*\*\s*:\s*"""),
            Regex("""(?is)^\*\*Application:\*\*\s*"""),
            Regex("""(?is)^\*\*Application\*\*:\s*"""),
            Regex("""(?is)^\*\*App:\*\*\s*"""),
            Regex("""(?mi)^Definition:\s*"""),
            Regex("""(?mi)^Analogy:\s*"""),
            Regex("""(?mi)^Application:\s*"""),
        )
        for (re in leadingEcho) {
            val next = s.replaceFirst(re, "")
            if (next != s) {
                s = next.trim()
                break
            }
        }
        return s.trim()
    }
}

data class AssistantLessonBubbleSection(
    val polishedBody: String,
    val caption: String?,
    val useAnalogyCallout: Boolean,
    val fallbackTintIndex: Int,
)

data class AssistantLessonBubblePresentation(
    val explicitLabeledTriple: Boolean,
    val sections: List<AssistantLessonBubbleSection>,
    /** When true (OUTPUT lesson without all three headers), paragraphs use tinted surfaces. */
    val applyFallbackTintSurfaces: Boolean = false,
) {
    fun joinedForPlainDisplay(): String =
        sections.joinToString("\n\n") { it.polishedBody }
}
