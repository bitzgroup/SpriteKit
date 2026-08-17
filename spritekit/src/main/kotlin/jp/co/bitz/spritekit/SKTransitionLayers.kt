package jp.co.bitz.spritekit

/**
 * Which half of the viewport a transition layer is clipped to -- [SKTransitionKind.Doorway]'s two
 * splitting panels. `None` means unclipped.
 */
internal enum class SKTransitionSplit {
    None,
    Left,
    Right,
}

/**
 * One `SKSceneRenderer.draw` call's worth of a transition frame: which scene, at what
 * `glViewport` offset/size (`null` size means the full viewport), overall alpha multiplier,
 * whether it should clear the framebuffer first (the first layer of a frame always should; later
 * layers drawn on top of it shouldn't), and which half of the viewport (if any) to clip to.
 */
internal data class SKTransitionLayer(
    val useToScene: Boolean,
    val viewportOffset: Vector2 = Vector2.Zero,
    val viewportSize: Vector2? = null,
    val alpha: Float = 1f,
    val clearFirst: Boolean = true,
    val split: SKTransitionSplit = SKTransitionSplit.None,
)

/**
 * [direction]'s motion as a `(viewWidth, viewHeight)`-scaled vector -- one full screen's worth of
 * travel in that direction.
 */
internal fun directionVector(
    direction: SKTransitionDirection,
    viewWidth: Int,
    viewHeight: Int,
): Vector2 =
    when (direction) {
        SKTransitionDirection.Right -> Vector2(viewWidth.toFloat(), 0f)
        SKTransitionDirection.Left -> Vector2(-viewWidth.toFloat(), 0f)
        SKTransitionDirection.Up -> Vector2(0f, viewHeight.toFloat())
        SKTransitionDirection.Down -> Vector2(0f, -viewHeight.toFloat())
    }

/**
 * The ordered (back-to-front) [SKTransitionLayer]s to draw for [transition] at [progress] (`0..1`,
 * clamped) into a [viewWidth] by [viewHeight] viewport -- pure geometry/timing math, with no
 * OpenGL calls, so it's unit-testable independent of a live GL context; `SKView` is the only
 * caller, translating this into actual `SKSceneRenderer.draw` calls.
 */
internal fun transitionLayers(
    transition: SKTransition,
    progress: Float,
    viewWidth: Int,
    viewHeight: Int,
): List<SKTransitionLayer> {
    val t = progress.coerceIn(0f, 1f)
    val motion = directionVector(transition.direction, viewWidth, viewHeight)
    return when (transition.kind) {
        SKTransitionKind.Fade -> fadeLayers(t)
        SKTransitionKind.CrossFade -> crossFadeLayers(t)
        SKTransitionKind.MoveIn -> moveInLayers(motion, t)
        SKTransitionKind.Push -> pushLayers(motion, t)
        SKTransitionKind.Reveal -> revealLayers(motion, t)
        SKTransitionKind.Doorway -> doorwayLayers(viewWidth, t)
        SKTransitionKind.FlipHorizontal -> flipLayers(t, horizontal = true, viewWidth, viewHeight)
        SKTransitionKind.FlipVertical -> flipLayers(t, horizontal = false, viewWidth, viewHeight)
    }
}

private fun fadeLayers(t: Float): List<SKTransitionLayer> =
    if (t < 0.5f) {
        listOf(SKTransitionLayer(useToScene = false, alpha = 1f - 2f * t))
    } else {
        listOf(SKTransitionLayer(useToScene = true, alpha = 2f * t - 1f))
    }

private fun crossFadeLayers(t: Float): List<SKTransitionLayer> =
    listOf(
        SKTransitionLayer(useToScene = false),
        SKTransitionLayer(useToScene = true, alpha = t, clearFirst = false),
    )

private fun moveInLayers(
    motion: Vector2,
    t: Float,
): List<SKTransitionLayer> =
    listOf(
        SKTransitionLayer(useToScene = false),
        SKTransitionLayer(useToScene = true, viewportOffset = motion * -(1f - t), clearFirst = false),
    )

private fun pushLayers(
    motion: Vector2,
    t: Float,
): List<SKTransitionLayer> =
    listOf(
        SKTransitionLayer(useToScene = false, viewportOffset = motion * t),
        SKTransitionLayer(useToScene = true, viewportOffset = motion * -(1f - t), clearFirst = false),
    )

private fun revealLayers(
    motion: Vector2,
    t: Float,
): List<SKTransitionLayer> =
    listOf(
        SKTransitionLayer(useToScene = true),
        SKTransitionLayer(useToScene = false, viewportOffset = motion * t, clearFirst = false),
    )

private fun doorwayLayers(
    viewWidth: Int,
    t: Float,
): List<SKTransitionLayer> {
    val halfWidth = viewWidth * 0.5f
    return listOf(
        SKTransitionLayer(useToScene = true),
        SKTransitionLayer(
            useToScene = false,
            viewportOffset = Vector2(-halfWidth * t, 0f),
            clearFirst = false,
            split = SKTransitionSplit.Left,
        ),
        SKTransitionLayer(
            useToScene = false,
            viewportOffset = Vector2(halfWidth * t, 0f),
            clearFirst = false,
            split = SKTransitionSplit.Right,
        ),
    )
}

private fun flipLayers(
    t: Float,
    horizontal: Boolean,
    viewWidth: Int,
    viewHeight: Int,
): List<SKTransitionLayer> {
    // 0f..0.5f: the outgoing scene squashes down to nothing; 0.5f..1f: the incoming scene grows
    // back out from nothing -- this port's 2D stand-in for a true 3D flip.
    val fullSize = Vector2(viewWidth.toFloat(), viewHeight.toFloat())
    val shrinking = t < 0.5f
    val localT = if (shrinking) t / 0.5f else (t - 0.5f) / 0.5f
    val scale = if (shrinking) 1f - localT else localT
    val size = if (horizontal) Vector2(fullSize.x * scale, fullSize.y) else Vector2(fullSize.x, fullSize.y * scale)
    val offset = (fullSize - size) * 0.5f
    return listOf(SKTransitionLayer(useToScene = !shrinking, viewportOffset = offset, viewportSize = size))
}
