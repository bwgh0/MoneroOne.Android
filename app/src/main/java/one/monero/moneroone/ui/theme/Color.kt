package one.monero.moneroone.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * Monero One color tokens (monero-one-brand tokens.json `color`). The iOS app
 * is the north star: neutrals follow the iOS system colors, and the semantic
 * colors switch between their light and dark values like iOS system colors do.
 */

// Brand: the one action color, #FF6600 in light and dark.
val MoneroOrange = Color(0xFFFF6600)

/** Pressed fill of a prominent (filled) button. */
val MoneroOrangePressed = Color(0xFFE65C00)

/** Pending and in-flight states (connecting, syncing, 0-9 confirmations) use the brand orange. */
val PendingOrange = MoneroOrange

/** True inside a dark [MoneroOneTheme]. The semantic colors below read it. */
val LocalDarkTheme = staticCompositionLocalOf { false }

// Semantic light / dark values (tokens.json color.semantic and color.tiles).
internal val GreenLight = Color(0xFF34C759)
internal val GreenDark = Color(0xFF30D158)
internal val RedLight = Color(0xFFFF383C)
internal val RedDark = Color(0xFFFF4245)
internal val YellowLight = Color(0xFFFFCC00)
internal val YellowDark = Color(0xFFFFD600)
internal val BlueLight = Color(0xFF0088FF)
internal val BlueDark = Color(0xFF0091FF)
internal val PurpleLight = Color(0xFFCB30E0)
internal val PurpleDark = Color(0xFFDB34F2)
internal val CyanLight = Color(0xFF00C0E8)
internal val CyanDark = Color(0xFF3CD3FE)
internal val IndigoLight = Color(0xFF6155F5)
internal val IndigoDark = Color(0xFF6D7CFF)
internal val PinkLight = Color(0xFFFF2D55)
internal val PinkDark = Color(0xFFFF375F)
internal val Gray = Color(0xFF8E8E93)

/** Incoming XMR, Receive, confirmed, synced, price up, copied, every switch that is on. */
val SuccessGreen: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalDarkTheme.current) GreenDark else GreenLight

/** Errors, failed transactions, destructive actions, price down, not connected. */
val ErrorRed: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalDarkTheme.current) RedDark else RedLight

/** Caution glyphs and caution tints. Never a text color. */
val WarningYellow: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalDarkTheme.current) YellowDark else YellowLight

// Settings icon tiles (tokens.json color.tiles).

/** Informational glyphs and the blue settings tiles. */
val SettingsBlue: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalDarkTheme.current) BlueDark else BlueLight

val SettingsPurple: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalDarkTheme.current) PurpleDark else PurpleLight

val SettingsCyan: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalDarkTheme.current) CyanDark else CyanLight

val SettingsIndigo: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalDarkTheme.current) IndigoDark else IndigoLight

val SettingsGreen: Color
    @Composable @ReadOnlyComposable
    get() = SuccessGreen

val SettingsPink: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalDarkTheme.current) PinkDark else PinkLight

val SettingsGray: Color
    @Composable @ReadOnlyComposable
    get() = Gray

/**
 * iOS secondarySystemFill, #787880 at 0.16 (light) or 0.32 (dark): the track
 * of a switch that is off and of progress bars.
 */
val SystemFill: Color
    @Composable @ReadOnlyComposable
    get() = Color(0xFF787880).copy(alpha = if (LocalDarkTheme.current) 0.32f else 0.16f)

// Neutrals (tokens.json color.neutral), iOS system colors.
internal val LightLabel = Color(0xFF000000)
internal val LightLabelSecondary = Color(0xFF8A8A8E)
// Tertiary label is translucent, as on iOS, so it reads the same on any fill.
internal val LightLabelTertiary = Color(0x4D3C3C43)
internal val LightBackground = Color(0xFFFFFFFF)
internal val LightGroupedBackground = Color(0xFFF2F2F7)
internal val LightCard = Color(0xFFFFFFFF)
internal val LightFill = Color(0xFFF2F2F7)
internal val LightFillElevated = Color(0xFFFFFFFF)
internal val LightFillHighest = Color(0xFFE5E5EA)
internal val LightSeparator = Color(0xFFE8E8E9)
internal val LightOutline = Color(0xFFC6C6C8)

internal val DarkLabel = Color(0xFFFFFFFF)
internal val DarkLabelSecondary = Color(0xFF8D8D93)
internal val DarkLabelTertiary = Color(0x4DEBEBF5)
internal val DarkBackground = Color(0xFF000000)
internal val DarkGroupedBackground = Color(0xFF000000)
internal val DarkCard = Color(0xFF1C1C1E)
internal val DarkFill = Color(0xFF1C1C1E)
internal val DarkFillElevated = Color(0xFF2C2C2E)
internal val DarkFillHighest = Color(0xFF3A3A3C)
internal val DarkSeparator = Color(0xFF38383A)
internal val DarkOutline = Color(0xFF48484A)

/**
 * Tokens Material 3 has no slot for. Read them through [MoneroTheme.colors].
 */
@Immutable
data class MoneroColors(
    /** Brand orange, the action color. */
    val brand: Color,
    /** Settings and other grouped lists. */
    val bgGrouped: Color,
    /** Placeholders, disabled text, chevrons. */
    val labelTertiary: Color,
    /** Text fields, option cards, chips and tiles on the background. */
    val fill: Color,
    /** Fields and cells inside a card or a sheet. */
    val fillElevated: Color,
    /** Hairlines between rows. */
    val separator: Color,
    /** Disabled button labels, offline banner, neutral tiles. */
    val gray: Color,
    val positive: Color,
    val negative: Color,
    val warning: Color
)

internal val LightMoneroColors = MoneroColors(
    brand = MoneroOrange,
    bgGrouped = LightGroupedBackground,
    labelTertiary = LightLabelTertiary,
    fill = LightFill,
    fillElevated = LightFillElevated,
    separator = LightSeparator,
    gray = Gray,
    positive = GreenLight,
    negative = RedLight,
    warning = YellowLight
)

internal val DarkMoneroColors = MoneroColors(
    brand = MoneroOrange,
    bgGrouped = DarkGroupedBackground,
    labelTertiary = DarkLabelTertiary,
    fill = DarkFill,
    fillElevated = DarkFillElevated,
    separator = DarkSeparator,
    gray = Gray,
    positive = GreenDark,
    negative = RedDark,
    warning = YellowDark
)

val LocalMoneroColors = staticCompositionLocalOf { LightMoneroColors }

object MoneroTheme {
    val colors: MoneroColors
        @Composable @ReadOnlyComposable
        get() = LocalMoneroColors.current

    val isDark: Boolean
        @Composable @ReadOnlyComposable
        get() = LocalDarkTheme.current
}
