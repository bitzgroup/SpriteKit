package jp.co.bitz.spritekit

import kotlin.math.sqrt

/**
 * How far Apple's `body(in:)` reaches past a body's bounding box: Box2D's 0.1 m AABB margin,
 * 15 points (a rect 10 points away finds the body, 16 points away doesn't — verified on iOS/tvOS
 * simulators).
 */
private const val QUERY_AABB_MARGIN: Float = 0.1f * POINTS_PER_METER

/** One place a ray crosses a body's outline; see [SKPhysicsWorld.enumerateBodies]. */
internal data class SKRayHit(
    val body: SKPhysicsBody,
    val point: Vector2,
    /** Facing back along the ray, at Apple's length of [POINTS_PER_METER] rather than `1`. */
    val normal: Vector2,
    val fraction: Float,
)

/** Every body in [scene]'s tree, paused subtrees included, with its shape in scene space. */
internal fun physicsShapesIn(scene: SKScene): List<Pair<SKPhysicsBody, SKWorldShape>> {
    val out = mutableListOf<Pair<SKPhysicsBody, SKWorldShape>>()

    fun visit(node: SKNode) {
        node.physicsBody?.let { out += it to worldShape(node, scene, it.shape) }
        node.children.forEach(::visit)
    }
    visit(scene)
    return out
}

/** Whether [point] is inside [shape]. An edge chain has no inside, so never. */
internal fun SKWorldShape.contains(point: Vector2): Boolean =
    when (this) {
        is SKWorldShape.Circle -> (point - center).lengthSquared() <= radius * radius
        is SKWorldShape.Polygon -> convexPolygonContains(vertices, point)
        is SKWorldShape.EdgeChain -> false
        is SKWorldShape.Compound -> parts.any { it.contains(point) }
    }

private fun convexPolygonContains(
    vertices: List<Vector2>,
    point: Vector2,
): Boolean {
    var positive = false
    var negative = false
    for (i in vertices.indices) {
        val a = vertices[i]
        val b = vertices[(i + 1) % vertices.size]
        val side = (b - a) cross (point - a)
        if (side > 0f) positive = true
        if (side < 0f) negative = true
    }
    return !(positive && negative)
}

/** Whether [rect] overlaps [shape]'s bounding box widened by [QUERY_AABB_MARGIN], as Apple's `body(in:)` tests. */
internal fun SKWorldShape.queryBoxOverlaps(rect: Rect): Boolean {
    val box = aabb()
    val padded =
        Rect(
            box.left - QUERY_AABB_MARGIN,
            box.top - QUERY_AABB_MARGIN,
            box.right + QUERY_AABB_MARGIN,
            box.bottom + QUERY_AABB_MARGIN,
        )
    return padded.intersects(rect)
}

/**
 * Where the segment [start]→[end] crosses [shape]'s outline, as Apple reports it: a circle or
 * polygon once, where the ray enters it (not at all if [start] is already inside); an edge chain
 * at every segment it crosses, from either side. Each normal faces back toward [start].
 */
internal fun SKWorldShape.rayHits(
    body: SKPhysicsBody,
    start: Vector2,
    end: Vector2,
): List<SKRayHit> =
    when (this) {
        is SKWorldShape.Circle -> listOfNotNull(circleEntry(body, start, end))
        is SKWorldShape.Polygon -> polygonEntry(body, start, end)
        is SKWorldShape.EdgeChain ->
            segmentHits(body, if (closed) closedSegments(vertices) else vertices.zipWithNext(), start, end)
        is SKWorldShape.Compound -> compoundHits(body, start, end)
    }

/**
 * Each part's hits, leaving out those inside another part -- where a ray crosses from one piece
 * of a split concave polygon into the next, say, rather than into the body.
 */
private fun SKWorldShape.Compound.compoundHits(
    body: SKPhysicsBody,
    start: Vector2,
    end: Vector2,
): List<SKRayHit> =
    parts.flatMapIndexed { index, part ->
        part.rayHits(body, start, end).filter { hit ->
            parts.withIndex().none { (other, shape) -> other != index && shape.contains(hit.point) }
        }
    }

private fun SKWorldShape.Polygon.polygonEntry(
    body: SKPhysicsBody,
    start: Vector2,
    end: Vector2,
): List<SKRayHit> {
    if (contains(start)) return emptyList()
    val entry = segmentHits(body, closedSegments(vertices), start, end).minByOrNull { it.fraction }
    return listOfNotNull(entry)
}

private fun closedSegments(vertices: List<Vector2>): List<Pair<Vector2, Vector2>> =
    vertices.indices.map {
        vertices[it] to vertices[(it + 1) % vertices.size]
    }

private fun SKWorldShape.Circle.circleEntry(
    body: SKPhysicsBody,
    start: Vector2,
    end: Vector2,
): SKRayHit? {
    val direction = end - start
    val offset = start - center
    val a = direction dot direction
    val b = 2f * (offset dot direction)
    val c = (offset dot offset) - radius * radius
    val discriminant = b * b - 4f * a * c
    // Starting inside (c < 0) is no hit, as on Apple; nor is a ray of no length or one that misses.
    val fraction = if (c < 0f || a == 0f || discriminant < 0f) -1f else (-b - sqrt(discriminant)) / (2f * a)
    if (fraction !in 0f..1f) return null
    val point = start + direction * fraction
    return SKRayHit(body, point, (point - center).normalized() * POINTS_PER_METER, fraction)
}

private fun segmentHits(
    body: SKPhysicsBody,
    segments: List<Pair<Vector2, Vector2>>,
    start: Vector2,
    end: Vector2,
): List<SKRayHit> {
    val direction = end - start
    return segments.mapNotNull { (a, b) ->
        val edge = b - a
        val denominator = direction cross edge
        if (denominator == 0f) return@mapNotNull null
        val fraction = ((a - start) cross edge) / denominator
        val along = ((a - start) cross direction) / denominator
        if (fraction !in 0f..1f || along !in 0f..1f) return@mapNotNull null
        val perpendicular = Vector2(edge.y, 0f - edge.x).normalized()
        val facing = if ((perpendicular dot direction) > 0f) -perpendicular else perpendicular
        SKRayHit(body, start + direction * fraction, facing * POINTS_PER_METER, fraction)
    }
}
