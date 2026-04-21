package ai.gyango.chatbot.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import ai.gyango.assistant.AssistantOutput
import ai.gyango.assistant.GyangoOutputEnvelope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val BlockSpacing = 4.dp
private val ParagraphGapExtra = 10.dp
private val ListMarkerMinWidth = 24.dp
/** Italic “thinking” line while streaming before section bodies appear. */
private val LessonIntroStyle: TextStyle
    @Composable get() = MaterialTheme.typography.bodyLarge.copy(
        fontSize = 18.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.15.sp,
    )

/** One body size for all lesson paragraphs; section headings carry hierarchy. */
private val LessonBodyStyle: TextStyle
    @Composable get() = MaterialTheme.typography.bodyMedium.copy(
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.11.sp,
    )

/** `**…**` / `` `…` `` highlights on white section cards. */
@Composable
private fun lessonKeywordHighlightColor(): Color =
    if (isSystemInDarkTheme()) Color(0xFF82B1FF) else Color(0xFF1565C0)
private val LabelLineHeight = 26.sp
private val CodeLineHeightMin = 21.sp

private enum class LessonSectionTone {
    Definition,
    Analogy,
    Application,
}

private fun lessonToneFromCaption(caption: String?): LessonSectionTone? = when (caption) {
    "Definition" -> LessonSectionTone.Definition
    "Analogy" -> LessonSectionTone.Analogy
    "Application" -> LessonSectionTone.Application
    else -> null
}

private fun lessonSectionHeadingLabel(caption: String): String =
    caption.trim().removeSuffix(":").trim()

@Composable
private fun LessonSectionHeadingRow(caption: String) {
    val label = lessonSectionHeadingLabel(caption)
    val icon = when (label) {
        "Definition" -> Icons.Default.Description
        "Analogy" -> Icons.Default.Lightbulb
        "Application" -> Icons.Default.Build
        else -> null
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 6.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f),
        )
    }
}

/**
 * Human-readable assistant replies: when **Definition** / **Analogy** / **Application** headers are all
 * present, each block uses a white (light) or neutral surface (dark) card with an icon heading. Lesson body
 * copy uses one consistent size. `**keywords**` and `` `inline terms` `` render in a strong blue. If those
 * section tags are not all found, paragraphs are not section-boxed (except a lone **Analogy:**-led
 * paragraph, which still uses the same card). Spark chips appear only after generation finishes when
 * parsed chips exist (hidden while [streamInProgress]). `[SAVE_CONTEXT]` / memory lines are never shown.
 */
@Composable
fun AssistantReadableText(
    /** User-visible assistant text (merged / polished). */
    raw: String,
    /**
     * When set (assistant messages after streaming), used to detect **Definition:** / **Analogy:** /
     * **Application:** for section tints. If null, [raw] is used — often flattened, so tints may not apply.
     */
    rawEnvelopeForLessonParsing: String? = null,
    textColor: Color,
    modifier: Modifier = Modifier,
    /** When set (e.g. after save), used for spark chips; otherwise sparks are parsed from [raw]. */
    parsedSparksCsv: String? = null,
    /** True while the model is still writing the last assistant bubble (live token stream). */
    streamInProgress: Boolean = false,
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
    val showStreamWaitLine = streamInProgress && presentation.sections.none { it.polishedBody.isNotBlank() }
    var waitHintIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(streamInProgress, awaitingStreamHints) {
        if (!streamInProgress) return@LaunchedEffect
        waitHintIndex = 0
        val hints = awaitingStreamHints
        if (hints.isEmpty()) return@LaunchedEffect
        while (isActive) {
            delay(1700)
            waitHintIndex = (waitHintIndex + 1) % hints.size
        }
    }
    val waitLineText = when {
        !showStreamWaitLine -> ""
        awaitingStreamHints.isNotEmpty() ->
            awaitingStreamHints[waitHintIndex % awaitingStreamHints.size]
        else -> jsonStreamingPlaceholder
    }
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val markerColor = secondary.copy(alpha = 0.8f)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(BlockSpacing),
    ) {
        if (showStreamWaitLine && waitLineText.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StreamWaitPulseDots(dotColor = secondary.copy(alpha = 0.85f))
                Text(
                    text = waitLineText,
                    modifier = Modifier.weight(1f),
                    style = LessonIntroStyle.copy(
                        hyphens = Hyphens.None,
                        fontStyle = FontStyle.Italic,
                    ),
                    color = secondary.copy(alpha = 0.92f),
                )
            }
        }
        val keywordHighlight = lessonKeywordHighlightColor()
        presentation.sections.forEach { section ->
            if (section.polishedBody.isBlank()) return@forEach
            val prepared = stripLessonParagraphDecor(section.polishedBody)
            val blocks = MessageTextFormatter.assistantDisplayBlocksFromPolished(prepared)
            val sectionContent: @Composable () -> Unit = {
                LessonSectionBlocks(
                    blocks = blocks,
                    textColor = textColor,
                    markerColor = markerColor,
                    secondary = secondary,
                    keywordHighlightColor = keywordHighlight,
                    baseStyle = LessonBodyStyle,
                )
            }
            val padded = Modifier.padding(bottom = 12.dp)
            val toneFromCaption = lessonToneFromCaption(section.caption)
            when {
                presentation.explicitLabeledTriple && toneFromCaption != null ->
                    LessonTintedSection(
                        caption = section.caption,
                        tone = toneFromCaption,
                        modifier = padded,
                        content = sectionContent,
                    )

                section.useAnalogyCallout ->
                    LessonTintedSection(
                        caption = "Analogy",
                        tone = LessonSectionTone.Analogy,
                        modifier = padded,
                        content = sectionContent,
                    )

                else ->
                    Column(modifier = padded) { sectionContent() }
            }
        }
        if (showSparksSection) {
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val chipShape = RoundedCornerShape(28.dp)
                val colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                val outline = MaterialTheme.colorScheme.outline
                val chipBorder = BorderStroke(
                    width = 1.dp,
                    color = if (isSystemInDarkTheme()) {
                        outline.copy(alpha = 0.55f)
                    } else {
                        outline.copy(alpha = 0.4f)
                    },
                )
                sparkChips.forEach { chip ->
                    SuggestionChip(
                        onClick = {
                            if (sparkChipsEnabled) onSparkChipClick?.invoke(chip)
                        },
                        label = {
                            Text(
                                text = chip,
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    lineHeight = 20.sp,
                                    letterSpacing = 0.02.sp,
                                ),
                                maxLines = 2,
                                textAlign = TextAlign.Center,
                            )
                        },
                        enabled = sparkChipsEnabled && onSparkChipClick != null,
                        shape = chipShape,
                        colors = colors,
                        border = chipBorder,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun LessonTintedSection(
    caption: String?,
    tone: LessonSectionTone,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val fill = when (tone) {
        LessonSectionTone.Definition,
        LessonSectionTone.Analogy,
        LessonSectionTone.Application ->
            if (isDark) MaterialTheme.colorScheme.surface else Color.White
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = fill,
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            caption?.let { cap -> LessonSectionHeadingRow(cap) }
            content()
        }
    }
}

/** Strip blockquote `>` and legacy Def/Analogy/Application echo labels at paragraph starts. */
private fun stripLessonParagraphDecor(section: String): String {
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

@Composable
private fun LessonSectionBlocks(
    blocks: List<AssistantDisplayBlock>,
    textColor: Color,
    markerColor: Color,
    secondary: Color,
    keywordHighlightColor: Color,
    baseStyle: TextStyle,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(BlockSpacing),
    ) {
        blocks.forEach { block ->
            when (block) {
                AssistantDisplayBlock.ParagraphBreak ->
                    Spacer(Modifier.height(ParagraphGapExtra))

                is AssistantDisplayBlock.Paragraph ->
                    LessonRichText(
                        text = block.text,
                        color = textColor,
                        emphasisColor = keywordHighlightColor,
                        inlineCodeColor = keywordHighlightColor,
                        style = baseStyle.copy(fontWeight = FontWeight.Normal),
                    )

                is AssistantDisplayBlock.SectionLabel ->
                    LessonRichText(
                        text = block.text,
                        color = textColor,
                        emphasisColor = keywordHighlightColor,
                        inlineCodeColor = keywordHighlightColor,
                        style = MaterialTheme.typography.titleMedium.copy(
                            hyphens = Hyphens.None,
                            lineHeight = LabelLineHeight,
                        ),
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                    )

                is AssistantDisplayBlock.BulletItem ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        Text(
                            text = "•",
                            style = baseStyle,
                            color = markerColor,
                            modifier = Modifier.widthIn(min = ListMarkerMinWidth),
                            fontWeight = FontWeight.Bold,
                        )
                        LessonRichText(
                            text = block.text,
                            color = textColor,
                            emphasisColor = keywordHighlightColor,
                            inlineCodeColor = keywordHighlightColor,
                            style = baseStyle.copy(fontWeight = FontWeight.Normal),
                            modifier = Modifier.weight(1f),
                        )
                    }

                is AssistantDisplayBlock.OrderedItem ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = "${block.index}.",
                            style = baseStyle,
                            color = markerColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.widthIn(min = ListMarkerMinWidth),
                        )
                        LessonRichText(
                            text = block.text,
                            color = textColor,
                            emphasisColor = keywordHighlightColor,
                            inlineCodeColor = keywordHighlightColor,
                            style = baseStyle.copy(fontWeight = FontWeight.Normal),
                            modifier = Modifier.weight(1f),
                        )
                    }

                is AssistantDisplayBlock.CodeBlock ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(secondary.copy(alpha = 0.14f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = block.text,
                            style = baseStyle.copy(
                                hyphens = Hyphens.None,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = CodeLineHeightMin,
                            ),
                            color = textColor,
                            fontWeight = FontWeight.Normal,
                        )
                    }
            }
        }
    }
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
