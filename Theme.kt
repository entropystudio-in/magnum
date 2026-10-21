package com.cadence.player

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Colours follow the iOS system palette Apple Music uses.
 * Typeface: Apple's SF Pro is proprietary and cannot be bundled, so this uses the
 * system sans (Roboto) with Apple's type scale, weights and tracking.
 * To use SF Pro on your own build, add the font files to res/font and set [AppFont].
 */
val AppFont: FontFamily = FontFamily.Default

@Immutable
data class CadenceColors(
    val background: Color,
    val elevated: Color,       // grouped / card surfaces
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val separator: Color,
    val accent: Color,
    val fill: Color,           // slider tracks, chips
    val isDark: Boolean
)

val LightCadence = CadenceColors(
    background = Color(0xFFFFFFFF),
    elevated = Color(0xFFF2F2F7),
    label = Color(0xFF000000),
    secondaryLabel = Color(0xFF8A8A8E),
    tertiaryLabel = Color(0xFFC4C4C6),
    separator = Color(0xFFE5E5EA),
    accent = Color(0xFFFA233B),
    fill = Color(0xFFE9E9EB),
    isDark = false
)

val DarkCadence = CadenceColors(
    background = Color(0xFF000000),
    elevated = Color(0xFF1C1C1E),
    label = Color(0xFFFFFFFF),
    secondaryLabel = Color(0xFF98989F),
    tertiaryLabel = Color(0xFF48484A),
    separator = Color(0xFF2C2C2E),
    accent = Color(0xFFFA2D48),
    fill = Color(0xFF2C2C2E),
    isDark = true
)

val LocalCadence = staticCompositionLocalOf { LightCadence }

object T {
    val largeTitle = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = 0.37.sp)
    val title1 = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = 0.36.sp)
    val title2 = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 0.35.sp)
    val title3 = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = 0.38.sp)
    val headline = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, letterSpacing = (-0.41).sp)
    val body = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 17.sp, letterSpacing = (-0.41).sp)
    val callout = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 16.sp, letterSpacing = (-0.32).sp)
    val subhead = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 15.sp, letterSpacing = (-0.24).sp)
    val footnote = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 13.sp, letterSpacing = (-0.08).sp)
    val caption = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 12.sp)
    val tabLabel = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 0.1.sp)
}

@Composable
fun CadenceTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (dark) DarkCadence else LightCadence
    val scheme = if (dark) {
        darkColorScheme(primary = c.accent, background = c.background, surface = c.background, onSurface = c.label, onBackground = c.label)
    } else {
        lightColorScheme(primary = c.accent, background = c.background, surface = c.background, onSurface = c.label, onBackground = c.label)
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalCadence provides c) {
        MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
    }
}

val cadence: CadenceColors
    @Composable get() = LocalCadence.current
