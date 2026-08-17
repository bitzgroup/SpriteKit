package jp.co.bitz.spritekit

/**
 * Owns the physics simulation for an [SKScene] (via [SKScene.physicsWorld]) — mirrors Apple's
 * `SKPhysicsWorld`. [SKView]'s frame loop advances the simulation once per frame for the
 * presented scene; there's no public step function here, matching Apple (the scene drives
 * simulation implicitly).
 *
 */
public class SKPhysicsWorld {
    /**
     * Acceleration applied to every [SKPhysicsBody] with [SKPhysicsBody.affectedByGravity] set.
     * Defaults to `(0, -9.8)`, matching Apple.
     */
    public var gravity: Vector2 = Vector2(0f, -9.8f)

    /** Simulation rate multiplier — `2` runs physics twice as fast as real time, `0` freezes it. Defaults to `1`. */
    public var speed: Float = 1f

    /**
     * Receives [SKPhysicsContact] notifications for bodies whose bitmasks opt into them. `null`
     * (the default) receives none.
     */
    public var contactDelegate: SKPhysicsContactDelegate? = null

    /**
     * The body pairs currently touching (per [SKPhysicsBody.contactTestBitMask]), each keyed by
     * its most recent contact manifold -- lets the simulation report `didEnd` with the last known
     * contact point/normal even on the frame the bodies actually separate.
     */
    internal val activeContacts: MutableMap<Pair<SKPhysicsBody, SKPhysicsBody>, SKContactManifold> = mutableMapOf()

    private val mutableJoints = mutableListOf<SKPhysicsJoint>()

    /** Every [SKPhysicsJoint] currently active in this world. */
    public val joints: List<SKPhysicsJoint> get() = mutableJoints

    /** Activates [joint], whose two bodies must belong to nodes in this world's scene to have any effect. */
    public fun add(joint: SKPhysicsJoint) {
        mutableJoints += joint
    }

    /** Deactivates [joint]. No-op if it isn't currently active. */
    public fun remove(joint: SKPhysicsJoint) {
        mutableJoints -= joint
    }

    /** Deactivates every currently-active joint. */
    public fun removeAllJoints() {
        mutableJoints.clear()
    }
}
