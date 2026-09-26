package com.example.ai_agent.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.ai_agent.ui.jarvis.JarvisHudColors

private val EarthyColorScheme = lightColorScheme(
    primary = TerracottaPrimary,
    onPrimary = LinenOnPrimary,
    secondary = SandSecondary,
    tertiary = SageTertiary,
    background = EarthBackground,
    surface = ClaySurface,
    onSurface = UmberOnSurface,
    outline = EarthOutline,
)

/**
 * Cyan HUD-flavoured dark scheme. Uses solid variants of [JarvisHudColors] so Material
 * surfaces render correctly even outside the HUD's radial-gradient background. Semi-
 * transparent tokens (Glass*, BorderSoft) intentionally stay HUD-only.
 */
private val JarvisDeepBackground = Color(0xFF050A0E)
private val JarvisSurface = Color(0xFF0A1520)
private val JarvisSurfaceVariant = Color(0xFF10202B)
private val JarvisOutline = Color(0xFF1F4152)

private val JarvisColorScheme = darkColorScheme(
    primary = JarvisHudColors.Accent,
    onPrimary = JarvisDeepBackground,
    primaryContainer = JarvisSurfaceVariant,
    onPrimaryContainer = JarvisHudColors.TextPrimary,
    secondary = JarvisHudColors.AccentTeal,
    onSecondary = JarvisDeepBackground,
    secondaryContainer = JarvisSurfaceVariant,
    onSecondaryContainer = JarvisHudColors.TextPrimary,
    tertiary = JarvisHudColors.AccentWarm,
    onTertiary = JarvisDeepBackground,
    background = JarvisDeepBackground,
    onBackground = JarvisHudColors.TextPrimary,
    surface = JarvisSurface,
    onSurface = JarvisHudColors.TextPrimary,
    surfaceVariant = JarvisSurfaceVariant,
    onSurfaceVariant = JarvisHudColors.TextSecondary,
    outline = JarvisOutline,
    outlineVariant = JarvisHudColors.BorderSoft,
    error = JarvisHudColors.Error,
    onError = JarvisDeepBackground,
    inverseSurface = JarvisHudColors.TextPrimary,
    inverseOnSurface = JarvisDeepBackground,
)

/**
 * @param jarvisMode when true, the whole app switches to the cyan HUD palette; when false,
 *  the default Earthy scheme is used. The HUD screen itself continues to use
 *  [JarvisHudColors] directly for the gradient / glass / neon tokens that don't fit the
 *  Material 3 slot system.
 */
@Composable
fun AiAgentTheme(
    jarvisMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (jarvisMode) JarvisColorScheme else EarthyColorScheme

    // Keep the status-bar and nav-bar icon appearance in sync with the current theme. When
    // Jarvis mode is on the background is near-black, so system icons must be light; the
    // Earthy scheme is light so icons must be dark.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !jarvisMode
            insetsController.isAppearanceLightNavigationBars = !jarvisMode
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
