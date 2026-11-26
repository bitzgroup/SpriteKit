package jp.co.bitz.spritekit

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** How many vertices a circle body's outline has. */
private const val CIRCLE_OUTLINE_VERTICES = 8

/**
 * The outline of [node]'s physics body in its scene's coordinate space, or `null` when it has
 * none. Not part of Apple's SpriteKit, which keeps body shapes private. It exists so that
 * [bitzgroup/GKSKBridge](https://github.com/bitzgroup/GKSKBridge) can mirror Apple's
 * `SKNode.obstacles(fromNodePhysicsBodies:)`, and it gives the outline Apple's obstacles have
 * (verified on iOS/tvOS simulators):
 *
 * - **Rectangle or convex polygon:** its vertices, counterclockwise from the first one it was
 *   given (a clockwise outline is reversed after that first vertex).
 * - **Circle:** eight vertices on the circle at 0°, 45°, …, 315° from its center. The angles are
 *   in scene space, so the node's rotation turns none of them.
 * - **Edge chain:** its vertices, in the order given. A closed chain (`edgeLoopFrom`) repeats its
 *   first vertex at the end.
 * - **Body of one part** (`bodies` with a single body): that part's outline.
 * - `null` for a single edge (`edgeFrom`), for a body of several parts, and for a concave polygon
 *   (which is several convex parts). It is also `null` for a node with no physics body, or one
 *   that isn't in a scene. Apple's framework crashes for a node outside a scene.
 *
 * Vertices go through the node's current transform and its ancestors', up to the scene. Apple's
 * framework uses the transform its physics engine last synced from the node, so it lags behind a
 * node moved, rotated or scaled after its body joined the scene, until the next frame.
 *
 * Like any node access, call it on the scene's render thread.
 */
public fun physicsBodyOutline(node: SKNode): List<Vector2>? {
    val body = node.physicsBody
    val scene = node.scene
    return if (body == null || scene == null) null else outline(body.shape, worldShape(node, scene, body.shape))
}

private fun outline(
    shape: SKPhysicsShape,
    world: SKWorldShape,
): List<Vector2>? =
    when (world) {
        is SKWorldShape.Polygon -> world.vertices
        is SKWorldShape.Circle ->
            List(CIRCLE_OUTLINE_VERTICES) {
                val angle = 2.0 * PI * it / CIRCLE_OUTLINE_VERTICES
                world.center + Vector2(cos(angle).toFloat(), sin(angle).toFloat()) * world.radius
            }
        is SKWorldShape.EdgeChain ->
            when {
                (shape as SKPhysicsShape.EdgeChain).segment -> null
                world.closed -> world.vertices + world.vertices.first()
                else -> world.vertices
            }
        is SKWorldShape.Compound -> {
            val parts = (shape as SKPhysicsShape.Compound).parts
            parts.singleOrNull()?.let { outline(it, world.parts.single()) }
        }
    }
