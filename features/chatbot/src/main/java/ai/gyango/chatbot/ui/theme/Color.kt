package ai.gyango.chatbot.ui.theme

import androidx.compose.ui.graphics.Color

val GyangoBlue = Color(0xFF1565C0)
val GyangoBlueLight = Color(0xFF1976D2)
val GyangoBlueDark = Color(0xFF0D47A1)
val GyangoBlueMuted = Color(0xFFE3F2FD)
val GyangoBlueSurface = Color(0xFFE8F4FD)

val AmberAccent = Color(0xFFD4A853)
val AmberAccentLight = Color(0xFFE8C97A)

val Charcoal = Color(0xFF2D3436)
val Slate = Color(0xFF636E72)
val Mist = Color(0xFFB2BEC3)
val Snow = Color(0xFFFDFEFE)

/** User bubble — light: brand blue (matches header family). */
val UserMessageBg = Color(0xFF1565C0)
/**
 * User bubble — dark: deep indigo (same blue family as the app bar, not saturated #1976 on near-black).
 */
val UserMessageBgDark = Color(0xFF303F9F)
/** High-contrast text on [UserMessageBgDark]. */
val ChatUserBubbleTextDark = Color(0xFFE8EAF6)

/** Assistant bubble — light: soft icy surface (pairs with [Charcoal] body text). */
val AssistantMessageBg = Color(0xFFE8F4FD)
/**
 * Assistant bubble — dark: elevated blue-slate (readable vs [CharcoalBackground], not same as page surface).
 */
val AssistantMessageBgDark = Color(0xFF2E3D52)
/** Assistant bubble body text in dark mode (not theme onSurface, so chips/links stay balanced). */
val ChatAssistantBodyTextDark = Color(0xFFECEFF1)
/** Row label, thinking line, typing dots — dark assistant context. */
val ChatAssistantSecondaryDark = Color(0xFFCFD8DC)
/** Markdown links & spark chips on assistant bubble — dark (readable on slate; header blue unchanged). */
val ChatAssistantLinkDark = Color(0xFF90CAF9)
/** Light assistant: links/sparks slightly deeper than body for WCAG on pale blue. */
val ChatAssistantLinkLight = Color(0xFF0D47A1)

val GyangoBlueDarkPrimary = Color(0xFF64B5F6)
val GyangoBlueDarkContainer = Color(0xFF0D47A1)
val CharcoalSurface = Color(0xFF1E272E)
val CharcoalBackground = Color(0xFF0F1419)
