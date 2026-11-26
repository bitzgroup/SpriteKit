package jp.co.bitz.spritekit

import android.graphics.Color
import kotlin.time.Duration

/**
 * A visual effect blending between two [SKScene]s, played by [SKView.presentScene] — mirrors
 * Apple's `SKTransition`. One concrete class configured by factory functions (matching Apple's
 * own shape here, the same pattern [SKFieldNode] already uses), rather than a family of
 * subclasses.
 *
 * This renderer has no offscreen-framebuffer support (see `docs/ARCHITECTURE.md`), so every
 * effect is built from the same primitives: drawing each scene at a `glViewport` offset/size, an
 * overall alpha multiplier, and — for the door effects — a plain rectangular clip. The door
 * effects ([doorsOpenHorizontal] and its siblings) match Apple's geometry and timing as recorded
 * on iOS/tvOS simulators. [doorway], [flipHorizontal] and [flipVertical] approximate Apple's 3D
 * effects in 2D, since this renderer has no 3D perspective transform. See
 * `docs/API_COMPATIBILITY.md`.
 */
public class SKTransition private constructor(
    internal val kind: SKTransitionKind,
    public val duration: Duration,
    public val direction: SKTransitionDirection = SKTransitionDirection.Right,
    public val color: Int = Color.BLACK,
) {
    /**
     * Whether the incoming scene is paused while the transition plays — Apple's
     * `pausesIncomingScene`, default `true`. A paused scene's `isPaused` reads `true` during the
     * transition, and is set back to `false` once it ends (verified on iOS/tvOS simulators).
     */
    public var pausesIncomingScene: Boolean = true

    /**
     * Whether the outgoing scene is paused while the transition plays — Apple's
     * `pausesOutgoingScene`, default `true`. When `false`, the outgoing scene keeps running (and
     * animating on screen) until the transition ends. Either way its `isPaused` is `false`
     * afterwards, as on Apple.
     */
    public var pausesOutgoingScene: Boolean = true

    public companion object {
        /** The outgoing scene fades to [color], then the incoming scene fades in from it. */
        public fun fade(
            duration: Duration,
            color: Int = Color.BLACK,
        ): SKTransition = SKTransition(SKTransitionKind.Fade, duration, color = color)

        /** The incoming scene dissolves in on top of the outgoing one, which stays put. */
        public fun crossFade(duration: Duration): SKTransition = SKTransition(SKTransitionKind.CrossFade, duration)

        /**
         * The incoming scene slides in over the (stationary) outgoing one, arriving from the
         * side opposite [direction].
         */
        public fun moveIn(
            direction: SKTransitionDirection,
            duration: Duration,
        ): SKTransition = SKTransition(SKTransitionKind.MoveIn, duration, direction)

        /**
         * The outgoing scene slides out towards [direction] as the incoming scene slides in
         * behind it from the opposite side.
         */
        public fun push(
            direction: SKTransitionDirection,
            duration: Duration,
        ): SKTransition = SKTransition(SKTransitionKind.Push, duration, direction)

        /**
         * The outgoing scene slides away towards [direction], revealing the (stationary)
         * incoming scene underneath.
         */
        public fun reveal(
            direction: SKTransitionDirection,
            duration: Duration,
        ): SKTransition = SKTransition(SKTransitionKind.Reveal, duration, direction)

        /**
         * The outgoing scene splits down the middle and each half slides apart, revealing the
         * incoming scene as it comes forward from a distance, over black — this port's 2D stand-in
         * for Apple's doors swinging away in perspective.
         */
        public fun doorway(duration: Duration): SKTransition = SKTransition(SKTransitionKind.Doorway, duration)

        /**
         * The outgoing scene splits down the middle and each half slides out sideways, revealing
         * the (stationary) incoming scene underneath.
         */
        public fun doorsOpenHorizontal(duration: Duration): SKTransition =
            SKTransition(SKTransitionKind.DoorsOpenHorizontal, duration)

        /**
         * The outgoing scene splits across the middle and each half slides out, up and down,
         * revealing the (stationary) incoming scene underneath.
         */
        public fun doorsOpenVertical(duration: Duration): SKTransition =
            SKTransition(SKTransitionKind.DoorsOpenVertical, duration)

        /**
         * The incoming scene's left and right halves slide in from the sides over the
         * (stationary) outgoing scene, meeting in the middle.
         */
        public fun doorsCloseHorizontal(duration: Duration): SKTransition =
            SKTransition(SKTransitionKind.DoorsCloseHorizontal, duration)

        /**
         * The incoming scene's bottom and top halves slide in from below and above over the
         * (stationary) outgoing scene, meeting in the middle.
         */
        public fun doorsCloseVertical(duration: Duration): SKTransition =
            SKTransition(SKTransitionKind.DoorsCloseVertical, duration)

        /**
         * The outgoing scene squashes away horizontally, then the incoming scene grows in from
         * nothing -- this port's 2D stand-in for Apple's 3D flip.
         */
        public fun flipHorizontal(duration: Duration): SKTransition =
            SKTransition(SKTransitionKind.FlipHorizontal, duration)

        /**
         * The outgoing scene squashes away vertically, then the incoming scene grows in from
         * nothing -- this port's 2D stand-in for Apple's 3D flip.
         */
        public fun flipVertical(duration: Duration): SKTransition =
            SKTransition(SKTransitionKind.FlipVertical, duration)
    }
}

/** Which built-in effect an [SKTransition] instance was configured with — not part of the public API. */
internal enum class SKTransitionKind {
    Fade,
    CrossFade,
    MoveIn,
    Push,
    Reveal,
    Doorway,
    DoorsOpenHorizontal,
    DoorsOpenVertical,
    DoorsCloseHorizontal,
    DoorsCloseVertical,
    FlipHorizontal,
    FlipVertical,
}
