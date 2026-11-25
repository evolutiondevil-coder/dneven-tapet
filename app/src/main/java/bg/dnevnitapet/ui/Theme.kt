package bg.dnevnitapet.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import bg.dnevnitapet.R

/*
 * The app's own warm "sunset" look, taken from the logo: orange for everything you press or
 * choose, warm dark and light backgrounds, a soft lavender as the second colour. It is the same
 * every day – the phone's Material You colours follow the wallpaper, and this app changes the
 * wallpaper every day, so its colours would change every day too.
 */

private val DarkColors = darkColorScheme(
    primary = SelectedOrange,
    onPrimary = OnSelectedOrange,
    primaryContainer = Color(0xFF5C3300),
    onPrimaryContainer = Color(0xFFFFDCBE),
    inversePrimary = Color(0xFF8F4E00),
    secondary = Color(0xFFE5C1A6),
    onSecondary = Color(0xFF412C1A),
    secondaryContainer = Color(0xFF59422F),
    onSecondaryContainer = Color(0xFFFFDCC2),
    tertiary = Color(0xFFCBBEFF),
    onTertiary = Color(0xFF32226A),
    tertiaryContainer = Color(0xFF493A82),
    onTertiaryContainer = Color(0xFFE7DEFF),
    background = Color(0xFF17120E),
    onBackground = Color(0xFFEEE0D7),
    surface = Color(0xFF17120E),
    onSurface = Color(0xFFEEE0D7),
    surfaceVariant = Color(0xFF52443A),
    onSurfaceVariant = Color(0xFFD8C3B4),
    surfaceTint = SelectedOrange,
    inverseSurface = Color(0xFFEEE0D7),
    inverseOnSurface = Color(0xFF362F2A),
    outline = Color(0xFFA08D80),
    outlineVariant = Color(0xFF52443A),
    scrim = Color.Black,
    surfaceBright = Color(0xFF3F3732),
    surfaceDim = Color(0xFF17120E),
    surfaceContainerLowest = Color(0xFF110D0A),
    surfaceContainerLow = Color(0xFF1F1A16),
    surfaceContainer = Color(0xFF241E1A),
    surfaceContainerHigh = Color(0xFF2F2924),
    surfaceContainerHighest = Color(0xFF3A332E),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFB85000),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCC2),
    onPrimaryContainer = Color(0xFF331100),
    inversePrimary = Color(0xFFFFB77C),
    secondary = Color(0xFF755945),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDCC5),
    onSecondaryContainer = Color(0xFF2B1709),
    tertiary = Color(0xFF6553A6),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE8DDFF),
    onTertiaryContainer = Color(0xFF21005E),
    background = Color(0xFFFFF8F4),
    onBackground = Color(0xFF221A15),
    surface = Color(0xFFFFF8F4),
    onSurface = Color(0xFF221A15),
    surfaceVariant = Color(0xFFF4DED0),
    onSurfaceVariant = Color(0xFF52443A),
    surfaceTint = Color(0xFFB85000),
    inverseSurface = Color(0xFF382F29),
    inverseOnSurface = Color(0xFFFFEDE3),
    outline = Color(0xFF85736A),
    outlineVariant = Color(0xFFD8C3B4),
    scrim = Color.Black,
    surfaceBright = Color(0xFFFFF8F4),
    surfaceDim = Color(0xFFE8D7CD),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFF1E9),
    surfaceContainer = Color(0xFFFCEBE1),
    surfaceContainerHigh = Color(0xFFF6E5DB),
    surfaceContainerHighest = Color(0xFFF0DFD5),
)

/** Manrope: a modern, rounded sans with full Cyrillic (the standard letter shapes). */
val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)

/** Softer, rounder corners everywhere (cards, sheets, dialogs, menus). */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

/**
 * Lines of a wrapped text are made about equally long ("balanced"), so a single word never
 * hangs alone on the last line. Used by every text style of the app.
 */
private val Balanced = LineBreak(
    strategy = LineBreak.Strategy.Balanced,
    strictness = LineBreak.Strictness.Normal,
    wordBreak = LineBreak.WordBreak.Default,
)

private fun TextStyle.app(weight: FontWeight, tracking: Double = 0.0) =
    copy(fontFamily = Manrope, fontWeight = weight, letterSpacing = tracking.em, lineBreak = Balanced)

private val AppTypography = Typography().let { t ->
    Typography(
        displayLarge = t.displayLarge.app(FontWeight.ExtraBold, -0.02),
        displayMedium = t.displayMedium.app(FontWeight.ExtraBold, -0.02),
        displaySmall = t.displaySmall.app(FontWeight.Bold, -0.015),
        headlineLarge = t.headlineLarge.app(FontWeight.ExtraBold, -0.015),
        headlineMedium = t.headlineMedium.app(FontWeight.Bold, -0.01),
        headlineSmall = t.headlineSmall.app(FontWeight.Bold, -0.01),
        titleLarge = t.titleLarge.app(FontWeight.Bold, -0.005),
        titleMedium = t.titleMedium.app(FontWeight.SemiBold),
        titleSmall = t.titleSmall.app(FontWeight.SemiBold),
        bodyLarge = t.bodyLarge.app(FontWeight.Normal),
        bodyMedium = t.bodyMedium.app(FontWeight.Normal),
        bodySmall = t.bodySmall.app(FontWeight.Normal),
        labelLarge = t.labelLarge.app(FontWeight.SemiBold),
        labelMedium = t.labelMedium.app(FontWeight.SemiBold),
        labelSmall = t.labelSmall.app(FontWeight.Medium),
    )
}
