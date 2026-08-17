package jp.co.bitz.spritekit

/**
 * A 2D point or vector — this library's stand-in for Apple's `CGPoint`/`CGVector` (both are
 * `(x, y)` pairs; SpriteKit's split between the two is a Core Graphics/Objective-C legacy this
 * port doesn't need, so a single type covers both roles, documented in
 * `docs/API_COMPATIBILITY.md`). Single-precision, matching Android's `View`/`Canvas` coordinate
 * conventions (and [jp.co.bitz.gameplaykit.Vector2] in the sibling GameplayKit-for-Android
 * library, which this type deliberately mirrors the shape of — the two libraries have no
 * dependency on each other, so this is a separate, identically-shaped type, not a shared one).
 */
public data class Vector2(
    public val x: Float = 0f,
    public val y: Float = 0f,
) {
    public operator fun plus(other: Vector2): Vector2 = Vector2(x + other.x, y + other.y)

    public operator fun minus(other: Vector2): Vector2 = Vector2(x - other.x, y - other.y)

    public operator fun times(scalar: Float): Vector2 = Vector2(x * scalar, y * scalar)

    /**
     * `0 - this`, componentwise. Not `Vector2(-x, -y)`: negating `0f` directly flips it to
     * `-0.0f`, which breaks equality checks.
     */
    public operator fun unaryMinus(): Vector2 = Vector2(0f - x, 0f - y)

    public companion object {
        public val Zero: Vector2 = Vector2(0f, 0f)
    }
}
