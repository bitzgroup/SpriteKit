package jp.co.bitz.spritekit

import android.graphics.Color

/**
 * A node that draws a textured (or plain-colored) rectangle — mirrors Apple's `SKSpriteNode`.
 *
 * Deviation: [size] always defaults to [Vector2.Zero], even when [texture] is set. Apple
 * auto-sizes a sprite to its texture's pixel dimensions (adjusted by scale factor); doing the
 * same here would mean reading [android.graphics.Bitmap.getWidth]/`getHeight` from inside this
 * class's own logic, which — like the rest of this library — stays out of code paths meant to be
 * pure-Kotlin/unit-testable (see `docs/API_COMPATIBILITY.md`). Set [size] explicitly.
 */
public open class SKSpriteNode(
    public var texture: SKTexture? = null,
    public var size: Vector2 = Vector2.Zero,
    public var color: Int = Color.WHITE,
) : SKNode() {
    /**
     * How much [color] tints [texture]'s own colors, from `0` (texture shows through unmodified,
     * the default) to `1` (fully replaced by [color]). Untextured sprites always render as a
     * solid [color] rectangle regardless of this value.
     */
    public var colorBlendFactor: Float = 0f

    /** How this sprite's pixels combine with what's already drawn. Defaults to [SKBlendMode.Alpha]. */
    public var blendMode: SKBlendMode = SKBlendMode.Alpha

    /**
     * A custom fragment shader this sprite draws with, replacing the renderer's default fragment
     * shader — `null` (the default) draws normally. See `SKShader.kt`.
     */
    public var shader: SKShader? = null

    /**
     * The point within [size] (normalized `0..1` on each axis) that [SKNode.position] refers to.
     * `(0.5, 0.5)` (the default) centers the sprite on its position; `(0, 0)` anchors its
     * bottom-left corner there instead.
     */
    public var anchorPoint: Vector2 = Vector2(0.5f, 0.5f)

    override val localBounds: Rect
        get() =
            Rect(
                // `0f - (...)` rather than unary `-`, so an anchorPoint of exactly 0 yields +0f,
                // not -0f (IEEE 754: negating zero flips its sign bit; subtracting doesn't).
                left = 0f - size.x * anchorPoint.x,
                top = 0f - size.y * anchorPoint.y,
                right = size.x * (1f - anchorPoint.x),
                bottom = size.y * (1f - anchorPoint.y),
            )

    /**
     * [localBounds]'s four corners, in this node's own local space, in CCW winding order starting
     * bottom-left. Used by the sprite renderer to build this sprite's world-space quad — a member
     * function (rather than an extension elsewhere) since it needs access to the `protected`
     * [localBounds].
     */
    internal fun localQuadCorners(): List<Vector2> {
        val bounds = localBounds
        return listOf(
            Vector2(bounds.left, bounds.top),
            Vector2(bounds.right, bounds.top),
            Vector2(bounds.right, bounds.bottom),
            Vector2(bounds.left, bounds.bottom),
        )
    }
}
