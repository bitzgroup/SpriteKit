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
 * [lineWidth] changes) — see [triangulatedShape]'s KDoc for why this matters. Reassign [path] to
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

    /** The outline's width, in points. `0` (the default) draws no outline regardless of [strokeColor]. */
    public var lineWidth: Float = 0f

    /** Stored for API parity; not implemented — see this class's KDoc. */
    public var glowWidth: Float = 0f

    /**
     * Render-thread-confined cache of this shape's flattened-and-triangulated geometry, keyed by
     * the exact [path] instance and [lineWidth] it was computed from — see
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

    override val localBounds: Rect
        get() {
            val currentPath = path ?: return Rect.Zero
            val bounds = RectF()
            currentPath.computeBounds(bounds, true)
            return Rect(bounds.left, bounds.top, bounds.right, bounds.bottom)
        }
}
