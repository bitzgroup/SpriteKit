package jp.co.bitz.spritekit

import android.graphics.Path

/**
 * A rigid-body shape and its dynamics, attached to an [SKNode] via [SKNode.physicsBody] — mirrors
 * Apple's `SKPhysicsBody`. Construct via the [circleOfRadius]/[rectangleOf]/[polygonFrom]/
 * [edgeLoopFrom]/[edgeChainFrom]/[edgeFrom]/[bodies] factories, matching Apple's own initializers
 * (`bodies(fromTexture:...)` texture-alpha-mask bodies aren't implemented — see
 * `docs/API_COMPATIBILITY.md`).
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
     * This body's moment of inertia about its center of mass, in kg·m² with lengths in meters of
     * 150 points (as Apple measures it), derived from [density] like [mass]. Not part of Apple's
     * API, which keeps it internal; [applyTorque] and [applyAngularImpulse] change
     * [angularVelocity] by the torque or impulse divided by it.
     */
    public val inertia: Float get() = massProperties.inertia

    /**
     * Where this body's center of mass sits in its node's local space, in points: the shape's
     * centroid. The body turns about it, as Apple's does — a body whose shape is off its node's
     * origin swings the node around it.
     */
    internal val centerOfMass: Vector2 get() = massProperties.centroid

    private val massProperties: SKMassProperties get() = shape.massProperties(density)

    /**
     * Coulomb friction coefficient applied between touching bodies, from `0` (frictionless) up.
     * Defaults to `0.2`, matching Apple.
     */
    public var friction: Float = 0.2f

    /** Bounciness on collision, from `0` (no bounce) to `1` (elastic). Defaults to `0.2`, matching Apple. */
    public var restitution: Float = 0.2f

    /**
     * This body's electrical charge — Apple's `charge`, `0` by default. Stored, and animated by
     * [SKAction.changeChargeTo]/[SKAction.changeChargeBy], but it has no effect yet: this port has
     * no electric or magnetic fields to act on it.
     */
    public var charge: Float = 0f

    /** Fraction of linear velocity retained each second, damping motion. Defaults to `0.1`, matching Apple. */
    public var linearDamping: Float = 0.1f

    /** Fraction of angular velocity retained each second, damping rotation. Defaults to `0.1`, matching Apple. */
    public var angularDamping: Float = 0.1f

    /** Current linear velocity, in points per second. Setting it clears [isResting]. */
    public var velocity: Vector2 = Vector2.Zero
        set(value) {
            field = value
            resting = false
        }

    /** Current angular velocity, in radians per second. Setting it clears [isResting]. */
    public var angularVelocity: Float = 0f
        set(value) {
            field = value
            resting = false
        }

    /**
     * The node this body is attached to via [SKNode.physicsBody] — Apple's `node`. `null` while
     * it's attached to none.
     */
    public var node: SKNode? = null
        internal set

    /**
     * This body's area in square meters, measuring 150 points as one meter as Apple does — so
     * a circle of radius `10` has an area of about `0.014`, and [mass] is [density] times it. The
     * node's scale doesn't change it. `0` for an edge body.
     */
    public val area: Float get() = shapeArea

    private var resting = false

    /**
     * Apple's `isResting`, as it behaves (verified on iOS/tvOS simulators): the simulation never
     * sets it — a body lying still on the floor reads `false` — and setting it `true` stops the
     * body at once (zeroing [velocity] and [angularVelocity]) but lasts only until the next
     * simulation step, or until a velocity is set, which clears it again.
     */
    public var isResting: Boolean
        get() = resting
        set(value) {
            if (value) {
                velocity = Vector2.Zero
                angularVelocity = 0f
            }
            resting = value
        }

    /**
     * The joints in this body's world that connect it, while both of each joint's bodies are in
     * the same scene — Apple's `joints`. Empty while the body isn't in a scene. A joint whose
     * other body's node left the scene isn't listed, as on Apple.
     */
    public val joints: List<SKPhysicsJoint>
        get() {
            val scene = node?.scene ?: return emptyList()
            return scene.physicsWorld.joints.filter { joint ->
                (joint.bodyA === this || joint.bodyB === this) &&
                    joint.bodyA.node?.scene === scene &&
                    joint.bodyB.node?.scene === scene
            }
        }

    /** The bodies [allContactedBodies] reports, refreshed every simulation step. */
    internal var contactedBodies: List<SKPhysicsBody> = emptyList()

    /**
     * The bodies touching this one as of the last simulation step — Apple's
     * `allContactedBodies()`. A touching pair counts when either body collides with the other or
     * tests for contact with it ([collisionBitMask]/[contactTestBitMask] against the other's
     * [categoryBitMask]); two non-dynamic bodies never do. Empty before the first step.
     */
    public fun allContactedBodies(): List<SKPhysicsBody> = contactedBodies

    /** Called at the start of each simulation step: whatever set [isResting], the step clears it. */
    internal fun wake() {
        resting = false
    }

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
     * Whether collisions, joints, forces and impulses can change this body's rotation. Defaults
     * to `true`.
     */
    public var allowsRotation: Boolean = true

    /**
     * When `true`, this body can still rotate about its origin but never translates — Apple's
     * `pinned`. Defaults to `false`.
     */
    public var pinned: Boolean = false

    /**
     * Requests swept (tunneling-resistant) collision detection for this body. Accepted for API
     * parity with no effect — see `docs/API_COMPATIBILITY.md`. Defaults to `false`.
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
     * [inverseInertia] for lever arms and impulses measured in points rather than meters — what a
     * linear impulse applied off the center of mass turns into angular velocity by:
     * `Δω = (r × J) · inverseInertiaInPoints`.
     */
    internal val inverseInertiaInPoints: Float get() = inverseInertia / (POINTS_PER_METER * POINTS_PER_METER)

    /** [centerOfMass] in the coordinates of its node's scene (or topmost ancestor), where velocities live. */
    internal fun worldCenterOfMass(): Vector2 {
        val node = node ?: return centerOfMass
        var root = node
        while (true) root = root.parent ?: break
        return node.convertTo(centerOfMass, root)
    }

    /**
     * Accumulates [force] to be applied continuously over the next physics step (cleared
     * afterward) at the center of mass — Apple's `applyForce(_:)`.
     */
    public fun applyForce(force: Vector2) {
        forceAccumulator += force
    }

    /**
     * Accumulates [force] applied at [at], a point in scene coordinates, for the next physics step
     * — Apple's `applyForce(_:at:)`. Besides pushing the body as [applyForce] does, a point off
     * the center of mass turns it, by the torque `(at − center of mass) × force` with lengths in
     * meters of 150 points (verified on iOS/tvOS simulators).
     */
    public fun applyForce(
        force: Vector2,
        at: Vector2,
    ) {
        forceAccumulator += force
        torqueAccumulator += ((at - worldCenterOfMass()) cross force) / (POINTS_PER_METER * POINTS_PER_METER)
    }

    /** Immediately changes [velocity] by `impulse / mass` — Apple's `applyImpulse(_:)`. */
    public fun applyImpulse(impulse: Vector2) {
        velocity += impulse * inverseMass
    }

    /**
     * Applies [impulse] at [at], a point in scene coordinates — Apple's `applyImpulse(_:at:)`.
     * [velocity] changes as for [applyImpulse]; a point off the center of mass also changes
     * [angularVelocity], by the angular impulse `(at − center of mass) × impulse` with lengths in
     * meters of 150 points (verified on iOS/tvOS simulators).
     */
    public fun applyImpulse(
        impulse: Vector2,
        at: Vector2,
    ) {
        velocity += impulse * inverseMass
        angularVelocity += ((at - worldCenterOfMass()) cross impulse) * inverseInertiaInPoints
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
         * A polygon body with [vertices] as its outline (in the node's local space, either
         * winding, closed back to the first) — Apple's `init(polygonFrom:)` for a path of
         * straight lines. A concave outline works, as Apple's does: the body keeps its full area
         * and collides in its notches.
         */
        public fun polygonFrom(vertices: List<Vector2>): SKPhysicsBody = SKPhysicsBody(polygonShape(vertices))

        /**
         * A polygon body with [path]'s first subpath as its outline, closed back to its start —
         * Apple's `init(polygonFrom:)`, which reads only the first subpath too. Curves are
         * flattened into straight edges, except that a full circle makes an exact circle body, as
         * on Apple (verified on iOS/tvOS simulators). Concave outlines work, as in the
         * vertex-list overload.
         */
        public fun polygonFrom(path: Path): SKPhysicsBody = SKPhysicsBody(pathPolygonShape(firstSubpath(path).points))

        /**
         * A static boundary along [path]'s first subpath — Apple's `init(edgeChainFrom:)`, which
         * reads only the first subpath too; curves are flattened. Closed only if the subpath is.
         * Always [isDynamic] `false`, like [edgeFrom].
         */
        public fun edgeChainFrom(path: Path): SKPhysicsBody {
            val subpath = firstSubpath(path)
            return SKPhysicsBody(SKPhysicsShape.EdgeChain(subpath.points, subpath.closed)).apply { isDynamic = false }
        }

        /**
         * A static boundary along [path]'s first subpath, closed back to its start — Apple's
         * `init(edgeLoopFrom: CGPath)`, which reads only the first subpath too; curves are
         * flattened. Always [isDynamic] `false`, like [edgeFrom].
         */
        public fun edgeLoopFrom(path: Path): SKPhysicsBody =
            SKPhysicsBody(
                SKPhysicsShape.EdgeChain(firstSubpath(path).points, closed = true),
            ).apply { isDynamic = false }

        /**
         * One body made of [bodies]' shapes, each where it sits in its own body — Apple's
         * `init(bodies:)`. Every part collides, so a body can have gaps. As on Apple (verified
         * on iOS/tvOS simulators): its area is the parts' areas summed, its [density],
         * [restitution] and [friction] are the first body's (so its mass is that density times
         * the summed area), and everything else starts at the defaults. A compound among
         * [bodies] adds its parts to collide with but no mass. Of no bodies, it has no shape and
         * a density of `0`.
         */
        public fun bodies(bodies: List<SKPhysicsBody>): SKPhysicsBody {
            val parts = bodies.flatMap { (it.shape as? SKPhysicsShape.Compound)?.parts ?: listOf(it.shape) }
            val solidParts = bodies.map { it.shape }.filter { it !is SKPhysicsShape.Compound }
            val first = bodies.firstOrNull()
            return SKPhysicsBody(SKPhysicsShape.Compound(parts, solidParts)).apply {
                density = first?.density ?: 0f
                restitution = first?.restitution ?: restitution
                friction = first?.friction ?: friction
            }
        }

        private fun firstSubpath(path: Path): SKFlattenedContour =
            flattenPath(path).firstOrNull() ?: SKFlattenedContour(emptyList(), closed = false)

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
            SKPhysicsBody(SKPhysicsShape.EdgeChain(listOf(point1, point2), closed = false, segment = true)).apply {
                isDynamic = false
            }

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
