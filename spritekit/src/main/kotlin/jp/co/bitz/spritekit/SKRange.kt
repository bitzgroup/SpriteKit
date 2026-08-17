package jp.co.bitz.spritekit

/**
 * A closed interval used by [SKConstraint], mirroring Apple's `SKRange`. Use the companion
 * factories instead of Apple's overloaded `SKRange(lowerLimit:)`/`SKRange(upperLimit:)`
 * initializers, which collide as a single Kotlin overload — Swift argument labels aren't
 * significant for Kotlin overload resolution (the same rename pattern as `SKNode.convertTo`/
 * `convertFrom`; see `docs/API_COMPATIBILITY.md`).
 */
public class SKRange private constructor(
    public val lowerLimit: Float,
    public val upperLimit: Float,
) {
    internal fun clamp(value: Float): Float = value.coerceIn(lowerLimit, upperLimit)

    public companion object {
        /** No constraint in either direction. */
        public val unlimited: SKRange = SKRange(Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY)

        /** Between [lowerLimit] and [upperLimit], inclusive. */
        public fun of(
            lowerLimit: Float,
            upperLimit: Float,
        ): SKRange = SKRange(lowerLimit, upperLimit)

        /** Exactly [value] — matches Apple's `SKRange(constantValue:)`. */
        public fun constant(value: Float): SKRange = SKRange(value, value)

        /** No upper bound. */
        public fun atLeast(lowerLimit: Float): SKRange = SKRange(lowerLimit, Float.POSITIVE_INFINITY)

        /** No lower bound. */
        public fun atMost(upperLimit: Float): SKRange = SKRange(Float.NEGATIVE_INFINITY, upperLimit)
    }
}
