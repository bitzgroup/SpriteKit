package jp.co.bitz.spritekit

/**
 * Converts a raw view-space touch point ([viewX], [viewY] in pixels, origin top-left, y-down)
 * into [projection]'s space (the same "camera-relative or scene-relative" space
 * [SKRenderCommandList.kt] projects render commands into) -- the inverse of the mapping
 * [computeSceneProjection] describes, reusing its exact math so touch input and rendering always
 * agree on where things are.
 */
internal fun viewToScenePoint(
    viewX: Float,
    viewY: Float,
    projection: SKSceneProjection,
    viewWidth: Int,
    viewHeight: Int,
): Vector2 {
    val fractionX = viewX / viewWidth
    val fractionY = viewY / viewHeight
    return Vector2(
        x = projection.left + fractionX * (projection.right - projection.left),
        y = projection.top - fractionY * (projection.top - projection.bottom),
    )
}

/**
 * Routes [event] (already in view space) into [scene]'s node tree: hit-tests on
 * [SKTouchPhase.Began], then delivers to whichever node is tracking that pointer for every
 * subsequent phase, via [SKNode.touchesBegan]/[SKNode.touchesMoved]/[SKNode.touchesEnded]/
 * [SKNode.touchesCancelled]. A no-op if [viewWidth]/[viewHeight] aren't known yet (before the
 * first `onSurfaceChanged`).
 */
internal fun dispatchTouch(
    scene: SKScene,
    event: SKTouchEvent,
    viewWidth: Int,
    viewHeight: Int,
) {
    if (viewWidth <= 0 || viewHeight <= 0) return
    val referenceNode = scene.camera ?: scene
    val projection = computeSceneProjection(scene.size, scene.anchorPoint, scene.scaleMode, viewWidth, viewHeight)
    val referencePoint = viewToScenePoint(event.x, event.y, projection, viewWidth, viewHeight)

    when (event.phase) {
        SKTouchPhase.Began -> dispatchBegan(scene, event, referenceNode, referencePoint)
        SKTouchPhase.Moved -> dispatchToTrackedTarget(scene, event, referenceNode, referencePoint, SKNode::touchesMoved)
        SKTouchPhase.Ended ->
            dispatchToReleasedTarget(
                scene,
                event,
                referenceNode,
                referencePoint,
                SKNode::touchesEnded,
            )
        SKTouchPhase.Cancelled ->
            dispatchToReleasedTarget(
                scene,
                event,
                referenceNode,
                referencePoint,
                SKNode::touchesCancelled,
            )
    }
}

private fun dispatchBegan(
    scene: SKScene,
    event: SKTouchEvent,
    referenceNode: SKNode,
    referencePoint: Vector2,
) {
    val target = hitTestInteractiveNode(scene, referenceNode, referencePoint) ?: return
    scene.activeTouchTargets[event.pointerId] = target
    target.touchesBegan(SKTouch(event.pointerId, target.convertFrom(referencePoint, referenceNode)))
}

/**
 * Delivers to the node already tracking [event]'s pointer, if any, without changing that tracking
 * -- for [SKTouchPhase.Moved].
 */
private fun dispatchToTrackedTarget(
    scene: SKScene,
    event: SKTouchEvent,
    referenceNode: SKNode,
    referencePoint: Vector2,
    deliver: SKNode.(SKTouch) -> Unit,
) {
    val target = scene.activeTouchTargets[event.pointerId] ?: return
    target.deliver(SKTouch(event.pointerId, target.convertFrom(referencePoint, referenceNode)))
}

/**
 * Delivers to the node tracking [event]'s pointer and stops tracking it -- for
 * [SKTouchPhase.Ended]/[SKTouchPhase.Cancelled].
 */
private fun dispatchToReleasedTarget(
    scene: SKScene,
    event: SKTouchEvent,
    referenceNode: SKNode,
    referencePoint: Vector2,
    deliver: SKNode.(SKTouch) -> Unit,
) {
    val target = scene.activeTouchTargets.remove(event.pointerId) ?: return
    target.deliver(SKTouch(event.pointerId, target.convertFrom(referencePoint, referenceNode)))
}

/**
 * The frontmost (highest [SKNode.zPosition], ties broken by tree-traversal order -- the same rule
 * [SKRenderCommandList.kt] sorts draw order by) [SKNode.isUserInteractionEnabled] node in
 * [scene]'s tree whose [SKNode.containsLocalPoint] contains [referencePoint] (expressed in
 * [referenceNode]'s space). Skips hidden subtrees, like rendering does. `null` if nothing matches.
 */
private fun hitTestInteractiveNode(
    scene: SKScene,
    referenceNode: SKNode,
    referencePoint: Vector2,
): SKNode? {
    val candidates = mutableListOf<Pair<SKNode, Pair<Float, Int>>>()
    var order = 0

    fun visit(
        node: SKNode,
        inheritedHidden: Boolean,
    ) {
        order++
        val hidden = inheritedHidden || node.isHidden
        if (!hidden && node.isUserInteractionEnabled) {
            val localPoint = node.convertFrom(referencePoint, referenceNode)
            if (node.containsLocalPoint(localPoint)) candidates += node to (node.zPosition to order)
        }
        for (child in node.children) visit(child, hidden)
    }

    visit(scene, false)
    return candidates.maxWithOrNull(compareBy({ it.second.first }, { it.second.second }))?.first
}
