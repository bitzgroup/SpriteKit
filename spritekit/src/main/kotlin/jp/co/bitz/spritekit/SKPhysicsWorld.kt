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

    /**
     * Activates [joint], whose two bodies must belong to nodes in this world's scene to have any
     * effect. When both already do, its anchors are fixed to the bodies right away, from where
     * they are now, as on Apple; otherwise that happens on the first simulation step that finds
     * both in the scene.
     */
    public fun add(joint: SKPhysicsJoint) {
        mutableJoints += joint
        val nodeA = joint.bodyA.node
        val nodeB = joint.bodyB.node
        val scene = nodeA?.scene?.takeIf { nodeB?.scene === it }
        if (nodeA != null && nodeB != null && scene != null) {
            bindJoints(listOf(joint), mapOf(joint.bodyA to nodeA, joint.bodyB to nodeB), scene)
        }
    }

    /** The scene that owns this world, set by [SKScene]. */
    internal var scene: SKScene? = null

    private fun shapes(): List<Pair<SKPhysicsBody, SKWorldShape>> = scene?.let(::physicsShapesIn) ?: emptyList()

    /**
     * A body whose shape contains [at], a point in scene coordinates — Apple's `body(at:)`. Edge
     * bodies have no inside and are never found. Category masks don't matter. If several bodies
     * contain the point, which one is returned isn't specified (as on Apple).
     */
    public fun body(at: Vector2): SKPhysicsBody? = bodiesAt(at).firstOrNull()

    /** Calls [block] for every body [body] (`at`) could return — Apple's `enumerateBodies(at:using:)`. */
    public fun enumerateBodies(
        at: Vector2,
        block: (SKPhysicsBody) -> Unit,
    ) {
        bodiesAt(at).forEach(block)
    }

    /**
     * A body near [rect] (scene coordinates) — Apple's `body(in:)`, which tests bounding boxes,
     * not shapes: it finds any body whose bounding box, widened by 15 points, overlaps [rect], so
     * also an edge loop around the rect, or a circle whose box corner the rect touches (verified
     * on iOS/tvOS simulators). Renamed from `in`, a Kotlin keyword.
     */
    public fun body(rect: Rect): SKPhysicsBody? = bodiesIn(rect).firstOrNull()

    /** Calls [block] for every body [body] (`rect`) could return — Apple's `enumerateBodies(in:using:)`. */
    public fun enumerateBodies(
        rect: Rect,
        block: (SKPhysicsBody) -> Unit,
    ) {
        bodiesIn(rect).forEach(block)
    }

    /**
     * The first body the segment from [alongRayStart] to [end] hits — Apple's
     * `body(alongRayStart:end:)`. A body the ray starts inside isn't hit.
     */
    public fun body(
        alongRayStart: Vector2,
        end: Vector2,
    ): SKPhysicsBody? = rayHits(alongRayStart, end).firstOrNull()?.body

    /**
     * Calls [block] for every place the segment from [alongRayStart] to [end] crosses a body,
     * nearest first, with the point and the surface normal there — Apple's
     * `enumerateBodies(alongRayStart:end:using:)`. As on Apple (verified on iOS/tvOS simulators):
     * a circle or polygon is hit once, where the ray enters it, and not at all if the ray starts
     * inside it; an edge is hit wherever the ray crosses it, from either side; and the normal
     * faces back along the ray with a length of `150`, not `1`.
     */
    public fun enumerateBodies(
        alongRayStart: Vector2,
        end: Vector2,
        block: (body: SKPhysicsBody, point: Vector2, normal: Vector2) -> Unit,
    ) {
        rayHits(alongRayStart, end).forEach { block(it.body, it.point, it.normal) }
    }

    private fun bodiesAt(point: Vector2): List<SKPhysicsBody> =
        shapes().filter {
            it.second.contains(point)
        }.map { it.first }

    private fun bodiesIn(rect: Rect): List<SKPhysicsBody> =
        shapes().filter {
            it.second.queryBoxOverlaps(rect)
        }.map { it.first }

    private fun rayHits(
        start: Vector2,
        end: Vector2,
    ): List<SKRayHit> = shapes().flatMap { (body, shape) -> shape.rayHits(body, start, end) }.sortedBy { it.fraction }

    /** Deactivates [joint]. No-op if it isn't currently active. */
    public fun remove(joint: SKPhysicsJoint) {
        mutableJoints -= joint
    }

    /** Deactivates every currently-active joint. */
    public fun removeAllJoints() {
        mutableJoints.clear()
    }
}
