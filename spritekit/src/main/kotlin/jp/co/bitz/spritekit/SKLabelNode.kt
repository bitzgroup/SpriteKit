package jp.co.bitz.spritekit

import android.graphics.Color

/**
 * A node that draws text — mirrors Apple's `SKLabelNode`. Text is rendered into a cached
 * texture (regenerated only when something that affects it actually changes) via
 * `android.graphics.Paint`/`Canvas`, and `android.text.StaticLayout` for multi-line text; see
 * [renderLabelBitmap] and `docs/API_COMPATIBILITY.md`.
 */
public open class SKLabelNode(
    public var text: String = "",
) : SKNode() {
    /**
     * The font, named the way Apple names fonts: a family plus an optional PostScript-style
     * `-Style` suffix (`"Helvetica-Bold"`, `"HelveticaNeue-LightItalic"`, `"Menlo"`) — the suffix's
     * weight and italic are applied to the family Android resolves via `Typeface.create`. Android
     * family names (`"sans-serif"`, `"monospace"`, `"sans-serif-medium"`) work too. `null` (the
     * default) uses the platform default font. See `docs/API_COMPATIBILITY.md`.
     */
    public var fontName: String? = null

    /** The font size, in points. */
    public var fontSize: Float = 32f

    /** The text color. Defaults to opaque white, matching Apple. */
    public var fontColor: Int = Color.WHITE

    /**
     * Where the text sits horizontally relative to [SKNode.position]. Defaults to
     * [SKLabelHorizontalAlignmentMode.Center].
     */
    public var horizontalAlignmentMode: SKLabelHorizontalAlignmentMode = SKLabelHorizontalAlignmentMode.Center

    /**
     * Where the text sits vertically relative to [SKNode.position]. Defaults to
     * [SKLabelVerticalAlignmentMode.Baseline].
     */
    public var verticalAlignmentMode: SKLabelVerticalAlignmentMode = SKLabelVerticalAlignmentMode.Baseline

    /**
     * How many lines the text may take — Apple's `numberOfLines`. `1` (the default) draws one
     * line however long, ignoring [preferredMaxLayoutWidth] and [lineBreakMode] and drawing a
     * newline in place rather than breaking at it. `0` breaks at newlines and wraps at
     * [preferredMaxLayoutWidth] with no line limit; a larger number does the same but keeps at
     * most that many lines, truncating per [lineBreakMode] (verified on iOS/tvOS simulators).
     */
    public var numberOfLines: Int = 1

    /**
     * The width, in points, a multi-line label wraps at — Apple's `preferredMaxLayoutWidth`. `0`
     * (the default) means no limit: lines break only at newlines. Ignored when
     * [numberOfLines] is `1`.
     */
    public var preferredMaxLayoutWidth: Float = 0f

    /**
     * How a multi-line label wraps and truncates — Apple's `lineBreakMode`. Defaults to
     * [SKLineBreakMode.ByTruncatingTail], as on Apple. Ignored when [numberOfLines] is `1`.
     */
    public var lineBreakMode: SKLineBreakMode = SKLineBreakMode.ByTruncatingTail

    private var cacheKey: SKLabelCacheKey? = null
    private var cachedTexture: SKTexture? = null
    private var cachedMetrics: SKLabelMetrics? = null

    /**
     * (Re)renders [text] if it (or the font) changed since the last call, and returns the
     * current texture + metrics.
     */
    internal fun renderedLabel(): Pair<SKTexture, SKLabelMetrics>? {
        val layout =
            if (numberOfLines == 1) {
                null
            } else {
                SKLabelLineLayout(numberOfLines, preferredMaxLayoutWidth, lineBreakMode, horizontalAlignmentMode)
            }
        val key = SKLabelCacheKey(text, fontName, fontSize, fontColor, layout)
        if (key != cacheKey) {
            val rendered = renderLabelBitmap(key)
            cachedTexture = rendered?.let { (bitmap, _) -> SKTexture(bitmap) }
            cachedMetrics = rendered?.second
            cacheKey = key
        }
        val texture = cachedTexture
        val metrics = cachedMetrics
        return if (texture != null && metrics != null) texture to metrics else null
    }

    override val localBounds: Rect
        get() {
            val (_, metrics) = renderedLabel() ?: return Rect.Zero
            val corners = labelQuadCorners(metrics, horizontalAlignmentMode, verticalAlignmentMode)
            return Rect(left = corners[0].x, top = corners[0].y, right = corners[2].x, bottom = corners[2].y)
        }
}
