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
 * For [SKSceneScaleMode.AspectFit]/[SKSceneScaleMode.AspectFill], the declared [sceneSize] rect
 * is always centered within the viewport — [anchorPoint] never shifts *where* it renders, only
 * which local coordinate a node must use to sit at a given point of that (fixed, centered) rect.
 * Verified against Apple's real `SKScene`/`SKView` on an iOS Simulator: with the default
 * `anchorPoint` of `(0, 0)` and `.aspectFit`, the extra space `.aspectFit` reveals beyond
 * [sceneSize] on the non-constraining axis splits evenly above and below (or left and right of)
 * the scene, not entirely to one side the way an anchor of `(0, 0)` might suggest. (Originally
 * implemented as a straightforward `anchorPoint`-scaled offset into the full letterboxed/cropped
 * `projectedWidth`/`projectedHeight` rect — correct for [SKSceneScaleMode.Fill]/
 * [SKSceneScaleMode.ResizeFill], where there is no such extra space, but not for `.aspectFit`/
 * `.aspectFill`, where it put *all* the extra space on the max-coordinate side, pushing everything
 * else in the scene the other way. `bitzgroup/tic-tac-toe`'s Android build visibly sat lower on
 * screen than its iOS twin because of this. See `docs/API_COMPATIBILITY.md`.)
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
    // Where the declared sceneSize rect's own center sits, in the local coordinates anchorPoint
    // puts it in: (0.5, 0.5) always means "at scene-local (0, 0)" — halfway between the anchorPoint
    // (0, 0)) and (1, 1) corners this rect spans, in whichever direction anchorPoint offsets them.
    val centerX = sceneSize.x * (0.5f - anchorPoint.x)
    val centerY = sceneSize.y * (0.5f - anchorPoint.y)
    return SKSceneProjection(
        left = centerX - projectedWidth / 2f,
        right = centerX + projectedWidth / 2f,
        bottom = centerY - projectedHeight / 2f,
        top = centerY + projectedHeight / 2f,
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
