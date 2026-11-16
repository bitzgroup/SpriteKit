package jp.co.bitz.spritekit

import kotlin.math.abs

/** One cell of a 9-slice sprite: its local corners (`[bottomLeft, bottomRight, topRight, topLeft]`) and texture UVs. */
internal data class SKSpriteSlice(
    val corners: List<Vector2>,
    val uv: Rect,
)

/** [SKSpriteNode.centerRect]'s default: the whole texture stretches. */
internal val FULL_UNIT_RECT: Rect = Rect(0f, 0f, 1f, 1f)

/**
 * Splits a sprite of local [bounds] into the cells [SKSpriteNode.centerRect] describes — Apple's
 * 9-slice stretching, verified on iOS/tvOS simulators: the border cells keep the size they have
 * in the texture (in scene points, so the sprite's own [scale] is undone for them), and only the
 * center cell stretches. When the sprite is too small for both borders on an axis, they shrink
 * in proportion and the center cell vanishes.
 *
 * [centerRect] is in unit texture space with its origin at the bottom left (`top` the lower
 * edge, as [Rect] always stores the smaller y in `top`). [uv] is the texture's own
 * [SKTexture.textureRect], whose `top` is the image's top edge. Cells with no area are skipped.
 */
internal fun nineSlices(
    bounds: Rect,
    centerRect: Rect,
    textureSize: Vector2,
    scale: Vector2,
    uv: Rect,
): List<SKSpriteSlice> {
    val xs =
        sliceEdges(bounds.left, bounds.right, centerRect.left, centerRect.right, pointsPerUnit(textureSize.x, scale.x))
    val ys =
        sliceEdges(bounds.top, bounds.bottom, centerRect.top, centerRect.bottom, pointsPerUnit(textureSize.y, scale.y))
    val us = listOf(0f, centerRect.left, centerRect.right, 1f).map { uv.left + it * uv.width }
    // Fractions run up from the bottom; the texture's v runs down from its top.
    val vs = listOf(0f, centerRect.top, centerRect.bottom, 1f).map { uv.bottom - it * uv.height }
    val slices = mutableListOf<SKSpriteSlice>()
    for (row in 0..2) {
        for (column in 0..2) {
            val left = xs[column]
            val right = xs[column + 1]
            val bottom = ys[row]
            val top = ys[row + 1]
            if (right <= left || top <= bottom) continue
            slices +=
                SKSpriteSlice(
                    corners =
                        listOf(Vector2(left, bottom), Vector2(right, bottom), Vector2(right, top), Vector2(left, top)),
                    uv = Rect(left = us[column], top = vs[row + 1], right = us[column + 1], bottom = vs[row]),
                )
        }
    }
    return slices
}

/**
 * How many local points one unit of texture space spans along an axis so that a border keeps its
 * texture size on screen: the texture's length with the sprite's [scale] undone. `0` when the
 * scale is `0` (nothing shows anyway).
 */
private fun pointsPerUnit(
    textureLength: Float,
    scale: Float,
): Float = if (scale == 0f) 0f else textureLength / abs(scale)

/** The four edges along one axis: start, end of the low border, start of the high border, end. */
private fun sliceEdges(
    start: Float,
    end: Float,
    centerStart: Float,
    centerEnd: Float,
    pointsPerUnit: Float,
): List<Float> {
    val length = end - start
    var low = centerStart * pointsPerUnit
    var high = (1f - centerEnd) * pointsPerUnit
    val borders = low + high
    if (borders > length && borders > 0f) {
        low *= length / borders
        high *= length / borders
    }
    return listOf(start, start + low, end - high, end)
}
