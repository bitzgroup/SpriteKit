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
 * integration, an O(n²) AABB broad phase, [narrowPhase] for shape-pair detection, a linear-only
 * sequential-impulse solver (contact-point torque -- i.e. spin from an off-center hit -- is
 * deferred; see `docs/API_COMPATIBILITY.md`), and [SKPhysicsContactDelegate] notifications for
 * whichever touching pairs opt in via [SKPhysicsBody.contactTestBitMask].
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
    val observations = findContactObservations(bodies, shapes)

    val physicalContacts = observations.mapNotNull(::toResolvedContact)
    repeat(VELOCITY_ITERATIONS) { physicalContacts.forEach(::resolveVelocity) }
    physicalContacts.forEach(::correctPosition)

    reportContacts(world, observations)

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
 * Two bodies actually found touching this step -- the shared basis for both physical resolution
 * and contact reporting.
 */
private data class SKContactObservation(
    val nodeA: SKNode,
    val bodyA: SKPhysicsBody,
    val nodeB: SKNode,
    val bodyB: SKPhysicsBody,
    val manifold: SKContactManifold,
)

/**
 * The O(n²) broad phase, narrowed by [narrowPhase] -- not scoped to handle very large body
 * counts, see `docs/API_COMPATIBILITY.md`. Independent of [shouldCollide]/[shouldNotifyContact]:
 * those bitmask tests are applied downstream by [toResolvedContact]/[reportContacts]
 * respectively, since Apple's `collisionBitMask` and `contactTestBitMask` are independent knobs.
 */
private fun findContactObservations(
    bodies: List<SKPhysicsEntry>,
    shapes: Map<SKPhysicsEntry, SKWorldShape>,
): List<SKContactObservation> {
    val observations = mutableListOf<SKContactObservation>()
    for (i in bodies.indices) {
        for (j in i + 1 until bodies.size) {
            observeContact(bodies[i], bodies[j], shapes)?.let { observations += it }
        }
    }
    return observations
}

// Early returns cover the "no contact this step" cases (broad phase, then narrow phase).
@Suppress("ReturnCount")
private fun observeContact(
    entryA: SKPhysicsEntry,
    entryB: SKPhysicsEntry,
    shapes: Map<SKPhysicsEntry, SKWorldShape>,
): SKContactObservation? {
    val shapeA = shapes.getValue(entryA)
    val shapeB = shapes.getValue(entryB)
    if (!broadPhaseOverlap(shapeA, shapeB)) return null
    val manifold = narrowPhase(shapeA, shapeB) ?: return null
    return SKContactObservation(entryA.node, entryA.body, entryB.node, entryB.body, manifold)
}

/**
 * [observation], if its bodies should physically collide and aren't both immovable -- `null`
 * otherwise (e.g. a sensor-only pair).
 */
@Suppress("ReturnCount")
private fun toResolvedContact(observation: SKContactObservation): SKResolvedContact? {
    val a = observation.bodyA
    val b = observation.bodyB
    if (a.inverseMass == 0f && b.inverseMass == 0f) return null // both immovable
    if (!shouldCollide(a, b)) return null
    return SKResolvedContact(
        observation.nodeA,
        a,
        observation.nodeB,
        b,
        observation.manifold.normal,
        observation.manifold.penetration,
        maxOf(a.restitution, b.restitution),
        kotlin.math.sqrt(a.friction * b.friction),
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

/**
 * Apple's documented contact-test contract: a pair is reported if either body's category is in
 * the *other* body's [SKPhysicsBody.contactTestBitMask] -- independent of [shouldCollide].
 */
private fun shouldNotifyContact(
    a: SKPhysicsBody,
    b: SKPhysicsBody,
): Boolean = (a.categoryBitMask and b.contactTestBitMask) != 0 || (b.categoryBitMask and a.contactTestBitMask) != 0

/** A stable, order-independent key identifying a body pair, for [SKPhysicsWorld.activeContacts]. */
private fun contactPairKey(
    a: SKPhysicsBody,
    b: SKPhysicsBody,
): Pair<SKPhysicsBody, SKPhysicsBody> = if (System.identityHashCode(a) <= System.identityHashCode(b)) a to b else b to a

private fun SKContactObservation.toContact(): SKPhysicsContact =
    SKPhysicsContact(
        bodyA,
        bodyB,
        manifold.point,
        manifold.normal,
        0f,
    )

private fun contactFrom(
    key: Pair<SKPhysicsBody, SKPhysicsBody>,
    manifold: SKContactManifold,
): SKPhysicsContact = SKPhysicsContact(key.first, key.second, manifold.point, manifold.normal, 0f)

/**
 * Diffs this step's touching pairs (from [observations], filtered by [shouldNotifyContact])
 * against [SKPhysicsWorld.activeContacts] to fire [SKPhysicsContactDelegate.didBegin] for newly
 * touching pairs and [SKPhysicsContactDelegate.didEnd] for pairs that stopped touching --
 * including a pair where one body left the scene entirely, since it simply won't appear in
 * [observations] anymore either.
 */
private fun reportContacts(
    world: SKPhysicsWorld,
    observations: List<SKContactObservation>,
) {
    val delegate = world.contactDelegate
    val seenThisFrame = mutableSetOf<Pair<SKPhysicsBody, SKPhysicsBody>>()
    for (observation in observations) {
        if (!shouldNotifyContact(observation.bodyA, observation.bodyB)) continue
        val key = contactPairKey(observation.bodyA, observation.bodyB)
        seenThisFrame += key
        val isNewContact = world.activeContacts.put(key, observation.manifold) == null
        if (isNewContact) delegate?.didBegin(observation.toContact())
    }
    for (key in world.activeContacts.keys.filter { it !in seenThisFrame }) {
        val manifold = world.activeContacts.remove(key) ?: continue
        delegate?.didEnd(contactFrom(key, manifold))
    }
}
