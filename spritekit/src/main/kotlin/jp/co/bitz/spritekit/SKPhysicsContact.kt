package jp.co.bitz.spritekit

/**
 * Describes two [SKPhysicsBody]s touching — mirrors Apple's `SKPhysicsContact`. Delivered to
 * [SKPhysicsWorld.contactDelegate] by the physics simulation; never constructed directly.
 *
 * [collisionImpulse] is always `0` — this port doesn't thread the resolved impulse magnitude back
 * out to the notification path; see `docs/API_COMPATIBILITY.md`.
 */
public class SKPhysicsContact internal constructor(
    /** One of the two touching bodies. Order is arbitrary — not necessarily the order either was added to the world. */
    public val bodyA: SKPhysicsBody,
    /** The other touching body. */
    public val bodyB: SKPhysicsBody,
    /** Where [bodyA] and [bodyB] touch, in world space. */
    public val contactPoint: Vector2,
    /** The contact surface's normal at [contactPoint], in world space, pointing from [bodyA] towards [bodyB]. */
    public val contactNormal: Vector2,
    public val collisionImpulse: Float,
)

/**
 * Receives contact notifications from an [SKPhysicsWorld] via [SKPhysicsWorld.contactDelegate] —
 * mirrors Apple's `SKPhysicsContactDelegate` protocol. Both methods default to a no-op, so a
 * conforming type only needs to override the one(s) it cares about (Apple's `@objc optional`
 * equivalent).
 *
 * A body pair is reported here only when at least one direction of their
 * [SKPhysicsBody.contactTestBitMask]/[SKPhysicsBody.categoryBitMask] test passes — independent of
 * [SKPhysicsBody.collisionBitMask], which governs physical collision *response* instead. See
 * `docs/API_COMPATIBILITY.md`.
 */
public interface SKPhysicsContactDelegate {
    /** Called the first frame two bodies satisfying the contact-test bitmask condition are found touching. */
    public fun didBegin(contact: SKPhysicsContact) {}

    /** Called the first frame after two previously-touching bodies stop touching (or one leaves the scene). */
    public fun didEnd(contact: SKPhysicsContact) {}
}
