package ai.gyango.chatbot.ui

import android.graphics.drawable.ColorDrawable
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import ai.gyango.chatbot.ui.theme.LocalGyangoAppInDarkTheme
import android.graphics.Color as AndroidColor
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.image.coil.CoilImagesPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import io.noties.markwon.linkify.LinkifyPlugin

/**
 * JLatexMathPlugin expects `$$…$$` for inline math; models often emit single-dollar `$…$`.
 * Heuristic: only rewrite pairs that look like TeX (backslash, braces, sub/sup markers).
 */
internal fun normalizeSingleDollarLatexForJlMath(markdown: String): String {
    val looksTexy = Regex("""[\\^_{}]|\\frac|\\text|\\mathrm|\\sqrt|\\sum|\\int""")
    val looksMathy = Regex("""[=+\-*/<>]|[A-Za-z][0-9]|[0-9][A-Za-z]|[A-Z][a-z]?[0-9]""")
    val looksCurrencyOnly = Regex("""\s*[0-9]+(?:[.,][0-9]{1,2})?\s*""")
    return Regex("""\$([^$\n]+)\$""").replace(markdown) { m ->
        val inner = m.groupValues[1]
        val shouldNormalize =
            !looksCurrencyOnly.matches(inner) &&
                (looksTexy.containsMatchIn(inner) || looksMathy.containsMatchIn(inner))
        if (shouldNormalize) "$$${inner}$$" else m.value
    }
}

@Composable
internal fun rememberGyangoMarkwon(
    bodyTextStyle: TextStyle,
    textColor: Color,
    linkColor: Color,
): Markwon {
    val context = LocalContext.current
    val density = LocalDensity.current
    val bodySp = bodyTextStyle.fontSize.takeIf { it != TextUnit.Unspecified } ?: 17.sp
    val textSizePx = with(density) { bodySp.toPx() }
    val textArgb = textColor.toArgb()
    val linkArgb = linkColor.toArgb()
    val dark = LocalGyangoAppInDarkTheme.current
    val mathSurface = if (dark) {
        // Slightly stronger than before so formula chips read on blue-slate assistant bubbles.
        AndroidColor.argb(48, 255, 255, 255)
    } else {
        AndroidColor.argb(36, 0, 0, 0)
    }
    return remember(context, textArgb, linkArgb, textSizePx, dark, bodySp) {
        Markwon.builder(context)
            .usePlugin(MarkwonInlineParserPlugin.create())
            .usePlugin(
                JLatexMathPlugin.create(textSizePx) { builder ->
                    builder.inlinesEnabled(true)
                    builder.theme()
                        .textColor(textArgb)
                        .inlineTextColor(textArgb)
                        .blockTextColor(textArgb)
                        .backgroundProvider { ColorDrawable(mathSurface) }
                },
            )
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(CoilImagesPlugin.create(context))
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder.linkColor(linkArgb)
                }
            })
            .build()
    }
}

@Composable
internal fun AssistantMarkdownTextView(
    markdown: String,
    textColor: Color,
    bodyStyle: TextStyle,
    modifier: Modifier = Modifier,
    linkColor: Color? = null,
) {
    val resolvedLink = linkColor ?: MaterialTheme.colorScheme.primary
    val markwon = rememberGyangoMarkwon(
        bodyTextStyle = bodyStyle,
        textColor = textColor,
        linkColor = resolvedLink,
    )
    val bodySp = (bodyStyle.fontSize.takeIf { it != TextUnit.Unspecified } ?: 17.sp)
    val mult = if (bodyStyle.fontSize != TextUnit.Unspecified && bodyStyle.lineHeight != TextUnit.Unspecified) {
        (bodyStyle.lineHeight.value / bodyStyle.fontSize.value).coerceAtLeast(1f)
    } else {
        1.2f
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                movementMethod = LinkMovementMethod.getInstance()
                linksClickable = true
                setTextIsSelectable(false)
                includeFontPadding = false
            }
        },
        update = { tv ->
            tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, bodySp.value)
            tv.setTextColor(textColor.toArgb())
            tv.setLineSpacing(0f, mult)
            val normalized = markdown.trimEnd()
            val lastRendered = tv.getTag() as? String
            if (lastRendered != normalized) {
                markwon.setMarkdown(tv, normalized)
                tv.setTag(normalized)
            }
        },
    )
}
