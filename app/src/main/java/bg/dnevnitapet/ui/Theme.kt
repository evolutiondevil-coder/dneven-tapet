package bg.dnevnitapet.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF2D5DA8),
    secondary = Color(0xFF55606E),
    tertiary = Color(0xFF6E5676),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C7FF),
    secondary = Color(0xFFBDC7D7),
    tertiary = Color(0xFFDABDE2),
)

/** Material You: colours follow the phone's wallpaper/theme on Android 12+, light and dark. */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, typography = BalancedTypography, content = content)
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

private fun TextStyle.balanced() = copy(lineBreak = Balanced)

private val BalancedTypography = Typography().let { t ->
    Typography(
        displayLarge = t.displayLarge.balanced(),
        displayMedium = t.displayMedium.balanced(),
        displaySmall = t.displaySmall.balanced(),
        headlineLarge = t.headlineLarge.balanced(),
        headlineMedium = t.headlineMedium.balanced(),
        headlineSmall = t.headlineSmall.balanced(),
        titleLarge = t.titleLarge.balanced(),
        titleMedium = t.titleMedium.balanced(),
        titleSmall = t.titleSmall.balanced(),
        bodyLarge = t.bodyLarge.balanced(),
        bodyMedium = t.bodyMedium.balanced(),
        bodySmall = t.bodySmall.balanced(),
        labelLarge = t.labelLarge.balanced(),
        labelMedium = t.labelMedium.balanced(),
        labelSmall = t.labelSmall.balanced(),
    )
}
