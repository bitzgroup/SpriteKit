package jp.co.bitz.spritekit

import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.DurationUnit

/**
 * A fixed step size for [advanceEmitterSimulationTime], so a large fast-forward still emits/ages
 * in reasonable increments.
 */
private const val ADVANCE_STEP_SECONDS = 1f / 60f

/**
 * Every [SKEmitterNode] in [node]'s subtree, skipping [SKNode.isPaused] subtrees -- the same
 * scope every other per-frame collector in this library uses.
 */
private fun collectEmitterNodes(
    node: SKNode,
    out: MutableList<SKEmitterNode> = mutableListOf(),
): List<SKEmitterNode> {
    if (node.isPaused) return out
    if (node is SKEmitterNode) out += node
    for (child in node.children) collectEmitterNodes(child, out)
    return out
}

/**
 * Steps every [SKEmitterNode] in [scene] by [deltaTime] -- called once per frame by [SKView],
 * after physics/constraints and before rendering. Ages/moves/removes existing particles (applying
 * [SKEmitterNode.xAcceleration]/[SKEmitterNode.yAcceleration] plus any matching [SKFieldNode]'s
 * force, via [SKEmitterNode.fieldBitMask]), then emits new ones per [SKEmitterNode.particleBirthRate].
 */
internal fun stepEmitters(
    scene: SKScene,
    deltaTime: Duration,
) {
    val dt = deltaTime.toDouble(DurationUnit.SECONDS).toFloat()
    if (dt <= 0f) return
    val emitters = collectEmitterNodes(scene)
    if (emitters.isEmpty()) return
    val fields = enabledFieldNodes(scene)

    for (emitter in emitters) {
        ageParticles(emitter, dt) { particle -> fieldAccelerationSum(emitter, fields, particle, scene) }
        emitParticles(emitter, dt)
    }
}

/**
 * Fast-forwards [emitter]'s simulation by [seconds] in fixed [ADVANCE_STEP_SECONDS] increments --
 * Apple's `advanceSimulationTime(_:)`. Ignores [SKFieldNode] forces (this has no scene to look
 * fields up in, unlike [stepEmitters]) — see `docs/API_COMPATIBILITY.md`.
 */
internal fun advanceEmitterSimulationTime(
    emitter: SKEmitterNode,
    seconds: Float,
) {
    var remaining = seconds
    while (remaining > 0f) {
        val dt = minOf(ADVANCE_STEP_SECONDS, remaining)
        ageParticles(emitter, dt) { Vector2.Zero }
        emitParticles(emitter, dt)
        remaining -= dt
    }
}

/**
 * Ages every one of [emitter]'s particles by [dt], removing any that outlive
 * [SKParticle.lifetime]; [extraAcceleration] adds field forces, if any.
 */
private fun ageParticles(
    emitter: SKEmitterNode,
    dt: Float,
    extraAcceleration: (SKParticle) -> Vector2,
) {
    val baseAcceleration = Vector2(emitter.xAcceleration, emitter.yAcceleration)
    val iterator = emitter.particles.iterator()
    while (iterator.hasNext()) {
        val particle = iterator.next()
        particle.age += dt
        if (particle.age >= particle.lifetime) {
            iterator.remove()
            continue
        }
        particle.velocity += (baseAcceleration + extraAcceleration(particle)) * dt
        particle.position += particle.velocity * dt
    }
}

/**
 * The combined acceleration every enabled, [SKEmitterNode.fieldBitMask]-matching field in
 * [fields] contributes at [particle]'s current world position.
 */
private fun fieldAccelerationSum(
    emitter: SKEmitterNode,
    fields: List<Pair<SKFieldNode, Vector2>>,
    particle: SKParticle,
    scene: SKScene,
): Vector2 {
    if (fields.isEmpty() || emitter.fieldBitMask == 0) return Vector2.Zero
    val worldPosition = emitter.convertTo(particle.position, scene)
    var sum = Vector2.Zero
    for ((field, fieldWorldPosition) in fields) {
        sum += fieldContribution(field, fieldWorldPosition, emitter.fieldBitMask, worldPosition, particle)
    }
    return sum
}

/**
 * [field]'s contribution to [particle]'s acceleration this step -- [Vector2.Zero] if it doesn't
 * match [fieldBitMask]. A [SKFieldKind.Velocity] field overrides [particle]'s velocity directly
 * instead (same as it does for physics bodies), contributing zero acceleration either way.
 */
@Suppress("ReturnCount")
private fun fieldContribution(
    field: SKFieldNode,
    fieldWorldPosition: Vector2,
    fieldBitMask: Int,
    worldPosition: Vector2,
    particle: SKParticle,
): Vector2 {
    if (!fieldAffects(field, fieldBitMask)) return Vector2.Zero
    if (field.kind == SKFieldKind.Velocity) {
        particle.velocity = fieldOverrideVelocity(field)
        return Vector2.Zero
    }
    return fieldAcceleration(field, fieldWorldPosition, worldPosition, particle.velocity)
}

private fun emitParticles(
    emitter: SKEmitterNode,
    dt: Float,
) {
    if (emitter.particleBirthRate <= 0f) return
    if (emitter.numParticlesToEmit > 0 && emitter.totalEmitted >= emitter.numParticlesToEmit) return

    emitter.emissionAccumulator += emitter.particleBirthRate * dt
    while (emitter.emissionAccumulator >= 1f) {
        if (emitter.numParticlesToEmit > 0 && emitter.totalEmitted >= emitter.numParticlesToEmit) break
        emitter.particles += spawnParticle(emitter)
        emitter.totalEmitted++
        emitter.emissionAccumulator -= 1f
    }
}

/**
 * `base` plus a uniform random offset in `[-range/2, range/2]` -- Apple's documented
 * `particleXxx`/`particleXxxRange` relationship.
 */
private fun randomized(
    base: Float,
    range: Float,
): Float = if (range == 0f) base else base + (Random.nextFloat() - 0.5f) * range

private fun spawnParticle(emitter: SKEmitterNode): SKParticle {
    val lifetime = randomized(emitter.particleLifetime, emitter.particleLifetimeRange).coerceAtLeast(0.0001f)
    val speed = randomized(emitter.particleSpeed, emitter.particleSpeedRange)
    val angle = randomized(emitter.emissionAngle, emitter.emissionAngleRange)
    val position =
        Vector2(
            randomized(0f, emitter.particlePositionRange.x),
            randomized(0f, emitter.particlePositionRange.y),
        )
    return SKParticle(
        position = position,
        velocity = Vector2(cos(angle), sin(angle)) * speed,
        lifetime = lifetime,
        initialAlpha = randomized(emitter.particleAlpha, emitter.particleAlphaRange).coerceIn(0f, 1f),
        alphaSpeed = emitter.particleAlphaSpeed,
        initialScale = randomized(emitter.particleScale, emitter.particleScaleRange),
        scaleSpeed = emitter.particleScaleSpeed,
        initialRotation = randomized(emitter.particleRotation, emitter.particleRotationRange),
        rotationSpeed = emitter.particleRotationSpeed,
        initialColorBlendFactor =
            randomized(
                emitter.particleColorBlendFactor,
                emitter.particleColorBlendFactorRange,
            ).coerceIn(0f, 1f),
        colorBlendFactorSpeed = emitter.particleColorBlendFactorSpeed,
        initialZPosition = randomized(emitter.particleZPosition, emitter.particleZPositionRange),
        zPositionSpeed = emitter.particleZPositionSpeed,
    )
}
