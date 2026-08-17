package jp.co.bitz.spritekit

import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/**
 * A single, reusable "recipe" for animating a node over time — mirrors Apple's `SKAction`.
 * Create one via this class's companion factory functions (matching Apple's static
 * `SKAction.moveTo` etc.), then run it with [SKNode.run]. The same instance can be run on
 * multiple nodes, or multiple times on the same node — all per-run state (elapsed time, captured
 * starting values) lives in the render thread's internal action-execution state
 * ([SKActionState]), not on this object.
 *
 * All actions execute on [SKView]'s render thread, evaluated once per frame, before physics and
 * constraints — see `docs/ARCHITECTURE.md`.
 */
public class SKAction internal constructor(
    internal val duration: Duration,
    internal val kind: SKActionKind,
) {
    /** How this action's progress eases over [duration]. Ignored if [timingFunction] is set. */
    public var timingMode: SKActionTimingMode = SKActionTimingMode.Linear

    /** A custom easing curve (input/output both `0..1`), overriding [timingMode] if set. */
    public var timingFunction: ((Float) -> Float)? = null

    /** A speed multiplier for how fast this action's time passes. `2` runs twice as fast; `0.5` runs at half speed. */
    public var speed: Float = 1f

    /** A new action that undoes this one's effect, where meaningful — see `docs/API_COMPATIBILITY.md`. */
    public fun reversed(): SKAction =
        SKAction(duration, kind.reversed()).also {
            it.timingMode = timingMode
            it.timingFunction = timingFunction
            it.speed = speed
        }

    public companion object {
        public fun moveTo(
            position: Vector2,
            duration: Duration,
        ): SKAction = SKAction(duration, SKActionKind.MoveTo(position))

        public fun moveBy(
            delta: Vector2,
            duration: Duration,
        ): SKAction = SKAction(duration, SKActionKind.MoveBy(delta))

        public fun scaleTo(
            scale: Float,
            duration: Duration,
        ): SKAction = scaleTo(scale, scale, duration)

        public fun scaleTo(
            xScale: Float,
            yScale: Float,
            duration: Duration,
        ): SKAction = SKAction(duration, SKActionKind.ScaleTo(xScale, yScale))

        public fun scaleBy(
            scale: Float,
            duration: Duration,
        ): SKAction = scaleBy(scale, scale, duration)

        public fun scaleBy(
            dx: Float,
            dy: Float,
            duration: Duration,
        ): SKAction = SKAction(duration, SKActionKind.ScaleBy(dx, dy))

        /** Rotates to [angle] (radians) by the shortest angular path. */
        public fun rotateTo(
            angle: Float,
            duration: Duration,
        ): SKAction = SKAction(duration, SKActionKind.RotateTo(angle))

        public fun rotateBy(
            delta: Float,
            duration: Duration,
        ): SKAction = SKAction(duration, SKActionKind.RotateBy(delta))

        /** No-op on nodes other than [SKSpriteNode] (the only node type with a mutable [SKSpriteNode.size]). */
        public fun resizeTo(
            size: Vector2,
            duration: Duration,
        ): SKAction = SKAction(duration, SKActionKind.ResizeTo(size))

        /** No-op on nodes other than [SKSpriteNode]. */
        public fun resizeBy(
            delta: Vector2,
            duration: Duration,
        ): SKAction = SKAction(duration, SKActionKind.ResizeBy(delta))

        public fun fadeIn(duration: Duration): SKAction = fadeAlphaTo(1f, duration)

        public fun fadeOut(duration: Duration): SKAction = fadeAlphaTo(0f, duration)

        /**
         * Renamed from Apple's overloaded `fadeAlpha(to:duration:)`/`fadeAlpha(by:duration:)` —
         * Swift argument labels aren't significant for Kotlin overload resolution, so both would
         * collide as a single `fadeAlpha(Float, Duration)` overload (same rename pattern as
         * `SKNode.convertTo`/`convertFrom`; see `docs/API_COMPATIBILITY.md`).
         */
        public fun fadeAlphaTo(
            alpha: Float,
            duration: Duration,
        ): SKAction = SKAction(duration, SKActionKind.FadeAlphaTo(alpha))

        public fun fadeAlphaBy(
            delta: Float,
            duration: Duration,
        ): SKAction = SKAction(duration, SKActionKind.FadeAlphaBy(delta))

        public fun hide(): SKAction = SKAction(Duration.ZERO, SKActionKind.Hide)

        public fun unhide(): SKAction = SKAction(Duration.ZERO, SKActionKind.Unhide)

        /** No-op on nodes other than [SKSpriteNode]. */
        public fun colorize(
            color: Int,
            colorBlendFactor: Float,
            duration: Duration,
        ): SKAction = SKAction(duration, SKActionKind.Colorize(color, colorBlendFactor))

        /**
         * Animates [SKSpriteNode.colorBlendFactor] only, leaving [SKSpriteNode.color] unchanged.
         * No-op on other nodes.
         */
        public fun colorize(
            colorBlendFactor: Float,
            duration: Duration,
        ): SKAction = SKAction(duration, SKActionKind.Colorize(null, colorBlendFactor))

        public fun wait(duration: Duration): SKAction = SKAction(duration, SKActionKind.Wait)

        /**
         * Waits a random duration in `[duration - range/2, duration + range/2]`.
         *
         * Deviation: the random duration is picked once, when this action is created. Apple
         * re-randomizes on every run of a reused action instance (e.g. inside a
         * [repeatForever]); this library doesn't, for simplicity — see
         * `docs/API_COMPATIBILITY.md`.
         */
        public fun wait(
            duration: Duration,
            withRange: Duration,
        ): SKAction {
            val halfRangeNanos = withRange.inWholeNanoseconds / 2
            val jitterNanos = if (halfRangeNanos > 0) Random.nextLong(-halfRangeNanos, halfRangeNanos + 1) else 0L
            val resolvedNanos = (duration.inWholeNanoseconds + jitterNanos).coerceAtLeast(0L)
            return SKAction(resolvedNanos.nanoseconds, SKActionKind.Wait)
        }

        /** Runs [block] once, taking no time. */
        public fun run(block: () -> Unit): SKAction = SKAction(Duration.ZERO, SKActionKind.RunBlock(block))

        public fun removeFromParent(): SKAction = SKAction(Duration.ZERO, SKActionKind.RemoveFromParent)

        /** Runs [actions] one after another, each starting once the previous finishes. */
        public fun sequence(actions: List<SKAction>): SKAction {
            val total = actions.fold(Duration.ZERO) { acc, action -> acc + action.duration }
            return SKAction(total, SKActionKind.Sequence(actions))
        }

        /** Runs [actions] concurrently; finishes once every one of them has. */
        public fun group(actions: List<SKAction>): SKAction {
            val total = actions.maxOfOrNull { it.duration } ?: Duration.ZERO
            return SKAction(total, SKActionKind.Group(actions))
        }

        public fun repeat(
            action: SKAction,
            count: Int,
        ): SKAction = SKAction(action.duration * count, SKActionKind.Repeat(action, count))

        public fun repeatForever(action: SKAction): SKAction =
            SKAction(
                Duration.INFINITE,
                SKActionKind.Repeat(action, null),
            )

        /**
         * [block] is called once per frame with the elapsed time so far (`0` to [duration]), for
         * custom per-frame animation.
         */
        public fun customAction(
            duration: Duration,
            block: (SKNode, Duration) -> Unit,
        ): SKAction = SKAction(duration, SKActionKind.Custom(block))

        /**
         * Steps [SKSpriteNode.texture] through [textures], holding each for [timePerFrame]. If
         * [restore] is `true`, the sprite's original texture (from before this action started)
         * is restored once it finishes. No-op on nodes other than [SKSpriteNode].
         *
         * Deviation: Apple's `resize` parameter (also resizing the sprite to match each
         * texture's pixel dimensions) isn't implemented, for the same reason
         * [SKSpriteNode.size] doesn't auto-size from a texture in the first place — see
         * `docs/API_COMPATIBILITY.md`.
         */
        public fun animate(
            textures: List<SKTexture>,
            timePerFrame: Duration,
            restore: Boolean = false,
        ): SKAction = SKAction(timePerFrame * textures.size, SKActionKind.Animate(textures, timePerFrame, restore))

        /** Starts (or resumes) playback. No-op on nodes other than [SKAudioNode]. */
        public fun play(): SKAction = SKAction(Duration.ZERO, SKActionKind.Play)

        /** Pauses playback in place. No-op on nodes other than [SKAudioNode]. */
        public fun pause(): SKAction = SKAction(Duration.ZERO, SKActionKind.Pause)

        /** Stops playback and rewinds to the start. No-op on nodes other than [SKAudioNode]. */
        public fun stop(): SKAction = SKAction(Duration.ZERO, SKActionKind.Stop)

        /** Animates [SKAudioNode.volume] to [to] over [duration]. No-op on other nodes. */
        public fun changeVolume(
            to: Float,
            duration: Duration,
        ): SKAction = SKAction(duration, SKActionKind.ChangeVolumeTo(to))

        /** Animates [SKAudioNode.playbackRate] to [to] over [duration]. No-op on other nodes. */
        public fun changePlaybackRate(
            to: Float,
            duration: Duration,
        ): SKAction = SKAction(duration, SKActionKind.ChangePlaybackRateTo(to))

        /**
         * Plays the clip at [fileNamed] once, fire-and-forget — independent of any [SKAudioNode]
         * (runnable on any node). If [waitForCompletion] is `true`, this action doesn't finish
         * until playback actually completes; its [SKAction.duration] is always reported as `0`
         * regardless, since the clip's real length isn't known ahead of time — see
         * `docs/API_COMPATIBILITY.md`.
         */
        public fun playSoundFileNamed(
            fileNamed: String,
            waitForCompletion: Boolean,
        ): SKAction = SKAction(Duration.ZERO, SKActionKind.PlaySoundFileNamed(fileNamed, waitForCompletion))
    }
}
