package jp.co.bitz.spritekit

/**
 * A 2D point, vector, or size — this library's stand-in for Apple's `CGPoint`/`CGVector`/`CGSize`
 * (all `(x, y)`-shaped pairs; SpriteKit's split between them is a Core Graphics/Objective-C legacy
 * this port doesn't need, so a single type covers every role, documented in
 * `docs/API_COMPATIBILITY.md`). When it holds a size, [width]/[height] read it the way Apple's
 * `CGSize` does. Single-precision, matching Android's `View`/`Canvas` coordinate
 * conventions (and [jp.co.bitz.gameplaykit.Vector2] in the sibling GameplayKit-for-Android
 * library, which this type deliberately mirrors the shape of — the two libraries have no
 * dependency on each other, so this is a separate, identically-shaped type, not a shared one).
 */
public data class Vector2(
    public val x: Float = 0f,
    public val y: Float = 0f,
) {
    /** [x], read as a size's width — Apple's `CGSize.width`. */
    public val width: Float get() = x

    /** [y], read as a size's height — Apple's `CGSize.height`. */
    public val height: Float get() = y

    public operator fun plus(other: Vector2): Vector2 = Vector2(x + other.x, y + other.y)

    public operator fun minus(other: Vector2): Vector2 = Vector2(x - other.x, y - other.y)

    public operator fun times(scalar: Float): Vector2 = Vector2(x * scalar, y * scalar)

    /**
     * `0 - this`, componentwise. Not `Vector2(-x, -y)`: negating `0f` directly flips it to
     * `-0.0f`, which breaks equality checks.
     */
    public operator fun unaryMinus(): Vector2 = Vector2(0f - x, 0f - y)

    public infix fun dot(other: Vector2): Float = x * other.x + y * other.y

    /**
     * The z component of `this × other`, treating both as 3D vectors with `z = 0` — this
     * library's 2D cross product.
     */
    public infix fun cross(other: Vector2): Float = x * other.y - y * other.x

    public fun lengthSquared(): Float = x * x + y * y

    public fun length(): Float = kotlin.math.sqrt(lengthSquared())

    /** This vector scaled to unit length, or [Zero] itself if it already is (rather than dividing by zero). */
    public fun normalized(): Vector2 {
        val len = length()
        return if (len == 0f) Zero else this * (1f / len)
    }

    public companion object {
        /** The zero vector/origin point. */
        public val Zero: Vector2 = Vector2(0f, 0f)
    }
}
