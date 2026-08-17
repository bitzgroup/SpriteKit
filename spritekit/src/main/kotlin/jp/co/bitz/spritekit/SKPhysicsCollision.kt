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
 * toward the second, [penetration] is how deep they overlap along it, and [point] is an
 * approximate world-space contact point (not exact for polygon-polygon/polygon-chain cases, which
 * use an edge midpoint rather than true clipped contact points — see `docs/API_COMPATIBILITY.md`).
 */
internal data class SKContactManifold(val normal: Vector2, val penetration: Float, val point: Vector2)

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
        is SKWorldShape.Polygon -> polygonPolygon(a, b)
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
private fun outwardNormal(
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

private fun List<Vector2>.centroid(): Vector2 {
    var sum = Vector2.Zero
    for (vertex in this) sum += vertex
    return sum * (1f / size)
}

private fun midpoint(
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

// Early returns are the clearest way to express "no collision" at each stage of the SAT test.
@Suppress("ReturnCount")
private fun polygonPolygon(
    polyA: SKWorldShape.Polygon,
    polyB: SKWorldShape.Polygon,
): SKContactManifold? {
    val (separationA, edgeA) = maxSeparation(polyA.vertices, polyB.vertices)
    if (separationA >= 0f) return null
    val (separationB, edgeB) = maxSeparation(polyB.vertices, polyA.vertices)
    if (separationB >= 0f) return null

    // The shallower-penetration axis is the more accurate contact normal, standard SAT practice.
    return if (separationA > separationB) {
        val a = polyA.vertices[edgeA]
        val b = polyA.vertices[(edgeA + 1) % polyA.vertices.size]
        SKContactManifold(outwardNormal(polyA.vertices, edgeA), 0f - separationA, midpoint(a, b))
    } else {
        val a = polyB.vertices[edgeB]
        val b = polyB.vertices[(edgeB + 1) % polyB.vertices.size]
        // outwardNormal here points away from B (i.e. from B towards A); this file's convention
        // wants A-to-B, so it's flipped.
        SKContactManifold(-outwardNormal(polyB.vertices, edgeB), 0f - separationB, midpoint(a, b))
    }
}

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

// Early returns are the clearest way to express "no collision" at each stage of the SAT test.
@Suppress("ReturnCount")
private fun polygonSegment(
    polygon: SKWorldShape.Polygon,
    a: Vector2,
    b: Vector2,
): SKContactManifold? {
    val candidateNormals = listOf(segmentNormal(a, b), -segmentNormal(a, b))
    var segmentSeparation = Float.NEGATIVE_INFINITY
    var segmentNormalOut = candidateNormals[0]
    for (normal in candidateNormals) {
        val separation = polygon.vertices.minOf { normal dot (it - a) }
        if (separation > segmentSeparation) {
            segmentSeparation = separation
            segmentNormalOut = normal
        }
    }
    if (segmentSeparation >= 0f) return null

    val (polygonSeparation, polygonEdge) = maxSeparation(polygon.vertices, listOf(a, b))
    if (polygonSeparation >= 0f) return null

    return if (segmentSeparation > polygonSeparation) {
        val closest = closestPointOnSegment(polygon.vertices.centroid(), a, b)
        // segmentNormalOut points away from the segment (towards A); this file's convention wants
        // A-to-B (polygon-to-chain), so it's flipped.
        SKContactManifold(-segmentNormalOut, 0f - segmentSeparation, closest)
    } else {
        val edgeA = polygon.vertices[polygonEdge]
        val edgeB = polygon.vertices[(polygonEdge + 1) % polygon.vertices.size]
        SKContactManifold(outwardNormal(polygon.vertices, polygonEdge), 0f - polygonSeparation, midpoint(edgeA, edgeB))
    }
}

private fun polygonEdgeChain(
    polygon: SKWorldShape.Polygon,
    chain: SKWorldShape.EdgeChain,
): SKContactManifold? =
    segments(chain).mapNotNull {
            (a, b) ->
        polygonSegment(polygon, a, b)
    }.maxByOrNull { it.penetration }
