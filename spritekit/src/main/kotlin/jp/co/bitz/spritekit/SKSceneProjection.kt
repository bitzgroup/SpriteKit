package jp.co.bitz.spritekit

/**
 * The scene-space rect (see `docs/ARCHITECTURE.md`'s "Coordinate systems" section — y-up, before
 * any `EGLContext`-side viewport mapping) that an [SKView]'s full viewport maps to. Not part of
 * this library's public API — an implementation detail of how [SKScene.scaleMode] is realized.
 */
internal data class SKSceneProjection(
    val left: Float,
    val right: Float,
    val bottom: Float,
    val top: Float,
)

/**
 * Computes the [SKSceneProjection] for a scene of [sceneSize] with [scaleMode] and [anchorPoint],
 * presented in a viewport of [viewWidth] by [viewHeight] pixels.
 *
 * Pure Kotlin — no OpenGL/Android dependency — so the letterbox/crop math for each [scaleMode] is
 * unit-testable independent of a live GL context; only building the actual orthographic matrix
 * from this result (in the sprite renderer) touches `android.opengl.Matrix`.
 */
internal fun computeSceneProjection(
    sceneSize: Vector2,
    anchorPoint: Vector2,
    scaleMode: SKSceneScaleMode,
    viewWidth: Int,
    viewHeight: Int,
): SKSceneProjection {
    val (projectedWidth, projectedHeight) =
        when (scaleMode) {
            SKSceneScaleMode.Fill, SKSceneScaleMode.ResizeFill -> sceneSize.x to sceneSize.y
            SKSceneScaleMode.AspectFit -> aspectScaledSize(sceneSize, viewWidth, viewHeight, useMinScale = true)
            SKSceneScaleMode.AspectFill -> aspectScaledSize(sceneSize, viewWidth, viewHeight, useMinScale = false)
        }
    return SKSceneProjection(
        // `0f - (...)` rather than unary `-`, so an anchorPoint of exactly 0 yields +0f, not -0f
        // (IEEE 754: negating zero flips its sign bit; subtracting from zero doesn't).
        left = 0f - projectedWidth * anchorPoint.x,
        right = projectedWidth * (1f - anchorPoint.x),
        bottom = 0f - projectedHeight * anchorPoint.y,
        top = projectedHeight * (1f - anchorPoint.y),
    )
}

/**
 * The scene-space size that, uniformly scaled by whichever axis is the limiting one (the smaller
 * scale factor for [SKSceneScaleMode.AspectFit], the larger for [SKSceneScaleMode.AspectFill]),
 * exactly fills [viewWidth] by [viewHeight] pixels.
 */
private fun aspectScaledSize(
    sceneSize: Vector2,
    viewWidth: Int,
    viewHeight: Int,
    useMinScale: Boolean,
): Pair<Float, Float> {
    val widthScale = viewWidth / sceneSize.x
    val heightScale = viewHeight / sceneSize.y
    val scale = if (useMinScale) minOf(widthScale, heightScale) else maxOf(widthScale, heightScale)
    return (viewWidth / scale) to (viewHeight / scale)
}
