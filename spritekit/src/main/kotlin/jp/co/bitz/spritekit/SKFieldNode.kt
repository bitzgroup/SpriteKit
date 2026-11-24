package jp.co.bitz.spritekit

/**
 * A region of the scene that applies a force (or, for [velocityField], a direct velocity) to
 * every [SKPhysicsBody] within it whose [SKPhysicsBody.fieldBitMask] matches [categoryBitMask] —
 * mirrors Apple's `SKFieldNode`. Construct via [radialGravityField]/[linearGravityField]/
 * [dragField]/[velocityField], matching Apple's factory-method surface but as idiomatic Kotlin
 * companion functions (same pattern [SKPhysicsBody]'s shape factories already use), all
 * configuring instances of this one class rather than separate subclasses, matching Apple's own
 * shape here.
 *
 * Only this subset of Apple's field kinds is implemented — noise, turbulence, electric, magnetic,
 * spring, vortex, and checkerboard-texture fields aren't; see `docs/ROADMAP.md`'s "Explicitly Out
 * of Scope". Apple's `region` property isn't implemented either — every field is always unbounded
 * (as if `region` were `null`), since this port has no `SKRegion` implementation, the same
 * limitation `SKConstraint` has. [isExclusive] is stored but not enforced. See
 * `docs/API_COMPATIBILITY.md`.
 */
public class SKFieldNode private constructor(
    internal val kind: SKFieldKind,
) : SKNode() {
    /**
     * Scales this field's effect. Negative values reverse it (e.g. a repelling
     * [radialGravityField]). Defaults to `1`. For the gravity fields, `1` accelerates at one meter
     * per second squared — 150 points per second squared, the same units as
     * [SKPhysicsWorld.gravity] — before [falloff] applies.
     */
    public var strength: Float = 1f

    /**
     * The exponent of how a gravity field weakens with distance from this node: its strength is
     * divided by the distance in meters (150 points) raised to this power, so `0` keeps it constant
     * and `2` is inverse-square. Applies to [radialGravityField] and [linearGravityField], as on
     * Apple; [dragField] and [velocityField] ignore it. Defaults to `2` for [radialGravityField]
     * and `0` for the others, matching Apple.
     */
    public var falloff: Float = if (kind == SKFieldKind.RadialGravity) 2f else 0f

    /**
     * The distance, in points, below which [falloff] stops strengthening the field further,
     * avoiding a singularity at the field's own position. Defaults to Apple's `0.0045776367`.
     */
    public var minimumRadius: Float = DEFAULT_MINIMUM_RADIUS

    /** Whether this field currently affects bodies at all. Defaults to `true`. */
    public var isEnabled: Boolean = true

    /**
     * When `true`, only this field (and other currently-exclusive fields) should affect bodies in
     * its region, suppressing all non-exclusive fields there. Accepted for API parity but not
     * enforced — every enabled, bitmask-matching field affects a body regardless of this flag; see
     * `docs/API_COMPATIBILITY.md`.
     */
    public var isExclusive: Boolean = false

    /** Which [SKPhysicsBody.fieldBitMask] categories this field affects. Defaults to all bits set, matching Apple. */
    public var categoryBitMask: Int = -1

    /**
     * The acceleration [linearGravityField] applies (in meters per second squared, multiplied by
     * [strength] and *not* normalized, as on Apple) or the target velocity [velocityField] drives
     * bodies towards. Unused by [radialGravityField]/[dragField].
     */
    public var direction: Vector2 = Vector2.Zero

    override fun copy(): SKFieldNode = copyInto(SKFieldNode(kind))

    override fun copyState(target: SKNode) {
        super.copyState(target)
        if (target !is SKFieldNode) return
        target.strength = strength
        target.falloff = falloff
        target.minimumRadius = minimumRadius
        target.isEnabled = isEnabled
        target.isExclusive = isExclusive
        target.categoryBitMask = categoryBitMask
        target.direction = direction
    }

    public companion object {
        /** Apple's default [minimumRadius], in points. */
        private const val DEFAULT_MINIMUM_RADIUS = 0.0045776367f

        /** A field that accelerates bodies towards (or, with negative [strength], away from) this node's position. */
        public fun radialGravityField(): SKFieldNode = SKFieldNode(SKFieldKind.RadialGravity)

        /**
         * A field that accelerates every affected body by [direction] (meters per second squared),
         * scaled by [strength] — like a local, opt-in addition to [SKPhysicsWorld.gravity].
         */
        public fun linearGravityField(direction: Vector2): SKFieldNode =
            SKFieldNode(SKFieldKind.LinearGravity).apply { this.direction = direction }

        /** A field that decelerates bodies proportionally to their own velocity, simulating air/fluid resistance. */
        public fun dragField(): SKFieldNode = SKFieldNode(SKFieldKind.Drag)

        /**
         * A field that drives every affected body's velocity directly towards `vector *
         * strength`, rather than applying a force.
         */
        public fun velocityField(vector: Vector2): SKFieldNode =
            SKFieldNode(SKFieldKind.Velocity).apply { direction = vector }
    }
}

/** Which built-in behavior an [SKFieldNode] instance was configured with — not part of the public API. */
internal enum class SKFieldKind {
    RadialGravity,
    LinearGravity,
    Drag,
    Velocity,
}
