package jp.co.bitz.spritekit

import android.graphics.Color

/**
 * Which half of its scene a transition layer is clipped to -- the door effects' two panels.
 * `None` means unclipped.
 */
internal enum class SKTransitionSplit {
    None,
    Left,
    Right,
    Bottom,
    Top,
}

/** How small the incoming scene starts out in [SKTransitionKind.Doorway], as recorded on iOS/tvOS simulators. */
private const val DOORWAY_INCOMING_START_SCALE = 0.42f

/**
 * One `SKSceneRenderer.draw` call's worth of a transition frame: which scene, at what
 * `glViewport` offset/size (`null` size means the full viewport), overall alpha multiplier,
 * whether it should clear the framebuffer first (the first layer of a frame always should; later
 * layers drawn on top of it shouldn't), and which half of its scene (if any) to clip to.
 */
internal data class SKTransitionLayer(
    val useToScene: Boolean,
    val viewportOffset: Vector2 = Vector2.Zero,
    val viewportSize: Vector2? = null,
    val alpha: Float = 1f,
    val clearFirst: Boolean = true,
    val split: SKTransitionSplit = SKTransitionSplit.None,
    /** What [clearFirst] clears to — `null` for the layer's own scene's background color. */
    val clearColor: Int? = null,
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
        SKTransitionKind.Doorway -> doorwayLayers(viewWidth, viewHeight, t)
        SKTransitionKind.DoorsOpenHorizontal -> doorsLayers(Vector2(viewWidth * 0.5f, 0f), opening = true, t)
        SKTransitionKind.DoorsOpenVertical -> doorsLayers(Vector2(0f, viewHeight * 0.5f), opening = true, t)
        SKTransitionKind.DoorsCloseHorizontal -> doorsLayers(Vector2(viewWidth * 0.5f, 0f), opening = false, t)
        SKTransitionKind.DoorsCloseVertical -> doorsLayers(Vector2(0f, viewHeight * 0.5f), opening = false, t)
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

/**
 * Apple's door effects ease in and out — `3t² − 2t³`, as recorded on iOS/tvOS simulators, with the
 * panels moving by exactly that fraction of half the view.
 */
private fun doorsEasing(t: Float): Float = t * t * (3f - 2f * t)

/**
 * The two panels of a door effect, [travel] being how far each panel moves from closed to fully
 * open (half the view, sideways or up/down). Opening splits the outgoing scene over the incoming
 * one; closing slides the incoming scene's halves in over the outgoing one. Each panel is the
 * scene's own half, moved without scaling, as on Apple.
 */
private fun doorsLayers(
    travel: Vector2,
    opening: Boolean,
    t: Float,
): List<SKTransitionLayer> {
    val eased = doorsEasing(t)
    val open = if (opening) eased else 1f - eased
    val horizontal = travel.x != 0f
    val panelScene = !opening // closing doors are the incoming scene
    return listOf(
        SKTransitionLayer(useToScene = opening),
        SKTransitionLayer(
            useToScene = panelScene,
            viewportOffset = travel * -open,
            clearFirst = false,
            split = if (horizontal) SKTransitionSplit.Left else SKTransitionSplit.Bottom,
        ),
        SKTransitionLayer(
            useToScene = panelScene,
            viewportOffset = travel * open,
            clearFirst = false,
            split = if (horizontal) SKTransitionSplit.Right else SKTransitionSplit.Top,
        ),
    )
}

/**
 * [SKTransitionKind.Doorway]: the incoming scene comes forward from [DOORWAY_INCOMING_START_SCALE]
 * of its size over black (as a perspective approach would, scaled by `1 / (1 + distance)`), while
 * the outgoing scene opens like [SKTransitionKind.DoorsOpenHorizontal]. Apple's panels also swing
 * away in perspective, shrinking and darkening, which this 2D version doesn't do.
 */
private fun doorwayLayers(
    viewWidth: Int,
    viewHeight: Int,
    t: Float,
): List<SKTransitionLayer> {
    val eased = doorsEasing(t)
    val distance = (1f / DOORWAY_INCOMING_START_SCALE - 1f) * (1f - eased)
    val scale = 1f / (1f + distance)
    val fullSize = Vector2(viewWidth.toFloat(), viewHeight.toFloat())
    val size = fullSize * scale
    val incoming =
        SKTransitionLayer(
            useToScene = true,
            viewportOffset = (fullSize - size) * 0.5f,
            viewportSize = size,
            clearColor = Color.BLACK,
        )
    return listOf(incoming) + doorsLayers(Vector2(viewWidth * 0.5f, 0f), opening = true, t).drop(1)
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
