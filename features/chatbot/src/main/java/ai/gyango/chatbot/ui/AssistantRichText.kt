package ai.gyango.chatbot.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.TextStyle

/**
 * Lightweight rich text for lesson bubbles: **bold**, *italic*, `inline code`, __bold__.
 * [emphasisColor] styles `**…**` / `__…__` (e.g. keyword highlights); [inlineCodeColor] when set styles `` `…` ``.
 * No WebView — keeps memory predictable on low-RAM devices.
 */
fun buildLessonAnnotatedString(
    source: String,
    baseColor: Color,
    emphasisColor: Color = baseColor,
    inlineCodeColor: Color? = null,
): AnnotatedString = buildAnnotatedString {
    var i = 0
    val text = source
    while (i < text.length) {
        when {
            i <= text.length - 2 && text[i] == '*' && text[i + 1] == '*' -> {
                val end = text.indexOf("**", startIndex = i + 2)
                if (end >= i + 2) {
                    pushStyle(SpanStyle(color = emphasisColor, fontWeight = FontWeight.Bold))
                    append(text, i + 2, end)
                    pop()
                    i = end + 2
                    continue
                }
            }
            i <= text.length - 2 && text[i] == '_' && text[i + 1] == '_' -> {
                val end = text.indexOf("__", startIndex = i + 2)
                if (end >= i + 2) {
                    pushStyle(SpanStyle(color = emphasisColor, fontWeight = FontWeight.Bold))
                    append(text, i + 2, end)
                    pop()
                    i = end + 2
                    continue
                }
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', startIndex = i + 1)
                if (end > i) {
                    val codeStyle = SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                    )
                    pushStyle(
                        if (inlineCodeColor != null) {
                            codeStyle.copy(color = inlineCodeColor)
                        } else {
                            codeStyle
                        },
                    )
                    append(text, i + 1, end)
                    pop()
                    i = end + 1
                    continue
                }
            }
            text[i] == '*' -> {
                val end = text.indexOf('*', startIndex = i + 1)
                if (end > i) {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic, color = baseColor.copy(alpha = 0.94f)))
                    append(text, i + 1, end)
                    pop()
                    i = end + 1
                    continue
                }
            }
        }
        append(text[i])
        i++
    }
}

@Composable
fun LessonRichText(
    text: String,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    emphasisColor: Color = color,
    inlineCodeColor: Color? = null,
) {
    val annotated = remember(text, color, emphasisColor, inlineCodeColor) {
        buildLessonAnnotatedString(
            text,
            baseColor = color,
            emphasisColor = emphasisColor,
            inlineCodeColor = inlineCodeColor,
        )
    }
    Text(
        text = annotated,
        modifier = modifier,
        style = style.copy(hyphens = Hyphens.None),
        color = color,
    )
}
