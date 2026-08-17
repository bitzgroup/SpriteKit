package jp.co.bitz.spritekit

/** An [SKLabelNode]'s cache key: regenerate its texture only when one of these actually changes. */
internal data class SKLabelCacheKey(
    val text: String,
    val fontName: String?,
    val fontSize: Float,
    val fontColor: Int,
)

/**
 * A rendered label's measured extent, in points: [width], and how far the text rises ([ascent])
 * and falls ([descent]) from its baseline.
 */
internal data class SKLabelMetrics(
    val width: Float,
    val ascent: Float,
    val descent: Float,
)

/**
 * The local-space quad corners (`[bottomLeft, bottomRight, topRight, topLeft]`, matching
 * [SKSpriteNode.localQuadCorners]'s order) for a label of [metrics], positioned per
 * [horizontalAlignment]/[verticalAlignment] relative to [SKNode.position].
 *
 * Pure Kotlin — [metrics] is already-measured text extent, so this alignment math is
 * unit-testable independent of `android.graphics.Paint`.
 */
internal fun labelQuadCorners(
    metrics: SKLabelMetrics,
    horizontalAlignment: SKLabelHorizontalAlignmentMode,
    verticalAlignment: SKLabelVerticalAlignmentMode,
): List<Vector2> {
    val left =
        when (horizontalAlignment) {
            SKLabelHorizontalAlignmentMode.Left -> 0f
            SKLabelHorizontalAlignmentMode.Center -> 0f - metrics.width / 2f
            SKLabelHorizontalAlignmentMode.Right -> 0f - metrics.width
        }
    val right = left + metrics.width

    val height = metrics.ascent + metrics.descent
    val bottom =
        when (verticalAlignment) {
            SKLabelVerticalAlignmentMode.Baseline -> 0f - metrics.descent
            SKLabelVerticalAlignmentMode.Center -> 0f - height / 2f
            SKLabelVerticalAlignmentMode.Top -> 0f - height
            SKLabelVerticalAlignmentMode.Bottom -> 0f
        }
    val top = bottom + height

    return listOf(Vector2(left, bottom), Vector2(right, bottom), Vector2(right, top), Vector2(left, top))
}
