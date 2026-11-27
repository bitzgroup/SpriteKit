package jp.co.bitz.spritekit

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin
import kotlin.time.Duration
import kotlin.time.DurationUnit

/** Sequential-impulse passes per step -- more passes converge stacked/resting contacts better, at linear cost. */
private const val VELOCITY_ITERATIONS = 8

/**
 * Physics substeps per second: Apple advances its simulation in steps of 1/120 of a second, however
 * long the frame (verified on iOS/tvOS simulators -- a body falling at 20 frames a second lands
 * where six 1/120-second steps a frame put it).
 */
private const val SUBSTEPS_PER_SECOND = 120f

/** The most a body turns in one substep, in radians -- Box2D's limit, which Apple's matches. */
private const val MAX_ROTATION_PER_STEP = (PI / 2).toFloat()

/** The farthest a body moves in one substep, in points (2 meters) -- Box2D's limit, which Apple's matches. */
private const val MAX_TRANSLATION_PER_STEP = 2f * POINTS_PER_METER

/**
 * How many substeps [simulatePhysics] splits a frame of [dt] seconds into: the nearest whole
 * number of 1/120-second steps, at least one, so each lasts about 1/120 of a second and together
 * they cover the frame exactly.
 */
internal fun physicsSubsteps(dt: Float): Int = maxOf(1, kotlin.math.round(dt * SUBSTEPS_PER_SECOND).toInt())

/**
 * Advances [scene]'s [SKScene.physicsWorld] simulation by [deltaTime] -- called once per frame by
 * [SKView], between action evaluation and [SKScene.didSimulatePhysics] -- in [physicsSubsteps]
 * steps of about 1/120 of a second, as Apple does. Forces and torques applied before the frame act
 * through all of them, then clear. Semi-implicit Euler
 * integration, an O(n²) AABB broad phase, [narrowPhase] for shape-pair detection, a
 * sequential-impulse solver pushing at each contact point (so an off-center hit turns a body, as
 * on Apple), [SKPhysicsContactDelegate] notifications for
 * whichever touching pairs opt in via [SKPhysicsBody.contactTestBitMask], [SKPhysicsJoint]
 * constraint solving (see `SKPhysicsJointSimulation.kt`), and [SKFieldNode] force/velocity fields
 * (see `SKFieldSimulation.kt`).
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
    val nodeByBody = bodies.associate { it.body to it.node }
    bindJoints(world.joints, nodeByBody, scene)

    val steps = physicsSubsteps(dt)
    repeat(steps) { step(scene, bodies, nodeByBody, dt / steps) }
    for (entry in bodies) entry.body.clearAccumulators()
}

/** One substep of [simulatePhysics], [dt] seconds long. */
private fun step(
    scene: SKScene,
    bodies: List<SKPhysicsEntry>,
    nodeByBody: Map<SKPhysicsBody, SKNode>,
    dt: Float,
) {
    val world = scene.physicsWorld
    for (entry in bodies) {
        entry.body.wake()
        integrateForces(entry.body, world.gravity, dt)
    }
    applyJointForces(world.joints, nodeByBody, scene, dt)
    applyFieldForces(scene, bodies, dt)

    val shapes = bodies.associateWith { worldShape(it.node, scene, it.body.shape) }
    val observations = findContactObservations(bodies, shapes)
    recordContactedBodies(bodies, observations)

    val physicalContacts = observations.flatMap { toResolvedContacts(it, scene) }
    repeat(VELOCITY_ITERATIONS) {
        physicalContacts.forEach(::resolveVelocity)
        resolveJointVelocities(world.joints, nodeByBody, scene)
    }
    physicalContacts.forEach(::correctPosition)
    correctJointPositions(world.joints, nodeByBody, scene)

    reportContacts(world, observations)

    for (entry in bodies) integrateVelocity(entry.node, entry.body, dt)
}

internal data class SKPhysicsEntry(val node: SKNode, val body: SKPhysicsBody)

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

private fun dampingFactor(
    damping: Float,
    dt: Float,
): Float = 1f / (1f + dt * damping)

/**
 * Integrates [body]'s accumulated forces/torque and [gravity] into its velocity and applies
 * damping. The accumulators stay until the frame's last substep is done.
 */
private fun integrateForces(
    body: SKPhysicsBody,
    gravity: Vector2,
    dt: Float,
) {
    if (!body.isDynamic) return
    // Forces accelerate at force/mass points per second squared, but gravity is in meters per
    // second squared -- both as Apple applies them (verified on iOS/tvOS simulators).
    val gravityAcceleration = if (body.affectedByGravity) gravity * POINTS_PER_METER else Vector2.Zero
    val acceleration = body.forceAccumulator * body.inverseMass + gravityAcceleration
    body.velocity += acceleration * dt
    if (body.allowsRotation) {
        body.angularVelocity += body.torqueAccumulator * body.inverseInertia * dt
    }
    body.velocity *= dampingFactor(body.linearDamping, dt)
    body.angularVelocity *= dampingFactor(body.angularDamping, dt)
    if (body.pinned) body.velocity = Vector2.Zero // translation is locked; don't let velocity build up unused
}

/**
 * Applies [body]'s (now-resolved) velocity to [node]'s position/rotation. A no-op for a
 * non-dynamic body. The body turns about its center of mass, so a node whose body's shape is off
 * its origin swings around that point; [SKPhysicsBody.velocity] is the center of mass's.
 */
private fun integrateVelocity(
    node: SKNode,
    body: SKPhysicsBody,
    dt: Float,
) {
    if (!body.isDynamic) return
    // Apple caps how far a body moves and turns in one substep (verified on iOS/tvOS simulators:
    // 36,000 points and 60π radians a second at most).
    val travel = body.velocity.length() * dt
    if (travel > MAX_TRANSLATION_PER_STEP) body.velocity *= MAX_TRANSLATION_PER_STEP / travel
    if (abs(body.angularVelocity * dt) > MAX_ROTATION_PER_STEP) {
        body.angularVelocity = sign(body.angularVelocity) * MAX_ROTATION_PER_STEP / dt
    }
    val oldRotation = node.zRotation
    if (!body.pinned) node.position += body.velocity * dt
    if (body.allowsRotation) node.zRotation += body.angularVelocity * dt
    val center = body.centerOfMass
    if (!body.pinned && center != Vector2.Zero && node.zRotation != oldRotation) {
        val scaled = unscaled(Vector2(center.x * node.xScale, center.y * node.yScale), body.attachedScale)
        node.position += scaled.rotated(oldRotation) - scaled.rotated(node.zRotation)
    }
}

private fun Vector2.rotated(angle: Float): Vector2 {
    val c = cos(angle)
    val s = sin(angle)
    return Vector2(x * c - y * s, x * s + y * c)
}

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
    /** Every place the two touch -- more than [manifold], the deepest, when either is a compound. */
    val manifolds: List<SKContactManifold> = listOf(manifold),
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
    val manifolds = narrowPhaseAll(shapeA, shapeB)
    val deepest = manifolds.maxByOrNull { it.penetration } ?: return null
    return SKContactObservation(entryA.node, entryA.body, entryB.node, entryB.body, deepest, manifolds)
}

/**
 * [observation]'s contacts to resolve, one per place its bodies touch, if they should physically
 * collide and aren't both immovable -- none otherwise (e.g. a sensor-only pair).
 */
private fun toResolvedContacts(
    observation: SKContactObservation,
    scene: SKScene,
): List<SKResolvedContact> = observation.manifolds.mapNotNull { toResolvedContact(observation, it, scene) }

/** [manifold], one of [observation]'s, as a contact to resolve -- `null` if it shouldn't be. */
@Suppress("ReturnCount")
private fun toResolvedContact(
    observation: SKContactObservation,
    manifold: SKContactManifold,
    scene: SKScene,
): SKResolvedContact? {
    val a = observation.bodyA
    val b = observation.bodyB
    if (a.inverseMass == 0f && b.inverseMass == 0f) return null // both immovable
    if (!shouldCollide(a, b)) return null
    val normal = manifold.normal
    val tangent = Vector2(normal.y, -normal.x)
    val restitution = maxOf(a.restitution, b.restitution)
    val centerA = observation.nodeA.convertTo(a.centerOfMass, scene)
    val centerB = observation.nodeB.convertTo(b.centerOfMass, scene)
    val points =
        manifold.points.map { point ->
            val rA = point - centerA
            val rB = point - centerB
            val approach = relativeVelocity(a, rA, b, rB) dot normal
            SKContactPointSolver(
                rA,
                rB,
                effectiveMass(a, rA, b, rB, normal),
                effectiveMass(a, rA, b, rB, tangent),
                if (approach < 0f) -restitution * approach else 0f,
            )
        }
    return SKResolvedContact(
        SKPhysicsEntry(observation.nodeA, a),
        SKPhysicsEntry(observation.nodeB, b),
        normal,
        manifold.penetration,
        kotlin.math.sqrt(a.friction * b.friction),
        points,
    )
}

/**
 * Refreshes every body's [SKPhysicsBody.allContactedBodies] from this step's touching pairs. As
 * on Apple (verified on iOS/tvOS simulators), a pair counts when either body collides with the
 * other -- one-sidedly is enough -- or tests for contact with it, but never between two
 * non-dynamic bodies.
 */
private fun recordContactedBodies(
    bodies: List<SKPhysicsEntry>,
    observations: List<SKContactObservation>,
) {
    val contacted = bodies.associate { it.body to mutableListOf<SKPhysicsBody>() }
    for (observation in observations) {
        val a = observation.bodyA
        val b = observation.bodyB
        val collidesEitherWay =
            (a.categoryBitMask and b.collisionBitMask) != 0 || (b.categoryBitMask and a.collisionBitMask) != 0
        val counts = (a.isDynamic || b.isDynamic) && (collidesEitherWay || shouldNotifyContact(a, b))
        if (counts) {
            contacted[a]?.add(b)
            contacted[b]?.add(a)
        }
    }
    for ((body, list) in contacted) body.contactedBodies = list
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
