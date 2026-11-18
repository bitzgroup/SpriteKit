package jp.co.bitz.spritekit

import android.graphics.Path

/**
 * A shape's flattened contours plus their fill/stroke triangle lists, all concatenated into one
 * [allLocalVertices] (with [vertexAlphas], one alpha multiplier per vertex: `1` except across an
 * antialiased outline's fading fringe) and [fillRanges]/[strokeRanges] marking each contour's slice of it — so a
 * caller needing "every vertex this shape ever draws" (see [SKRenderCommandList.kt]'s
 * `worldVertices`, which converts them all into world space in one batch) doesn't need to flatten
 * a list-of-lists itself, and slicing out one contour's fill or stroke triangles back out
 * ([List.subList]) is a view, not a copy.
 *
 * Everything here is local-space and path-derived — it's *not* the per-vertex world-space
 * transform, which still has to run every frame since the node (or an ancestor) can move
 * independently of its own geometry; see [SKRenderCommandList.kt]'s `worldVertices`.
 */
@Suppress("LongParameterList")
internal class SKShapeTriangulationCache(
    val path: Path,
    val strokeStyle: SKStrokeStyle,
    val contours: List<SKFlattenedContour>,
    val allLocalVertices: List<Vector2>,
    val fillRanges: List<IntRange>,
    val strokeRanges: List<IntRange>,
    val vertexAlphas: List<Float>,
) {
    /**
     * The local-space bounding box of every fill vertex across all contours — the area
     * [SKShapeNode.fillTexture] is stretched across (see [fillTextureVertices]) — or `null` if the
     * shape has no fill geometry at all. Computed once per triangulation, not per frame.
     */
    val fillBounds: Rect? by lazy { fillBoundsOf(allLocalVertices, fillRanges) }
}

/**
 * Every [SKShapeNode] property that shapes its stroke geometry — the part of the cache key besides
 * the [Path] instance itself — plus [antialiasPixelSize]: one screen pixel in the shape's local
 * units when [SKShapeNode.isAntialiased] (see [triangulateAntialiasedStroke]), `0` for a hard-edged
 * outline. Changing any of them re-triangulates.
 */
internal data class SKStrokeStyle(
    val lineWidth: Float,
    val lineCap: LineCap,
    val lineJoin: LineJoin,
    val miterLimit: Float,
    val antialiasPixelSize: Float,
)

/** The bounding box of [vertices]' [fillRanges] slices, or `null` if every range is empty. */
internal fun fillBoundsOf(
    vertices: List<Vector2>,
    fillRanges: List<IntRange>,
): Rect? {
    val fillVertices = fillRanges.flatMap { range -> range.map { vertices[it] } }
    return if (fillVertices.isEmpty()) null else boundingRectOf(fillVertices)
}

/**
 * [node]'s triangulated geometry for [path], reusing [SKShapeNode.triangulationCache] when it was
 * computed from this exact [path] instance (mutating a `Path` in place and reusing it is not
 * supported — assign a new `Path` instead, matching this library's own usage throughout the
 * `scenes` layer of consumer apps) and the same stroke style ([SKShapeNode.lineWidth],
 * [SKShapeNode.lineCap], [SKShapeNode.lineJoin], [SKShapeNode.miterLimit], and — when
 * [SKShapeNode.isAntialiased] — [pixelSize], one screen pixel in local units; a caller that only
 * needs the contours passes `null` to keep whatever the cache was built with), and recomputing
 * (flatten, then ear-clip fill / ribbon stroke) otherwise.
 *
 * This is the fix for the hot path [SKRenderCommandList.kt]'s `addShapeCommands` used to run
 * unconditionally on every single frame: [triangulateFill]'s ear-clipping is worst-case cubic in
 * the contour's point count, and a circle flattened at [flattenPath]'s default step produces on
 * the order of a hundred points for a typical on-screen radius — cheap once, ruinous at 60fps
 * across a few dozen static shapes (as a board-game scene with many circular pieces discovered).
 * [SKLabelNode] already cached its own (comparably expensive) glyph rendering the same way; this
 * brings [SKShapeNode] in line with that precedent.
 */
internal fun triangulatedShape(
    node: SKShapeNode,
    path: Path,
    pixelSize: Float? = null,
): SKShapeTriangulationCache {
    val cached = node.triangulationCache
    val antialiasPixelSize =
        when {
            !node.isAntialiased -> 0f
            pixelSize != null -> pixelSize
            else -> cached?.strokeStyle?.antialiasPixelSize ?: 0f
        }
    val strokeStyle = SKStrokeStyle(node.lineWidth, node.lineCap, node.lineJoin, node.miterLimit, antialiasPixelSize)
    if (cached != null && cached.path === path && cached.strokeStyle == strokeStyle) return cached

    val contours = flattenPath(path)
    val allLocalVertices = mutableListOf<Vector2>()
    val vertexAlphas = mutableListOf<Float>()
    val fillRanges =
        contours.map {
            val triangles = triangulateFill(it.points)
            repeat(triangles.size) { vertexAlphas += 1f }
            appendRange(allLocalVertices, triangles)
        }
    val strokeRanges =
        contours.map {
            val mesh = strokeMesh(it, strokeStyle)
            vertexAlphas += mesh.alphas
            appendRange(allLocalVertices, mesh.vertices)
        }
    val fresh =
        SKShapeTriangulationCache(
            path = path,
            strokeStyle = strokeStyle,
            contours = contours,
            allLocalVertices = allLocalVertices,
            fillRanges = fillRanges,
            strokeRanges = strokeRanges,
            vertexAlphas = vertexAlphas,
        )
    node.triangulationCache = fresh
    return fresh
}

/** [contour]'s outline in [style]: antialiased when [SKStrokeStyle.antialiasPixelSize] is set. */
private fun strokeMesh(
    contour: SKFlattenedContour,
    style: SKStrokeStyle,
): SKStrokeMesh =
    if (style.antialiasPixelSize > 0f) {
        triangulateAntialiasedStroke(
            contour.points,
            style.lineWidth,
            contour.closed,
            style.lineCap,
            style.lineJoin,
            style.miterLimit,
            style.antialiasPixelSize,
        )
    } else {
        val triangles =
            triangulateStroke(
                contour.points,
                style.lineWidth,
                contour.closed,
                style.lineCap,
                style.lineJoin,
                style.miterLimit,
            )
        SKStrokeMesh(triangles, List(triangles.size) { 1f })
    }

/** Appends [vertices] to [target], returning the index range ([target] indices) it now occupies. */
private fun appendRange(
    target: MutableList<Vector2>,
    vertices: List<Vector2>,
): IntRange {
    val start = target.size
    target += vertices
    return start until target.size
}
