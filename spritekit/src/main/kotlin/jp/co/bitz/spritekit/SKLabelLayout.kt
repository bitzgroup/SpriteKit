package jp.co.bitz.spritekit

/** An [SKLabelNode]'s cache key: regenerate its texture only when one of these actually changes. */
internal data class SKLabelCacheKey(
    val text: String,
    val fontName: String?,
    val fontSize: Float,
    val fontColor: Int,
    /** `null` for a one-line label. */
    val lineLayout: SKLabelLineLayout? = null,
    /**
     * [SKLabelNode.attributedText], an immutable snapshot. When set, it alone is drawn: [text] and
     * the font fields are left at fixed values so changing them doesn't re-render (see [labelCacheKey]).
     */
    val attributedText: CharSequence? = null,
)

/**
 * The cache key for a label showing [text] in the given font — or, when [attributedText] isn't
 * `null`, showing that instead: Apple draws and sizes a label from its `attributedText` alone while
 * one is set, ignoring `text`, `fontName`, `fontSize` and `fontColor` (verified on iOS/tvOS
 * simulators), so those don't take part in the key then.
 */
@Suppress("LongParameterList")
internal fun labelCacheKey(
    text: String,
    fontName: String?,
    fontSize: Float,
    fontColor: Int,
    lineLayout: SKLabelLineLayout?,
    attributedText: CharSequence?,
): SKLabelCacheKey =
    if (attributedText == null) {
        SKLabelCacheKey(text, fontName, fontSize, fontColor, lineLayout)
    } else {
        SKLabelCacheKey("", null, 0f, 0, lineLayout, attributedText)
    }

/**
 * A multi-line label's layout settings ([SKLabelNode.numberOfLines] other than `1`).
 * [alignment] aligns the lines within the block, the way the label's
 * [SKLabelNode.horizontalAlignmentMode] places the block itself.
 */
internal data class SKLabelLineLayout(
    val numberOfLines: Int,
    val maxWidth: Float,
    val lineBreakMode: SKLineBreakMode,
    val alignment: SKLabelHorizontalAlignmentMode,
)

/**
 * A rendered label's measured extent, in points: [width], and how far the text rises ([ascent])
 * and falls ([descent]) from its baseline.
 *
 * A multi-line block has no single baseline: Apple aligns it as if the baseline were its bottom
 * edge (verified on iOS/tvOS simulators), so its metrics are its whole height as [ascent] and no
 * [descent] — see [multiLineMetrics].
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

/** The [SKLabelMetrics] of a multi-line block [width] × [height]; see [SKLabelMetrics]. */
internal fun multiLineMetrics(
    width: Float,
    height: Float,
): SKLabelMetrics = SKLabelMetrics(width, ascent = height, descent = 0f)
