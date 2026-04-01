package ai.pocket.chatbot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BlockSpacing = 4.dp
private val ParagraphGapExtra = 8.dp
private val ListMarkerMinWidth = 24.dp
private val BodyLineHeight = 22.sp
private val LabelLineHeight = 24.sp

/**
 * Human-readable assistant replies: paragraph spacing, bold section labels,
 * bullets and accurate numbered lines.
 */
@Composable
fun AssistantReadableText(
    raw: String,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(raw) { MessageTextFormatter.assistantDisplayBlocks(raw) }
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val markerColor = secondary.copy(alpha = 0.8f)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(BlockSpacing),
    ) {
        blocks.forEach { block ->
            when (block) {
                AssistantDisplayBlock.ParagraphBreak ->
                    Spacer(Modifier.height(ParagraphGapExtra))

                is AssistantDisplayBlock.Paragraph ->
                    Text(
                        text = block.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor,
                        fontWeight = FontWeight.Normal,
                        lineHeight = BodyLineHeight,
                        letterSpacing = 0.15.sp,
                    )

                is AssistantDisplayBlock.SectionLabel ->
                    Text(
                        text = block.text,
                        style = MaterialTheme.typography.titleMedium,
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        lineHeight = LabelLineHeight,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )

                is AssistantDisplayBlock.BulletItem ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyLarge,
                            color = markerColor,
                            modifier = Modifier.widthIn(min = ListMarkerMinWidth),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = block.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = textColor,
                            fontWeight = FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                            lineHeight = BodyLineHeight,
                        )
                    }

                is AssistantDisplayBlock.OrderedItem ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = "${block.index}.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = markerColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.widthIn(min = ListMarkerMinWidth),
                        )
                        Text(
                            text = block.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = textColor,
                            fontWeight = FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                            lineHeight = BodyLineHeight,
                        )
                    }
            }
        }
    }
}
