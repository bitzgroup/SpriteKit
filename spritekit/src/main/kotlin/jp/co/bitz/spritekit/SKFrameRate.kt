package jp.co.bitz.spritekit

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * How many display refreshes [SKView] waits between frames for [preferredFramesPerSecond] on a
 * display refreshing [displayRefreshRate] times a second. Only whole fractions of the display rate
 * are possible (60, 30, 20, 15, 12, ... at 60 Hz); this picks the one nearest the requested rate,
 * the higher one on a tie. On iOS/tvOS simulators at 60 Hz that matches Apple's choice for every
 * rate from 11 to 60 except two: Apple runs 24 at 30 (here 20) and 45 at 30 (here 60). Apple's
 * own rule isn't documented, and no simple one fits all fifty. A negative rate or
 * one at or above the display's runs every refresh, and 0 runs once a second, as on Apple.
 */
internal fun skFrameInterval(
    preferredFramesPerSecond: Int,
    displayRefreshRate: Float,
): Int {
    // Whole hertz: displays report rates like 59.94 or 60.000004, which would otherwise tip ties.
    val reported = if (displayRefreshRate > 0f) displayRefreshRate else DEFAULT_DISPLAY_REFRESH_RATE
    val refresh = reported.roundToInt().toFloat()
    return when {
        preferredFramesPerSecond == 0 -> refresh.roundToInt().coerceAtLeast(1)
        preferredFramesPerSecond < 0 || preferredFramesPerSecond >= refresh -> 1
        else -> nearestFrameInterval(preferredFramesPerSecond.toFloat(), refresh)
    }
}

// The interval whose rate (refresh / interval) is nearest [target], the shorter one on a tie.
private fun nearestFrameInterval(
    target: Float,
    refresh: Float,
): Int {
    fun distance(interval: Int) = abs(refresh / interval - target)
    var interval = 1
    while (distance(interval + 1) < distance(interval)) interval++
    return interval
}

/** Apple's default `preferredFramesPerSecond`. */
internal const val DEFAULT_PREFERRED_FRAMES_PER_SECOND: Int = 60

/** Assumed when the view isn't on a display yet. */
internal const val DEFAULT_DISPLAY_REFRESH_RATE: Float = 60f
