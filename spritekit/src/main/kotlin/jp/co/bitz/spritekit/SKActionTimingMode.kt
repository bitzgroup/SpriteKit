package jp.co.bitz.spritekit

/**
 * How an [SKAction]'s progress eases over its duration, mirroring Apple's `SKActionTimingMode`.
 * Each curve is the one Apple's SpriteKit uses (Apple doesn't document them; they were recorded
 * on iOS/tvOS simulators). Set [SKAction.timingFunction] instead for a fully custom curve.
 */
public enum class SKActionTimingMode {
    /** Constant rate. This library's default, matching Apple's. */
    Linear,

    /** Starts slow, speeds up: `2t² − t³` (halfway through, 3/8 of the way). */
    EaseIn,

    /** Starts fast, slows down: `t + t² − t³`, the mirror of [EaseIn] (halfway, 5/8). */
    EaseOut,

    /** Starts slow, speeds up through the middle, slows down again: `3t² − 2t³` (halfway, 1/2). */
    EaseInEaseOut,
}

/** Maps `t` (`0..1`) through this timing curve, returning the eased progress (`0..1`). */
internal fun SKActionTimingMode.ease(t: Float): Float =
    when (this) {
        SKActionTimingMode.Linear -> t
        SKActionTimingMode.EaseIn -> t * t * (2f - t)
        SKActionTimingMode.EaseOut -> t * (1f + t - t * t)
        SKActionTimingMode.EaseInEaseOut -> t * t * (3f - 2f * t)
    }
