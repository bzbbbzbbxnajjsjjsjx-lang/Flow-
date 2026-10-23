package com.akis.app.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Green brand scheme (seed #2E6B4F) on soft mint surfaces.
// Light counterpart of the navy palette: #F6F7FF background, #E9ECFA cards, #C9D2FF accent, #1B1B2F text.
private val LightColors = lightColorScheme(
    primary = Color(0xFF3A4A9F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC9D2FF),
    onPrimaryContainer = Color(0xFF0A1450),
    secondary = Color(0xFF565B7A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDE1FA),
    onSecondaryContainer = Color(0xFF151A33),
    tertiary = Color(0xFF8B3F95),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF4C6F7),
    onTertiaryContainer = Color(0xFF3B0B45),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF6F7FF),
    onBackground = Color(0xFF1B1B2F),
    surface = Color(0xFFF6F7FF),
    onSurface = Color(0xFF1B1B2F),
    surfaceVariant = Color(0xFFE1E4F5),
    onSurfaceVariant = Color(0xFF454860),
    outline = Color(0xFF767A94),
    outlineVariant = Color(0xFFC6C9E0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEEF0FC),
    surfaceContainer = Color(0xFFE9ECFA),
    surfaceContainerHigh = Color(0xFFE3E6F6),
    surfaceContainerHighest = Color(0xFFDDE0F2),
)

// Deep navy palette sampled from the reference screenshot: #060A2D background, #0E153F cards,
// #2F3B9F selected pill, #EFA4F7 pink FAB, #DDE2FF text.
private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9C3FF),
    onPrimary = Color(0xFF0A1068),
    primaryContainer = Color(0xFF2F3B9F),
    onPrimaryContainer = Color(0xFFDDE2FF),
    secondary = Color(0xFFC3C5EA),
    onSecondary = Color(0xFF2A2F63),
    secondaryContainer = Color(0xFF1E2A66),
    onSecondaryContainer = Color(0xFFDDE2FF),
    tertiary = Color(0xFFEFA4F7),
    onTertiary = Color(0xFF3B0B45),
    tertiaryContainer = Color(0xFFEFA4F7),
    onTertiaryContainer = Color(0xFF2E0A38),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF060A2D),
    onBackground = Color(0xFFDDE2FF),
    surface = Color(0xFF060A2D),
    onSurface = Color(0xFFDDE2FF),
    surfaceVariant = Color(0xFF1B2352),
    onSurfaceVariant = Color(0xFF8A8FB5),
    outline = Color(0xFF5F6490),
    outlineVariant = Color(0xFF262C5E),
    surfaceContainerLowest = Color(0xFF040720),
    surfaceContainerLow = Color(0xFF0E153F),
    surfaceContainer = Color(0xFF121A47),
    surfaceContainerHigh = Color(0xFF182152),
    surfaceContainerHighest = Color(0xFF1E285C),
)

// Sepia: warm paper for evening reading.
private val SepiaColors = LightColors.copy(
    primary = Color(0xFF8A5A00),
    primaryContainer = Color(0xFFF1D9A8),
    onPrimaryContainer = Color(0xFF2B1A00),
    secondary = Color(0xFF6E5B3F),
    secondaryContainer = Color(0xFFEBDDBF),
    onSecondaryContainer = Color(0xFF2A2010),
    tertiary = Color(0xFF9A4A2B),
    tertiaryContainer = Color(0xFFF2C9B4),
    onTertiaryContainer = Color(0xFF3A1505),
    background = Color(0xFFF4ECD8),
    onBackground = Color(0xFF3B2F1E),
    surface = Color(0xFFF4ECD8),
    onSurface = Color(0xFF3B2F1E),
    surfaceVariant = Color(0xFFE6DAC0),
    onSurfaceVariant = Color(0xFF5E5240),
    outline = Color(0xFF8C7F68),
    outlineVariant = Color(0xFFD3C6AA),
    surfaceContainerLowest = Color(0xFFFBF6E9),
    surfaceContainerLow = Color(0xFFEFE5CC),
    surfaceContainer = Color(0xFFEBE0C5),
    surfaceContainerHigh = Color(0xFFE6DABD),
    surfaceContainerHighest = Color(0xFFE1D4B6),
)

// Pure black background saves power on OLED screens; cards stay just above it.
private val BlackColors = DarkColors.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0B0D1C),
    surfaceContainer = Color(0xFF101326),
    surfaceContainerHigh = Color(0xFF161A30),
    surfaceContainerHighest = Color(0xFF1C2038),
)

const val PALETTE_SEPIA = 1
const val PALETTE_BLACK = 2

/** The app uses the navy palette unless sepia or black is chosen; dynamic color is opt-in. */
@Composable
fun AkisTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    palette: Int = 0,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        palette == PALETTE_SEPIA -> SepiaColors
        palette == PALETTE_BLACK -> BlackColors
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialExpressiveTheme(colorScheme = scheme, content = content)
}
