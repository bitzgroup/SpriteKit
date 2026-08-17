package jp.co.bitz.spritekit

import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Turns successive monotonic-clock readings (typically `System.nanoTime()`) into per-frame
 * [Duration]s for [SKView]'s render loop. Not part of this library's public API surface (there's
 * no Apple equivalent to mirror); kept as its own small class, rather than inlined into [SKView],
 * so the delta-time math is unit-testable without a live render thread.
 */
internal class SKFrameClock {
    private var lastFrameNanos: Long? = null

    /**
     * Returns the duration since the previous call to [tick], given the current time in
     * nanoseconds. Returns [Duration.ZERO] on the first call (or the first call after [reset]),
     * since there's no previous frame to measure from.
     */
    fun tick(nowNanos: Long): Duration {
        val previous = lastFrameNanos
        lastFrameNanos = nowNanos
        return if (previous == null) Duration.ZERO else (nowNanos - previous).nanoseconds
    }

    /** Resets the clock so the next [tick] call returns [Duration.ZERO], as if newly started. */
    fun reset() {
        lastFrameNanos = null
    }
}
