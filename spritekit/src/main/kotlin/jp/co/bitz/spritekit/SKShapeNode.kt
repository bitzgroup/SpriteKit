package jp.co.bitz.spritekit

import android.graphics.Color
import android.graphics.Path
import android.graphics.RectF

/**
 * A node that draws a filled and/or stroked vector shape — mirrors Apple's `SKShapeNode`.
 * [path] is a plain `android.graphics.Path` (this library's `CGPath` stand-in, since it already
 * fits the role well — see `docs/API_COMPATIBILITY.md`), triangulated for rendering via
 * [triangulateFill]/[triangulateStroke] once flattened by [flattenPath].
 *
 * Deviation: [glowWidth] is stored for API parity but doesn't render a glow — that needs a
 * blur/glow shader pass, deferred with the rest of the advanced shader work (Phase 13); see
 * `docs/ROADMAP.md`.
 */
public open class SKShapeNode(
    public var path: Path? = null,
) : SKNode() {
    /** The shape's outline color. Defaults to opaque white, matching Apple. */
    public var strokeColor: Int = Color.WHITE

    /** The shape's fill color. Defaults to transparent (no fill), matching Apple. */
    public var fillColor: Int = Color.TRANSPARENT

    /** The outline's width, in points. `0` (the default) draws no outline regardless of [strokeColor]. */
    public var lineWidth: Float = 0f

    /** Stored for API parity; not implemented — see this class's KDoc. */
    public var glowWidth: Float = 0f

    override val localBounds: Rect
        get() {
            val currentPath = path ?: return Rect.Zero
            val bounds = RectF()
            currentPath.computeBounds(bounds, true)
            return Rect(bounds.left, bounds.top, bounds.right, bounds.bottom)
        }
}
