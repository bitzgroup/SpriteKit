package jp.co.bitz.spritekit

import android.graphics.Color
import kotlin.math.abs

/**
 * A node that draws a textured (or plain-colored) rectangle — mirrors Apple's `SKSpriteNode`.
 *
 * Like Apple's `SKSpriteNode(texture:)`, the size defaults to [texture]'s own [SKTexture.size] when
 * one is given (and to [Vector2.Zero] otherwise). As on Apple, that happens only at construction:
 * assigning a different [texture] later leaves [size] unchanged.
 *
 * @param size The sprite's size at a scale of `1` — see [size].
 */
public open class SKSpriteNode(
    public var texture: SKTexture? = null,
    size: Vector2 = texture?.size() ?: Vector2.Zero,
    public var color: Int = Color.WHITE,
) : SKNode() {
    /**
     * A sprite showing the image [imageNamed] from the host app's `assets/` folder, sized to it —
     * Apple's `SKSpriteNode(imageNamed:)`. See [SKTexture]'s `imageNamed` constructor for how the
     * name is found.
     */
    public constructor(imageNamed: String) : this(SKTexture(imageNamed))

    override fun copy(): SKSpriteNode = copyInto(SKSpriteNode())

    override fun copyState(target: SKNode) {
        super.copyState(target)
        if (target !is SKSpriteNode) return
        target.texture = texture
        target.color = color
        target.unscaledSize = unscaledSize
        target.centerRect = centerRect
        target.colorBlendFactor = colorBlendFactor
        target.blendMode = blendMode
        target.shader = shader
        target.anchorPoint = anchorPoint
    }

    /** The sprite's size before [xScale]/[yScale] apply — what it draws in its own local space. */
    internal var unscaledSize: Vector2 = size

    /**
     * The sprite's size including its scale, as on Apple (verified on iOS/tvOS simulators): its
     * unscaled size times the magnitude of [xScale]/[yScale], so doubling `xScale` doubles
     * `size.x`. Assigning it stores the value divided by that magnitude, so the scale itself is
     * unchanged; on an axis whose scale is `0`, the assignment is ignored (the unscaled size is
     * kept).
     */
    public var size: Vector2
        get() = Vector2(unscaledSize.x * abs(xScale), unscaledSize.y * abs(yScale))
        set(value) {
            unscaledSize =
                Vector2(
                    if (xScale == 0f) unscaledSize.x else value.x / abs(xScale),
                    if (yScale == 0f) unscaledSize.y else value.y / abs(yScale),
                )
        }

    /**
     * Sets [xScale]/[yScale] so that [size] becomes [to] — Apple's `scale(to:)`. Worked out from the
     * unscaled size, so the current scale (and rotation) doesn't matter. An axis whose unscaled
     * size is `0` keeps its scale.
     */
    public fun scale(to: Vector2) {
        val target = scaleForSize(to)
        xScale = target.x
        yScale = target.y
    }

    /** The `xScale`/`yScale` that would make [size] equal [target]; see [scale]. */
    internal fun scaleForSize(target: Vector2): Vector2 =
        Vector2(
            if (unscaledSize.x == 0f) xScale else target.x / unscaledSize.x,
            if (unscaledSize.y == 0f) yScale else target.y / unscaledSize.y,
        )

    /**
     * Apple's `centerRect`: the part of [texture] that stretches when the sprite is drawn larger
     * or smaller than the texture, in unit texture coordinates with the origin at the bottom left
     * (`Rect(left = x, top = y, right = x + width, bottom = y + height)` for Apple's
     * `CGRect(x:y:width:height:)`). The border around it keeps its texture size on screen —
     * 9-slice stretching, for panels and buttons. Applies whether the sprite grows through
     * [size] or through [xScale]/[yScale]. Defaults to the whole texture, which stretches all
     * of it (verified on iOS/tvOS simulators).
     */
    public var centerRect: Rect = FULL_UNIT_RECT

    /** This sprite's [centerRect] cells for [texture], in local space; see [nineSlices]. */
    internal fun nineSlices(texture: SKTexture): List<SKSpriteSlice> =
        nineSlices(localBounds, centerRect, texture.size(), Vector2(xScale, yScale), texture.textureRect)

    /**
     * How much [color] tints [texture]'s own colors, from `0` (texture shows through unmodified,
     * the default) to `1` (each pixel multiplied by [color], its alpha included); values outside
     * `0..1` are clamped. An untextured sprite always draws as a solid [color] rectangle (alpha
     * included) whatever this value is, as on Apple (verified on iOS/tvOS simulators).
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
                left = 0f - unscaledSize.x * anchorPoint.x,
                top = 0f - unscaledSize.y * anchorPoint.y,
                right = unscaledSize.x * (1f - anchorPoint.x),
                bottom = unscaledSize.y * (1f - anchorPoint.y),
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
