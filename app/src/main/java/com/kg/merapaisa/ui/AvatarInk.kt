package com.kg.merapaisa.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.pow

/**
 * The colours an avatar can be, and how one is made legible on a given theme.
 *
 * Two separate problems, and only the second is interesting.
 *
 * The first: the palette on offer was eight swatches off the Flat UI and Material sheets, two of
 * them (`#2ECC71`, `#E84B3A`) named in the skill's ban list, and the default was Material Green
 * 500, which was not even one of the eight. So every person added without opening the picker came
 * out the same green. [AVATAR_HUES] replaces those with eight inks that are not stock and are far
 * enough apart to tell two people apart at a glance.
 *
 * The second: **no single colour can be legible on both Paper and Amoled.** A mid green that reads
 * on `#F4F6F8` disappears on `#000000`, and one that reads on black glares on paper. The stored
 * colour is a *choice of hue*, not a choice of luminance, so [avatarInk] keeps the hue the person
 * picked and sets the lightness the theme needs. That fixes every avatar already in the ledger,
 * including the green ones, without overriding what anyone chose: green stays green.
 *
 * `pfpColor` itself lives in `data/Person.kt`, which is frozen, so the stored default cannot be
 * changed. This is the presentation-side answer to it.
 */

/**
 * Eight hues, spaced around the wheel, none of them a stock swatch.
 *
 * Stored at a mid lightness so a picker swatch looks like what it is, but only the hue survives
 * into the avatar, since [avatarInk] re-lights it per theme.
 */
val AVATAR_HUES = listOf(
    "#3F6DA8", // indigo
    "#2E8079", // teal
    "#6E8C3A", // olive
    "#A8802E", // ochre
    "#A65A2E", // rust
    "#A6423F", // brick
    "#8C4A7D", // plum
    "#5B5F8C"  // slate
)

/** How much of the ink washes the tile behind the initials. See [avatarInk]. */
const val AVATAR_WASH = 0.12f

/**
 * The stored colour, re-lit for this theme.
 *
 * Hue and saturation are kept; the lightness is chosen to hit a target **luminance**, not a target
 * HSL lightness. That distinction is the whole trick: at a fixed HSL lightness a blue is far darker
 * to the eye than an ochre, and the first version of this failed on exactly the blues, reds and
 * purples while the yellows passed. Solving for luminance normalises across the wheel.
 *
 * The targets are not symmetric, because a dark ground needs a colour above the midpoint to read as
 * ink rather than as a stain, while a light ground needs one clearly darker than the paper.
 * Saturation is floored so a colour stored as near-grey still arrives as a recognisable ink.
 *
 * The tile behind the initials is this same ink at [AVATAR_WASH], which is the reason that constant
 * is here rather than in the view: brightening the ink also brightens its own background, so the
 * two numbers only make sense chosen together. At the old 0.20 wash no target cleared 4.5:1 without
 * making the avatars glare.
 */
fun avatarInk(stored: String, isDarkTheme: Boolean): Color {
    // Something that is not a colour becomes a neutral ink, lit exactly like a real one: no hue,
    // no saturation, and the same target luminance. Two hand-picked fallback hexes would have been
    // the one pair of avatar colours in the app that nothing measured.
    val parsed = parseHex(stored)?.let(::toHsl)
    val h = parsed?.first ?: 0f
    val saturation = parsed?.second?.coerceIn(0.28f, 0.62f) ?: 0f
    val target = if (isDarkTheme) 0.42f else 0.10f

    // Lightness is monotonic in luminance at fixed hue and saturation, so a bisection converges.
    var low = 0f
    var high = 1f
    repeat(24) {
        val mid = (low + high) / 2f
        if (relativeLuminance(fromHsl(h, saturation, mid)) < target) low = mid else high = mid
    }
    return fromHsl(h, saturation, (low + high) / 2f)
}

private fun relativeLuminance(c: Color): Float {
    fun channel(v: Float) = if (v <= 0.03928f) v / 12.92f else
        ((v + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
    return 0.2126f * channel(c.red) + 0.7152f * channel(c.green) + 0.0722f * channel(c.blue)
}

private fun parseHex(value: String): Triple<Float, Float, Float>? {
    val hex = value.removePrefix("#")
    if (hex.length != 6 || !hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
    return Triple(
        hex.substring(0, 2).toInt(16) / 255f,
        hex.substring(2, 4).toInt(16) / 255f,
        hex.substring(4, 6).toInt(16) / 255f
    )
}

private fun toHsl(rgb: Triple<Float, Float, Float>): Triple<Float, Float, Float> {
    val (r, g, b) = rgb
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val l = (max + min) / 2f
    if (max == min) return Triple(0f, 0f, l)
    val d = max - min
    val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
    val h = when (max) {
        r -> ((g - b) / d + if (g < b) 6f else 0f)
        g -> ((b - r) / d + 2f)
        else -> ((r - g) / d + 4f)
    } / 6f
    return Triple(h, s, l)
}

private fun fromHsl(h: Float, s: Float, l: Float): Color {
    if (s == 0f) return Color(l, l, l)
    val q = if (l < 0.5f) l * (1 + s) else l + s - l * s
    val p = 2 * l - q
    fun channel(t0: Float): Float {
        var t = t0
        if (t < 0) t += 1f
        if (t > 1) t -= 1f
        return when {
            t < 1f / 6 -> p + (q - p) * 6 * t
            t < 1f / 2 -> q
            t < 2f / 3 -> p + (q - p) * (2f / 3 - t) * 6
            else -> p
        }
    }
    return Color(channel(h + 1f / 3), channel(h), channel(h - 1f / 3))
}

/** How far apart two stored hues are on the wheel, for the test that keeps the palette distinct. */
internal fun hueGap(a: String, b: String): Float {
    val ha = parseHex(a)?.let { toHsl(it).first } ?: return 1f
    val hb = parseHex(b)?.let { toHsl(it).first } ?: return 1f
    val d = abs(ha - hb)
    return minOf(d, 1f - d)
}
