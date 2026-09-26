package one.monero.moneroone.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import one.monero.moneroone.R

/*
 * Monero One type system (tokens.json `type`). iOS uses SF Pro; Android uses
 * Inter 4, its closest open twin, from one variable file. Inter has an optical
 * size axis: 14 ("Inter Text") for sizes below 20sp and 32 ("Inter Display")
 * for 20sp and up, the same split SF Pro makes between Text and Display.
 * Four weights only: 400, 500, 600, 700.
 */

private val InterWeights = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)

// Font variation settings on resource fonts are experimental in Compose UI 1.7.
@OptIn(ExperimentalTextApi::class)
private fun interFamily(opticalSize: Float) = FontFamily(
    InterWeights.map { weight ->
        Font(
            R.font.inter_variable,
            weight = weight,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(weight.weight),
                FontVariation.Setting("opsz", opticalSize)
            )
        )
    }
)

/** Inter at optical size 14: every style below 20sp. */
val InterText: FontFamily = interFamily(14f)

/** Inter at optical size 32: every style of 20sp and up. */
val InterDisplay: FontFamily = interFamily(32f)

@OptIn(ExperimentalTextApi::class)
private fun monoFamily() = FontFamily(
    InterWeights.map { weight ->
        Font(
            R.font.roboto_mono_variable,
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
        )
    }
)

/** Roboto Mono: addresses, transaction ids, keys, seed words. */
val MonoFamily: FontFamily = monoFamily()

/** Tabular figures, for every quantity that changes (balance, amount, price). */
const val TabularFigures = "tnum"

/**
 * Inter's text cut draws a little larger than SF Pro Text at the same size:
 * x-height .546 vs .526 (font files, opsz 14 vs 17) and body lines about 4%
 * wider. Text roles (under 20sp) use the iOS size times this factor so they
 * look the same size as iOS. Display roles (20sp and up) and hero numerals use
 * Inter Display, which already matches SF Pro Display (x-height .516 vs .523),
 * so they keep the iOS sizes. Line heights always stay on the iOS values.
 * tokens.json `type.androidTextScale`.
 */
const val InterTextScale = 0.96f

// Full line box with the glyphs centered in it, as on iOS: a 17/22 line is 22 tall.
private val MoneroLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

private fun role(
    size: Float,
    line: Float,
    weight: FontWeight,
    trackingEm: Float
) = TextStyle(
    fontFamily = if (size >= 20f) InterDisplay else InterText,
    fontWeight = weight,
    fontSize = (if (size >= 20f) size else size * InterTextScale).sp,
    lineHeight = line.sp,
    letterSpacing = trackingEm.em,
    lineHeightStyle = MoneroLineHeightStyle
)

// Hero numerals: iOS uses SF Pro Rounded with monospaced digits; Android uses
// Inter Display Bold with tabular figures, at the iOS sizes.
private fun numeral(size: Float) = TextStyle(
    fontFamily = InterDisplay,
    fontWeight = FontWeight.Bold,
    fontSize = size.sp,
    lineHeight = TextUnit.Unspecified,
    letterSpacing = (-0.005).em,
    fontFeatureSettings = TabularFigures,
    lineHeightStyle = MoneroLineHeightStyle
)

/**
 * Material 3 slots mapped to the iOS text styles (tokens.json `type.roles`
 * and `type.numerals`). Sizes below are the iOS sizes; `role` applies
 * [InterTextScale] under 20sp. Letter spacing is Inter's `trackingInter` in em.
 */
val Typography = Typography(
    displayLarge = numeral(56f),                                   // amount
    displayMedium = numeral(42f),                                  // price
    displaySmall = numeral(32f),                                   // balance
    headlineLarge = role(34f, 41f, FontWeight.Bold, -0.005f),      // largeTitle
    headlineMedium = role(28f, 34f, FontWeight.Bold, -0.005f),     // title1
    headlineSmall = role(22f, 28f, FontWeight.SemiBold, -0.005f),  // title2
    titleLarge = role(20f, 25f, FontWeight.SemiBold, -0.005f),     // title3
    titleMedium = role(17f, 22f, FontWeight.SemiBold, -0.013f),    // headline
    titleSmall = role(15f, 20f, FontWeight.Medium, -0.009f),       // subheadline, medium
    bodyLarge = role(17f, 22f, FontWeight.Normal, -0.013f),        // body
    bodyMedium = role(15f, 20f, FontWeight.Normal, -0.009f),       // subheadline
    bodySmall = role(12f, 16f, FontWeight.Normal, 0f),             // caption
    labelLarge = role(16f, 21f, FontWeight.SemiBold, -0.011f),     // callout, semibold
    labelMedium = role(13f, 18f, FontWeight.Medium, -0.003f),      // footnote, medium
    labelSmall = role(11f, 13f, FontWeight.Medium, 0.005f)         // caption2, medium
)

/** Addresses and hashes: caption in mono (tokens.json `type.numerals.mono`). */
val MonoCaption: TextStyle = TextStyle(
    fontFamily = MonoFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.em,
    lineHeightStyle = MoneroLineHeightStyle
)
