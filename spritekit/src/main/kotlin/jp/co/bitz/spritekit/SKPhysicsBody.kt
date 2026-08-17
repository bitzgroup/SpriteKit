package jp.co.bitz.spritekit

/**
 * A rigid-body shape and its dynamics, attached to an [SKNode] via [SKNode.physicsBody] — mirrors
 * Apple's `SKPhysicsBody`. Construct via the [circleOfRadius]/[rectangleOf]/[polygonFrom]/
 * [edgeLoopFrom]/[edgeFrom] factories, matching Apple's own class-method surface (`bodies(fromTexture:...)`
 * texture-alpha-mask bodies aren't implemented — see `docs/API_COMPATIBILITY.md`).
 *
 * Simulated by [SKPhysicsWorld] once its owning node is part of a presented [SKScene]'s tree. All
 * state is confined to [SKView]'s render thread, like the rest of the scene graph.
 */
public class SKPhysicsBody internal constructor(
    internal val shape: SKPhysicsShape,
) {
    private val shapeArea: Float = shape.area()

    /**
     * Mass per unit area — the source of truth [mass] and [inertia] are derived from. Defaults to
     * `1`, matching Apple. Ignored (along with [mass]/[inertia], both `0`) for [edgeLoopFrom]/
     * [edgeFrom] bodies, which have no interior.
     */
    public var density: Float = 1f

    /**
     * This body's mass. Derived from [density] and this body's shape; setting it directly
     * back-computes [density] instead (matching Apple's documented `mass`/`density` relationship),
     * leaving it unchanged for a zero-area shape (an edge body), which has no meaningful density.
     */
    public var mass: Float
        get() = massProperties.mass
        set(value) {
            if (shapeArea > 0f) density = value / shapeArea
        }

    /**
     * This body's moment of inertia about its own origin (i.e. [SKNode.position]), derived from
     * [density] like [mass].
     */
    public val inertia: Float get() = massProperties.inertia

    private val massProperties: SKMassProperties get() = shape.massProperties(density)

    /**
     * Coulomb friction coefficient applied between touching bodies, from `0` (frictionless) up.
     * Defaults to `0.2`, matching Apple.
     */
    public var friction: Float = 0.2f

    /** Bounciness on collision, from `0` (no bounce) to `1` (elastic). Defaults to `0.2`, matching Apple. */
    public var restitution: Float = 0.2f

    /** Fraction of linear velocity retained each second, damping motion. Defaults to `0.1`, matching Apple. */
    public var linearDamping: Float = 0.1f

    /** Fraction of angular velocity retained each second, damping rotation. Defaults to `0.1`, matching Apple. */
    public var angularDamping: Float = 0.1f

    /** Current linear velocity, in points per second. */
    public var velocity: Vector2 = Vector2.Zero

    /** Current angular velocity, in radians per second. */
    public var angularVelocity: Float = 0f

    /**
     * Whether [SKPhysicsWorld] moves this body (forces, gravity, collision response) or treats it
     * as an immovable obstacle other bodies collide against. Defaults to `true`, except for
     * [edgeLoopFrom]/[edgeFrom] bodies, which default to `false` (edges have no interior/mass and
     * so can't sensibly be simulated as movers).
     */
    public var isDynamic: Boolean = true

    /** Whether [SKPhysicsWorld.gravity] applies to this body. Defaults to `true`. */
    public var affectedByGravity: Boolean = true

    /**
     * Whether collision response and [applyTorque]/[applyAngularImpulse] can change this body's
     * rotation. Defaults to `true`.
     */
    public var allowsRotation: Boolean = true

    /**
     * When `true`, this body can still rotate about its origin but never translates — Apple's
     * `pinned`. Defaults to `false`.
     */
    public var pinned: Boolean = false

    /**
     * Requests swept (tunneling-resistant) collision detection for this body. Accepted for API
     * parity but not yet implemented — see `docs/API_COMPATIBILITY.md`. Defaults to `false`.
     */
    public var usesPreciseCollisionDetection: Boolean = false

    /**
     * Which collision "categories" this body belongs to, as a bitmask. Defaults to all bits set,
     * matching Apple.
     */
    public var categoryBitMask: Int = -1

    /**
     * Which [categoryBitMask] categories this body physically collides with. Defaults to all
     * bits set, matching Apple.
     */
    public var collisionBitMask: Int = -1

    /**
     * Which [categoryBitMask] categories produce an `SKPhysicsContact` for this body without
     * necessarily colliding. Defaults to `0`.
     */
    public var contactTestBitMask: Int = 0

    /** Which [SKFieldNode.categoryBitMask] categories affect this body. Defaults to all bits set, matching Apple. */
    public var fieldBitMask: Int = -1

    internal var forceAccumulator: Vector2 = Vector2.Zero
        private set
    internal var torqueAccumulator: Float = 0f
        private set

    /**
     * `1/mass`, or `0` for an immovable body ([isDynamic] `false`, [pinned], or a zero-mass
     * shape) — Box2D-style convention used by [SKPhysicsWorld]'s solver.
     */
    internal val inverseMass: Float get() = if (!isDynamic || mass <= 0f) 0f else 1f / mass

    /**
     * `1/inertia`, or `0` when this body can't rotate ([isDynamic] `false`, [allowsRotation]
     * `false`, or zero inertia).
     */
    internal val inverseInertia: Float get() = if (!isDynamic || !allowsRotation || inertia <= 0f) 0f else 1f / inertia

    /**
     * Accumulates [force] to be applied continuously over the next physics step (cleared
     * afterward) — Apple's `applyForce(_:)`. The position-offset `applyForce(_:at:)` overload
     * (which would also impart torque) isn't implemented; see `docs/API_COMPATIBILITY.md`.
     */
    public fun applyForce(force: Vector2) {
        forceAccumulator += force
    }

    /**
     * Immediately changes [velocity] by `impulse / mass` — Apple's `applyImpulse(_:)`. The
     * position-offset `applyImpulse(_:at:)` overload isn't implemented; see
     * `docs/API_COMPATIBILITY.md`.
     */
    public fun applyImpulse(impulse: Vector2) {
        velocity += impulse * inverseMass
    }

    /**
     * Accumulates [torque] to be applied continuously over the next physics step (cleared
     * afterward) — Apple's `applyTorque(_:)`.
     */
    public fun applyTorque(torque: Float) {
        torqueAccumulator += torque
    }

    /** Immediately changes [angularVelocity] by `impulse / inertia` — Apple's `applyAngularImpulse(_:)`. */
    public fun applyAngularImpulse(impulse: Float) {
        angularVelocity += impulse * inverseInertia
    }

    /**
     * Clears [forceAccumulator]/[torqueAccumulator] after [SKPhysicsWorld] has integrated them
     * for the current step.
     */
    internal fun clearAccumulators() {
        forceAccumulator = Vector2.Zero
        torqueAccumulator = 0f
    }

    public companion object {
        /** A circular body of [radius], centered on its node's origin. */
        public fun circleOfRadius(radius: Float): SKPhysicsBody = SKPhysicsBody(SKPhysicsShape.Circle(radius))

        /** A circular body of [radius], offset from its node's origin by [center]. */
        public fun circleOfRadius(
            radius: Float,
            center: Vector2,
        ): SKPhysicsBody = SKPhysicsBody(SKPhysicsShape.Circle(radius, center))

        /** A rectangular body of [size], centered on its node's origin. */
        public fun rectangleOf(size: Vector2): SKPhysicsBody =
            SKPhysicsBody(SKPhysicsShape.Polygon(rectangleVertices(size, Vector2.Zero)))

        /** A rectangular body of [size], offset from its node's origin by [center]. */
        public fun rectangleOf(
            size: Vector2,
            center: Vector2,
        ): SKPhysicsBody = SKPhysicsBody(SKPhysicsShape.Polygon(rectangleVertices(size, center)))

        /**
         * A convex polygon body from [vertices] (CCW winding, in the node's local space) — Apple's
         * `polygonFrom(_:)`, taking a `CGPath` there; this library has no path type, so the vertex
         * list is passed directly (see `docs/API_COMPATIBILITY.md`). Concave input isn't validated
         * or corrected, matching Apple's own "must be convex" contract.
         */
        public fun polygonFrom(vertices: List<Vector2>): SKPhysicsBody = SKPhysicsBody(SKPhysicsShape.Polygon(vertices))

        /**
         * A static boundary following [rect]'s four edges — Apple's `edgeLoopFrom(_:)`. Always
         * [isDynamic] `false`: an edge body has no interior/mass to simulate as a mover.
         */
        public fun edgeLoopFrom(rect: Rect): SKPhysicsBody =
            SKPhysicsBody(SKPhysicsShape.EdgeChain(corners(rect), closed = true)).apply { isDynamic = false }

        /**
         * A static boundary segment from [point1] to [point2] — Apple's `edgeFrom(_:to:)`. Always
         * [isDynamic] `false`, for the same reason as [edgeLoopFrom].
         */
        public fun edgeFrom(
            point1: Vector2,
            point2: Vector2,
        ): SKPhysicsBody =
            SKPhysicsBody(SKPhysicsShape.EdgeChain(listOf(point1, point2), closed = false)).apply { isDynamic = false }

        private fun rectangleVertices(
            size: Vector2,
            center: Vector2,
        ): List<Vector2> {
            val halfWidth = size.x / 2f
            val halfHeight = size.y / 2f
            return listOf(
                Vector2(center.x - halfWidth, center.y - halfHeight),
                Vector2(center.x + halfWidth, center.y - halfHeight),
                Vector2(center.x + halfWidth, center.y + halfHeight),
                Vector2(center.x - halfWidth, center.y + halfHeight),
            )
        }
    }
}
