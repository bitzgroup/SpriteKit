package jp.co.bitz.spritekit

import android.graphics.Path

/**
 * A shape's flattened contours plus their fill/stroke triangle lists, all concatenated into one
 * [allLocalVertices] with [fillRanges]/[strokeRanges] marking each contour's slice of it — so a
 * caller needing "every vertex this shape ever draws" (see [SKRenderCommandList.kt]'s
 * `worldVertices`, which converts them all into world space in one batch) doesn't need to flatten
 * a list-of-lists itself, and slicing out one contour's fill or stroke triangles back out
 * ([List.subList]) is a view, not a copy.
 *
 * Everything here is local-space and path-derived — it's *not* the per-vertex world-space
 * transform, which still has to run every frame since the node (or an ancestor) can move
 * independently of its own geometry; see [SKRenderCommandList.kt]'s `worldVertices`.
 */
internal class SKShapeTriangulationCache(
    val path: Path,
    val lineWidth: Float,
    val contours: List<SKFlattenedContour>,
    val allLocalVertices: List<Vector2>,
    val fillRanges: List<IntRange>,
    val strokeRanges: List<IntRange>,
) {
    /**
     * The local-space bounding box of every fill vertex across all contours — the area
     * [SKShapeNode.fillTexture] is stretched across (see [fillTextureVertices]) — or `null` if the
     * shape has no fill geometry at all. Computed once per triangulation, not per frame.
     */
    val fillBounds: Rect? by lazy { fillBoundsOf(allLocalVertices, fillRanges) }
}

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
 * `scenes` layer of consumer apps) and [SKShapeNode.lineWidth] unchanged, and recomputing
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
): SKShapeTriangulationCache {
    val cached = node.triangulationCache
    if (cached != null && cached.path === path && cached.lineWidth == node.lineWidth) return cached

    val contours = flattenPath(path)
    val allLocalVertices = mutableListOf<Vector2>()
    val fillRanges = contours.map { appendRange(allLocalVertices, triangulateFill(it.points)) }
    val strokeRanges =
        contours.map { appendRange(allLocalVertices, triangulateStroke(it.points, node.lineWidth, it.closed)) }
    val fresh =
        SKShapeTriangulationCache(
            path = path,
            lineWidth = node.lineWidth,
            contours = contours,
            allLocalVertices = allLocalVertices,
            fillRanges = fillRanges,
            strokeRanges = strokeRanges,
        )
    node.triangulationCache = fresh
    return fresh
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
