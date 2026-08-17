package jp.co.bitz.spritekit

/**
 * How an [SKAction]'s progress eases over its duration, mirroring Apple's `SKActionTimingMode`.
 * Standard easing curves — *contract-conformant, not bit-identical* with Apple's own
 * (undocumented) curves. Set [SKAction.timingFunction] instead for a fully custom curve.
 */
public enum class SKActionTimingMode {
    /** Constant rate. This library's default, matching Apple's. */
    Linear,

    /** Starts slow, speeds up. */
    EaseIn,

    /** Starts fast, slows down. */
    EaseOut,

    /** Starts slow, speeds up through the middle, slows down again. */
    EaseInEaseOut,
}

/** Maps `t` (`0..1`) through this timing curve, returning the eased progress (`0..1`). */
internal fun SKActionTimingMode.ease(t: Float): Float =
    when (this) {
        SKActionTimingMode.Linear -> t
        SKActionTimingMode.EaseIn -> t * t
        SKActionTimingMode.EaseOut -> t * (2f - t)
        SKActionTimingMode.EaseInEaseOut -> t * t * (3f - 2f * t)
    }
