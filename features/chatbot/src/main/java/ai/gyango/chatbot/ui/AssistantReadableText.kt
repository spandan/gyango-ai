package ai.gyango.chatbot.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.gyango.assistant.AssistantOutput
import ai.gyango.assistant.AssistantTextPolisher
import ai.gyango.assistant.GyangoOutputEnvelope
private val BlockSpacing = 4.dp

/** Italic “thinking” line while streaming before section bodies appear. */
private val LessonIntroStyle: TextStyle
    @Composable get() = MaterialTheme.typography.bodyLarge.copy(
        fontSize = 18.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.15.sp,
    )

/** Body typography matched to [AssistantMarkdownTextView] / Markwon. */
private val LessonBodyStyle: TextStyle
    @Composable get() = MaterialTheme.typography.bodyMedium.copy(
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.11.sp,
    )

/**
 * Assistant lesson text rendered with Markwon (markdown + LaTeX). Spark chips render after the
 * stream completes when parsed chips exist (hidden while [streamInProgress]). Mentor tail is never shown.
 */
@Composable
fun AssistantReadableText(
    /** User-visible assistant text (merged / polished). */
    raw: String,
    /** When set, used to parse lesson layout from the raw envelope; if null, [raw] is used. */
    rawEnvelopeForLessonParsing: String? = null,
    textColor: Color,
    /** Markdown links and spark chips; defaults to theme primary when null. */
    accentColor: Color? = null,
    /** Thinking line, pulse dots; defaults to theme onSurfaceVariant when null. */
    secondaryLabelColor: Color? = null,
    modifier: Modifier = Modifier,
    /** When set (e.g. after save), used for spark chips; otherwise sparks are parsed from [raw]. */
    parsedSparksCsv: String? = null,
    /** True while the model is still writing the last assistant bubble (live token stream). */
    streamInProgress: Boolean = false,
    /** Whether to show the one-line thinking status while streaming. */
    showThoughtProcess: Boolean = true,
    /** Fallback one-liner if [awaitingStreamHints] is empty while the stream has nothing to render yet. */
    jsonStreamingPlaceholder: String = "Thinking…",
    /** Cycled in the assistant bubble whenever we stream but formatted lesson sections are still empty. */
    awaitingStreamHints: List<String> = emptyList(),
    /** When non-null and [sparkChipsEnabled], spark chips send this text as the next user message. */
    onSparkChipClick: ((String) -> Unit)? = null,
    /** When false (e.g. while the model is generating), spark chips are not tappable. */
    sparkChipsEnabled: Boolean = true,
) {
    val structureSource = rawEnvelopeForLessonParsing?.takeIf { it.isNotBlank() } ?: raw
    val presentation = remember(structureSource) {
        AssistantOutput.lessonBubblePresentation(structureSource)
    }
    val sparksCsvSource = remember(structureSource, parsedSparksCsv) {
        parsedSparksCsv?.trim()?.takeIf { it.isNotBlank() }
            ?: AssistantOutput.extractSparksCsv(structureSource).orEmpty()
    }
    val sparkChips = remember(sparksCsvSource) {
        GyangoOutputEnvelope.parseSparkChips(sparksCsvSource.takeIf { it.isNotBlank() })
    }
    val showSparksSection = !streamInProgress && sparkChips.isNotEmpty()
    val hasVisibleStreamText = raw.isNotBlank()
    val showStreamWaitLine = streamInProgress && showThoughtProcess && !hasVisibleStreamText
    val resolvedAccent = accentColor ?: MaterialTheme.colorScheme.primary
    val resolvedSecondary = secondaryLabelColor ?: MaterialTheme.colorScheme.onSurfaceVariant
    val bodyStyle = LessonBodyStyle
    val streamPlainPreview = remember(raw, streamInProgress) {
        if (streamInProgress && raw.isNotBlank()) {
            AssistantTextPolisher.plainTextForAssistantSpeech(raw)
        } else {
            ""
        }
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(BlockSpacing),
    ) {
        if (showStreamWaitLine) {
            val thinkingLabel = jsonStreamingPlaceholder.trim().ifBlank { "Thinking…" }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StreamWaitPulseDots(dotColor = resolvedSecondary.copy(alpha = 0.88f))
                Text(
                    text = thinkingLabel,
                    modifier = Modifier.weight(1f),
                    style = LessonIntroStyle.copy(
                        hyphens = Hyphens.None,
                        fontStyle = FontStyle.Italic,
                    ),
                    color = resolvedSecondary.copy(alpha = 0.95f),
                )
            }
        }
        if (streamInProgress && streamPlainPreview.isNotBlank()) {
            Text(
                text = streamPlainPreview,
                modifier = Modifier.fillMaxWidth(),
                style = bodyStyle,
                color = textColor,
            )
        }
        if (!streamInProgress) {
            presentation.sections.forEachIndexed { index, sectionText ->
                if (sectionText.isBlank()) return@forEachIndexed
                key(index, sectionText) {
                    val markdown = remember(sectionText) {
                        normalizeSingleDollarLatexForJlMath(stripLessonParagraphDecor(sectionText))
                    }
                    Column(modifier = Modifier.padding(bottom = 12.dp)) {
                        AssistantMarkdownTextView(
                            markdown = markdown,
                            textColor = textColor,
                            linkColor = resolvedAccent,
                            bodyStyle = bodyStyle,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        if (showSparksSection) {
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sparkChips.forEach { chip ->
                    Text(
                        text = chip,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                enabled = sparkChipsEnabled && onSparkChipClick != null,
                            ) {
                                onSparkChipClick?.invoke(chip)
                            },
                        style = MaterialTheme.typography.labelLarge.copy(
                            lineHeight = 20.sp,
                            letterSpacing = 0.02.sp,
                            textDecoration = TextDecoration.Underline,
                        ),
                        color = resolvedAccent,
                        textAlign = TextAlign.Start,
                    )
                }
            }
        }
    }
}

/** Keep lesson markdown semantics intact (blockquote/labels included). */
private fun stripLessonParagraphDecor(section: String): String {
    return section.trim()
}

@Composable
private fun StreamWaitPulseDots(dotColor: Color) {
    val transition = rememberInfiniteTransition(label = "stream_wait")
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { i ->
            val alpha by transition.animateFloat(
                initialValue = 0.28f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(520, delayMillis = i * 140, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "waitDot$i",
            )
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor.copy(alpha = alpha)),
            )
        }
    }
}
