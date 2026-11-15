package jp.co.bitz.spritekit

// View <-> scene point conversion behind `SKView.convertTo`/`convertFrom` and
// `SKScene.convertPointFromView`/`convertPointToView`, sharing `SKTouchDispatch.kt`'s projection
// math so converted points always agree with where touches land and content renders.

private fun projectionFor(
    scene: SKScene,
    viewWidth: Int,
    viewHeight: Int,
): SKSceneProjection =
    computeSceneProjection(
        scene.size,
        scene.anchorPoint,
        scene.scaleMode,
        viewWidth,
        viewHeight,
        hasCamera = scene.camera != null,
    )

/**
 * [viewPoint] (view pixels, origin top-left, y-down) in [scene]'s coordinate system, for a view of
 * [viewWidth] by [viewHeight] pixels. Accounts for [SKScene.scaleMode], [SKScene.anchorPoint] and
 * the scene's [SKScene.camera]. Returns [viewPoint] unchanged while the view has no size yet.
 */
internal fun viewPointToScene(
    scene: SKScene,
    viewPoint: Vector2,
    viewWidth: Int,
    viewHeight: Int,
): Vector2 {
    if (viewWidth <= 0 || viewHeight <= 0) return viewPoint
    val projection = projectionFor(scene, viewWidth, viewHeight)
    val reference = viewToScenePoint(viewPoint.x, viewPoint.y, projection, viewWidth, viewHeight)
    return scene.camera?.let { scene.convertFrom(reference, it) } ?: reference
}

/** The inverse of [viewPointToScene]: [scenePoint] in [scene]'s coordinate system, as view pixels. */
internal fun scenePointToView(
    scene: SKScene,
    scenePoint: Vector2,
    viewWidth: Int,
    viewHeight: Int,
): Vector2 {
    if (viewWidth <= 0 || viewHeight <= 0) return scenePoint
    val projection = projectionFor(scene, viewWidth, viewHeight)
    val reference = scene.camera?.convertFrom(scenePoint, scene) ?: scenePoint
    return Vector2(
        x = (reference.x - projection.left) / (projection.right - projection.left) * viewWidth,
        y = (projection.top - reference.y) / (projection.top - projection.bottom) * viewHeight,
    )
}
