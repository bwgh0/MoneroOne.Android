package one.monero.moneroone.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The payment link envelope's art (final v2 mockup, link-card/assets/final-env.css): dark laid
 * paper, an orange wax seal with the official mark, the orange liner and the ivory card. Fixed
 * colors, the same in light and dark (only the shadows change), as on iOS. The liner runs from
 * brand (#FF6600) to brandPressed (#E65C00), and the card's "XMR" is brandPressed (tokens.json).
 */
object LinkEnvelopeColors {
    // The pocket (the envelope's front), top to bottom.
    val PaperTop = Color(0xFF2E2E33)
    val PaperMid = Color(0xFF26262A)
    val PaperBottom = Color(0xFF202024)

    // The flap's outside, hinge to tip, and its inside around the liner.
    val FlapTop = Color(0xFF36363C)
    val FlapMid = Color(0xFF2D2D32)
    val FlapTip = Color(0xFF29292E)
    val FlapInside = Color(0xFF26262A)

    // The liner: brand to brandPressed.
    val LinerTop = Color(0xFFFF6600)
    val LinerMid = Color(0xFFF56200)
    val LinerBottom = Color(0xFFE65C00)

    // The wax seal.
    val WaxLight = Color(0xFFFF8A3D)
    val WaxBright = Color(0xFFFF6A0A)
    val WaxBody = Color(0xFFE65C00)
    val WaxEdge = Color(0xFFB54500)
    val WaxRing = Color(0xFFF06A10)
    val WaxStamp = Color(0xFFD95400)
    val WaxGlint = Color(0xFFFFC496)
    val WaxGlintStamp = Color(0xFFFFC89B)
    val WaxShade = Color(0xFF6E2400)
    val WaxShadeStamp = Color(0xFF6E2200)
    val WaxSpot = Color(0xFFFFE2C4)
    val WaxSweep = Color(0xFFFFF0E2)
    val Crack = Color(0xFF4A1800)

    // The ivory card.
    val CardLight = Color(0xFFFBF8F1)
    val CardMid = Color(0xFFF3EDE1)
    val CardEdge = Color(0xFFE9E1D1)
    val CardBorder = Color(0xFF503C1E)
    val CardInk = Color(0xFF1F1F22)
    val CardUnit = Color(0xFFE65C00)
    val CardLabel = Color(0xFF6E675C)

    // The share image: the backdrop (center to edge) and the foil amount.
    val BackdropCenter = Color(0xFFF1EFEB)
    val BackdropMid = Color(0xFFE4E0DA)
    val BackdropEdge = Color(0xFFD6D1C9)
    val FoilDeep = Color(0xFFB84A00)
    val FoilLight = Color(0xFFFF8A3D)
    val FoilBrand = Color(0xFFFF6600)
    val FoilEdge = Color(0xFFC24E00)
}
