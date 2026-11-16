package jp.co.bitz.spritekit

/** One cubic Bézier segment of a spline: [control1] and [control2] lead to [end]. */
internal data class SKCubicSegment(
    val control1: Vector2,
    val control2: Vector2,
    val end: Vector2,
)

/** A sixth: Catmull-Rom's tangent scale, as a cubic Bézier control-point offset. */
private const val SPLINE_TANGENT_FRACTION = 1f / 6f

/**
 * The cubic segments of Apple's `SKShapeNode(splinePoints:count:)` spline through [points],
 * after a move to the first point (verified on iOS/tvOS simulators): a Catmull-Rom spline, each
 * segment from `p[i]` to `p[i + 1]` with control points `p[i] + (p[i + 1] - p[i - 1]) / 6` and
 * `p[i + 1] - (p[i + 2] - p[i]) / 6`, indices clamped to the ends. Like Apple's, it starts with a
 * zero-length segment from the first point to itself (`i = -1`). Empty for no points.
 */
internal fun splineSegments(points: List<Vector2>): List<SKCubicSegment> {
    if (points.isEmpty()) return emptyList()

    fun at(index: Int) = points[index.coerceIn(0, points.lastIndex)]
    return (-1 until points.lastIndex).map { i ->
        SKCubicSegment(
            control1 = at(i) + (at(i + 1) - at(i - 1)) * SPLINE_TANGENT_FRACTION,
            control2 = at(i + 1) - (at(i + 2) - at(i)) * SPLINE_TANGENT_FRACTION,
            end = at(i + 1),
        )
    }
}
