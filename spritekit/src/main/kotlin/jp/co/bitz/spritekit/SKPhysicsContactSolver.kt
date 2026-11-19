package jp.co.bitz.spritekit

/** Penetration allowed to remain uncorrected each step, avoiding jitter from correcting to exactly zero. */
private const val PENETRATION_SLOP = 0.01f

/** Fraction of remaining penetration corrected per step (Baumgarte stabilization), not all at once, to stay stable. */
private const val POSITION_CORRECTION_PERCENT = 0.2f

/** The velocity [ω] (radians per second) gives a point [r] away from the center of mass: `ω × r`. */
private fun angularToLinear(
    angularVelocity: Float,
    r: Vector2,
): Vector2 = Vector2(-angularVelocity * r.y, angularVelocity * r.x)

/**
 * One contact point being solved: its lever arms from each body's center of mass, the effective
 * masses along the normal and the tangent, the bounce it's aiming for, and the impulses
 * accumulated so far this step (clamped as totals, as Box2D does, not per pass).
 */
internal class SKContactPointSolver(
    val rA: Vector2,
    val rB: Vector2,
    val normalMass: Float,
    val tangentMass: Float,
    val bounceVelocity: Float,
) {
    var normalImpulse = 0f
    var tangentImpulse = 0f
}

internal class SKResolvedContact(
    val a: SKPhysicsEntry,
    val b: SKPhysicsEntry,
    val normal: Vector2,
    val penetration: Float,
    val friction: Float,
    val points: List<SKContactPointSolver>,
)

/** The relative velocity of [b]'s point [rB] past [a]'s point [rA], both lever arms from the centers of mass. */
internal fun relativeVelocity(
    a: SKPhysicsBody,
    rA: Vector2,
    b: SKPhysicsBody,
    rB: Vector2,
): Vector2 = b.velocity + angularToLinear(b.angularVelocity, rB) - a.velocity - angularToLinear(a.angularVelocity, rA)

/** `1 / (the velocity change one unit of impulse along [direction] makes at the contact)`. */
internal fun effectiveMass(
    a: SKPhysicsBody,
    rA: Vector2,
    b: SKPhysicsBody,
    rB: Vector2,
    direction: Vector2,
): Float {
    val armA = rA cross direction
    val armB = rB cross direction
    val sum =
        a.inverseMass + b.inverseMass +
            a.inverseInertiaInPoints * armA * armA + b.inverseInertiaInPoints * armB * armB
    return if (sum > 0f) 1f / sum else 0f
}

/** Applies [impulse] to [b] and its opposite to [a], at lever arms [rA]/[rB]. */
private fun applyContactImpulse(
    a: SKPhysicsBody,
    rA: Vector2,
    b: SKPhysicsBody,
    rB: Vector2,
    impulse: Vector2,
) {
    a.velocity -= impulse * a.inverseMass
    a.angularVelocity -= (rA cross impulse) * a.inverseInertiaInPoints
    b.velocity += impulse * b.inverseMass
    b.angularVelocity += (rB cross impulse) * b.inverseInertiaInPoints
}

/**
 * One sequential-impulse pass over [contact]'s points: Coulomb friction along the tangent, then
 * the normal impulse that stops the bodies approaching (and bounces them by the restitution), each
 * pushing at the contact point, so it turns the bodies as well as moving them.
 */
internal fun resolveVelocity(contact: SKResolvedContact) {
    val a = contact.a.body
    val b = contact.b.body
    val normal = contact.normal
    val tangent = Vector2(normal.y, -normal.x)
    for (point in contact.points) {
        val tangentSpeed = relativeVelocity(a, point.rA, b, point.rB) dot tangent
        val maxFriction = contact.friction * point.normalImpulse
        val newTangentImpulse =
            (point.tangentImpulse - point.tangentMass * tangentSpeed).coerceIn(
                -maxFriction,
                maxFriction,
            )
        applyContactImpulse(a, point.rA, b, point.rB, tangent * (newTangentImpulse - point.tangentImpulse))
        point.tangentImpulse = newTangentImpulse

        val normalSpeed = relativeVelocity(a, point.rA, b, point.rB) dot normal
        val newNormalImpulse = maxOf(point.normalImpulse - point.normalMass * (normalSpeed - point.bounceVelocity), 0f)
        applyContactImpulse(a, point.rA, b, point.rB, normal * (newNormalImpulse - point.normalImpulse))
        point.normalImpulse = newNormalImpulse
    }
}

/**
 * Nudges [contact]'s two nodes apart along its normal to correct leftover penetration --
 * Baumgarte stabilization, not a velocity change.
 */
internal fun correctPosition(contact: SKResolvedContact) {
    val invMassSum = contact.a.body.inverseMass + contact.b.body.inverseMass
    if (invMassSum == 0f) return
    val depth = maxOf(contact.penetration - PENETRATION_SLOP, 0f)
    if (depth == 0f) return
    val correction = contact.normal * (depth / invMassSum * POSITION_CORRECTION_PERCENT)
    if (!contact.a.body.pinned) contact.a.node.position -= correction * contact.a.body.inverseMass
    if (!contact.b.body.pinned) contact.b.node.position += correction * contact.b.body.inverseMass
}
