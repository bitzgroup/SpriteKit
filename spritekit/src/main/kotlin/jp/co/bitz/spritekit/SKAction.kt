package jp.co.bitz.spritekit

import android.graphics.Path
import android.graphics.PathMeasure
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

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
    duration: Duration,
    internal val kind: SKActionKind,
) {
    /**
     * How long this action takes to run, at a [speed] of `1`. A [sequence]'s, [group]'s or
     * [repeat]'s is derived from its actions when it's created. Assigning it changes how long a
     * single action runs, as on Apple; assigning it on a [sequence]/[group]/[repeat] changes only
     * the reported value, not how long it runs — Apple's does the same (verified on iOS/tvOS
     * simulators).
     */
    public var duration: Duration = duration

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
        /** Moves this node to [position] (in its parent's coordinate space) over [duration]. */
        public fun moveTo(
            position: Vector2,
            duration: Duration,
        ): SKAction = SKAction(duration, SKActionKind.MoveTo(position))

        /** Moves this node's x coordinate to [x], leaving y alone — Apple's `moveTo(x:duration:)`. */
        public fun moveToX(
            x: Float,
            duration: Duration,
        ): SKAction = SKAction(duration, SKActionKind.MoveToAxis(x = x, y = null))

        /** Moves this node's y coordinate to [y], leaving x alone — Apple's `moveTo(y:duration:)`. */
        public fun moveToY(
            y: Float,
            duration: Duration,
        ): SKAction = SKAction(duration, SKActionKind.MoveToAxis(x = null, y = y))

        /** Moves this node by [delta], relative to wherever it is when this action starts, over [duration]. */
        public fun moveBy(
            delta: Vector2,
            duration: Duration,
        ): SKAction = SKAction(duration, SKActionKind.MoveBy(delta))

        /** Scales this node uniformly to [scale] on both axes over [duration]. */
        public fun scaleTo(
            scale: Float,
            duration: Duration,
        ): SKAction = scaleTo(scale, scale, duration)

        /** Scales this node to [xScale]/[yScale] independently over [duration]. */
        public fun scaleTo(
            xScale: Float,
            yScale: Float,
            duration: Duration,
        ): SKAction = SKAction(duration, SKActionKind.ScaleTo(xScale, yScale))

        /** Scales [SKNode.xScale] to [xScale], leaving `yScale` alone — Apple's `scaleX(to:duration:)`. */
        public fun scaleXTo(
            xScale: Float,
            duration: Duration,
        ): SKAction = SKAction(duration, SKActionKind.ScaleToAxis(xScale = xScale, yScale = null))

        /** Scales [SKNode.yScale] to [yScale], leaving `xScale` alone — Apple's `scaleY(to:duration:)`. */
        public fun scaleYTo(
            yScale: Float,
            duration: Duration,
        ): SKAction = SKAction(duration, SKActionKind.ScaleToAxis(xScale = null, yScale = yScale))

        /**
         * Scales an [SKSpriteNode] to whatever `xScale`/`yScale` give it [size] — Apple's
         * `scale(to: CGSize, duration:)`, the animated form of [SKSpriteNode.scale]. The target scale
         * is worked out from the sprite's unscaled size, so its current scale doesn't matter. No-op
         * on nodes other than [SKSpriteNode], as on Apple (verified on iOS/tvOS simulators).
         */
        public fun scaleTo(
            size: Vector2,
            duration: Duration,
        ): SKAction = SKAction(duration, SKActionKind.ScaleToSize(size))

        /**
         * Multiplies this node's scale by [scale] on both axes over [duration] — Apple's
         * `scale(by:duration:)`, which is multiplicative (verified on iOS/tvOS simulators).
         */
        public fun scaleBy(
            scale: Float,
            duration: Duration,
        ): SKAction = scaleBy(scale, scale, duration)

        /**
         * Multiplies [SKNode.xScale] by [dx] and [SKNode.yScale] by [dy] over [duration] — Apple's
         * `scaleX(by:y:duration:)`. [reversed] divides by the same factors.
         */
        public fun scaleBy(
            dx: Float,
            dy: Float,
            duration: Duration,
        ): SKAction = SKAction(duration, SKActionKind.ScaleBy(dx, dy))

        /**
         * Rotates to [angle] (radians), ending exactly on it — Apple's
         * `rotate(toAngle:duration:shortestUnitArc:)`. By default (as on Apple) the rotation is
         * interpolated straight from the current [SKNode.zRotation] to [angle], so going from `0.1`
         * to `6` turns almost a full circle. With [shortestUnitArc] it turns whichever way is
         * shorter instead — still ending on [angle] itself, not an equivalent angle. Verified on
         * iOS/tvOS simulators.
         */
        public fun rotateTo(
            angle: Float,
            duration: Duration,
            shortestUnitArc: Boolean = false,
        ): SKAction = SKAction(duration, SKActionKind.RotateTo(angle, shortestUnitArc))

        /** Rotates by [delta] radians, relative to this node's current rotation, over [duration]. */
        public fun rotateBy(
            delta: Float,
            duration: Duration,
        ): SKAction = SKAction(duration, SKActionKind.RotateBy(delta))

        /** No-op on nodes other than [SKSpriteNode] (the only node type with a mutable [SKSpriteNode.size]). */
        public fun resizeTo(
            size: Vector2,
            duration: Duration,
        ): SKAction = SKAction(duration, SKActionKind.ResizeTo(size))

        /** Resizes an [SKSpriteNode]'s width to [width], leaving its height — Apple's `resize(toWidth:duration:)`. */
        public fun resizeToWidth(
            width: Float,
            duration: Duration,
        ): SKAction = SKAction(duration, SKActionKind.ResizeToAxis(width = width, height = null))

        /** Resizes an [SKSpriteNode]'s height to [height], leaving its width — Apple's `resize(toHeight:duration:)`. */
        public fun resizeToHeight(
            height: Float,
            duration: Duration,
        ): SKAction = SKAction(duration, SKActionKind.ResizeToAxis(width = null, height = height))

        /** No-op on nodes other than [SKSpriteNode]. */
        public fun resizeBy(
            delta: Vector2,
            duration: Duration,
        ): SKAction = SKAction(duration, SKActionKind.ResizeBy(delta))

        /**
         * Moves this node along [path] over [duration]. Only the first contour of [path] is
         * followed — see `docs/API_COMPATIBILITY.md`.
         *
         * If [asOffset] is `true`, [path]'s points are relative to this node's position when the
         * action starts (so a path built around the origin can be reused at any position); if
         * `false`, they're absolute coordinates in the node's parent, and the node jumps straight
         * to the path's start point the moment the action begins.
         *
         * If [orientToPath] is `true`, [SKNode.zRotation] is continuously set to face the
         * direction of travel along the path (`0` pointing along the positive x-axis, matching
         * how zero rotation is interpreted everywhere else in this library).
         */
        public fun follow(
            path: Path,
            asOffset: Boolean,
            orientToPath: Boolean,
            duration: Duration,
        ): SKAction = SKAction(duration, SKActionKind.Follow(path, asOffset, orientToPath))

        /**
         * Like [follow], but [speed] (parent-space units per second) determines the duration
         * instead of taking one directly — the length of [path]'s first contour divided by
         * [speed]. A non-positive [speed] (or a path with no measurable length) produces a
         * zero-duration action that jumps straight to the path's end.
         */
        public fun follow(
            path: Path,
            asOffset: Boolean,
            orientToPath: Boolean,
            speed: Float,
        ): SKAction {
            val length = PathMeasure(path, false).length
            val duration = if (speed > 0f && length > 0f) (length / speed).toDouble().seconds else Duration.ZERO
            return follow(path, asOffset, orientToPath, duration)
        }

        /** Fades [SKNode.alpha] to `1` (fully opaque) over [duration]. */
        public fun fadeIn(duration: Duration): SKAction = fadeAlphaTo(1f, duration)

        /** Fades [SKNode.alpha] to `0` (fully transparent) over [duration]. */
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

        /** Fades [SKNode.alpha] by [delta], relative to its current value, over [duration]. */
        public fun fadeAlphaBy(
            delta: Float,
            duration: Duration,
        ): SKAction = SKAction(duration, SKActionKind.FadeAlphaBy(delta))

        /** Sets [SKNode.isHidden] to `true`, instantly. */
        public fun hide(): SKAction = SKAction(Duration.ZERO, SKActionKind.Hide)

        /** Sets [SKNode.isHidden] to `false`, instantly. */
        public fun unhide(): SKAction = SKAction(Duration.ZERO, SKActionKind.Unhide)

        /**
         * Animates the `color` and `colorBlendFactor` of an [SKSpriteNode] or [SKLabelNode]
         * (Apple colorizes both). No-op on other nodes.
         */
        public fun colorize(
            color: Int,
            colorBlendFactor: Float,
            duration: Duration,
        ): SKAction = SKAction(duration, SKActionKind.Colorize(color, colorBlendFactor))

        /**
         * Animates `colorBlendFactor` only, leaving `color` unchanged, on an [SKSpriteNode] or
         * [SKLabelNode]. No-op on other nodes.
         */
        public fun colorize(
            colorBlendFactor: Float,
            duration: Duration,
        ): SKAction = SKAction(duration, SKActionKind.Colorize(null, colorBlendFactor))

        /** Does nothing for [duration], then finishes. */
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

        /**
         * Sets an [SKSpriteNode]'s [SKSpriteNode.texture] to [texture], instantly — Apple's
         * `setTexture(_:resize:)`. The sprite keeps its size unless [resize] is `true`, which sets
         * [SKSpriteNode.size] to the texture's [SKTexture.size]. No-op on other nodes.
         */
        public fun setTexture(
            texture: SKTexture,
            resize: Boolean = false,
        ): SKAction = SKAction(Duration.ZERO, SKActionKind.SetTexture(texture, resize))

        /** Sets [SKNode.speed] to [speed] over [duration] — Apple's `speed(to:duration:)`. */
        public fun speedTo(
            speed: Float,
            duration: Duration,
        ): SKAction = SKAction(duration, SKActionKind.SpeedTo(speed))

        /**
         * Multiplies [SKNode.speed] by [factor] over [duration] — Apple's `speed(by:duration:)`,
         * which is multiplicative (verified on iOS/tvOS simulators). [reversed] divides by it.
         */
        public fun speedBy(
            factor: Float,
            duration: Duration,
        ): SKAction = SKAction(duration, SKActionKind.SpeedBy(factor))

        /**
         * Pushes the node's [SKNode.physicsBody] with [force], as [SKPhysicsBody.applyForce] would
         * for [duration] — Apple's `applyForce(_:duration:)`. As Apple's does (verified on iOS/tvOS
         * simulators), it changes the body's velocity as each frame's actions run, by the force
         * times [duration] times how far the action's eased progress moved that frame, so a
         * [timingMode] shapes how the push builds up. [reversed] pushes the same way again, as
         * Apple's does. No-op on a node without a body.
         */
        public fun applyForce(
            force: Vector2,
            duration: Duration,
        ): SKAction = SKAction(duration, SKActionKind.ApplyForce(force, null))

        /**
         * [applyForce] at [at], a point in scene coordinates — Apple's
         * `applyForce(_:at:duration:)`. A point off the body's center of mass also turns it, as
         * [SKPhysicsBody.applyForce] at a point does.
         */
        public fun applyForce(
            force: Vector2,
            at: Vector2,
            duration: Duration,
        ): SKAction = SKAction(duration, SKActionKind.ApplyForce(force, at))

        /**
         * Turns the node's [SKNode.physicsBody] with [torque] for [duration], applied frame by
         * frame as [applyForce] applies a force — Apple's `applyTorque(_:duration:)`.
         */
        public fun applyTorque(
            torque: Float,
            duration: Duration,
        ): SKAction = SKAction(duration, SKActionKind.ApplyTorque(torque))

        /**
         * Applies [impulse] to the node's [SKNode.physicsBody], spread over [duration] by the
         * action's eased progress — Apple's `applyImpulse(_:duration:)`. A zero [duration] applies
         * nothing, as on Apple. [reversed] pushes the same way again, as Apple's does.
         */
        public fun applyImpulse(
            impulse: Vector2,
            duration: Duration,
        ): SKAction = SKAction(duration, SKActionKind.ApplyImpulse(impulse, null))

        /**
         * [applyImpulse] at [at], a point in scene coordinates — Apple's
         * `applyImpulse(_:at:duration:)`. A point off the body's center of mass also turns it.
         */
        public fun applyImpulse(
            impulse: Vector2,
            at: Vector2,
            duration: Duration,
        ): SKAction = SKAction(duration, SKActionKind.ApplyImpulse(impulse, at))

        /**
         * Applies the angular [impulse] to the node's [SKNode.physicsBody], spread over [duration]
         * as [applyImpulse] spreads its own — Apple's `applyAngularImpulse(_:duration:)`.
         */
        public fun applyAngularImpulse(
            impulse: Float,
            duration: Duration,
        ): SKAction = SKAction(duration, SKActionKind.ApplyAngularImpulse(impulse))

        /**
         * Changes the node's [SKPhysicsBody.mass] to [mass] over [duration] — Apple's
         * `changeMass(to:duration:)`. Like setting [SKPhysicsBody.mass], it changes the body's
         * density. Renamed, like [fadeAlphaTo], so it doesn't collide with [changeMassBy]. Its
         * [reversed] does nothing (Apple's traps). Apple traps on a mass of `0` or less, too.
         */
        public fun changeMassTo(
            mass: Float,
            duration: Duration,
        ): SKAction = SKAction(duration, SKActionKind.ChangeMass(mass, relative = false))

        /** Adds [delta] to the node's [SKPhysicsBody.mass] over [duration] — Apple's `changeMass(by:duration:)`. */
        public fun changeMassBy(
            delta: Float,
            duration: Duration,
        ): SKAction = SKAction(duration, SKActionKind.ChangeMass(delta, relative = true))

        /**
         * Changes the node's [SKPhysicsBody.charge] to [charge] over [duration] — Apple's
         * `changeCharge(to:duration:)`. Its [reversed] does nothing, as Apple's does.
         */
        public fun changeChargeTo(
            charge: Float,
            duration: Duration,
        ): SKAction = SKAction(duration, SKActionKind.ChangeCharge(charge, relative = false))

        /**
         * Adds [delta] to the node's [SKPhysicsBody.charge] over [duration] — Apple's
         * `changeCharge(by:duration:)`. Its [reversed] does nothing, as Apple's does.
         */
        public fun changeChargeBy(
            delta: Float,
            duration: Duration,
        ): SKAction = SKAction(duration, SKActionKind.ChangeCharge(delta, relative = true))

        /**
         * Changes an [SKFieldNode]'s [SKFieldNode.strength] to [strength] over [duration] —
         * Apple's `strength(to:duration:)`. Its [reversed] does nothing, as Apple's does. No-op on
         * other nodes.
         */
        public fun strengthTo(
            strength: Float,
            duration: Duration,
        ): SKAction = SKAction(duration, SKActionKind.StrengthTo(strength))

        /**
         * Multiplies an [SKFieldNode]'s [SKFieldNode.strength] by [factor] over [duration] —
         * Apple's `strength(by:duration:)`, which is multiplicative (verified on iOS/tvOS
         * simulators). [reversed] divides by it. No-op on other nodes.
         */
        public fun strengthBy(
            factor: Float,
            duration: Duration,
        ): SKAction = SKAction(duration, SKActionKind.StrengthBy(factor))

        /**
         * Apple's `falloff(to:duration:)`, which leaves [SKFieldNode.falloff] as it is on every
         * field kind and never finishes — even with a zero [duration], or on a node that isn't a
         * field — so a [sequence] or [group] holding it never finishes either (verified on
         * iOS/tvOS simulators). This one does the same. Set [SKFieldNode.falloff] directly instead.
         */
        @Suppress("UnusedParameter") // kept for Apple's signature; Apple ignores it too
        public fun falloffTo(
            falloff: Float,
            duration: Duration,
        ): SKAction = SKAction(duration, SKActionKind.Falloff)

        /** Apple's `falloff(by:duration:)`, which, like [falloffTo], changes nothing and never finishes. */
        @Suppress("UnusedParameter") // kept for Apple's signature; Apple ignores it too
        public fun falloffBy(
            factor: Float,
            duration: Duration,
        ): SKAction = SKAction(duration, SKActionKind.Falloff)

        /** Runs [block] once, taking no time. */
        public fun run(block: () -> Unit): SKAction = SKAction(Duration.ZERO, SKActionKind.RunBlock(block))

        /**
         * Starts [action] on the first node [onChildWithName] finds from the running node — the
         * full search syntax [SKNode.childNode] describes, so `"//enemy"` works — taking no time
         * itself; the found node's action keeps running on its own afterwards. Only the first
         * match runs it, and nothing happens if there's none. Mirrors Apple's
         * `run(_:onChildWithName:)`.
         */
        public fun run(
            action: SKAction,
            onChildWithName: String,
        ): SKAction = SKAction(Duration.ZERO, SKActionKind.RunOnChild(action, onChildWithName))

        /** Removes this node from its parent, instantly — see [SKNode.removeFromParent]. */
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

        /** Runs [action] [count] times in a row. */
        public fun repeat(
            action: SKAction,
            count: Int,
        ): SKAction = SKAction(action.duration * count, SKActionKind.Repeat(action, count))

        /** Runs [action] over and over, indefinitely — never finishes on its own. */
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
         * Steps [SKSpriteNode.texture] through [textures], holding each for [timePerFrame] —
         * Apple's `animate(with:timePerFrame:resize:restore:)`. If [resize] is `true`, the
         * sprite's [SKSpriteNode.size] follows each texture's [SKTexture.size]. If [restore] is
         * `true`, the sprite's original texture (from before this action started) is restored
         * once it finishes — resized back to that texture's size too, if [resize] is also `true`.
         * No-op on nodes other than [SKSpriteNode].
         */
        public fun animate(
            textures: List<SKTexture>,
            timePerFrame: Duration,
            resize: Boolean = false,
            restore: Boolean = false,
        ): SKAction =
            SKAction(
                timePerFrame * textures.size,
                SKActionKind.Animate(textures, timePerFrame, resize, restore),
            )

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
         * Plays the clip [fileNamed] once, fire-and-forget — independent of any [SKAudioNode]
         * (runnable on any node). Like Apple's, a plain file name (`"tap.mp3"`) is looked up among
         * the resources bundled with the app — the host app's `assets/` folder here; an absolute
         * file path or a URL is used as-is. If [waitForCompletion] is `true`, this action doesn't finish
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
