package jp.co.bitz.spritekit

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * A flat triangle list ([vertices], a multiple of 3) plus one alpha multiplier per vertex
 * ([alphas], the same size), which the renderer interpolates across each triangle.
 */
internal class SKStrokeMesh(
    val vertices: List<Vector2>,
    val alphas: List<Float>,
)

/**
 * How far inside an antialiased outline's edge its fade starts, in pixels. Apple fades an outline
 * linearly over 1.7 pixels across each edge, from fully opaque this far inside the edge to fully
 * transparent [ANTIALIAS_OUTER_PIXELS] outside it (verified on iOS/tvOS simulators).
 */
internal const val ANTIALIAS_INNER_PIXELS = 1.12f

/** How far outside an antialiased outline's edge its fade ends, in pixels. See [ANTIALIAS_INNER_PIXELS]. */
internal const val ANTIALIAS_OUTER_PIXELS = 0.58f

/**
 * The narrowest half-width, in pixels, an antialiased outline is drawn at. Apple draws thinner
 * outlines (under about two and a half pixels) fainter and wider than their width, about as much
 * ink as a 2.4-pixel line; this approximates that.
 */
internal const val ANTIALIAS_MIN_HALF_WIDTH_PIXELS = 1.2f

/**
 * [triangulateStroke]'s outline, antialiased the way Apple's `SKShapeNode` antialiases one: an
 * opaque core [ANTIALIAS_INNER_PIXELS] narrower than the outline on each side, wrapped in a
 * fringe whose vertex alphas fade from `1` at the core to `0` [ANTIALIAS_OUTER_PIXELS] outside
 * the outline — along both sides, around the outer side of every join, and around both ends of
 * an open outline. [pixelSize] is one pixel in the shape's local units: Apple sizes the fade in
 * screen pixels at the node's own scale of `1`, so scaling the node scales the fade with it
 * (verified on iOS/tvOS simulators).
 */
@Suppress("LongParameterList")
internal fun triangulateAntialiasedStroke(
    contour: List<Vector2>,
    lineWidth: Float,
    closed: Boolean,
    lineCap: LineCap,
    lineJoin: LineJoin,
    miterLimit: Float,
    pixelSize: Float,
): SKStrokeMesh {
    val points = dedupeConsecutivePoints(dedupeClosingPoint(contour))
    if (points.size < 2 || lineWidth <= 0f) return SKStrokeMesh(emptyList(), emptyList())

    val halfWidth = maxOf(lineWidth / 2f, ANTIALIAS_MIN_HALF_WIDTH_PIXELS * pixelSize)
    val inner = maxOf(halfWidth - ANTIALIAS_INNER_PIXELS * pixelSize, 0f)
    val outer = halfWidth + ANTIALIAS_OUTER_PIXELS * pixelSize
    val isClosed = closed && points.size > 2
    // A butt end's fade starts inside the endpoint, as it does along the sides; square and round
    // ends already reach past it.
    val core = if (!isClosed && lineCap == LineCap.Butt) shortenEnds(points, halfWidth - inner) else points

    val mesh = StrokeMeshBuilder(inner, outer)
    mesh.addOpaque(triangulateStroke(core, inner * 2f, isClosed, lineCap, lineJoin, miterLimit))
    val segmentCount = if (isClosed) core.size else core.size - 1
    for (i in 0 until segmentCount) {
        val a = core[i]
        val b = core[(i + 1) % core.size]
        val normal = leftNormal(b - a)
        mesh.addSideFringe(a, b, normal)
        mesh.addSideFringe(b, a, -normal)
    }
    val joinVertices = if (isClosed) core.indices else 1 until core.size - 1
    for (i in joinVertices) {
        val prev = core[(i - 1 + core.size) % core.size]
        val next = core[(i + 1) % core.size]
        mesh.addJoinFringe(prev, core[i], next, lineJoin, miterLimit)
    }
    if (!isClosed) {
        mesh.addCapFringe(core[0], core[0] - core[1], lineCap)
        mesh.addCapFringe(core.last(), core.last() - core[core.size - 2], lineCap)
    }
    return mesh.build()
}

/**
 * [points] with its first and last points each moved [amount] toward its neighbor — at most half
 * the end segment for a single segment, so the two ends never cross.
 */
private fun shortenEnds(
    points: List<Vector2>,
    amount: Float,
): List<Vector2> {
    if (amount <= 0f) return points
    val result = points.toMutableList()
    val limitDivisor = if (points.size == 2) 2f else 1f

    fun moved(
        end: Vector2,
        toward: Vector2,
    ): Vector2 {
        val length = hypot(toward.x - end.x, toward.y - end.y)
        return end + (toward - end).normalized() * minOf(amount, length / limitDivisor)
    }
    result[0] = moved(points[0], points[1])
    result[result.lastIndex] = moved(points.last(), points[points.size - 2])
    return result
}

/** Accumulates an antialiased outline's triangles and their vertex alphas. */
private class StrokeMeshBuilder(
    private val inner: Float,
    private val outer: Float,
) {
    private val vertices = mutableListOf<Vector2>()
    private val alphas = mutableListOf<Float>()

    fun build(): SKStrokeMesh = SKStrokeMesh(vertices, alphas)

    fun addOpaque(triangles: List<Vector2>) {
        vertices += triangles
        repeat(triangles.size) { alphas += 1f }
    }

    /**
     * A quad fading from opaque along `innerStart → innerEnd` to transparent along
     * `outerStart → outerEnd`.
     */
    fun addBand(
        innerStart: Vector2,
        outerStart: Vector2,
        outerEnd: Vector2,
        innerEnd: Vector2,
    ) {
        vertices += listOf(innerStart, outerStart, outerEnd, innerStart, outerEnd, innerEnd)
        alphas += listOf(1f, 0f, 0f, 1f, 0f, 1f)
    }

    /** The fringe along the side of segment `from → to` that [normal] (a unit vector) points to. */
    fun addSideFringe(
        from: Vector2,
        to: Vector2,
        normal: Vector2,
    ) = addBand(from + normal * inner, from + normal * outer, to + normal * outer, to + normal * inner)

    /**
     * The fringe around the outer side of the turn at [curr], following the join's own shape. The
     * inner side needs none: each segment's core already covers the other's fringe there.
     */
    fun addJoinFringe(
        prev: Vector2,
        curr: Vector2,
        next: Vector2,
        lineJoin: LineJoin,
        miterLimit: Float,
    ) {
        val incoming = curr - prev
        val outgoing = next - curr
        val turn = cross(incoming, outgoing)
        if (turn == 0f) return
        val side = if (turn > 0f) -1f else 1f
        val n1 = leftNormal(incoming) * side
        val n2 = leftNormal(outgoing) * side
        val bisector = (n1 + n2).normalized()
        val cosHalf = bisector dot n1
        val ratio = if (cosHalf > 0f) 1f / cosHalf else Float.POSITIVE_INFINITY
        when {
            lineJoin == LineJoin.Round -> addArcFringe(curr, arcDirections(n1, n2))
            lineJoin == LineJoin.Miter && ratio <= miterLimit -> {
                val tipInner = curr + bisector * (inner * ratio)
                val tipOuter = curr + bisector * (outer * ratio)
                addBand(curr + n1 * inner, curr + n1 * outer, tipOuter, tipInner)
                addBand(tipInner, tipOuter, curr + n2 * outer, curr + n2 * inner)
            }
            else -> addBand(curr + n1 * inner, curr + n1 * outer, curr + n2 * outer, curr + n2 * inner)
        }
    }

    /** The fringe around an open outline's [end], where [outward] points away from the line. */
    fun addCapFringe(
        end: Vector2,
        outward: Vector2,
        lineCap: LineCap,
    ) {
        val direction = outward.normalized()
        if (direction == Vector2.Zero) return
        val normal = Vector2(-direction.y, direction.x)
        if (lineCap == LineCap.Round) {
            addArcFringe(end, arcDirections(normal, -normal, through = direction))
            return
        }
        // Where the opaque core stops: at the (already moved in) end for a butt cap, half the
        // core's width past it for a square one, whose sides then need fringe of their own.
        val coreEnd = if (lineCap == LineCap.Square) end + direction * inner else end
        if (lineCap == LineCap.Square) {
            addSideFringe(end, coreEnd, normal)
            addSideFringe(coreEnd, end, -normal)
        }
        val reach = direction * (outer - inner)
        addBand(
            coreEnd + normal * inner,
            coreEnd + normal * inner + reach,
            coreEnd - normal * inner + reach,
            coreEnd - normal * inner,
        )
        for (sign in listOf(1f, -1f)) {
            val corner = coreEnd + normal * (inner * sign)
            val sideOuter = coreEnd + normal * (outer * sign)
            vertices += listOf(corner, sideOuter, sideOuter + reach, corner, sideOuter + reach, corner + reach)
            alphas += listOf(1f, 0f, 0f, 1f, 0f, 0f)
        }
    }

    /** An annulus from [inner] to [outer] around [center], through each of [directions] in turn. */
    private fun addArcFringe(
        center: Vector2,
        directions: List<Vector2>,
    ) {
        for (i in 0 until directions.size - 1) {
            val from = directions[i]
            val to = directions[i + 1]
            addBand(center + from * inner, center + from * outer, center + to * outer, center + to * inner)
        }
    }
}

/**
 * Unit vectors stepping around an arc from unit direction [from] to [to] — the shorter way round,
 * or (for a half circle, where both ways are equally short) the way passing [through] — at
 * [ROUND_SEGMENTS_PER_HALF_CIRCLE] steps per half circle, both ends included.
 */
internal fun arcDirections(
    from: Vector2,
    to: Vector2,
    through: Vector2? = null,
): List<Vector2> {
    val start = atan2(from.y, from.x)
    var sweep = atan2(to.y, to.x) - start
    if (through != null) {
        val ccw = if (sweep > 0f) sweep else sweep + 2f * PI.toFloat()
        val mid = start + ccw / 2f
        sweep = if (Vector2(cos(mid), sin(mid)) dot through >= 0f) ccw else ccw - 2f * PI.toFloat()
    } else {
        while (sweep > PI.toFloat()) sweep -= 2f * PI.toFloat()
        while (sweep < -PI.toFloat()) sweep += 2f * PI.toFloat()
    }
    val steps = maxOf(1, ceil(abs(sweep) / PI.toFloat() * ROUND_SEGMENTS_PER_HALF_CIRCLE).toInt())
    return (0..steps).map { step ->
        val angle = start + sweep * step / steps
        Vector2(cos(angle), sin(angle))
    }
}
