package jp.co.bitz.spritekit

// [SKShapeNode.fillTexture] support for [SKRenderCommandList.kt]'s `addShapeCommands`: mapping a
// shape's fill triangles onto a texture. Kept separate from the command builder itself; the
// mapping is pure Kotlin and unit-tested (`SKShapeFillTextureTest`).

/**
 * One contour's fill triangles, [worldVertices] ([range] of [shape]'s vertices): as-is for a flat
 * [SKShapeNode.fillColor] fill, or with texture coordinates from [fillTextureVertices] when
 * [SKShapeNode.fillTexture] is set (the texture is then multiplied by [SKShapeNode.fillColor]).
 */
internal fun fillVertices(
    node: SKShapeNode,
    shape: SKShapeTriangulationCache,
    worldVertices: List<SKRenderVertex>,
    range: IntRange,
): List<SKRenderVertex> {
    val texture = node.fillTexture
    val bounds = shape.fillBounds
    return if (texture != null && bounds != null) {
        fillTextureVertices(
            worldVertices,
            shape.allLocalVertices.subList(range.first, range.last + 1),
            bounds,
            texture.textureRect,
        )
    } else {
        worldVertices
    }
}

/**
 * [worldVertices] (one fill triangle list) with texture coordinates added so that
 * [SKShapeNode.fillTexture] is stretched across [bounds] (the shape's local-space fill bounding box,
 * [SKShapeTriangulationCache.fillBounds]): each vertex's position within [bounds], read from its
 * matching [localVertices] entry, maps linearly left-to-right onto `uv.left..uv.right` and — local
 * space being y-up — bottom-to-top onto `uv.bottom..uv.top`, the same orientation [quadVertices]
 * gives a sprite. Areas of [bounds] outside the shape's own outline simply have no triangles, which
 * is what clips the texture to the shape.
 */
internal fun fillTextureVertices(
    worldVertices: List<SKRenderVertex>,
    localVertices: List<Vector2>,
    bounds: Rect,
    uv: Rect,
): List<SKRenderVertex> =
    worldVertices.mapIndexed { index, vertex ->
        val local = localVertices[index]
        vertex.copy(
            u = lerp(uv.left, uv.right, fractionAlong(local.x, bounds.left, bounds.width)),
            v = lerp(uv.bottom, uv.top, fractionAlong(local.y, bounds.top, bounds.height)),
        )
    }

/** Where [value] sits along `start..start + length`, as `0..1` (`0` for a degenerate zero [length]). */
private fun fractionAlong(
    value: Float,
    start: Float,
    length: Float,
): Float = if (length > 0f) (value - start) / length else 0f

private fun lerp(
    from: Float,
    to: Float,
    fraction: Float,
): Float = from + (to - from) * fraction
