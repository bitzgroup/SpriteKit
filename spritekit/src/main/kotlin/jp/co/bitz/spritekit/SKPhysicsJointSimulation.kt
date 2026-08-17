package jp.co.bitz.spritekit

import kotlin.math.PI

/**
 * Fraction of a joint's constraint error corrected per step -- a stiffer pull than contacts'
 * correction, since joints are meant to feel rigid.
 */
private const val JOINT_POSITION_CORRECTION_PERCENT = 0.3f

/** [nodeByBody]'s entries for [joint]'s two bodies, or `null` if either isn't part of a presented scene. */
@Suppress("ReturnCount")
private fun jointNodes(
    joint: SKPhysicsJoint,
    nodeByBody: Map<SKPhysicsBody, SKNode>,
): Pair<SKNode, SKNode>? {
    val nodeA = nodeByBody[joint.bodyA] ?: return null
    val nodeB = nodeByBody[joint.bodyB] ?: return null
    return nodeA to nodeB
}

private fun worldAnchorA(
    joint: SKPhysicsJoint,
    nodeA: SKNode,
    scene: SKScene,
): Vector2 = nodeA.convertTo(joint.localAnchorA!!, scene)

private fun worldAnchorB(
    joint: SKPhysicsJoint,
    nodeB: SKNode,
    scene: SKScene,
): Vector2 = nodeB.convertTo(joint.localAnchorB!!, scene)

/**
 * Caches every joint's per-body local anchor offset (and, for [SKPhysicsJointFixed]/
 * [SKPhysicsJointSpring]/[SKPhysicsJointLimit], its other lazily-bound state) the first time it's
 * processed, deriving it from each body's *current* transform -- called once per step, before
 * [applyJointForces]/[resolveJointVelocities]/[correctJointPositions] all rely on it being ready.
 */
internal fun bindJoints(
    joints: List<SKPhysicsJoint>,
    nodeByBody: Map<SKPhysicsBody, SKNode>,
    scene: SKScene,
) {
    for (joint in joints) {
        val (nodeA, nodeB) = jointNodes(joint, nodeByBody) ?: continue
        if (joint.localAnchorA == null) joint.localAnchorA = nodeA.convertFrom(joint.anchorPointA, scene)
        if (joint.localAnchorB == null) joint.localAnchorB = nodeB.convertFrom(joint.anchorPointB, scene)
        bindJointSpecificState(joint, nodeA, nodeB, scene)
    }
}

private fun bindJointSpecificState(
    joint: SKPhysicsJoint,
    nodeA: SKNode,
    nodeB: SKNode,
    scene: SKScene,
) {
    when (joint) {
        is SKPhysicsJointFixed -> {
            if (joint.relativeRotation == null) joint.relativeRotation = nodeB.zRotation - nodeA.zRotation
        }
        is SKPhysicsJointSpring -> {
            if (joint.restLength == null) {
                joint.restLength = (worldAnchorB(joint, nodeB, scene) - worldAnchorA(joint, nodeA, scene)).length()
            }
        }
        is SKPhysicsJointLimit -> {
            if (joint.maxLength == Float.POSITIVE_INFINITY) {
                joint.maxLength = (worldAnchorB(joint, nodeB, scene) - worldAnchorA(joint, nodeA, scene)).length()
            }
        }
        is SKPhysicsJointPin, is SKPhysicsJointSliding -> Unit
    }
}

/**
 * Applies every [SKPhysicsJointSpring]'s force to its two bodies' velocity for this step (the
 * other joint kinds affect velocity, if at all, via [resolveJointVelocities] instead).
 */
internal fun applyJointForces(
    joints: List<SKPhysicsJoint>,
    nodeByBody: Map<SKPhysicsBody, SKNode>,
    scene: SKScene,
    dt: Float,
) {
    for (joint in joints.filterIsInstance<SKPhysicsJointSpring>()) {
        val (nodeA, nodeB) = jointNodes(joint, nodeByBody) ?: continue
        applySpringForce(joint, nodeA, nodeB, scene, dt)
    }
}

@Suppress("ReturnCount")
private fun applySpringForce(
    joint: SKPhysicsJointSpring,
    nodeA: SKNode,
    nodeB: SKNode,
    scene: SKScene,
    dt: Float,
) {
    val invMassSum = joint.bodyA.inverseMass + joint.bodyB.inverseMass
    if (invMassSum == 0f) return

    val displacement = worldAnchorB(joint, nodeB, scene) - worldAnchorA(joint, nodeA, scene)
    val distance = displacement.length()
    if (distance == 0f) return
    val direction = displacement * (1f / distance)

    val stretch = distance - joint.restLength!!
    val stiffness = (2f * PI.toFloat() * joint.frequency).let { it * it }
    val velocityAlongAxis = (joint.bodyB.velocity - joint.bodyA.velocity) dot direction
    val springForce = -stiffness * stretch - joint.damping * velocityAlongAxis

    val impulse = direction * (springForce * dt)
    joint.bodyA.velocity -= impulse * joint.bodyA.inverseMass
    joint.bodyB.velocity += impulse * joint.bodyB.inverseMass
}

/**
 * Resolves every position-based joint's *velocity* constraint -- e.g. a pin cancels all relative
 * velocity between its two anchor points -- before [correctJointPositions] nudges any leftover
 * positional error. Without this, a joint fighting a continuous force like gravity would drift
 * indefinitely: position correction alone only ever catches up to *last* step's error, while
 * velocity keeps growing unchecked. [SKPhysicsJointSpring] isn't resolved here; it's velocity-only
 * already, via [applyJointForces].
 */
internal fun resolveJointVelocities(
    joints: List<SKPhysicsJoint>,
    nodeByBody: Map<SKPhysicsBody, SKNode>,
    scene: SKScene,
) {
    for (joint in joints) {
        if (jointNodes(joint, nodeByBody) == null) continue
        when (joint) {
            is SKPhysicsJointPin -> resolvePointVelocity(joint.bodyA, joint.bodyB)
            is SKPhysicsJointFixed -> resolvePointVelocity(joint.bodyA, joint.bodyB)
            is SKPhysicsJointSliding -> resolveSlidingVelocity(joint)
            is SKPhysicsJointLimit -> resolveLimitVelocity(joint, nodeByBody, scene)
            is SKPhysicsJointSpring -> Unit
        }
    }
}

/**
 * Cancels all relative velocity between [bodyA] and [bodyB] -- a full 2D point constraint, used
 * by pin and fixed joints alike.
 */
private fun resolvePointVelocity(
    bodyA: SKPhysicsBody,
    bodyB: SKPhysicsBody,
) {
    val invMassSum = bodyA.inverseMass + bodyB.inverseMass
    if (invMassSum == 0f) return
    val correction = (bodyB.velocity - bodyA.velocity) * (1f / invMassSum)
    bodyA.velocity += correction * bodyA.inverseMass
    bodyB.velocity -= correction * bodyB.inverseMass
}

/**
 * Cancels only the component of relative velocity perpendicular to [SKPhysicsJointSliding.axis],
 * leaving sliding motion free.
 */
private fun resolveSlidingVelocity(joint: SKPhysicsJointSliding) {
    val bodyA = joint.bodyA
    val bodyB = joint.bodyB
    val invMassSum = bodyA.inverseMass + bodyB.inverseMass
    if (invMassSum == 0f) return
    val axis = if (joint.axis.lengthSquared() > 0f) joint.axis.normalized() else Vector2(1f, 0f)
    val relativeVelocity = bodyB.velocity - bodyA.velocity
    val correction = (relativeVelocity - axis * (relativeVelocity dot axis)) * (1f / invMassSum)
    bodyA.velocity += correction * bodyA.inverseMass
    bodyB.velocity -= correction * bodyB.inverseMass
}

/**
 * Cancels the outward (stretching) component of relative velocity once the two anchors are
 * at/beyond [SKPhysicsJointLimit.maxLength] -- like a rope going taut; slack motion stays free.
 */
@Suppress("ReturnCount")
private fun resolveLimitVelocity(
    joint: SKPhysicsJointLimit,
    nodeByBody: Map<SKPhysicsBody, SKNode>,
    scene: SKScene,
) {
    val (nodeA, nodeB) = jointNodes(joint, nodeByBody) ?: return
    val bodyA = joint.bodyA
    val bodyB = joint.bodyB
    val invMassSum = bodyA.inverseMass + bodyB.inverseMass
    if (invMassSum == 0f) return

    val separation = worldAnchorB(joint, nodeB, scene) - worldAnchorA(joint, nodeA, scene)
    val distance = separation.length()
    if (distance == 0f || distance < joint.maxLength) return // slack -- free to move
    val direction = separation * (1f / distance)

    val velocityAlongDirection = (bodyB.velocity - bodyA.velocity) dot direction
    if (velocityAlongDirection <= 0f) return // already closing or stationary
    val correction = direction * (velocityAlongDirection / invMassSum)
    bodyA.velocity += correction * bodyA.inverseMass
    bodyB.velocity -= correction * bodyB.inverseMass
}

/**
 * Applies every position-based joint's constraint correction directly to its bodies' node
 * positions (and, for [SKPhysicsJointFixed], rotation) -- Baumgarte-style, like
 * [SKPhysicsSimulation]'s contact position correction, on top of [resolveJointVelocities]'s
 * velocity-level fix.
 */
internal fun correctJointPositions(
    joints: List<SKPhysicsJoint>,
    nodeByBody: Map<SKPhysicsBody, SKNode>,
    scene: SKScene,
) {
    for (joint in joints) {
        val (nodeA, nodeB) = jointNodes(joint, nodeByBody) ?: continue
        when (joint) {
            is SKPhysicsJointPin -> correctPinJoint(joint, nodeA, nodeB, scene)
            is SKPhysicsJointFixed -> correctFixedJoint(joint, nodeA, nodeB, scene)
            is SKPhysicsJointSliding -> correctSlidingJoint(joint, nodeA, nodeB, scene)
            is SKPhysicsJointLimit -> correctLimitJoint(joint, nodeA, nodeB, scene)
            is SKPhysicsJointSpring -> Unit // velocity-only, handled by applyJointForces
        }
    }
}

/**
 * Nudges [nodeA]/[nodeB] so their anchor points move by [separation] (`worldAnchorB -
 * worldAnchorA`) towards each other, split by inverse mass.
 */
private fun pullAnchorsTogether(
    nodeA: SKNode,
    bodyA: SKPhysicsBody,
    nodeB: SKNode,
    bodyB: SKPhysicsBody,
    separation: Vector2,
) {
    val invMassSum = bodyA.inverseMass + bodyB.inverseMass
    if (invMassSum == 0f) return
    val correction = separation * JOINT_POSITION_CORRECTION_PERCENT
    if (!bodyA.pinned) nodeA.position += correction * (bodyA.inverseMass / invMassSum)
    if (!bodyB.pinned) nodeB.position -= correction * (bodyB.inverseMass / invMassSum)
}

private fun correctPinJoint(
    joint: SKPhysicsJointPin,
    nodeA: SKNode,
    nodeB: SKNode,
    scene: SKScene,
) {
    val separation = worldAnchorB(joint, nodeB, scene) - worldAnchorA(joint, nodeA, scene)
    pullAnchorsTogether(nodeA, joint.bodyA, nodeB, joint.bodyB, separation)
}

private fun correctFixedJoint(
    joint: SKPhysicsJointFixed,
    nodeA: SKNode,
    nodeB: SKNode,
    scene: SKScene,
) {
    val separation = worldAnchorB(joint, nodeB, scene) - worldAnchorA(joint, nodeA, scene)
    pullAnchorsTogether(nodeA, joint.bodyA, nodeB, joint.bodyB, separation)
    nodeB.zRotation = nodeA.zRotation + joint.relativeRotation!!
}

private fun correctSlidingJoint(
    joint: SKPhysicsJointSliding,
    nodeA: SKNode,
    nodeB: SKNode,
    scene: SKScene,
) {
    val separation = worldAnchorB(joint, nodeB, scene) - worldAnchorA(joint, nodeA, scene)
    val axis = if (joint.axis.lengthSquared() > 0f) joint.axis.normalized() else Vector2(1f, 0f)
    val alongAxis = separation dot axis

    // Free to slide along the axis -- only the perpendicular drift is corrected.
    pullAnchorsTogether(nodeA, joint.bodyA, nodeB, joint.bodyB, separation - axis * alongAxis)

    if (!joint.shouldEnableLimits) return
    val clampedAlongAxis = alongAxis.coerceIn(joint.lowerDistanceLimit, joint.upperDistanceLimit)
    if (clampedAlongAxis != alongAxis) {
        pullAnchorsTogether(nodeA, joint.bodyA, nodeB, joint.bodyB, axis * (alongAxis - clampedAlongAxis))
    }
}

private fun correctLimitJoint(
    joint: SKPhysicsJointLimit,
    nodeA: SKNode,
    nodeB: SKNode,
    scene: SKScene,
) {
    val separation = worldAnchorB(joint, nodeB, scene) - worldAnchorA(joint, nodeA, scene)
    val distance = separation.length()
    if (distance == 0f || distance <= joint.maxLength) return

    pullAnchorsTogether(nodeA, joint.bodyA, nodeB, joint.bodyB, separation * ((distance - joint.maxLength) / distance))
}
