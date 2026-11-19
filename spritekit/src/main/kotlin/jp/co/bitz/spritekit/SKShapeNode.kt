package jp.co.bitz.spritekit

import android.graphics.Color
import android.graphics.Path
import android.graphics.RectF

/**
 * A node that draws a filled and/or stroked vector shape — mirrors Apple's `SKShapeNode`.
 * [path] is a plain `android.graphics.Path` (this library's `CGPath` stand-in, since it already
 * fits the role well — see `docs/API_COMPATIBILITY.md`), triangulated for rendering via
 * [triangulateFill]/[triangulateStroke] once flattened by [flattenPath]. That triangulation is
 * cached per [triangulationCache] and only redone when [path] is reassigned to a new instance (or
 * the stroke style — [lineWidth], [lineCap], [lineJoin], [miterLimit], [isAntialiased] — changes) — see
 * [triangulatedShape]'s KDoc for why this matters. Reassign [path] to
 * change a shape's geometry (`shape.path = newPath`); mutating the existing `Path` object in
 * place and keeping the same instance defeats this cache and renders stale geometry.
 *
 * Deviation: [glowWidth] is stored for API parity but doesn't render a glow — that needs a
 * blur/glow shader pass, deferred with the rest of the advanced shader work (Phase 13); see
 * `docs/ROADMAP.md`.
 */
public open class SKShapeNode(
    public var path: Path? = null,
) : SKNode() {
    public companion object {
        /**
         * A rectangle exactly at [rect] in the node's space — not centered on its origin. Corners
         * are rounded by [cornerRadius] (`0` for square corners). Mirrors Apple's
         * `SKShapeNode(rect:cornerRadius:)`.
         */
        public fun rect(
            rect: Rect,
            cornerRadius: Float = 0f,
        ): SKShapeNode {
            val bounds = RectF(rect.left, rect.top, rect.right, rect.bottom)
            return SKShapeNode(
                Path().apply {
                    if (cornerRadius > 0f) {
                        addRoundRect(bounds, cornerRadius, cornerRadius, Path.Direction.CW)
                    } else {
                        addRect(bounds, Path.Direction.CW)
                    }
                },
            )
        }

        /**
         * An ellipse inscribed in [rect], not centered on the node's origin. Mirrors Apple's
         * `SKShapeNode(ellipseIn:)`.
         */
        public fun ellipseIn(rect: Rect): SKShapeNode =
            SKShapeNode(
                Path().apply { addOval(RectF(rect.left, rect.top, rect.right, rect.bottom), Path.Direction.CW) },
            )

        /**
         * A shape drawing [path] — moved so its bounding box is centered on the node's origin when
         * [centered] is `true`, as given otherwise. Mirrors Apple's `SKShapeNode(path:centered:)`.
         * [path] itself isn't modified.
         */
        public fun path(
            path: Path,
            centered: Boolean,
        ): SKShapeNode {
            val copy = Path(path)
            if (centered) {
                val bounds = RectF()
                copy.computeBounds(bounds, true)
                copy.offset(-bounds.centerX(), -bounds.centerY())
            }
            return SKShapeNode(copy)
        }

        /**
         * An open polyline through [points] — Apple's `SKShapeNode(points:count:)`. Like Apple's,
         * its first line runs from the first point to itself.
         */
        public fun points(points: List<Vector2>): SKShapeNode =
            SKShapeNode(
                Path().apply {
                    points.firstOrNull()?.let { moveTo(it.x, it.y) }
                    points.forEach { lineTo(it.x, it.y) }
                },
            )

        /**
         * A smooth curve through every one of [points] — Apple's
         * `SKShapeNode(splinePoints:count:)`, built the same way (a Catmull-Rom spline as cubic
         * Béziers; see `docs/API_COMPATIBILITY.md`).
         */
        public fun splinePoints(points: List<Vector2>): SKShapeNode =
            SKShapeNode(
                Path().apply {
                    points.firstOrNull()?.let { moveTo(it.x, it.y) }
                    splineSegments(points).forEach {
                        cubicTo(it.control1.x, it.control1.y, it.control2.x, it.control2.y, it.end.x, it.end.y)
                    }
                },
            )

        /**
         * A circle of [radius] centered on the node's origin. Mirrors Apple's
         * `SKShapeNode(circleOfRadius:)`.
         */
        public fun circle(radius: Float): SKShapeNode =
            SKShapeNode(Path().apply { addCircle(0f, 0f, radius, Path.Direction.CW) })

        /**
         * A rectangle of [size] centered on the node's origin, with corners rounded by
         * [cornerRadius] (`0` for square corners). Mirrors Apple's
         * `SKShapeNode(rectOf:cornerRadius:)`.
         */
        public fun rect(
            size: Vector2,
            cornerRadius: Float = 0f,
        ): SKShapeNode {
            val half = Vector2(size.x / 2f, size.y / 2f)
            val bounds = RectF(-half.x, -half.y, half.x, half.y)
            return SKShapeNode(
                Path().apply {
                    if (cornerRadius > 0f) {
                        addRoundRect(bounds, cornerRadius, cornerRadius, Path.Direction.CW)
                    } else {
                        addRect(bounds, Path.Direction.CW)
                    }
                },
            )
        }

        /**
         * An ellipse inscribed in a rectangle of [size] centered on the node's origin. Mirrors
         * Apple's `SKShapeNode(ellipseOf:)`.
         */
        public fun ellipse(size: Vector2): SKShapeNode =
            SKShapeNode(
                Path().apply {
                    addOval(
                        RectF(-size.x / 2f, -size.y / 2f, size.x / 2f, size.y / 2f),
                        Path.Direction.CW,
                    )
                },
            )
    }

    /** The shape's outline color. Defaults to opaque white, matching Apple. */
    public var strokeColor: Int = Color.WHITE

    /**
     * The shape's fill color. Defaults to transparent (no fill), matching Apple. When [fillTexture]
     * is set, this color multiplies the sampled texture (so white shows the texture unmodified,
     * and the default transparent shows nothing), matching Apple.
     */
    public var fillColor: Int = Color.TRANSPARENT

    /**
     * A texture to fill the shape with instead of a flat color, or `null` (the default) for a
     * plain [fillColor] fill — mirrors Apple's `fillTexture`. The texture is stretched across the
     * bounding box of the shape's fill geometry and clipped to the shape's own outline, then
     * multiplied by [fillColor].
     */
    public var fillTexture: SKTexture? = null

    /**
     * The outline's width, in points. Defaults to `1`, matching Apple (verified on iOS/tvOS
     * simulators): a shape draws a thin white outline unless [strokeColor] or this is changed.
     * `0` draws no outline regardless of [strokeColor].
     */
    public var lineWidth: Float = 1f

    /** How the ends of an open stroke are drawn. Defaults to [LineCap.Butt], matching Apple. */
    public var lineCap: LineCap = LineCap.Butt

    /** How the stroke's segments meet at corners. Defaults to [LineJoin.Miter], matching Apple. */
    public var lineJoin: LineJoin = LineJoin.Miter

    /**
     * The longest a [LineJoin.Miter] corner may be, in multiples of the line width, before it's
     * drawn as [LineJoin.Bevel] instead. Defaults to `10`, matching Apple.
     */
    public var miterLimit: Float = DEFAULT_MITER_LIMIT

    /**
     * Stored for API parity; not drawn — see this class's KDoc. As on Apple, it does widen the
     * shape's frame by this much on each side of a drawn outline.
     */
    public var glowWidth: Float = 0f

    /**
     * Apple's `isAntialiased`, defaulting to `true`: smooths the outline's edges (a fill's edges
     * never are) and widens a drawn outline's frame by 0.85 points on each side. As on Apple, each
     * edge fades linearly over 1.7 screen pixels — from opaque 1.12 pixels inside it to clear 0.58
     * outside — measured at the node's own scale, so scaling the node scales the fade too, and
     * outlines thinner than about 2.4 pixels are drawn fainter and that wide (verified on iOS/tvOS
     * simulators). See [triangulateAntialiasedStroke] and `docs/API_COMPATIBILITY.md`.
     */
    public var isAntialiased: Boolean = true

    /** How this shape's fill and outline combine with what's already drawn. Defaults to [SKBlendMode.Alpha]. */
    public var blendMode: SKBlendMode = SKBlendMode.Alpha

    /**
     * The length of [path]'s outline, in points — Apple's `lineLength`: the sum of every
     * contour's length (a closed contour's closing segment included), with curves measured on the
     * flattened path. Apple's iOS/tvOS simulators report `0` for every path; this follows the
     * documented meaning instead.
     */
    public val lineLength: Float
        get() = path?.let { flattenPath(it).sumOf { contour -> contourLength(contour).toDouble() }.toFloat() } ?: 0f

    /** A copy with its own copy of [path] — see [SKNode.copy]. */
    override fun copy(): SKShapeNode = copyInto(SKShapeNode())

    override fun copyState(target: SKNode) {
        super.copyState(target)
        if (target !is SKShapeNode) return
        target.path = path?.let(::Path)
        target.strokeColor = strokeColor
        target.fillColor = fillColor
        target.fillTexture = fillTexture
        target.lineWidth = lineWidth
        target.lineCap = lineCap
        target.lineJoin = lineJoin
        target.miterLimit = miterLimit
        target.glowWidth = glowWidth
        target.isAntialiased = isAntialiased
        target.blendMode = blendMode
    }

    /**
     * Render-thread-confined cache of this shape's flattened-and-triangulated geometry, keyed by
     * the exact [path] instance and stroke style it was computed from — see
     * [triangulatedShape]. `null` until first rendered. Not part of the public API.
     */
    internal var triangulationCache: SKShapeTriangulationCache? = null

    /**
     * Render-thread-confined cache of [triangulationCache]'s [SKShapeTriangulationCache.allLocalVertices]
     * already converted into world space, keyed by [SKNode.worldTransformVersion] — see
     * [SKRenderCommandList.kt]'s `worldVertices`. `null` until first rendered. Not part of the
     * public API.
     */
    internal var worldVerticesCache: SKShapeWorldVerticesCache? = null

    /**
     * The path's bounds, plus — when an outline is drawn ([lineWidth] above `0` and a
     * [strokeColor] that isn't fully transparent) — the outline itself, widened by [glowWidth] and
     * by [isAntialiased]'s padding, as Apple's frame is (verified on iOS/tvOS simulators). See
     * [strokedBounds].
     */
    override val localBounds: Rect
        get() {
            val currentPath = path ?: return Rect.Zero
            val bounds = RectF()
            currentPath.computeBounds(bounds, true)
            val pathBounds = Rect(bounds.left, bounds.top, bounds.right, bounds.bottom)
            if (lineWidth <= 0f || alphaOf(strokeColor) <= 0f) return pathBounds
            val width = lineWidth + 2f * glowWidth + if (isAntialiased) ANTIALIASING_FRAME_PADDING * 2f else 0f
            val stroke = strokedBounds(triangulatedShape(this, currentPath).contours, width, lineCap)
            return if (stroke == null) pathBounds else pathBounds.union(stroke)
        }
}

/** How far Apple widens a drawn, antialiased outline's frame on each side, in points. */
internal const val ANTIALIASING_FRAME_PADDING: Float = 0.85f

/**
 * The bounds of [contours] stroked at [width] with [lineCap], the way Apple's `SKShapeNode.frame`
 * counts an outline: each segment's ribbon and the caps, but not the corners' miters — Apple's
 * frame of a sharp `^` stops at the segments' own corners (verified on iOS/tvOS simulators), so
 * joins are taken as bevels. `null` if nothing would be stroked.
 */
internal fun strokedBounds(
    contours: List<SKFlattenedContour>,
    width: Float,
    lineCap: LineCap,
): Rect? {
    val vertices = contours.flatMap { triangulateStroke(it.points, width, it.closed, lineCap, LineJoin.Bevel) }
    return if (vertices.isEmpty()) null else boundingRectOf(vertices)
}

/** [contour]'s length: its segments, plus the closing one back to the start when it's closed. */
internal fun contourLength(contour: SKFlattenedContour): Float {
    val points = contour.points
    if (points.size < 2) return 0f
    val open = points.zipWithNext { a, b -> (b - a).length() }.sum()
    return if (contour.closed) open + (points.first() - points.last()).length() else open
}
