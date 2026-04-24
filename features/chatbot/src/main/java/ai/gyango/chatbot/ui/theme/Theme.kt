package ai.gyango.chatbot.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = GyangoBlue,
    onPrimary = Snow,
    primaryContainer = GyangoBlueMuted,
    onPrimaryContainer = GyangoBlueDark,
    secondary = GyangoBlueLight,
    onSecondary = Snow,
    secondaryContainer = GyangoBlueSurface,
    onSecondaryContainer = GyangoBlueDark,
    tertiary = AmberAccent,
    onTertiary = Charcoal,
    tertiaryContainer = AmberAccentLight.copy(alpha = 0.3f),
    onTertiaryContainer = Charcoal,
    surface = Snow,
    onSurface = Charcoal,
    surfaceVariant = GyangoBlueSurface,
    onSurfaceVariant = Slate,
    outline = Mist,
    background = Snow,
    onBackground = Charcoal
)

private val DarkColorScheme = darkColorScheme(
    primary = GyangoBlueDarkPrimary,
    onPrimary = Charcoal,
    primaryContainer = GyangoBlueDarkContainer,
    onPrimaryContainer = GyangoBlueDarkPrimary,
    secondary = GyangoBlueDarkPrimary,
    onSecondary = Charcoal,
    secondaryContainer = GyangoBlueDark,
    onSecondaryContainer = GyangoBlueDarkPrimary,
    tertiary = AmberAccent,
    onTertiary = Charcoal,
    tertiaryContainer = AmberAccent.copy(alpha = 0.2f),
    onTertiaryContainer = AmberAccentLight,
    surface = CharcoalSurface,
    onSurface = Snow,
    surfaceVariant = CharcoalBackground,
    onSurfaceVariant = Mist,
    outline = Slate,
    background = CharcoalBackground,
    onBackground = Snow
)

/**
 * True when [GyangoAITheme] is using the dark color scheme (including user-forced dark).
 * Prefer this over [isSystemInDarkTheme] for chat chrome and embedded AndroidViews.
 */
val LocalGyangoAppInDarkTheme = compositionLocalOf { false }

@Composable
fun GyangoAITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val ctx = view.context
            if (ctx is Activity) {
                val window = ctx.window
                window.statusBarColor = colorScheme.primary.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialTheme(colorScheme = colorScheme) {
        CompositionLocalProvider(LocalGyangoAppInDarkTheme provides darkTheme) {
            content()
        }
    }
}
