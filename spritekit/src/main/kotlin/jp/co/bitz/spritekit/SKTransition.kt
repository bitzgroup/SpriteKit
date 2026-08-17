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
 * overall alpha multiplier, and — for [doorway] — a plain rectangular clip. [flipHorizontal]/
 * [flipVertical] approximate Apple's true 3D flip as a 2D squash-then-grow, since this renderer
 * has no 3D perspective transform either. All *contract-conformant, not bit-identical* with
 * Apple's own effects — see `docs/API_COMPATIBILITY.md`.
 */
public class SKTransition private constructor(
    internal val kind: SKTransitionKind,
    public val duration: Duration,
    public val direction: SKTransitionDirection = SKTransitionDirection.Right,
    public val color: Int = Color.BLACK,
) {
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
         * incoming scene underneath, like double doors opening.
         */
        public fun doorway(duration: Duration): SKTransition = SKTransition(SKTransitionKind.Doorway, duration)

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
    FlipHorizontal,
    FlipVertical,
}
