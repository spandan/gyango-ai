package ai.gyango.chatbot.ui

/**
 * English learning check-in questions by age band (shared fallback for [learningCheckInContentLanguage]
 * values that reuse English pools).
 */
object LearningCheckInEnglishPools {
    fun forBand(band: Int): List<LearningCheckInQuestion> = when (band) {
        0 -> listOf(
            LearningCheckInQuestion("When you learn something new, what feels best?", listOf("Show me a picture", "Tell me a short story", "Give me easy steps", "Let me try a tiny challenge")),
            LearningCheckInQuestion("If something feels tricky, what should GyanGo do?", listOf("Make it super simple", "Use a fun example", "Break it into steps", "Give me a puzzle to try")),
            LearningCheckInQuestion("Which topics sound exciting today?", listOf("Animals and nature", "Space and science", "Numbers and patterns", "Making and building")),
            LearningCheckInQuestion("How long should answers be?", listOf("Very short", "Short and fun", "A little more", "Give me extra detail")),
            LearningCheckInQuestion("What helps you understand fastest?", listOf("One idea at a time", "A real-life example", "A how-to list", "A challenge question")),
            LearningCheckInQuestion("When you ask why something happens, what do you want next?", listOf("A tiny answer", "A fun reason", "A step-by-step reason", "A big deeper reason")),
            LearningCheckInQuestion("Pick the style you like most.", listOf("Friendly and simple", "Curious and playful", "Clear and step-by-step", "Smart and challenging")),
            LearningCheckInQuestion("If you get stuck, what should I say?", listOf("It's okay, let's keep it easy", "Let's try a fun example", "Let's solve it step by step", "You're ready for a harder clue")),
        )
        1 -> listOf(
            LearningCheckInQuestion("When you ask a question, what kind of answer do you like first?", listOf("Short and simple", "A fun example", "Step by step", "A bigger challenge")),
            LearningCheckInQuestion("What helps you learn best?", listOf("Simple words", "Stories and examples", "Clear steps", "Tricky questions to solve")),
            LearningCheckInQuestion("If something is confusing, what should happen?", listOf("Make it easier", "Use a new example", "Slow it down into parts", "Give me a challenge hint")),
            LearningCheckInQuestion("Which sounds most like you?", listOf("I like easy explanations", "I like knowing why", "I like solving things", "I like big challenges")),
            LearningCheckInQuestion("How much detail should answers have?", listOf("Just the main point", "A little more", "Enough to practice", "Extra depth")),
            LearningCheckInQuestion("What topics pull you in most?", listOf("General wonder questions", "Science and nature", "Math and patterns", "Coding and making")),
            LearningCheckInQuestion("When GyanGo teaches, what should it do?", listOf("Keep it calm and short", "Use examples I know", "Guide me in steps", "Push me a little more")),
            LearningCheckInQuestion("When you finish an answer, what do you want next?", listOf("Stop there", "One fun fact", "One extra example", "One challenge question")),
        )
        2 -> listOf(
            LearningCheckInQuestion("What kind of explanation usually helps you most?", listOf("Very clear basics", "Examples that connect ideas", "Step-by-step reasoning", "Challenge me a bit more")),
            LearningCheckInQuestion("When you ask 'why', what do you really want?", listOf("A simple reason", "A real-life connection", "The logic in steps", "A deeper idea to think about")),
            LearningCheckInQuestion("How should GyanGo respond if you're stuck?", listOf("Make it easier right away", "Try a different example", "Break it down more carefully", "Give me a stronger hint")),
            LearningCheckInQuestion("How much challenge feels good?", listOf("Keep it easy", "Mostly comfortable", "A little stretch", "Push me further")),
            LearningCheckInQuestion("Which area sounds most fun right now?", listOf("Curiosity questions", "Science", "Math", "Coding / building")),
            LearningCheckInQuestion("How long should most answers be?", listOf("Quick", "Short with one example", "Medium with steps", "Longer with depth")),
            LearningCheckInQuestion("What style fits you best?", listOf("Gentle and simple", "Curious and thoughtful", "Structured and practical", "Stretch my thinking")),
            LearningCheckInQuestion("What should happen at the end of a good answer?", listOf("Wrap up simply", "Add one cool fact", "Give me a small next step", "Give me a challenge to try")),
        )
        else -> listOf(
            LearningCheckInQuestion("When learning something new, what helps most?", listOf("Simple overview", "Clear examples", "Reasoning in steps", "Deeper challenge")),
            LearningCheckInQuestion("What do you want GyanGo to optimize for?", listOf("Clarity", "Curiosity", "Problem solving", "Stretch thinking")),
            LearningCheckInQuestion("If a topic gets hard, what should happen?", listOf("Simplify it", "Use a relatable example", "Break down the logic", "Keep the challenge but guide me")),
            LearningCheckInQuestion("How much detail should most answers include?", listOf("Only essentials", "Essentials plus examples", "Enough for practice", "More depth and extension")),
            LearningCheckInQuestion("Which area feels most like you?", listOf("Explorer", "Thinker", "Builder", "Mix it up with more challenge")),
            LearningCheckInQuestion("Which topic lane do you reach for first?", listOf("General curiosity", "Science / nature", "Math / patterns", "Coding / creating")),
            LearningCheckInQuestion("What ending is most useful?", listOf("Short wrap-up", "One good example", "A next practice step", "A stretch question")),
            LearningCheckInQuestion("What's the best tone for GyanGo?", listOf("Calm and simple", "Curious and insightful", "Clear and practical", "Sharp and challenging")),
        )
    }
}
