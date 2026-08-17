package jp.co.bitz.spritekit

import kotlin.time.Duration
import kotlin.time.DurationUnit

/** Sequential-impulse passes per step -- more passes converge stacked/resting contacts better, at linear cost. */
private const val VELOCITY_ITERATIONS = 8

/** Penetration allowed to remain uncorrected each step, avoiding jitter from correcting to exactly zero. */
private const val PENETRATION_SLOP = 0.01f

/** Fraction of remaining penetration corrected per step (Baumgarte stabilization), not all at once, to stay stable. */
private const val POSITION_CORRECTION_PERCENT = 0.2f

/**
 * Advances [scene]'s [SKScene.physicsWorld] simulation by [deltaTime] -- called once per frame by
 * [SKView], between action evaluation and [SKScene.didSimulatePhysics]. Semi-implicit Euler
 * integration, an O(n²) AABB broad phase, [narrowPhase] for shape-pair detection, and a
 * linear-only sequential-impulse solver (contact-point torque -- i.e. spin from an off-center hit
 * -- is deferred; see `docs/API_COMPATIBILITY.md`).
 */
internal fun simulatePhysics(
    scene: SKScene,
    deltaTime: Duration,
) {
    val world = scene.physicsWorld
    val dt = deltaTime.toDouble(DurationUnit.SECONDS).toFloat() * world.speed
    if (dt <= 0f) return

    val bodies = collectPhysicsBodies(scene)
    if (bodies.isEmpty()) return

    for (entry in bodies) integrateForces(entry.body, world.gravity, dt)

    val shapes = bodies.associateWith { worldShape(it.node, scene, it.body.shape) }
    val contacts = findContacts(bodies, shapes)
    repeat(VELOCITY_ITERATIONS) { contacts.forEach(::resolveVelocity) }
    contacts.forEach(::correctPosition)

    for (entry in bodies) integrateVelocity(entry.node, entry.body, dt)
}

private data class SKPhysicsEntry(val node: SKNode, val body: SKPhysicsBody)

/**
 * Every (node, physicsBody) pair in [node]'s subtree, skipping [SKNode.isPaused] subtrees -- the
 * same scope [SKNode.stepActions] uses.
 */
private fun collectPhysicsBodies(
    node: SKNode,
    out: MutableList<SKPhysicsEntry> = mutableListOf(),
): List<SKPhysicsEntry> {
    if (node.isPaused) return out
    node.physicsBody?.let { out += SKPhysicsEntry(node, it) }
    for (child in node.children) collectPhysicsBodies(child, out)
    return out
}

/**
 * [shape], transformed from [node]'s local space into [scene]'s space. A circle's world radius is
 * approximated from where its local +x edge lands after the transform -- exact under uniform
 * scale/rotation, an approximation (treating an ellipse as a circle) under non-uniform scale.
 */
private fun worldShape(
    node: SKNode,
    scene: SKScene,
    shape: SKPhysicsShape,
): SKWorldShape =
    when (shape) {
        is SKPhysicsShape.Circle -> {
            val center = node.convertTo(shape.center, scene)
            val edge = node.convertTo(shape.center + Vector2(shape.radius, 0f), scene)
            SKWorldShape.Circle(center, (edge - center).length())
        }
        is SKPhysicsShape.Polygon -> SKWorldShape.Polygon(shape.vertices.map { node.convertTo(it, scene) })
        is SKPhysicsShape.EdgeChain ->
            SKWorldShape.EdgeChain(
                shape.vertices.map { node.convertTo(it, scene) },
                shape.closed,
            )
    }

private fun dampingFactor(
    damping: Float,
    dt: Float,
): Float = 1f / (1f + dt * damping)

/**
 * Integrates [body]'s accumulated forces/torque and [gravity] into its velocity, applies
 * damping, then clears the accumulators.
 */
private fun integrateForces(
    body: SKPhysicsBody,
    gravity: Vector2,
    dt: Float,
) {
    if (!body.isDynamic) {
        body.clearAccumulators()
        return
    }
    val acceleration = body.forceAccumulator * body.inverseMass + if (body.affectedByGravity) gravity else Vector2.Zero
    body.velocity += acceleration * dt
    if (body.allowsRotation) {
        body.angularVelocity += body.torqueAccumulator * body.inverseInertia * dt
    }
    body.velocity *= dampingFactor(body.linearDamping, dt)
    body.angularVelocity *= dampingFactor(body.angularDamping, dt)
    if (body.pinned) body.velocity = Vector2.Zero // translation is locked; don't let velocity build up unused
    body.clearAccumulators()
}

/** Applies [body]'s (now-resolved) velocity to [node]'s position/rotation. A no-op for a non-dynamic body. */
private fun integrateVelocity(
    node: SKNode,
    body: SKPhysicsBody,
    dt: Float,
) {
    if (!body.isDynamic) return
    if (!body.pinned) node.position += body.velocity * dt
    if (body.allowsRotation) node.zRotation += body.angularVelocity * dt
}

private data class SKResolvedContact(
    val nodeA: SKNode,
    val bodyA: SKPhysicsBody,
    val nodeB: SKNode,
    val bodyB: SKPhysicsBody,
    val normal: Vector2,
    val penetration: Float,
    val restitution: Float,
    val friction: Float,
)

/**
 * Apple's documented category/collision-mask contract: two bodies collide only if each one's
 * category is in the other's collision mask.
 */
private fun shouldCollide(
    a: SKPhysicsBody,
    b: SKPhysicsBody,
): Boolean = (a.categoryBitMask and b.collisionBitMask) != 0 && (b.categoryBitMask and a.collisionBitMask) != 0

/**
 * The O(n²) broad phase, narrowed by [narrowPhase] -- not scoped to handle very large body
 * counts, see `docs/API_COMPATIBILITY.md`.
 */
private fun findContacts(
    bodies: List<SKPhysicsEntry>,
    shapes: Map<SKPhysicsEntry, SKWorldShape>,
): List<SKResolvedContact> {
    val contacts = mutableListOf<SKResolvedContact>()
    for (i in bodies.indices) {
        for (j in i + 1 until bodies.size) {
            contactBetween(bodies[i], bodies[j], shapes)?.let { contacts += it }
        }
    }
    return contacts
}

// Early returns are the clearest way to express "these two bodies don't need a contact" at each
// stage (mass, bitmasks, broad phase, narrow phase).
@Suppress("ReturnCount")
private fun contactBetween(
    entryA: SKPhysicsEntry,
    entryB: SKPhysicsEntry,
    shapes: Map<SKPhysicsEntry, SKWorldShape>,
): SKResolvedContact? {
    if (entryA.body.inverseMass == 0f && entryB.body.inverseMass == 0f) return null // both immovable
    if (!shouldCollide(entryA.body, entryB.body)) return null
    val shapeA = shapes.getValue(entryA)
    val shapeB = shapes.getValue(entryB)
    if (!broadPhaseOverlap(shapeA, shapeB)) return null
    val manifold = narrowPhase(shapeA, shapeB) ?: return null
    return SKResolvedContact(
        entryA.node,
        entryA.body,
        entryB.node,
        entryB.body,
        manifold.normal,
        manifold.penetration,
        maxOf(entryA.body.restitution, entryB.body.restitution),
        kotlin.math.sqrt(entryA.body.friction * entryB.body.friction),
    )
}

/**
 * One sequential-impulse pass: resolves [contact]'s normal (restitution) and tangential (Coulomb
 * friction) velocity, linear-only. Early returns cover the "nothing to resolve" cases (both
 * immovable, already separating, no tangential motion) before the actual impulse math.
 */
@Suppress("ReturnCount")
private fun resolveVelocity(contact: SKResolvedContact) {
    val a = contact.bodyA
    val b = contact.bodyB
    val invMassSum = a.inverseMass + b.inverseMass
    if (invMassSum == 0f) return

    val relativeVelocity = b.velocity - a.velocity
    val velocityAlongNormal = relativeVelocity dot contact.normal
    if (velocityAlongNormal > 0f) return // already separating

    val normalImpulseMagnitude = -(1f + contact.restitution) * velocityAlongNormal / invMassSum
    val normalImpulse = contact.normal * normalImpulseMagnitude
    a.velocity -= normalImpulse * a.inverseMass
    b.velocity += normalImpulse * b.inverseMass

    val relativeVelocityAfterNormal = b.velocity - a.velocity
    val normalComponent = contact.normal * (relativeVelocityAfterNormal dot contact.normal)
    val tangentComponent = relativeVelocityAfterNormal - normalComponent
    val tangent = if (tangentComponent.lengthSquared() > 0f) tangentComponent.normalized() else return
    val velocityAlongTangent = relativeVelocityAfterNormal dot tangent

    val maxFrictionImpulse = normalImpulseMagnitude * contact.friction
    val frictionImpulseMagnitude =
        (-velocityAlongTangent / invMassSum).coerceIn(
            -maxFrictionImpulse,
            maxFrictionImpulse,
        )
    val frictionImpulse = tangent * frictionImpulseMagnitude
    a.velocity -= frictionImpulse * a.inverseMass
    b.velocity += frictionImpulse * b.inverseMass
}

/**
 * Nudges [contact]'s two nodes apart along its normal to correct leftover penetration --
 * Baumgarte stabilization, not a velocity change.
 */
private fun correctPosition(contact: SKResolvedContact) {
    val invMassSum = contact.bodyA.inverseMass + contact.bodyB.inverseMass
    if (invMassSum == 0f) return
    val depth = maxOf(contact.penetration - PENETRATION_SLOP, 0f)
    if (depth == 0f) return
    val correction = contact.normal * (depth / invMassSum * POSITION_CORRECTION_PERCENT)
    if (!contact.bodyA.pinned) contact.nodeA.position -= correction * contact.bodyA.inverseMass
    if (!contact.bodyB.pinned) contact.nodeB.position += correction * contact.bodyB.inverseMass
}
