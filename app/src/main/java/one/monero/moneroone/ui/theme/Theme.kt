package one.monero.moneroone.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/*
 * Every slot is set, so no Material baseline (purple-tinted) value reaches the
 * UI through a library default. Slot meanings follow tokens.json `neutral`:
 * surfaceContainer = card, surfaceVariant = fill, surfaceContainerHigh = fill
 * on a card, surfaceContainerLow = grouped page.
 */

private val LightColorScheme = lightColorScheme(
    primary = MoneroOrange,
    onPrimary = Color.White,
    primaryContainer = MoneroOrange.copy(alpha = 0.15f).compositeOver(LightBackground),
    onPrimaryContainer = LightLabel,
    inversePrimary = MoneroOrange,
    secondary = Gray,
    onSecondary = Color.White,
    secondaryContainer = LightFill,
    onSecondaryContainer = LightLabel,
    tertiary = GreenLight,
    onTertiary = Color.White,
    tertiaryContainer = GreenLight.copy(alpha = 0.15f).compositeOver(LightBackground),
    onTertiaryContainer = LightLabel,
    background = LightBackground,
    onBackground = LightLabel,
    surface = LightBackground,
    onSurface = LightLabel,
    surfaceVariant = LightFill,
    onSurfaceVariant = LightLabelSecondary,
    surfaceTint = LightBackground,
    inverseSurface = DarkCard,
    inverseOnSurface = DarkLabel,
    error = RedLight,
    onError = Color.White,
    errorContainer = RedLight.copy(alpha = 0.15f).compositeOver(LightBackground),
    onErrorContainer = LightLabel,
    outline = LightOutline,
    outlineVariant = LightSeparator,
    scrim = Color.Black,
    surfaceBright = LightBackground,
    surfaceDim = LightFillHighest,
    surfaceContainer = LightCard,
    surfaceContainerHigh = LightFillElevated,
    surfaceContainerHighest = LightFillHighest,
    surfaceContainerLow = LightGroupedBackground,
    surfaceContainerLowest = LightBackground
)

private val DarkColorScheme = darkColorScheme(
    primary = MoneroOrange,
    onPrimary = Color.White,
    primaryContainer = MoneroOrange.copy(alpha = 0.15f).compositeOver(DarkBackground),
    onPrimaryContainer = DarkLabel,
    inversePrimary = MoneroOrange,
    secondary = Gray,
    onSecondary = Color.White,
    secondaryContainer = DarkFillElevated,
    onSecondaryContainer = DarkLabel,
    tertiary = GreenDark,
    onTertiary = Color.White,
    tertiaryContainer = GreenDark.copy(alpha = 0.15f).compositeOver(DarkBackground),
    onTertiaryContainer = DarkLabel,
    background = DarkBackground,
    onBackground = DarkLabel,
    surface = DarkBackground,
    onSurface = DarkLabel,
    surfaceVariant = DarkFill,
    onSurfaceVariant = DarkLabelSecondary,
    surfaceTint = DarkBackground,
    inverseSurface = LightFill,
    inverseOnSurface = LightLabel,
    error = RedDark,
    onError = Color.White,
    errorContainer = RedDark.copy(alpha = 0.15f).compositeOver(DarkBackground),
    onErrorContainer = DarkLabel,
    outline = DarkOutline,
    outlineVariant = DarkSeparator,
    scrim = Color.Black,
    surfaceBright = DarkFillElevated,
    surfaceDim = DarkBackground,
    surfaceContainer = DarkCard,
    surfaceContainerHigh = DarkFillElevated,
    surfaceContainerHighest = DarkFillHighest,
    surfaceContainerLow = DarkGroupedBackground,
    surfaceContainerLowest = DarkBackground
)

@Composable
fun MoneroOneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Make system bars transparent for edge-to-edge
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()

            // Set system bar content colors based on theme
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalMoneroColors provides if (darkTheme) DarkMoneroColors else LightMoneroColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
