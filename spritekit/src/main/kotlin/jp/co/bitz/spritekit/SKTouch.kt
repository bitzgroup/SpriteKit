package jp.co.bitz.spritekit

/**
 * One finger on the screen — this library's stand-in for Apple's `UITouch`. Delivered to whichever
 * [SKNode] is handling it via [SKNode.touchesBegan]/[SKNode.touchesMoved]/[SKNode.touchesEnded]/
 * [SKNode.touchesCancelled].
 *
 * Like `UITouch`, one instance persists for a finger's whole lifetime (from touch-down until it
 * lifts or is cancelled) and is updated in place as the finger moves, so it can be compared by
 * identity or kept in a `Set` across callbacks. Query where it is with [location]/
 * [previousLocation], which convert into any node's coordinate space — Apple's
 * `location(in:)`/`previousLocation(in:)`.
 */
public class SKTouch internal constructor(
    /** The Android pointer ID this touch tracks (`MotionEvent.getPointerId`). */
    public val pointerId: Int,
    internal val referenceNode: SKNode,
    referencePoint: Vector2,
) {
    /** The phase this touch was in when it was last delivered. */
    public var phase: SKTouchPhase = SKTouchPhase.Began
        internal set

    internal var referencePoint: Vector2 = referencePoint
        private set

    internal var previousReferencePoint: Vector2 = referencePoint
        private set

    internal fun moveTo(point: Vector2) {
        previousReferencePoint = referencePoint
        referencePoint = point
    }

    /** This touch's current location in [node]'s coordinate space — Apple's `location(in:)`. */
    public fun location(node: SKNode): Vector2 = node.convertFrom(referencePoint, referenceNode)

    /**
     * Where this touch was before its most recent move, in [node]'s coordinate space — Apple's
     * `previousLocation(in:)`. Equals [location] until the touch has moved.
     */
    public fun previousLocation(node: SKNode): Vector2 = node.convertFrom(previousReferencePoint, referenceNode)
}

/**
 * The event a batch of touches was delivered as — this library's stand-in for Apple's `UIEvent`,
 * passed as the `event` argument of [SKNode.touchesBegan] and its siblings.
 */
public class SKEvent internal constructor(
    /** Every touch currently on the screen, including ones not part of this delivery — Apple's `allTouches`. */
    public val allTouches: Set<SKTouch>,
)
