package jp.co.bitz.spritekit

/**
 * A [SKPhysicsShape], already transformed into world (scene) space for one frame — what
 * [narrowPhase] actually operates on. Kept separate from [SKPhysicsShape] (which is expressed in
 * a node's local space) so this whole file stays pure Kotlin/unit-testable, with no [SKNode]
 * dependency.
 */
internal sealed class SKWorldShape {
    data class Circle(val center: Vector2, val radius: Float) : SKWorldShape()

    /** A convex polygon, CCW winding — both [SKPhysicsShape.Polygon] and rectangle bodies become this. */
    data class Polygon(val vertices: List<Vector2>) : SKWorldShape()

    data class EdgeChain(val vertices: List<Vector2>, val closed: Boolean) : SKWorldShape()
}

/** The axis-aligned bounding box of [SKWorldShape], used for broad-phase overlap tests. */
internal fun SKWorldShape.aabb(): Rect =
    when (this) {
        is SKWorldShape.Circle -> Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius)
        is SKWorldShape.Polygon -> boundingRectOf(vertices)
        is SKWorldShape.EdgeChain -> boundingRectOf(vertices)
    }

/** Whether [a] and [b]'s [aabb]s overlap — the O(n²) broad phase run over every body pair. */
internal fun broadPhaseOverlap(
    a: SKWorldShape,
    b: SKWorldShape,
): Boolean = a.aabb().intersects(b.aabb())

/**
 * A collision between two shapes: [normal] points from the first shape passed to [narrowPhase]
 * toward the second, [penetration] is how deep they overlap along it, and [points] are where they
 * touch in world space — one for a contact involving a circle, up to two for two polygons (or a
 * polygon and an edge) meeting face to face, clipped as Box2D clips them. The solver pushes at
 * each of them, which is what turns a body landing on a corner. [point] is the one contact point
 * reported to [SKPhysicsContactDelegate]: their average.
 */
internal data class SKContactManifold(
    val normal: Vector2,
    val penetration: Float,
    val points: List<Vector2>,
) {
    constructor(normal: Vector2, penetration: Float, point: Vector2) : this(normal, penetration, listOf(point))

    val point: Vector2 get() = points.reduce { sum, p -> sum + p } * (1f / points.size)
}

/**
 * Tests [a] against [b] for overlap, returning the contact manifold if they do — the narrow
 * phase, run only on pairs [broadPhaseOverlap] already flagged. `null` for two [SKWorldShape.EdgeChain]s
 * (two static boundaries never need to collide with each other).
 */
internal fun narrowPhase(
    a: SKWorldShape,
    b: SKWorldShape,
): SKContactManifold? =
    when (a) {
        is SKWorldShape.Circle -> narrowPhaseFromCircle(a, b)
        is SKWorldShape.Polygon -> narrowPhaseFromPolygon(a, b)
        is SKWorldShape.EdgeChain -> narrowPhaseFromEdgeChain(a, b)
    }

private fun narrowPhaseFromCircle(
    a: SKWorldShape.Circle,
    b: SKWorldShape,
): SKContactManifold? =
    when (b) {
        is SKWorldShape.Circle -> circleCircle(a, b)
        is SKWorldShape.Polygon -> circlePolygon(a, b)
        is SKWorldShape.EdgeChain -> circleEdgeChain(a, b)
    }

private fun narrowPhaseFromPolygon(
    a: SKWorldShape.Polygon,
    b: SKWorldShape,
): SKContactManifold? =
    when (b) {
        is SKWorldShape.Circle -> circlePolygon(b, a)?.flip()
        is SKWorldShape.Polygon -> polygonPolygon(a.vertices, b.vertices)
        is SKWorldShape.EdgeChain -> polygonEdgeChain(a, b)
    }

private fun narrowPhaseFromEdgeChain(
    a: SKWorldShape.EdgeChain,
    b: SKWorldShape,
): SKContactManifold? =
    when (b) {
        is SKWorldShape.Circle -> circleEdgeChain(b, a)?.flip()
        is SKWorldShape.Polygon -> polygonEdgeChain(b, a)?.flip()
        is SKWorldShape.EdgeChain -> null
    }

private fun SKContactManifold.flip(): SKContactManifold = copy(normal = -normal)

private fun circleCircle(
    a: SKWorldShape.Circle,
    b: SKWorldShape.Circle,
): SKContactManifold? {
    val delta = b.center - a.center
    val distance = delta.length()
    val radiusSum = a.radius + b.radius
    if (distance >= radiusSum) return null
    val normal = if (distance > 0f) delta * (1f / distance) else Vector2(1f, 0f)
    return SKContactManifold(normal, radiusSum - distance, a.center + normal * a.radius)
}

/** The outward-facing normal of `vertices[edgeIndex] -> vertices[edgeIndex + 1]`, for a CCW-wound polygon. */
internal fun outwardNormal(
    vertices: List<Vector2>,
    edgeIndex: Int,
): Vector2 {
    val edge = vertices[(edgeIndex + 1) % vertices.size] - vertices[edgeIndex]
    return Vector2(edge.y, 0f - edge.x).normalized()
}

private fun closestPointOnSegment(
    point: Vector2,
    a: Vector2,
    b: Vector2,
): Vector2 {
    val edge = b - a
    val lengthSquared = edge.lengthSquared()
    if (lengthSquared == 0f) return a
    val t = (((point - a) dot edge) / lengthSquared).coerceIn(0f, 1f)
    return a + edge * t
}

/** An arbitrary (but consistent) perpendicular to `a -> b`, used where a segment has no inherent "outward" side. */
private fun segmentNormal(
    a: Vector2,
    b: Vector2,
): Vector2 {
    val edge = b - a
    return Vector2(edge.y, 0f - edge.x).normalized()
}

internal fun midpoint(
    a: Vector2,
    b: Vector2,
): Vector2 = (a + b) * 0.5f

// Early returns are the clearest way to express "no collision" at each stage of the SAT test.
@Suppress("ReturnCount")
private fun circlePolygon(
    circle: SKWorldShape.Circle,
    polygon: SKWorldShape.Polygon,
): SKContactManifold? {
    val vertices = polygon.vertices
    var bestEdge = 0
    var maxSeparation = Float.NEGATIVE_INFINITY
    for (i in vertices.indices) {
        val separation = outwardNormal(vertices, i) dot (circle.center - vertices[i])
        if (separation > maxSeparation) {
            maxSeparation = separation
            bestEdge = i
        }
    }
    if (maxSeparation > circle.radius) return null

    val a = vertices[bestEdge]
    val b = vertices[(bestEdge + 1) % vertices.size]
    val edgeNormal = outwardNormal(vertices, bestEdge)
    val closest = closestPointOnSegment(circle.center, a, b)

    if (maxSeparation < 0f) {
        // The circle's center is inside the polygon -- push it out along the shallowest edge.
        return SKContactManifold(-edgeNormal, circle.radius - maxSeparation, closest)
    }
    val toClosest = closest - circle.center
    val distance = toClosest.length()
    if (distance >= circle.radius) return null
    val normal = if (distance > 0f) toClosest * (1f / distance) else -edgeNormal
    return SKContactManifold(normal, circle.radius - distance, closest)
}

/**
 * The separation of [reference]'s farthest-out edge from every point in [other] (negative when
 * they overlap), and that edge's index.
 */
private fun maxSeparation(
    reference: List<Vector2>,
    other: List<Vector2>,
): Pair<Float, Int> {
    var bestSeparation = Float.NEGATIVE_INFINITY
    var bestEdge = 0
    for (i in reference.indices) {
        val normal = outwardNormal(reference, i)
        val a = reference[i]
        val minProjection = other.minOf { normal dot (it - a) }
        if (minProjection > bestSeparation) {
            bestSeparation = minProjection
            bestEdge = i
        }
    }
    return bestSeparation to bestEdge
}

/**
 * Two convex polygons (a segment counts, as a two-vertex polygon), tested on every edge normal of
 * both (SAT), with Box2D's contact clipping: the edge of least penetration is the reference face,
 * the other polygon's edge most facing it is clipped to the reference face's sides, and its ends
 * still behind the face are the contact points. Early returns are the clearest way to express
 * "no collision" at each stage of the SAT test.
 */
@Suppress("ReturnCount")
private fun polygonPolygon(
    a: List<Vector2>,
    b: List<Vector2>,
): SKContactManifold? {
    val (separationA, edgeA) = maxSeparation(a, b)
    if (separationA >= 0f) return null
    val (separationB, edgeB) = maxSeparation(b, a)
    if (separationB >= 0f) return null

    // The shallower-penetration axis is the more accurate contact normal, standard SAT practice;
    // a small preference for A's faces keeps a resting contact from flipping between the two.
    return if (separationB > separationA + REFERENCE_FACE_TOLERANCE) {
        clippedContact(reference = b, referenceEdge = edgeB, incident = a, penetration = -separationB)
            .let { it.copy(normal = -it.normal) }
    } else {
        clippedContact(reference = a, referenceEdge = edgeA, incident = b, penetration = -separationA)
    }
}

/** How much deeper B's best face must be than A's to be preferred as the reference face, in points. */
private const val REFERENCE_FACE_TOLERANCE = 0.05f

private fun segments(chain: SKWorldShape.EdgeChain): List<Pair<Vector2, Vector2>> {
    val vertices = chain.vertices
    if (vertices.size < 2) return emptyList()
    val count = if (chain.closed) vertices.size else vertices.size - 1
    return (0 until count).map { i -> vertices[i] to vertices[(i + 1) % vertices.size] }
}

private fun circleSegment(
    circle: SKWorldShape.Circle,
    a: Vector2,
    b: Vector2,
): SKContactManifold? {
    val closest = closestPointOnSegment(circle.center, a, b)
    val toClosest = closest - circle.center
    val distance = toClosest.length()
    if (distance >= circle.radius) return null
    val normal = if (distance > 0f) toClosest * (1f / distance) else segmentNormal(a, b)
    return SKContactManifold(normal, circle.radius - distance, closest)
}

private fun circleEdgeChain(
    circle: SKWorldShape.Circle,
    chain: SKWorldShape.EdgeChain,
): SKContactManifold? =
    segments(chain).mapNotNull {
            (a, b) ->
        circleSegment(circle, a, b)
    }.maxByOrNull { it.penetration }

/** [polygon] against the segment [a]–[b], as a two-vertex polygon whose two edges face both ways. */
private fun polygonSegment(
    polygon: SKWorldShape.Polygon,
    a: Vector2,
    b: Vector2,
): SKContactManifold? = if (a == b) null else polygonPolygon(polygon.vertices, listOf(a, b))

private fun polygonEdgeChain(
    polygon: SKWorldShape.Polygon,
    chain: SKWorldShape.EdgeChain,
): SKContactManifold? =
    segments(chain).mapNotNull {
            (a, b) ->
        polygonSegment(polygon, a, b)
    }.maxByOrNull { it.penetration }
