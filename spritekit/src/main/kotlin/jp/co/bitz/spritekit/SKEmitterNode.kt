package jp.co.bitz.spritekit

import android.graphics.Color

/**
 * A node that emits and simulates a stream of small textured quads ("particles") — mirrors
 * Apple's `SKEmitterNode`. Configured programmatically (no `.sks` particle-editor archive format
 * to parse — see `docs/API_COMPATIBILITY.md`); simulated once per frame by [SKView]'s frame loop,
 * rendered by reusing [SKSceneRenderer]'s existing textured-quad batching (the same path
 * [SKSpriteNode] uses).
 *
 * Every `particleXxx`/`particleXxxRange` pair works like Apple's: a new particle's actual value is
 * drawn uniformly from `[particleXxx - particleXxxRange/2, particleXxx + particleXxxRange/2]`.
 * Every `particleXxxSpeed` is a per-second rate of change applied over each particle's lifetime
 * (e.g. `particleAlphaSpeed` fades a particle in/out over time).
 *
 * [particleSize] has no Apple equivalent — Apple auto-sizes each particle from [particleTexture]'s
 * pixel dimensions, but reading a `Bitmap`'s dimensions is intentionally kept out of this port's
 * otherwise-pure-Kotlin node/config classes (the same reason [SKSpriteNode.size] must be set
 * explicitly instead of inferred from its texture) — see `docs/API_COMPATIBILITY.md`.
 * Apple's `targetNode` (reparenting newly-born particles into another node's space so they don't
 * move with the emitter) and [particleColorSequence]'s sibling `Sequence` properties for
 * scale/rotation/alpha aren't implemented — see `docs/API_COMPATIBILITY.md`.
 */
public class SKEmitterNode : SKNode() {
    /**
     * The texture each particle renders with. `null` renders a flat-colored quad, like an
     * untextured [SKSpriteNode].
     */
    public var particleTexture: SKTexture? = null

    /**
     * Each particle's rendered size — see this class's docs for why this has no Apple equivalent.
     * Defaults to `32x32`.
     */
    public var particleSize: Vector2 = Vector2(32f, 32f)

    /** New particles emitted per second. `0` (the default) emits nothing. */
    public var particleBirthRate: Float = 0f

    /** Total particles to ever emit; `0` (the default) means unlimited (governed by [particleBirthRate] alone). */
    public var numParticlesToEmit: Int = 0

    /** How long (in seconds) a particle lives before being removed. Defaults to `1`. */
    public var particleLifetime: Float = 1f

    /** Random variance applied to [particleLifetime] for each new particle. */
    public var particleLifetimeRange: Float = 0f

    /** Random variance in a new particle's birth position, in this node's local space, on each axis independently. */
    public var particlePositionRange: Vector2 = Vector2.Zero

    /** A new particle's initial speed, in points per second, along [emissionAngle]. */
    public var particleSpeed: Float = 0f

    /** Random variance applied to [particleSpeed] for each new particle. */
    public var particleSpeedRange: Float = 0f

    /**
     * The direction (in radians, `0` along `+x`, matching [SKNode.zRotation]'s convention) new
     * particles are emitted in.
     */
    public var emissionAngle: Float = 0f

    /** Random variance applied to [emissionAngle] for each new particle. */
    public var emissionAngleRange: Float = 0f

    /** Constant horizontal acceleration applied to every particle throughout its life, in this node's local space. */
    public var xAcceleration: Float = 0f

    /** Constant vertical acceleration applied to every particle throughout its life, in this node's local space. */
    public var yAcceleration: Float = 0f

    /** A new particle's initial opacity. Defaults to `1`. */
    public var particleAlpha: Float = 1f

    /** Random variance applied to [particleAlpha] for each new particle. */
    public var particleAlphaRange: Float = 0f

    /** Per-second rate of change applied to a particle's opacity over its lifetime. */
    public var particleAlphaSpeed: Float = 0f

    /** A new particle's initial scale multiplier, applied on top of [particleSize]. Defaults to `1`. */
    public var particleScale: Float = 1f

    /** Random variance applied to [particleScale] for each new particle. */
    public var particleScaleRange: Float = 0f

    /** Per-second rate of change applied to a particle's scale over its lifetime. */
    public var particleScaleSpeed: Float = 0f

    /** A new particle's initial rotation, in radians. */
    public var particleRotation: Float = 0f

    /** Random variance applied to [particleRotation] for each new particle. */
    public var particleRotationRange: Float = 0f

    /** Per-second rate of change (in radians) applied to a particle's rotation over its lifetime. */
    public var particleRotationSpeed: Float = 0f

    /**
     * A particle's tint color (ARGB, matching [android.graphics.Color]) — ignored while
     * [particleColorSequence] is set. Defaults to white.
     */
    public var particleColor: Int = Color.WHITE

    /** How much [particleColor] tints [particleTexture]'s own colors, like [SKSpriteNode.colorBlendFactor]. */
    public var particleColorBlendFactor: Float = 0f

    /** Random variance applied to [particleColorBlendFactor] for each new particle. */
    public var particleColorBlendFactorRange: Float = 0f

    /** Per-second rate of change applied to a particle's color blend factor over its lifetime. */
    public var particleColorBlendFactorSpeed: Float = 0f

    /**
     * A color-over-lifetime ramp overriding [particleColor]/[particleColorBlendFactor] entirely
     * while set. `null` by default.
     */
    public var particleColorSequence: SKKeyframeSequence<Int>? = null

    /** How a particle's pixels combine with what's already drawn. Defaults to [SKBlendMode.Alpha]. */
    public var particleBlendMode: SKBlendMode = SKBlendMode.Alpha

    /** A new particle's initial [SKNode.zPosition]-equivalent draw ordering. */
    public var particleZPosition: Float = 0f

    /** Random variance applied to [particleZPosition] for each new particle. */
    public var particleZPositionRange: Float = 0f

    /** Per-second rate of change applied to a particle's z-position over its lifetime. */
    public var particleZPositionSpeed: Float = 0f

    /**
     * Which [SKFieldNode.categoryBitMask] categories affect this emitter's particles. Defaults to
     * `0` (unaffected by any field), matching Apple.
     */
    public var fieldBitMask: Int = 0

    internal val particles: MutableList<SKParticle> = mutableListOf()
    internal var emissionAccumulator: Float = 0f
    internal var totalEmitted: Int = 0

    /**
     * Advances this emitter's simulation by [seconds] immediately, e.g. to "pre-warm" it so it
     * isn't empty right after being added to a scene.
     */
    public fun advanceSimulationTime(seconds: Float) {
        advanceEmitterSimulationTime(this, seconds)
    }

    /** Removes every currently-alive particle and resets emission counters, as if this emitter had just started. */
    public fun resetSimulation() {
        particles.clear()
        emissionAccumulator = 0f
        totalEmitted = 0
    }
}

/**
 * One simulated particle's mutable state, in its emitting [SKEmitterNode]'s local space. Not part
 * of the public API.
 *
 * One parameter per animatable attribute's `initial`/`Speed` pair, mirroring [SKEmitterNode]'s own
 * `particleXxx`/`particleXxxSpeed` shape -- a legitimately wide constructor, not a design smell.
 */
@Suppress("LongParameterList")
internal class SKParticle(
    var position: Vector2,
    var velocity: Vector2,
    val lifetime: Float,
    val initialAlpha: Float,
    val alphaSpeed: Float,
    val initialScale: Float,
    val scaleSpeed: Float,
    val initialRotation: Float,
    val rotationSpeed: Float,
    val initialColorBlendFactor: Float,
    val colorBlendFactorSpeed: Float,
    val initialZPosition: Float,
    val zPositionSpeed: Float,
    var age: Float = 0f,
)
