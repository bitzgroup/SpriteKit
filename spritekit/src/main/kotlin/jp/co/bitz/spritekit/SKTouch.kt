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

    /**
     * How many quick taps in one place this touch is part of — Apple's `tapCount`: 1 for a single
     * tap, 2 for the second of a double tap, and so on, kept while the touch moves. As on Apple
     * (verified on an iOS simulator), a touch that was dragged away or held too long ends with 0,
     * and each finger of a multi-finger tap counts on its own. The time and distance limits are
     * Android's (`ViewConfiguration`'s double-tap timeout and slop, touch slop, long-press timeout),
     * as Apple doesn't document its own.
     */
    public var tapCount: Int = 1
        internal set

    /**
     * When this touch last changed, in seconds since boot — Apple's `timestamp`, on the same clock
     * as Apple's (`systemUptime`, here `SystemClock.uptimeMillis`): taken from the Android
     * `MotionEvent`, not from when the render thread delivered it.
     */
    public var timestamp: Double = 0.0
        internal set

    /**
     * Always 0, as Apple's `force` is on devices without force-sensing hardware (every iPhone without
     * 3D Touch, and the simulator). Android's touch pressure isn't the same measure, so it isn't
     * passed through.
     */
    public val force: Float = 0f

    /** Always 0, as Apple's `maximumPossibleForce` is without force-sensing hardware; see [force]. */
    public val maximumPossibleForce: Float = 0f

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
