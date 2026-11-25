package jp.co.bitz.spritekit

import kotlin.math.PI

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
 * The `particleXxxSequence` properties replace their `particleXxx`/`Range`/`Speed` settings with a
 * value over each particle's life, sampled at the fraction of its lifetime it has lived (keyframe
 * times past `1` are never reached). [targetNode] keeps particles in another node's coordinates, so
 * they stay where they were born when the emitter moves. The defaults, and what Apple's emitter does
 * and doesn't draw, are as recorded on iOS/tvOS simulators — see `docs/API_COMPATIBILITY.md`.
 */
public class SKEmitterNode : SKNode() {
    /**
     * The texture each particle renders with. `null` renders a flat-colored quad, like an
     * untextured [SKSpriteNode].
     */
    public var particleTexture: SKTexture? = null

    /**
     * Each particle's starting size — Apple's `particleSize`. Defaults to [Vector2.Zero], which
     * (as on Apple) means "use [particleTexture]'s own [SKTexture.size]"; an untextured emitter
     * left at [Vector2.Zero] therefore draws nothing, again matching Apple.
     */
    public var particleSize: Vector2 = Vector2.Zero

    /** [particleSize], or [particleTexture]'s size when [particleSize] is left at [Vector2.Zero]. */
    internal fun effectiveParticleSize(): Vector2 =
        if (particleSize != Vector2.Zero) particleSize else particleTexture?.size() ?: Vector2.Zero

    /** New particles emitted per second. `0` (the default) emits nothing. */
    public var particleBirthRate: Float = 0f

    /** Total particles to ever emit; `0` (the default) means unlimited (governed by [particleBirthRate] alone). */
    public var numParticlesToEmit: Int = 0

    /**
     * How long (in seconds) a particle lives before being removed. Defaults to `0`, as on Apple: a
     * particle with no lifetime is never drawn.
     */
    public var particleLifetime: Float = 0f

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
     * particles are emitted in. Defaults to `π/2` (straight up), as on Apple.
     */
    public var emissionAngle: Float = (PI / 2).toFloat()

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

    /**
     * A particle's opacity over its life, replacing [particleAlpha], [particleAlphaRange] and
     * [particleAlphaSpeed] while set. `null` by default.
     */
    public var particleAlphaSequence: SKKeyframeSequence<Float>? = null

    /** A new particle's initial scale multiplier, applied on top of [particleSize]. Defaults to `1`. */
    public var particleScale: Float = 1f

    /** Random variance applied to [particleScale] for each new particle. */
    public var particleScaleRange: Float = 0f

    /** Per-second rate of change applied to a particle's scale over its lifetime. */
    public var particleScaleSpeed: Float = 0f

    /**
     * A particle's scale over its life, replacing [particleScale], [particleScaleRange] and
     * [particleScaleSpeed] while set. `null` by default.
     */
    public var particleScaleSequence: SKKeyframeSequence<Float>? = null

    /** A new particle's initial rotation, in radians. */
    public var particleRotation: Float = 0f

    /** Random variance applied to [particleRotation] for each new particle. */
    public var particleRotationRange: Float = 0f

    /** Per-second rate of change (in radians) applied to a particle's rotation over its lifetime. */
    public var particleRotationSpeed: Float = 0f

    /**
     * A particle's tint color (ARGB, matching [android.graphics.Color]) — ignored while
     * [particleColorSequence] is set. Defaults to transparent white, as on Apple, which only shows
     * once [particleColorBlendFactor] is raised.
     */
    public var particleColor: Int = 0x00FFFFFF

    /**
     * How far a particle's color *and* opacity move from opaque white toward [particleColor]'s, the
     * way Apple mixes them: red at alpha `0.5` blended by `0.5` draws `(1, 0.5, 0.5)` at alpha
     * `0.75`. Defaults to `0`, so the particle keeps its texture's colors.
     */
    public var particleColorBlendFactor: Float = 0f

    /** Random variance applied to [particleColorBlendFactor] for each new particle. */
    public var particleColorBlendFactorRange: Float = 0f

    /** Per-second rate of change applied to a particle's color blend factor over its lifetime. */
    public var particleColorBlendFactorSpeed: Float = 0f

    /**
     * A particle's blend factor over its life, replacing [particleColorBlendFactor],
     * [particleColorBlendFactorRange] and [particleColorBlendFactorSpeed] while set. `null` by
     * default.
     */
    public var particleColorBlendFactorSequence: SKKeyframeSequence<Float>? = null

    /**
     * A particle's color over its life, replacing [particleColor] while set — only the color: the
     * blend factor still decides how much of it shows, as on Apple. `null` by default.
     */
    public var particleColorSequence: SKKeyframeSequence<Int>? = null

    /** How a particle's pixels combine with what's already drawn. Defaults to [SKBlendMode.Alpha]. */
    public var particleBlendMode: SKBlendMode = SKBlendMode.Alpha

    /**
     * A new particle's z-position, added to this node's own effective one: it orders particles
     * against other nodes.
     */
    public var particleZPosition: Float = 0f

    /** Random variance applied to [particleZPosition] for each new particle. */
    public var particleZPositionRange: Float = 0f

    /**
     * Accepted for API parity with no effect: on Apple, a particle's z-position doesn't change over
     * its life — it neither reorders particles nor moves them past other nodes.
     */
    public var particleZPositionSpeed: Float = 0f

    /**
     * Accepted for API parity with no effect: in every order, a newer particle is drawn on top of
     * an older one, which is what Apple draws for all three. Defaults to
     * [SKParticleRenderOrder.OldestLast], as on Apple.
     */
    public var particleRenderOrder: SKParticleRenderOrder = SKParticleRenderOrder.OldestLast

    /**
     * Accepted for API parity with no effect: Apple runs a particle action on no particle that
     * iOS/tvOS simulators draw (moves, scales, fades and colorizes all leave particles as they were).
     * `null` by default.
     */
    public var particleAction: SKAction? = null

    /**
     * The node whose coordinates new particles live in, or `null` (the default) for this emitter's
     * own. With a target, a particle is born where the emitter is but then belongs to the target:
     * it stays put when the emitter moves and moves with the target; it's drawn with the target's
     * transform and opacity, not the emitter's (hiding or fading the emitter leaves it showing),
     * and its emission angle, speed, accelerations and size are in the target's coordinates,
     * ignoring the emitter's rotation and scale. Removing the emitter from its scene removes its
     * particles from view. Apple holds the target weakly; this holds it strongly.
     */
    public var targetNode: SKNode? = null

    /**
     * Which [SKFieldNode.categoryBitMask] categories affect this emitter's particles. Defaults to
     * `0` (unaffected by any field), matching Apple.
     */
    public var fieldBitMask: Int = 0

    /**
     * A copy with the same settings, starting with no particles — see [SKNode.copy].
     */
    override fun copy(): SKEmitterNode = copyInto(SKEmitterNode())

    override fun copyState(target: SKNode) {
        super.copyState(target)
        if (target !is SKEmitterNode) return
        target.particleTexture = particleTexture
        target.particleSize = particleSize
        target.particleBirthRate = particleBirthRate
        target.numParticlesToEmit = numParticlesToEmit
        target.particleLifetime = particleLifetime
        target.particleLifetimeRange = particleLifetimeRange
        target.particlePositionRange = particlePositionRange
        target.particleSpeed = particleSpeed
        target.particleSpeedRange = particleSpeedRange
        target.emissionAngle = emissionAngle
        target.emissionAngleRange = emissionAngleRange
        target.xAcceleration = xAcceleration
        target.yAcceleration = yAcceleration
        target.particleAlpha = particleAlpha
        target.particleAlphaRange = particleAlphaRange
        target.particleAlphaSpeed = particleAlphaSpeed
        target.particleAlphaSequence = particleAlphaSequence
        target.particleScale = particleScale
        target.particleScaleRange = particleScaleRange
        target.particleScaleSpeed = particleScaleSpeed
        target.particleScaleSequence = particleScaleSequence
        target.particleRotation = particleRotation
        target.particleRotationRange = particleRotationRange
        target.particleRotationSpeed = particleRotationSpeed
        target.particleColor = particleColor
        target.particleColorBlendFactor = particleColorBlendFactor
        target.particleColorBlendFactorRange = particleColorBlendFactorRange
        target.particleColorBlendFactorSpeed = particleColorBlendFactorSpeed
        target.particleColorBlendFactorSequence = particleColorBlendFactorSequence
        target.particleColorSequence = particleColorSequence
        target.particleBlendMode = particleBlendMode
        target.particleZPosition = particleZPosition
        target.particleZPositionRange = particleZPositionRange
        target.particleZPositionSpeed = particleZPositionSpeed
        target.fieldBitMask = fieldBitMask
        target.particleRenderOrder = particleRenderOrder
        target.particleAction = particleAction
        target.targetNode = targetNode
    }

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
 * One simulated particle's mutable state, in [space]: its emitting [SKEmitterNode]'s local space,
 * or the emitter's [SKEmitterNode.targetNode]'s if it had one when the particle was born. Not part
 * of the public API.
 *
 * One parameter per animatable attribute's `initial`/`Speed` pair, mirroring [SKEmitterNode]'s own
 * `particleXxx`/`particleXxxSpeed` shape -- a legitimately wide constructor, not a design smell.
 */
@Suppress("LongParameterList")
internal class SKParticle(
    val space: SKNode,
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
    val zPosition: Float,
    var age: Float = 0f,
) {
    /** The scale [initialScale] and [scaleSpeed] give at [age], for an emitter with no scale sequence. */
    fun scale(): Float = initialScale + scaleSpeed * age

    /** The opacity [initialAlpha] and [alphaSpeed] give at [age], for an emitter with no alpha sequence. */
    fun alpha(): Float = initialAlpha + alphaSpeed * age

    /** The blend factor its initial value and speed give at [age], for an emitter with no blend-factor sequence. */
    fun colorBlendFactor(): Float = initialColorBlendFactor + colorBlendFactorSpeed * age
}

/**
 * The order an [SKEmitterNode] is asked to draw its particles in, mirroring Apple's
 * `SKParticleRenderOrder` — see [SKEmitterNode.particleRenderOrder].
 */
public enum class SKParticleRenderOrder {
    /** Oldest particles drawn last. The default, as on Apple. */
    OldestLast,

    /** Oldest particles drawn first. */
    OldestFirst,

    /** Any order. */
    DontCare,
}
