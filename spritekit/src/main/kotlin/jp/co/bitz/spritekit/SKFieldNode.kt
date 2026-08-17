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
 * limitation Phase 6 documented for `SKConstraint`. [isExclusive] is stored but not enforced. See
 * `docs/API_COMPATIBILITY.md`.
 */
public class SKFieldNode private constructor(
    internal val kind: SKFieldKind,
) : SKNode() {
    /**
     * Scales this field's effect. Negative values reverse it (e.g. a repelling
     * [radialGravityField]). Defaults to `1`.
     */
    public var strength: Float = 1f

    /**
     * The exponent controlling how a [radialGravityField]'s strength falls off with distance —
     * `0` (the default) means constant strength regardless of distance; `2` approximates
     * inverse-square gravity. Ignored by [linearGravityField]/[dragField]/[velocityField], which
     * have no notion of distance from the field.
     */
    public var falloff: Float = 0f

    /**
     * The distance below which [falloff] stops increasing a [radialGravityField]'s strength
     * further, avoiding a singularity at the field's own position.
     */
    public var minimumRadius: Float = 0f

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
     * The direction [linearGravityField] accelerates bodies in (normalized before [strength] is
     * applied) or the target velocity [velocityField] drives bodies towards. Unused by
     * [radialGravityField]/[dragField].
     */
    public var direction: Vector2 = Vector2.Zero

    public companion object {
        /** A field that accelerates bodies towards (or, with negative [strength], away from) this node's position. */
        public fun radialGravityField(): SKFieldNode = SKFieldNode(SKFieldKind.RadialGravity)

        /**
         * A field that accelerates every affected body uniformly along [direction], scaled by
         * [strength] — like a local, opt-in override of [SKPhysicsWorld.gravity].
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
