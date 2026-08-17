package jp.co.bitz.spritekit

/**
 * A single pointer's touch, delivered to whichever [SKNode] is handling it via
 * [SKNode.touchesBegan]/[SKNode.touchesMoved]/[SKNode.touchesEnded]/[SKNode.touchesCancelled].
 *
 * [location] is already expressed in the *receiving* node's own local space — convert it to
 * another node's space the usual way, via [SKNode.convertTo]/[SKNode.convertFrom], if needed.
 * This is this library's stand-in for Apple's `UITouch`: rather than a stateful object you query
 * `location(in:)` on, each dispatch gets its own immutable snapshot already resolved into the
 * node it's being delivered to — see `docs/API_COMPATIBILITY.md`.
 */
public data class SKTouch(
    public val pointerId: Int,
    public val location: Vector2,
)
