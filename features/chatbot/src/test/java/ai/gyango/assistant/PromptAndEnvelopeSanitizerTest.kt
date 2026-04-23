package ai.gyango.assistant

import ai.gyango.api.PromptBuilder
import ai.gyango.core.ChatPreferenceMappings
import ai.gyango.core.SafetyProfile
import ai.gyango.core.SubjectMode
import ai.gyango.core.SubjectModeAutoRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptAndEnvelopeSanitizerTest {

    @Test
    fun sanitize_preservesLineStartDecimal() {
        assertEquals("3.14 is approximately pi.", AssistantLlmSanitizer.sanitize("3.14 is approximately pi."))
    }

    @Test
    fun sanitize_fixesGluedListMarkerLetterSuffix() {
        assertEquals("3. Word", AssistantLlmSanitizer.sanitize("3.Word"))
    }

    private fun sampleClosedTail(): String = """
        |---
        |CONTEXT >> Topic summary for next turn
        |CURIOSITY >> User asked about plants
        |SPARKS >> Easy?||Medium?||Hard?
        """.trimMargin()

    @Test
    fun streamingLessonPartial_returnsBodyWhenMetadataPresent() {
        val raw = "Short answer\n${sampleClosedTail()}"
        assertEquals("Short answer", GyangoOutputEnvelope.streamingLessonPartial(raw))
    }

    @Test
    fun streamingLessonPartial_clipsAtSparksWithoutSeparator() {
        val raw = "Intro\nSPARKS >> a||b||c"
        assertEquals("Intro", GyangoOutputEnvelope.streamingLessonPartial(raw))
    }

    @Test
    fun streamingLessonPartial_clipsAtLeakedTailHeader() {
        val raw = "Actual answer paragraph.\nTAIL (MANDATORY OUTPUT)\nCONTEXT >> x"
        assertEquals("Actual answer paragraph.", GyangoOutputEnvelope.streamingLessonPartial(raw))
    }

    @Test
    fun streamingLessonPartial_clipsAtLeakedTailHeaderWithScaffoldPrefix() {
        val raw = "Actual answer paragraph.\n*** # TAIL.. \nCONTEXT >> x"
        assertEquals("Actual answer paragraph.", GyangoOutputEnvelope.streamingLessonPartial(raw))
    }

    @Test
    fun envelopeParsing_acceptsLooseTailMarkers() {
        val raw = """
            Photosynthesis uses sunlight.
            ---
            CONTEXT: summary for next session
            SPARKS > What is chlorophyll? || Why green? || Deeper?
            CURIOSITY : Plant leaves
        """.trimIndent()
        assertTrue(GyangoOutputEnvelope.isLessonEnvelope(raw))
        assertEquals(
            "What is chlorophyll? || Why green? || Deeper?",
            GyangoOutputEnvelope.parseSparksSection(raw),
        )
        assertTrue(
            GyangoOutputEnvelope.parseHintInner(raw)
                ?.startsWith("summary for next session") == true,
        )
        assertEquals("Plant leaves", GyangoOutputEnvelope.parseInterestInner(raw))
    }

    @Test
    fun streamingLessonPartial_doesNotClipJsonWhenNoScaffoldLeakEvidence() {
        val raw = "{\n  \"STATE\": \"x\"}"
        assertEquals(null, GyangoOutputEnvelope.streamingLessonPartial(raw))
    }

    @Test
    fun streamingLessonPartial_clipsJsonWhenScaffoldLeakExists() {
        val raw = """
            SESSION
            {
              "STATE": "x"
            }
        """.trimIndent()
        assertEquals("", GyangoOutputEnvelope.streamingLessonPartial(raw))
    }

    @Test
    fun hasClosedMetadataTail_requiresNonBlankLines() {
        val incomplete = "Body\n---\nCONTEXT >>\nCURIOSITY >>\nSPARKS >>"
        assertFalse(GyangoOutputEnvelope.hasClosedMetadataTail(incomplete))

        val complete = "Body\n${sampleClosedTail()}"
        assertTrue(GyangoOutputEnvelope.hasClosedMetadataTail(complete))
    }

    @Test
    fun parseSparkChips_takesAtMostThree() {
        val line = "a||b||c||d"
        assertEquals(listOf("a", "b", "c"), GyangoOutputEnvelope.parseSparkChips(line))
    }

    @Test
    fun parseSparkChips_stripsEchoedDifficultyLabels() {
        val line = "Easy: First question?||Harder: Second?||Hardest: Third?"
        assertEquals(
            listOf("First question?", "Second?", "Third?"),
            GyangoOutputEnvelope.parseSparkChips(line),
        )
    }

    @Test
    fun parseSparkChips_stripsEasyFollowupCompoundAndAnglePlaceholders() {
        val line =
            "Easy-Followup: What is X?||<easy follow-up>||Followup: Why Y?"
        assertEquals(
            listOf("What is X?", "Why Y?"),
            GyangoOutputEnvelope.parseSparkChips(line),
        )
    }

    @Test
    fun autoRouter_routesSocialGreetingToGeneral() {
        assertEquals(SubjectMode.GENERAL, SubjectModeAutoRouter.route("How are you?"))
        assertEquals(SubjectMode.GENERAL, SubjectModeAutoRouter.route("Hello there"))
        assertEquals(SubjectMode.CURIOSITY, SubjectModeAutoRouter.route("Why is the sky blue?"))
    }

    @Test
    fun buildChatPrompt_mathRoleBrief() {
        val p = PromptBuilder.buildChatPrompt(
            lastUserContent = "Solve 2x=4",
            subjectMode = SubjectMode.MATH,
        )
        assertTrue(p.contains("clear math tutor"))
        assertTrue(p.contains("CONTEXT >>"))
        assertTrue(p.contains("CURIOSITY >>"))
        assertTrue(p.contains("SPARKS >>"))
        assertTrue(p.contains("M:"))
        assertTrue(p.contains("U: Solve 2x=4"))
    }

    @Test
    fun buildChatPrompt_chemistryUsesScienceBrief() {
        val p = PromptBuilder.buildChatPrompt(
            lastUserContent = "Balance H2 + O2",
            subjectMode = SubjectMode.CHEMISTRY,
        )
        assertTrue(p.contains("science tutor"))
        assertTrue(p.contains("Topic: CHEMISTRY"))
    }

    @Test
    fun buildChatPrompt_allSubjectModesIncludeMetadataTailInstructions() {
        SubjectMode.entries.forEach { mode ->
            val prompt = PromptBuilder.buildChatPrompt(
                lastUserContent = "test",
                subjectMode = mode,
            )
            assertTrue(prompt.contains("---"))
            assertTrue(prompt.contains("CONTEXT >>"))
            assertTrue(prompt.contains("SPARKS >>"))
            assertTrue(prompt.contains("CURIOSITY >>"))
            assertFalse(prompt.contains("TAIL_SCHEMA"))
            assertFalse(prompt.contains("STATE >>"))
        }
    }

    @Test
    fun buildChatPrompt_examPrepUsesExamCoachBrief() {
        val prompt = PromptBuilder.buildChatPrompt(
            lastUserContent = "How to prepare for geometry exam?",
            subjectMode = SubjectMode.EXAM_PREP,
        )
        assertTrue(prompt.contains("exam coach"))
        assertFalse(prompt.contains("### Plan"))
    }

    @Test
    fun parseForDisplay_reportsPartialWhenTailIncomplete() {
        val partialRaw = "Short lesson only"
        val parsed = AssistantParsingPipeline.parseForDisplay(partialRaw, SubjectMode.MATH)
        assertEquals(AssistantParseStatus.PARTIAL, parsed.status)
        assertFalse(parsed.tailComplete)
    }

    @Test
    fun buildChatPrompt_thoughtHintIncludedOnlyWhenEnabled() {
        val withHint = PromptBuilder.buildChatPrompt(
            lastUserContent = "Explain photosynthesis",
            requestThoughtHints = true,
        )
        val withoutHint = PromptBuilder.buildChatPrompt(
            lastUserContent = "Explain photosynthesis",
            requestThoughtHints = false,
        )
        assertTrue(withHint.contains("private reasoning"))
        assertFalse(withoutHint.contains("private reasoning"))
    }

    @Test
    fun buildChatPrompt_includesImageBlockOnlyWhenImageContextProvided() {
        val withImage = PromptBuilder.buildChatPrompt(
            lastUserContent = "What does this label say?",
            imageOcrContext = "ACME\nBatch 42\nExpiry 2027",
        )
        val withoutImage = PromptBuilder.buildChatPrompt(
            lastUserContent = "What does this label say?",
            imageOcrContext = null,
        )

        assertTrue(withImage.contains("IMAGE_OCR:"))
        assertTrue(withImage.contains("INSTRUCTIONS: Use IMAGE_OCR only as supporting context for U."))
        assertTrue(withImage.contains("Batch 42"))
        assertFalse(withoutImage.contains("IMAGE_OCR:"))
        assertFalse(withoutImage.contains("INSTRUCTIONS: Use IMAGE_OCR only as supporting context for U."))
    }

    @Test
    fun buildChatPrompt_defaultKidsStrictSafetyLine() {
        val p = PromptBuilder.buildChatPrompt(lastUserContent = "How are you?")
        assertTrue(p.contains("Safety: kids-strict"))
    }

    @Test
    fun buildChatPrompt_supportsExpandedIndianLocaleNames() {
        val cases = listOf(
            "mr-IN" to "Marathi",
            "ta-IN" to "Tamil",
            "bn-IN" to "Bengali",
            "gu-IN" to "Gujarati",
            "kn-IN" to "Kannada",
            "ml-IN" to "Malayalam",
            "pa-IN" to "Punjabi",
        )
        cases.forEach { (tag, expectedLanguage) ->
            val p = PromptBuilder.buildChatPrompt(
                lastUserContent = "Explain this topic",
                preferredReplyLocaleTag = tag,
            )
            assertTrue(p.contains("Reply in $expectedLanguage unless the user asks otherwise."))
        }
    }

    @Test
    fun buildChatPrompt_usesExplicitSystemAndUserTurns() {
        val p = PromptBuilder.buildChatPrompt(lastUserContent = "Hello there")
        assertTrue(p.contains("<|turn>system"))
        assertTrue(p.contains("<|turn>user"))
        assertTrue(p.contains("<|turn>assistant"))
        assertTrue(p.contains("<turn|>"))
        assertFalse(p.contains("<start_of_turn>user"))
        assertFalse(p.contains("<start_of_turn>model"))
    }

    @Test
    fun chatPreferenceMappings_shortAndLongCapsDiffer() {
        assertTrue(
            ChatPreferenceMappings.MAX_TOKENS_SHORT_ANSWERS <
                ChatPreferenceMappings.MAX_TOKENS_LONG_ANSWERS,
        )
    }

    @Test
    fun moderateOutput_blocksSensitiveInfoForKids() {
        val output = "My email is kid@example.com and OTP is 483921."
        val result = KidsSafetyPolicy.moderateOutput(output, SafetyProfile.KIDS_STRICT)
        assertEquals(SafetyDecision.BLOCK, result.decision)
        assertTrue(result.safeReply?.contains("sensitive personal details") == true)
    }
}
