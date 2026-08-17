package jp.co.bitz.spritekit

/**
 * A constraint between two [SKPhysicsBody]s, active once added to an [SKPhysicsWorld] via
 * [SKPhysicsWorld.add] — mirrors Apple's `SKPhysicsJoint` family. Construct one of the concrete
 * subclasses directly (idiomatic Kotlin constructors, rather than Apple's
 * `joint(withBodyA:bodyB:...)` class-method factories).
 *
 * This port's joint solver is **linear-only**, the same simplification collision response already
 * makes (see `docs/API_COMPATIBILITY.md`'s Physics section): joints correct body *position* (and,
 * for [SKPhysicsJointFixed] only, rotation, kinematically) to satisfy their constraint, but don't
 * exchange torque, so a body's own rotation otherwise stays driven only by its own
 * [SKPhysicsBody.angularVelocity]/`applyTorque`. Apple's `reactionForce`/`reactionTorque`
 * (read-only, post-simulation) aren't implemented.
 */
public sealed class SKPhysicsJoint {
    public abstract val bodyA: SKPhysicsBody
    public abstract val bodyB: SKPhysicsBody

    /** [bodyA]'s/[bodyB]'s world-space binding point(s), as given to this joint's constructor. */
    internal abstract val anchorPointA: Vector2
    internal abstract val anchorPointB: Vector2

    /**
     * [anchorPointA]/[anchorPointB], re-expressed in each body's own local space -- cached lazily
     * (see `docs/API_COMPATIBILITY.md`'s note on this port's lazy anchor binding) the first time
     * the simulation processes this joint, then reused every frame after.
     */
    internal var localAnchorA: Vector2? = null
    internal var localAnchorB: Vector2? = null
}

/**
 * Pins [bodyA] and [bodyB] together at a common world-space [anchor] point, letting them rotate
 * freely about it — mirrors Apple's `SKPhysicsJointPin`.
 *
 * [shouldEnableLimits]/[lowerAngleLimit]/[upperAngleLimit]/[frictionTorque] are accepted for API
 * parity but not enforced by the solver, which doesn't model relative rotation between the two
 * bodies at all — see `docs/API_COMPATIBILITY.md`.
 */
public class SKPhysicsJointPin(
    override val bodyA: SKPhysicsBody,
    override val bodyB: SKPhysicsBody,
    public val anchor: Vector2,
) : SKPhysicsJoint() {
    public var shouldEnableLimits: Boolean = false
    public var lowerAngleLimit: Float = 0f
    public var upperAngleLimit: Float = 0f
    public var frictionTorque: Float = 0f

    override val anchorPointA: Vector2 get() = anchor
    override val anchorPointB: Vector2 get() = anchor
}

/**
 * Welds [bodyA] and [bodyB] rigidly together at a common world-space [anchor] point — mirrors
 * Apple's `SKPhysicsJointFixed`. In addition to [SKPhysicsJointPin]'s positional coupling, keeps
 * [bodyB]'s rotation locked to [bodyA]'s, offset by whatever their relative rotation was the first
 * time the simulation processes this joint — a kinematic sync rather than a torque exchange, see
 * `docs/API_COMPATIBILITY.md`.
 */
public class SKPhysicsJointFixed(
    override val bodyA: SKPhysicsBody,
    override val bodyB: SKPhysicsBody,
    public val anchor: Vector2,
) : SKPhysicsJoint() {
    internal var relativeRotation: Float? = null

    override val anchorPointA: Vector2 get() = anchor
    override val anchorPointB: Vector2 get() = anchor
}

/**
 * A damped spring pulling [anchorA] (on [bodyA]) and [anchorB] (on [bodyB]) together — mirrors
 * Apple's `SKPhysicsJointSpring`. [frequency]/[damping] shape the spring's stiffness/settling
 * behavior; this port derives stiffness from [frequency] as `(2π·frequency)²` and applies
 * [damping] as a direct velocity-damping coefficient along the spring's axis — an approximation of
 * Apple's own (undocumented) damped-harmonic-oscillator formula, see `docs/API_COMPATIBILITY.md`.
 */
public class SKPhysicsJointSpring(
    override val bodyA: SKPhysicsBody,
    override val bodyB: SKPhysicsBody,
    public val anchorA: Vector2,
    public val anchorB: Vector2,
) : SKPhysicsJoint() {
    public var frequency: Float = 1f
    public var damping: Float = 0.3f

    internal var restLength: Float? = null

    override val anchorPointA: Vector2 get() = anchorA
    override val anchorPointB: Vector2 get() = anchorB
}

/**
 * Constrains [bodyA] and [bodyB] to slide relative to each other only along [axis] (a world-space
 * direction, fixed for the joint's lifetime — it doesn't rotate with either body, see
 * `docs/API_COMPATIBILITY.md`), through a shared world-space [anchor] point — mirrors Apple's
 * `SKPhysicsJointSliding`. [shouldEnableLimits]/[lowerDistanceLimit]/[upperDistanceLimit] bound
 * how far the bodies may slide apart along [axis].
 */
public class SKPhysicsJointSliding(
    override val bodyA: SKPhysicsBody,
    override val bodyB: SKPhysicsBody,
    public val anchor: Vector2,
    public val axis: Vector2,
) : SKPhysicsJoint() {
    public var shouldEnableLimits: Boolean = false
    public var lowerDistanceLimit: Float = 0f
    public var upperDistanceLimit: Float = 0f

    override val anchorPointA: Vector2 get() = anchor
    override val anchorPointB: Vector2 get() = anchor
}

/**
 * A maximum-distance constraint (like a taut rope) between [anchorA] (on [bodyA]) and [anchorB]
 * (on [bodyB]) — mirrors Apple's `SKPhysicsJointLimit`. [maxLength] defaults to the distance
 * between the two anchors the first time the simulation processes this joint, matching Apple's
 * documented default (`the distance between anchorA and anchorB when the joint is created`,
 * adjusted for this port's lazy anchor binding — see `docs/API_COMPATIBILITY.md`).
 */
public class SKPhysicsJointLimit(
    override val bodyA: SKPhysicsBody,
    override val bodyB: SKPhysicsBody,
    public val anchorA: Vector2,
    public val anchorB: Vector2,
) : SKPhysicsJoint() {
    public var maxLength: Float = Float.POSITIVE_INFINITY

    override val anchorPointA: Vector2 get() = anchorA
    override val anchorPointB: Vector2 get() = anchorB
}
