package jp.co.bitz.spritekit

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos

/** How far apart two points may be and still count as the same vertex. */
private const val VERTEX_EPSILON = 1e-4f

/** How far a flattened outline's points may stray from one radius and still be read as a circle. */
private const val CIRCLE_RADIUS_TOLERANCE = 0.01f

/** The fewest points a flattened outline needs to be read as a circle. */
private const val MIN_CIRCLE_POINTS = 8

/**
 * The shape of a polygon body with [points] as its outline, closed back to the first point,
 * either winding: a convex [SKPhysicsShape.Polygon] (counterclockwise), or, for a concave
 * outline, a [SKPhysicsShape.Compound] of convex pieces covering it -- Apple's polygon bodies
 * take concave outlines too, keeping their full area and colliding in their notches (verified on
 * iOS/tvOS simulators).
 */
internal fun polygonShape(points: List<Vector2>): SKPhysicsShape {
    val ring = simplifiedRing(points)
    val ccw = if (ring.size >= 3 && ringArea(ring) < 0f) ring.reversed() else ring
    val pieces = if (ccw.size < 3 || isConvex(ccw)) listOf(ccw) else convexPieces(ccw)
    return pieces.singleOrNull()?.let { SKPhysicsShape.Polygon(it) }
        ?: SKPhysicsShape.Compound(pieces.map { SKPhysicsShape.Polygon(it) })
}

/**
 * The shape of a polygon body whose outline is [points], flattened from a path: a circle when
 * they all lie on one -- Apple makes a full circle's path into an exact circle body, with a
 * circle's area and inertia, while other curves are flattened into straight edges (verified on
 * iOS/tvOS simulators) -- or else [polygonShape].
 */
internal fun pathPolygonShape(points: List<Vector2>): SKPhysicsShape = circleThrough(points) ?: polygonShape(points)

/**
 * The circle every one of [points] lies on, if they're a curve's flattening: at least
 * [MIN_CIRCLE_POINTS] of them, all at one distance from their center, and close enough together
 * that the edges between them stay within [maxSag] of the circle -- which a polygon drawn with
 * straight lines, like a regular 12-gon, doesn't.
 */
internal fun circleThrough(
    points: List<Vector2>,
    maxSag: Float = FLATTEN_ACCEPTABLE_ERROR * 1.05f,
): SKPhysicsShape.Circle? {
    val ring = simplifiedRing(points)
    if (ring.size < MIN_CIRCLE_POINTS) return null
    val center = ring.fold(Vector2.Zero) { sum, p -> sum + p } * (1f / ring.size)
    val distances = ring.map { (it - center).length() }
    val radius = distances.average().toFloat()
    val round = radius > 0f && distances.all { abs(it - radius) <= radius * CIRCLE_RADIUS_TOLERANCE }
    val longestEdge = ring.indices.maxOf { (ring[(it + 1) % ring.size] - ring[it]).length() }
    val halfAngle = (longestEdge / (2f * radius)).coerceIn(0f, 1f).let(::asin)
    val sag = radius * (1f - cos(halfAngle))
    return if (round && sag <= maxSag) SKPhysicsShape.Circle(radius, center) else null
}

/** [points] without repeats -- consecutive, or the last closing back onto the first. */
private fun simplifiedRing(points: List<Vector2>): List<Vector2> {
    val ring = mutableListOf<Vector2>()
    for (p in points) if (ring.isEmpty() || (ring.last() - p).length() > VERTEX_EPSILON) ring += p
    while (ring.size > 1 && (ring.first() - ring.last()).length() <= VERTEX_EPSILON) ring.removeAt(ring.lastIndex)
    return ring
}

private fun ringArea(ring: List<Vector2>): Float =
    ring.indices.sumOf {
        (ring[it] cross ring[(it + 1) % ring.size]).toDouble()
    }.toFloat() / 2f

private fun turn(
    ring: List<Vector2>,
    i: Int,
): Float {
    val a = ring[(i - 1 + ring.size) % ring.size]
    val b = ring[i]
    val c = ring[(i + 1) % ring.size]
    return (b - a) cross (c - b)
}

private fun isConvex(ccw: List<Vector2>): Boolean = ccw.indices.all { turn(ccw, it) >= -VERTEX_EPSILON }

/**
 * [ccw] (a concave, counterclockwise outline) as convex pieces: ear-clipped into triangles, then
 * neighbors merged while the merged piece stays convex (Hertel-Mehlhorn), so a shape needs only a
 * few pieces -- an L shape two, not four.
 */
private fun convexPieces(ccw: List<Vector2>): List<List<Vector2>> {
    val pieces = triangulateFill(ccw).chunked(3).toMutableList()
    while (mergeOnePair(pieces)) Unit
    return pieces.map { piece -> piece.filterIndexed { i, _ -> abs(turn(piece, i)) > VERTEX_EPSILON } }
}

/** Merges the first two of [pieces] that share an edge and stay convex together; whether any did. */
private fun mergeOnePair(pieces: MutableList<List<Vector2>>): Boolean {
    for (i in pieces.indices) {
        val merge =
            (i + 1 until pieces.size).firstNotNullOfOrNull { j ->
                mergeAcrossSharedEdge(pieces[i], pieces[j])?.takeIf(::isConvex)?.let { j to it }
            }
        if (merge != null) {
            pieces[i] = merge.second
            pieces.removeAt(merge.first)
            return true
        }
    }
    return false
}

/** [a] and [b] (both counterclockwise) as one outline, if they share an edge. */
private fun mergeAcrossSharedEdge(
    a: List<Vector2>,
    b: List<Vector2>,
): List<Vector2>? {
    for (i in a.indices) {
        val from = a[i]
        val to = a[(i + 1) % a.size]
        for (j in b.indices) {
            if (b[j] != to || b[(j + 1) % b.size] != from) continue
            val fromA = List(a.size) { a[(i + 1 + it) % a.size] } // to ... from
            val fromB = List(b.size - 2) { b[(j + 2 + it) % b.size] } // b's others, after from
            return fromA + fromB
        }
    }
    return null
}
