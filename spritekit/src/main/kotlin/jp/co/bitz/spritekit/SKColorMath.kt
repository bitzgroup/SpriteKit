package jp.co.bitz.spritekit

/**
 * Pure bit-math ARGB channel extraction, kept independent of [android.graphics.Color]'s static
 * methods — those aren't safe to call from plain JVM unit tests without Robolectric (unlike its
 * compile-time-constant fields, e.g. `Color.WHITE`, which are fine). Not part of the public API.
 */
internal fun redOf(argb: Int): Float = ((argb shr 16) and 0xFF) / 255f

internal fun greenOf(argb: Int): Float = ((argb shr 8) and 0xFF) / 255f

internal fun blueOf(argb: Int): Float = (argb and 0xFF) / 255f

internal fun alphaOf(argb: Int): Float = ((argb ushr 24) and 0xFF) / 255f

/** Linearly interpolates each ARGB channel between [from] and [to] by [t] (`0..1`), for [SKAction.colorize]. */
internal fun lerpColor(
    from: Int,
    to: Int,
    t: Float,
): Int {
    val a = channel(alphaOf(from) + (alphaOf(to) - alphaOf(from)) * t)
    val r = channel(redOf(from) + (redOf(to) - redOf(from)) * t)
    val g = channel(greenOf(from) + (greenOf(to) - greenOf(from)) * t)
    val b = channel(blueOf(from) + (blueOf(to) - blueOf(from)) * t)
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

private fun channel(normalized: Float): Int = (normalized.coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
