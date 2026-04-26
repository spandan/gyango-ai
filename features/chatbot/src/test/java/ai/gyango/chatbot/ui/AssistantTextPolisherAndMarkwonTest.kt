package ai.gyango.chatbot.ui

import ai.gyango.assistant.AssistantOutput
import ai.gyango.assistant.AssistantTextPolisher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantTextPolisherAndMarkwonTest {

    @Test
    fun polisher_stripsZeroWidthSpaceAndBidiMarks() {
        val withZwsp = "word\u200Bnext"
        assertEquals("wordnext", AssistantTextPolisher.polishDisplayText(withZwsp))

        val withBidi = "x\u202Ey"
        assertEquals("xy", AssistantTextPolisher.polishDisplayText(withBidi))
    }

    @Test
    fun polisher_preservesEmojiZwjoiner() {
        val emoji = "\uD83D\uDC68\u200D\uD83E\uDDBB" // man technologist
        assertEquals(emoji, AssistantTextPolisher.polishDisplayText(emoji))
    }

    @Test
    fun polishForMarkdown_preservesSingleDollarTex() {
        val raw = "Energy \$E = mc^2\$ here."
        val plain = AssistantTextPolisher.polishDisplayText(raw)
        assertTrue("plain polish should flatten TeX", !plain.contains('$'))

        val md = AssistantTextPolisher.polishDisplayTextForMarkdown(raw)
        assertTrue(md.contains('$'))
    }

    @Test
    fun polishForMarkdown_preservesLatexCommandsStartingWithT() {
        val raw = "Equation: \$4\\\\times(8+5)\$ and label \$\\\\text{x}\$."
        val out = AssistantTextPolisher.polishDisplayTextForMarkdown(raw)
        assertTrue(out.contains("\\times"))
        assertTrue(out.contains("\\text{x}"))
        assertTrue(!out.contains("\times"))
    }

    @Test
    fun stripInlineMarkdown_removesMarkdownLinks() {
        val s = AssistantTextPolisher.stripInlineMarkdown("See [label](https://x.com) here.")
        assertEquals("See label here.", s)
    }

    @Test
    fun plainTextForAssistantSpeech_stripsMarkdownForTts() {
        val md = "## Title\n**Bold** and `code`."
        val plain = AssistantTextPolisher.plainTextForAssistantSpeech(md)
        assertTrue(!plain.contains("#"))
        assertTrue(!plain.contains("*"))
        assertTrue(!plain.contains("`"))
        assertTrue(plain.contains("Title"))
        assertTrue(plain.contains("Bold"))
    }

    @Test
    fun stripInlineMarkdown_removesImageLinks() {
        val s = AssistantTextPolisher.stripInlineMarkdown("![alt text](https://x.com/a.png)")
        assertEquals("alt text", s)
    }

    @Test
    fun stripInlineMarkdown_removesAutolinkAngle() {
        val s = AssistantTextPolisher.stripInlineMarkdown("Visit <https://example.com/path> today.")
        assertEquals("Visit today.", s)
    }

    @Test
    fun normalizeSingleDollarLatex_wrapsTexyContent() {
        val s = normalizeSingleDollarLatexForJlMath("Formula $\\frac{1}{2}$ end.")
        assertTrue(s.contains("$$"))
        assertTrue(s.contains("\\frac{1}{2}"))
    }

    @Test
    fun normalizeSingleDollarLatex_wrapsSimpleMathInline() {
        val s = normalizeSingleDollarLatexForJlMath("Compute \$x+1\$ now.")
        assertTrue(s.contains("\$\$x+1\$\$"))
    }

    @Test
    fun normalizeSingleDollarLatex_leavesCurrencyAlone() {
        val s = normalizeSingleDollarLatexForJlMath("Price is \$5 today.")
        assertEquals("Price is \$5 today.", s)
    }

    @Test
    fun normalizeSingleDollarLatex_wrapsBareSingleLetterVariable() {
        val s = normalizeSingleDollarLatexForJlMath("Let \$x\$ be positive.")
        assertTrue(s.contains("\$\$x\$\$"))
    }

    @Test
    fun normalizeSingleDollarLatex_wrapsShortAlphabeticVariable() {
        val s = normalizeSingleDollarLatexForJlMath("Solve for \$xy\$ given data.")
        assertTrue(s.contains("\$\$xy\$\$"))
    }

    @Test
    fun normalizeSingleDollarLatex_doesNotWrapThreeLetterAllCaps() {
        val s = normalizeSingleDollarLatexForJlMath("Price in \$USD\$ today.")
        assertEquals("Price in \$USD\$ today.", s)
    }

    @Test
    fun normalizeSingleDollarLatex_doesNotWrapCommonEnglishInDollars() {
        val s = normalizeSingleDollarLatexForJlMath("If \$it\$ fails, retry.")
        assertEquals("If \$it\$ fails, retry.", s)
    }

    @Test
    fun polishForMarkdown_repairsDoubleDollarClosedWithSingleDollar() {
        val raw = "\$\$x+y=10 x-y=6\$"
        val out = AssistantTextPolisher.polishDisplayTextForMarkdown(raw)
        assertEquals("\$\$x+y=10 x-y=6\$\$", out.trim())
    }

    @Test
    fun polishForMarkdown_insertsSpaceBetweenAdjacentDoubleDollarSpans() {
        val raw = "\$\$a\$\$\$\$b\$\$"
        val out = AssistantTextPolisher.polishDisplayTextForMarkdown(raw)
        assertTrue(out.contains("\$\$a\$\$ \$\$b\$\$"))
    }

    @Test
    fun polishForMarkdown_doesNotRepairDoubleDollarSingleInsideFencedCode() {
        val raw = "```\n\$\$x+1\$\n```"
        val out = AssistantTextPolisher.polishDisplayTextForMarkdown(raw)
        assertEquals(raw.trim(), out.trim())
    }

    @Test
    fun polishForMarkdown_insertsSpaceBetweenAdjacentInlineMath() {
        val raw = "We have \$x+y=10\$\$x-y=6\$."
        val out = AssistantTextPolisher.polishDisplayTextForMarkdown(raw)
        assertTrue(out.contains("\$x+y=10\$ \$x-y=6\$"))
    }

    @Test
    fun polishForMarkdown_insertsSpaceBetweenThreeAdjacentInlineMathSpans() {
        val raw = "\$a\$\$b\$\$c\$"
        val out = AssistantTextPolisher.polishDisplayTextForMarkdown(raw)
        assertEquals("\$a\$ \$b\$ \$c\$", out)
    }

    @Test
    fun polishForMarkdown_doesNotInsertSpaceInsideFencedCodeForAdjacentDollars() {
        val raw = "```\n\$x+y=10\$\$x-y=6\$\n```"
        val out = AssistantTextPolisher.polishDisplayTextForMarkdown(raw)
        assertTrue(out.contains("\$x+y=10\$\$x-y=6\$"))
        assertTrue(!out.contains("\$x+y=10\$ \$x-y=6\$"))
    }

    @Test
    fun normalizeSingleDollarLatex_splitsAfterPolisherAdjacentPair() {
        val polished = AssistantTextPolisher.polishDisplayTextForMarkdown("\$x+1\$\$y+2\$")
        val jl = normalizeSingleDollarLatexForJlMath(polished)
        assertTrue(jl.contains("\$\$x+1\$\$"))
        assertTrue(jl.contains("\$\$y+2\$\$"))
    }

    @Test
    fun polishForMarkdown_normalizesInlineFenceOpener() {
        val raw = "```text Step 1: isolate x\nStep 2: divide by 2\n```"
        val out = AssistantTextPolisher.polishDisplayTextForMarkdown(raw)
        assertTrue(!out.contains("```text Step 1: isolate x"))
        assertTrue(out.contains("Step 1: isolate x"))
    }

    @Test
    fun polishForMarkdown_normalizesThematicPrefixBeforeFence() {
        val raw = "***```text Step 1: isolate x\nStep 2: divide by 2\n```"
        val out = AssistantTextPolisher.polishDisplayTextForMarkdown(raw)
        assertTrue(!out.contains("***```text"))
        assertTrue(out.contains("```text"))
    }

    @Test
    fun polishForMarkdown_stripsPromptScaffoldEchoLines() {
        val raw = """
            [ACTIVATE: MATHEMATICAL_REASONING_ENGINE]
            SESSION
            TOPIC: MATH
            This is the real answer body.
            # TAIL
            STATE >> Goal:[MATH]
        """.trimIndent()
        val out = AssistantTextPolisher.polishDisplayTextForMarkdown(raw)
        assertTrue(!out.contains("[ACTIVATE:"))
        assertTrue(!out.contains("SESSION"))
        assertTrue(!out.contains("TOPIC: MATH"))
        assertTrue(!out.contains("# TAIL"))
        assertTrue(out.contains("This is the real answer body."))
    }

    @Test
    fun polishForMarkdown_dropsDanglingDollarNoise() {
        val raw = "Compute this: \$x + 2\$ then stray delimiter \$"
        val out = AssistantTextPolisher.polishDisplayTextForMarkdown(raw)
        assertTrue(out.contains("\$x + 2\$"))
        assertTrue(!out.endsWith("delimiter \$"))
    }

    @Test
    fun polishForMarkdown_keepsCurrencyDollarWhenMathDelimitersExist() {
        val raw = "Price is \$5 today; solve \$x + 1\$ now."
        val out = AssistantTextPolisher.polishDisplayTextForMarkdown(raw)
        assertTrue(out.contains("\$5"))
        assertTrue(out.contains("\$x + 1\$"))
    }

    @Test
    fun polishForMarkdown_removesTrailingOrphanDollarAfterEquation() {
        val raw = "8 + 5 = 13\$"
        val out = AssistantTextPolisher.polishDisplayTextForMarkdown(raw)
        assertEquals("8 + 5 = 13", out)
    }

    @Test
    fun polishForMarkdown_stripsBareMangledExtTextNotOnlyAfterDollar() {
        val raw = "Formula ext { H } _2 and gas ext{N}_2 (mangled \\\\text)."
        val out = AssistantTextPolisher.polishDisplayTextForMarkdown(raw)
        assertTrue("should not show literal ext{…}", !out.contains("ext{"))
        assertTrue("should not show spaced ext { … }", !Regex("""\bext\s*\{""").containsMatchIn(out))
        assertTrue(out.contains("N")) // subscript path may emit Unicode; just ensure not raw ext brace
    }

    @Test
    fun polishForMarkdown_stripsMangledExtAfterDollarWithSpaces() {
        val raw = "Oxygen is \$ ext { O } _2\$ in formulas."
        val out = AssistantTextPolisher.polishDisplayTextForMarkdown(raw)
        assertTrue(!out.contains("ext{"))
        assertTrue(!Regex("""\bext\s*\{""").containsMatchIn(out))
    }

    @Test
    fun polishForMarkdown_doesNotStripContextWord() {
        val raw = "Read the context{note} carefully — unrelated braces."
        val out = AssistantTextPolisher.polishDisplayTextForMarkdown(raw)
        assertTrue(out.contains("context{note}"))
    }

    @Test
    fun polishForMarkdown_stripsInlineExamQuestionTagFromVisibleBody() {
        val raw = "### Next Question\n[Question: 8]\nWhat is 12 x 3?"
        val out = AssistantTextPolisher.polishDisplayTextForMarkdown(raw)
        assertTrue(!out.contains("[Question: 8]"))
        assertTrue(out.contains("What is 12 x 3?"))
    }

    @Test
    fun polishForMarkdown_keepsNoExtraBlankLineAfterHeading() {
        val raw = "### Feedback\n\nNice try.\n\n### Next Question\n\nWhat is 2+2?"
        val out = AssistantTextPolisher.polishDisplayTextForMarkdown(raw)
        assertTrue(out.contains("### Feedback\nNice try."))
        assertTrue(out.contains("### Next Question\nWhat is 2+2?"))
    }

    @Test
    fun polishForMarkdown_stripsDeeperLayerLabelEverywhere() {
        val raw = "Deeper Layer: Try this angle.\n\n- Deeper Layer: Why does this happen?"
        val out = AssistantTextPolisher.polishDisplayTextForMarkdown(raw)
        assertTrue(!out.contains("Deeper Layer:", ignoreCase = true))
        assertTrue(out.contains("Try this angle."))
        assertTrue(out.contains("Why does this happen?"))
    }

    @Test
    fun polishForMarkdown_removesSingleTrailingOrphanDollarBeforeNewline() {
        val raw = "Compute total first$\nThen continue."
        val out = AssistantTextPolisher.polishDisplayTextForMarkdown(raw)
        assertTrue(!out.contains("first$"))
        assertTrue(out.contains("Compute total first\nThen continue."))
    }

    @Test
    fun polishForMarkdown_keepsBalancedInlineMathSpan() {
        val raw = "Equation is \$x+1\$ for this step."
        val out = AssistantTextPolisher.polishDisplayTextForMarkdown(raw)
        assertTrue(out.contains("\$x+1\$"))
    }

    @Test
    fun polishForMarkdown_keepsBalancedDoubleDollarSpan() {
        val raw = "\$\$x+y=10\$\$"
        val out = AssistantTextPolisher.polishDisplayTextForMarkdown(raw)
        assertTrue(out.contains("\$\$x+y=10\$\$"))
    }

    @Test
    fun polishForMarkdown_keepsCurrencyAtLineEnd() {
        val raw = "The fee is $12.99"
        val out = AssistantTextPolisher.polishDisplayTextForMarkdown(raw)
        assertEquals(raw, out)
    }

    @Test
    fun polishForMarkdown_doesNotModifyTrailingDollarInsideFencedCode() {
        val raw = "```\nvalue$ \n```"
        val out = AssistantTextPolisher.polishDisplayTextForMarkdown(raw)
        assertTrue(out.contains("```"))
        assertTrue(out.contains("value$"))
    }

    @Test
    fun lessonPresentation_preservesBlockquoteMarkdown() {
        val raw = """
            > Use this quote as context.
            ---
            CONTEXT >> Quote explained for next turn
            CURIOSITY >> English: quote writing
            SPARKS >> Why this quote matters?||How would you rephrase it?||Rewrite in your own words?
        """.trimIndent()
        val lesson = AssistantOutput.lessonBubblePresentation(raw).joinedForPlainDisplay()
        assertTrue(lesson.startsWith("> Use this quote as context."))
    }
}
