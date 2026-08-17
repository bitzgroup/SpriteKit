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
 * Generates a stroke ribbon for [contour] (already flattened) at [lineWidth]: two triangles (a
 * quad) per segment, offset by `lineWidth / 2` to each side of the segment's direction. [closed]
 * additionally strokes the closing segment back to the first point.
 *
 * Doesn't generate miter/bevel/round joins between segments — adjacent quads simply meet (or
 * gap slightly, at sharp angles) without extra join geometry, a documented simplification; see
 * `docs/API_COMPATIBILITY.md`.
 */
internal fun triangulateStroke(
    contour: List<Vector2>,
    lineWidth: Float,
    closed: Boolean,
): List<Vector2> {
    val points = dedupeClosingPoint(contour)
    if (points.size < 2 || lineWidth <= 0f) return emptyList()

    val halfWidth = lineWidth / 2f
    val segmentCount = if (closed) points.size else points.size - 1
    val triangles = mutableListOf<Vector2>()
    for (i in 0 until segmentCount) {
        val a = points[i]
        val b = points[(i + 1) % points.size]
        val direction = b - a
        val length = hypot(direction.x, direction.y)
        if (length == 0f) continue
        val normal = Vector2(-direction.y / length, direction.x / length) * halfWidth
        val a0 = a + normal
        val a1 = a - normal
        val b0 = b + normal
        val b1 = b - normal
        triangles += listOf(a0, a1, b0, a1, b1, b0)
    }
    return triangles
}

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

private fun cross(
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

private fun dedupeClosingPoint(contour: List<Vector2>): List<Vector2> =
    if (contour.size > 1 && contour.first() == contour.last()) contour.dropLast(1) else contour
