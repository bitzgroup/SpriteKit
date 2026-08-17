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
