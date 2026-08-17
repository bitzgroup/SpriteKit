package jp.co.bitz.spritekit

import android.graphics.Color

/**
 * A node that draws a line of text — mirrors Apple's `SKLabelNode`. Text is rendered into a
 * cached texture (regenerated only when [text]/[fontName]/[fontSize]/[fontColor] actually
 * change) via `android.graphics.Paint`/`Canvas`; see [renderLabelBitmap] and
 * `docs/API_COMPATIBILITY.md`.
 *
 * Deviation: single-line only — Apple's `numberOfLines`/`preferredMaxLayoutWidth` multi-line
 * wrapping isn't implemented; see `docs/API_COMPATIBILITY.md`.
 */
public open class SKLabelNode(
    public var text: String = "",
) : SKNode() {
    /**
     * The font family name, resolved via `Typeface.create`. `null` (the default) uses the
     * platform default font.
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

    private var cacheKey: SKLabelCacheKey? = null
    private var cachedTexture: SKTexture? = null
    private var cachedMetrics: SKLabelMetrics? = null

    /**
     * (Re)renders [text] if it (or the font) changed since the last call, and returns the
     * current texture + metrics.
     */
    internal fun renderedLabel(): Pair<SKTexture, SKLabelMetrics>? {
        val key = SKLabelCacheKey(text, fontName, fontSize, fontColor)
        if (key != cacheKey) {
            val rendered = renderLabelBitmap(text, fontName, fontSize, fontColor)
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
