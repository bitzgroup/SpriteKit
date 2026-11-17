package jp.co.bitz.spritekit

import kotlin.math.hypot

/**
 * Works out `UITouch.tapCount` for each pointer of an Android touch stream, following Apple's
 * behavior as recorded on an iOS simulator (the tic-tac-toe sample's
 * `SKTouchTapCountParityUITests`):
 *
 * - A touch that starts soon after a tap ended, close to where it was, continues that tap's count
 *   (1, 2, 3, ...); otherwise it starts at 1. Each finger of a multi-finger tap counts on its own.
 * - A touch keeps its count while it moves, but one that moved away (a drag) or was held too long
 *   (a long press) is no longer a tap: it ends with a count of 0, and the next touch starts over.
 *
 * Apple doesn't document its time and distance limits, so this takes Android's own
 * (`ViewConfiguration`'s double-tap timeout and slop, touch slop and long-press timeout). Times are
 * in milliseconds, distances in pixels.
 */
internal class SKTapCounter(
    private val doubleTapTimeoutMs: Long,
    private val doubleTapSlopPx: Float,
    private val touchSlopPx: Float,
    private val longPressTimeoutMs: Long,
) {
    private class Active(
        val x: Float,
        val y: Float,
        val downTime: Long,
        val count: Int,
        var movedAway: Boolean = false,
    )

    private class FinishedTap(
        val x: Float,
        val y: Float,
        val upTime: Long,
        val count: Int,
    )

    private val active = mutableMapOf<Int, Active>()
    private val finishedTaps = mutableListOf<FinishedTap>()

    /** A pointer went down; returns its tap count. */
    fun began(
        pointerId: Int,
        x: Float,
        y: Float,
        timeMs: Long,
    ): Int {
        finishedTaps.removeAll { timeMs - it.upTime > doubleTapTimeoutMs }
        val nearest = finishedTaps.minByOrNull { hypot(it.x - x, it.y - y) }
        val previous = nearest?.takeIf { hypot(it.x - x, it.y - y) <= doubleTapSlopPx }
        previous?.let { finishedTaps.remove(it) }
        val count = (previous?.count ?: 0) + 1
        active[pointerId] = Active(x, y, timeMs, count)
        return count
    }

    /** A pointer moved; returns its (unchanged) tap count. */
    fun moved(
        pointerId: Int,
        x: Float,
        y: Float,
    ): Int {
        val touch = active[pointerId] ?: return 0
        if (hypot(touch.x - x, touch.y - y) > touchSlopPx) touch.movedAway = true
        return touch.count
    }

    /** A pointer lifted; returns its final tap count — 0 if it was a drag or a long press. */
    fun ended(
        pointerId: Int,
        x: Float,
        y: Float,
        timeMs: Long,
    ): Int {
        val touch = active.remove(pointerId)
        if (touch != null && hypot(touch.x - x, touch.y - y) > touchSlopPx) touch.movedAway = true
        val isTap = touch != null && !touch.movedAway && timeMs - touch.downTime <= longPressTimeoutMs
        if (isTap) finishedTaps += FinishedTap(x, y, timeMs, checkNotNull(touch).count)
        return if (isTap) checkNotNull(touch).count else 0
    }

    /** A pointer was cancelled; returns its count so far. A cancelled touch never continues a count. */
    fun cancelled(pointerId: Int): Int = active.remove(pointerId)?.count ?: 0
}
