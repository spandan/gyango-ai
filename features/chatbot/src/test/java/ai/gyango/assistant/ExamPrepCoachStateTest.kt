package ai.gyango.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamPrepCoachStateTest {

    @Test
    fun parseQuestionLevelStatus_fromContextLine() {
        val line = "[Question: 3] [Level: 2.5] [Status: Incorrect]"
        assertEquals(3, ExamPrepCoachState.parseQuestionNumber(line))
        assertEquals(2.5, ExamPrepCoachState.parseLevel(line) ?: 0.0, 0.0)
        assertEquals("Incorrect", ExamPrepCoachState.parseStatus(line))
    }

    @Test
    fun parseQuestion_handlesBootstrapMemory() {
        assertEquals(0, ExamPrepCoachState.parseQuestionNumber(ExamPrepCoachState.BOOTSTRAP_MEMORY))
        assertEquals(1.0, ExamPrepCoachState.parseLevel(ExamPrepCoachState.BOOTSTRAP_MEMORY) ?: 0.0, 0.0)
        assertEquals("Start", ExamPrepCoachState.parseStatus(ExamPrepCoachState.BOOTSTRAP_MEMORY))
    }

    @Test
    fun splitDisplayAtNextQuestion_splitsOnHeading() {
        val md = "### Feedback\n\nYou were correct.\n\n### Next Question\n\nWhat is 2+2?"
        val (review, next) = ExamPrepCoachState.splitDisplayAtNextQuestion(md)
        assertEquals("### Feedback\nYou were correct.", review)
        assertEquals("What is 2+2?", next)
    }

    @Test
    fun splitDisplayAtNextQuestion_returnsNullSecondWhenMissing() {
        val md = "### Feedback\n\nOnly feedback here."
        val (review, next) = ExamPrepCoachState.splitDisplayAtNextQuestion(md)
        assertEquals(md, review)
        assertNull(next)
    }

    @Test
    fun extractLatestNextQuestion_stripsQuestionBracketLabel() {
        val md = "### Feedback\nGreat effort.\n\n### Next Question\n[Question: 4]\nWhat is 5 + 7?"
        val next = ExamPrepCoachState.extractLatestNextQuestion(md)
        assertEquals("What is 5 + 7?", next)
    }

    @Test
    fun coerceQuestionProgression_bumpsWhenQuestionDoesNotAdvance() {
        val prior = "[Question: 4] [Level: 2.5] [Status: Correct]"
        val current = "[Question: 4] [Level: 3.0] [Status: Correct]"
        val coerced = ExamPrepCoachState.coerceQuestionProgression(current, prior)
        assertTrue(coerced?.contains("[Question: 5]") == true)
    }
}
