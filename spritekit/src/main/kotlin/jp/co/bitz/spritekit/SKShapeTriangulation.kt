package jp.co.bitz.spritekit

import kotlin.math.hypot

/**
 * Triangulates [contour] (a simple, non-self-intersecting polygon, either winding order) via
 * classic ear-clipping, for [SKShapeNode]'s fill. Returns a flat triangle list ([contour].size - 2
 * triangles, i.e. `3 * (contour.size - 2)` points; empty if [contour] has fewer than 3 distinct
 * points). Doesn't support holes, and self-intersecting input stops early (returning whatever was
 * triangulated so far) rather than producing garbage geometry — see `docs/API_COMPATIBILITY.md`.
 *
 * Pure Kotlin — [contour] is expected to already be flattened (curves converted to line
 * segments) by the caller, so this is unit-testable independent of `android.graphics.Path`.
 */
internal fun triangulateFill(contour: List<Vector2>): List<Vector2> {
    val ring = dedupeClosingPoint(contour).toMutableList()
    if (ring.size < 3) return emptyList()
    if (signedArea(ring) < 0f) ring.reverse() // ear-clipping below assumes CCW winding

    val triangles = mutableListOf<Vector2>()
    val guardBudget = ring.size * ring.size + 1
    var guard = 0
    while (ring.size > 3 && guard < guardBudget) {
        guard++
        val earIndex = findEar(ring)
        if (earIndex == -1) break // no ear found (degenerate/self-intersecting input) -- stop rather than loop forever
        val prev = ring[(earIndex - 1 + ring.size) % ring.size]
        val curr = ring[earIndex]
        val next = ring[(earIndex + 1) % ring.size]
        triangles += listOf(prev, curr, next)
        ring.removeAt(earIndex)
    }
    if (ring.size == 3) triangles += ring
    return triangles
}

/**
 * Generates a stroke for [contour] (already flattened) at [lineWidth]: a ribbon of two triangles
 * (a quad) per segment, offset by `lineWidth / 2` to each side of the segment's direction, plus
 * join geometry between consecutive segments ([lineJoin], falling back from [LineJoin.Miter] to
 * [LineJoin.Bevel] past [miterLimit], matching Core Graphics) and, for an open contour, cap
 * geometry at both ends ([lineCap]). [closed] additionally strokes the closing segment back to the
 * first point and joins it to the first segment instead of capping.
 *
 * The defaults match Apple's `SKShapeNode`: [LineCap.Butt], [LineJoin.Miter], a miter limit of 10.
 */
@Suppress("LongParameterList")
internal fun triangulateStroke(
    contour: List<Vector2>,
    lineWidth: Float,
    closed: Boolean,
    lineCap: LineCap = LineCap.Butt,
    lineJoin: LineJoin = LineJoin.Miter,
    miterLimit: Float = DEFAULT_MITER_LIMIT,
): List<Vector2> {
    val points = dedupeConsecutivePoints(dedupeClosingPoint(contour))
    if (points.size < 2 || lineWidth <= 0f) return emptyList()

    val halfWidth = lineWidth / 2f
    val segmentCount = if (closed && points.size > 2) points.size else points.size - 1
    val triangles = mutableListOf<Vector2>()
    for (i in 0 until segmentCount) {
        val a = points[i]
        val b = points[(i + 1) % points.size]
        val normal = leftNormal(b - a) * halfWidth
        val a0 = a + normal
        val a1 = a - normal
        val b0 = b + normal
        val b1 = b - normal
        triangles += listOf(a0, a1, b0, a1, b1, b0)
    }

    // Joins: at every interior vertex, plus (for a closed contour) the first/last one.
    val joinVertices = if (closed && points.size > 2) points.indices else 1 until points.size - 1
    for (i in joinVertices) {
        val prev = points[(i - 1 + points.size) % points.size]
        val curr = points[i]
        val next = points[(i + 1) % points.size]
        addJoin(triangles, prev, curr, next, halfWidth, lineJoin, miterLimit)
    }

    if (!(closed && points.size > 2)) {
        addCap(triangles, points[0], points[0] - points[1], halfWidth, lineCap)
        addCap(triangles, points.last(), points.last() - points[points.size - 2], halfWidth, lineCap)
    }
    return triangles
}

/** Core Graphics' (and so Apple's `SKShapeNode`'s) default miter limit. */
internal const val DEFAULT_MITER_LIMIT: Float = 10f

/** Segments used to approximate a half circle for round caps/joins. */
internal const val ROUND_SEGMENTS_PER_HALF_CIRCLE = 8

/** The unit vector 90° counter-clockwise from [direction] (zero if [direction] is zero). */
internal fun leftNormal(direction: Vector2): Vector2 {
    val length = hypot(direction.x, direction.y)
    return if (length == 0f) Vector2.Zero else Vector2(-direction.y / length, direction.x / length)
}

/**
 * Fills the wedge on the outer side of the turn at [curr] (between the ribbons of `prev → curr`
 * and `curr → next`). Collinear segments need nothing.
 */
@Suppress("LongParameterList")
private fun addJoin(
    triangles: MutableList<Vector2>,
    prev: Vector2,
    curr: Vector2,
    next: Vector2,
    halfWidth: Float,
    lineJoin: LineJoin,
    miterLimit: Float,
) {
    val incoming = curr - prev
    val outgoing = next - curr
    val turn = cross(incoming, outgoing)
    if (turn == 0f) return
    // Turning left (counter-clockwise) puts the gap on the right side, and vice versa.
    val side = if (turn > 0f) -1f else 1f
    val n1 = leftNormal(incoming) * side
    val n2 = leftNormal(outgoing) * side
    val outer1 = curr + n1 * halfWidth
    val outer2 = curr + n2 * halfWidth
    when (lineJoin) {
        LineJoin.Bevel -> triangles += listOf(curr, outer1, outer2)
        LineJoin.Round -> addArcFan(triangles, curr, n1, n2, halfWidth)
        LineJoin.Miter -> {
            triangles += listOf(curr, outer1, outer2)
            val bisector = (n1 + n2).normalized()
            val cosHalf = bisector dot n1
            // miter length / line width, as Core Graphics measures it against the miter limit.
            val ratio = if (cosHalf > 0f) 1f / cosHalf else Float.POSITIVE_INFINITY
            if (ratio <= miterLimit) {
                triangles += listOf(outer1, curr + bisector * (halfWidth * ratio), outer2)
            }
        }
    }
}

/** A cap at an open contour's [end], where [outward] points away from the line. */
private fun addCap(
    triangles: MutableList<Vector2>,
    end: Vector2,
    outward: Vector2,
    halfWidth: Float,
    lineCap: LineCap,
) {
    val direction = leftNormal(outward).let { Vector2(it.y, -it.x) } // unit vector along [outward]
    if (direction == Vector2.Zero) return
    val normal = Vector2(-direction.y, direction.x)
    when (lineCap) {
        LineCap.Butt -> Unit
        LineCap.Square -> {
            val e0 = end + normal * halfWidth
            val e1 = end - normal * halfWidth
            val f0 = e0 + direction * halfWidth
            val f1 = e1 + direction * halfWidth
            triangles += listOf(e0, e1, f0, e1, f1, f0)
        }
        LineCap.Round -> addArcFan(triangles, end, normal, -normal, halfWidth, through = direction)
    }
}

/**
 * A triangle fan around [center] covering the arc of radius [radius] from unit direction [from]
 * to [to] — see [arcDirections] for which way round.
 */
@Suppress("LongParameterList")
private fun addArcFan(
    triangles: MutableList<Vector2>,
    center: Vector2,
    from: Vector2,
    to: Vector2,
    radius: Float,
    through: Vector2? = null,
) {
    val directions = arcDirections(from, to, through)
    for (i in 0 until directions.size - 1) {
        triangles += listOf(center, center + directions[i] * radius, center + directions[i + 1] * radius)
    }
}

internal fun dedupeConsecutivePoints(points: List<Vector2>): List<Vector2> =
    points.filterIndexed { i, point -> i == 0 || point != points[i - 1] }

private fun findEar(polygon: List<Vector2>): Int {
    for (i in polygon.indices) {
        val prevIndex = (i - 1 + polygon.size) % polygon.size
        val nextIndex = (i + 1) % polygon.size
        val prev = polygon[prevIndex]
        val curr = polygon[i]
        val next = polygon[nextIndex]
        if (!isConvex(prev, curr, next)) continue
        val containsOtherVertex =
            polygon.indices.any { j ->
                j != i && j != prevIndex && j != nextIndex && pointInTriangle(polygon[j], prev, curr, next)
            }
        if (!containsOtherVertex) return i
    }
    return -1
}

private fun isConvex(
    prev: Vector2,
    curr: Vector2,
    next: Vector2,
): Boolean = cross(curr - prev, next - curr) > 0f

private fun pointInTriangle(
    p: Vector2,
    a: Vector2,
    b: Vector2,
    c: Vector2,
): Boolean {
    val d1 = cross(b - a, p - a)
    val d2 = cross(c - b, p - b)
    val d3 = cross(a - c, p - c)
    val hasNegative = d1 < 0f || d2 < 0f || d3 < 0f
    val hasPositive = d1 > 0f || d2 > 0f || d3 > 0f
    return !(hasNegative && hasPositive)
}

internal fun cross(
    a: Vector2,
    b: Vector2,
): Float = a.x * b.y - a.y * b.x

private fun signedArea(polygon: List<Vector2>): Float {
    var sum = 0f
    for (i in polygon.indices) {
        val a = polygon[i]
        val b = polygon[(i + 1) % polygon.size]
        sum += a.x * b.y - b.x * a.y
    }
    return sum / 2f
}

internal fun dedupeClosingPoint(contour: List<Vector2>): List<Vector2> =
    if (contour.size > 1 && contour.first() == contour.last()) contour.dropLast(1) else contour
