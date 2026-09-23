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
 * Routes [events] — every pointer snapshot taken from one Android `MotionEvent`, already in view
 * space — into [scene]'s node tree the way Apple's SpriteKit delivers a `UIEvent`: a touch is
 * hit-tested on [SKTouchPhase.Began], then delivered to that same node for every later phase;
 * touches sharing a phase and a target node arrive together, in one [SKNode.touchesBegan]/
 * [SKNode.touchesMoved]/[SKNode.touchesEnded]/[SKNode.touchesCancelled] call per node. A pointer
 * reported as [SKTouchPhase.Moved] without actually having moved is left out, like Apple's
 * stationary touches. A no-op if [viewWidth]/[viewHeight] aren't known yet (before the first
 * `onSurfaceChanged`).
 */
internal fun dispatchTouches(
    scene: SKScene,
    events: List<SKTouchEvent>,
    viewWidth: Int,
    viewHeight: Int,
) {
    if (viewWidth <= 0 || viewHeight <= 0) return
    val referenceNode = scene.camera ?: scene
    val projection = computeSceneProjection(scene.size, scene.anchorPoint, scene.scaleMode, viewWidth, viewHeight)
    val deliveries = linkedMapOf<Pair<SKTouchPhase, SKNode>, MutableSet<SKTouch>>()
    val released = mutableListOf<Int>()

    for (event in events) {
        val point = viewToScenePoint(event.x, event.y, projection, viewWidth, viewHeight)
        val delivery = trackTouch(scene, event, referenceNode, point) ?: continue
        if (event.phase == SKTouchPhase.Ended || event.phase == SKTouchPhase.Cancelled) released += event.pointerId
        deliveries.getOrPut(event.phase to delivery.first) { linkedSetOf() } += delivery.second
    }

    val skEvent = SKEvent(scene.activeTouches.values.toSet())
    for (pointerId in released) {
        scene.activeTouchTargets.remove(pointerId)
        scene.activeTouches.remove(pointerId)
    }
    for ((key, touches) in deliveries) deliver(key.second, key.first, touches, skEvent)
}

private fun deliver(
    target: SKNode,
    phase: SKTouchPhase,
    touches: Set<SKTouch>,
    event: SKEvent,
) {
    when (phase) {
        SKTouchPhase.Began -> target.touchesBegan(touches, event)
        SKTouchPhase.Moved -> target.touchesMoved(touches, event)
        SKTouchPhase.Ended -> target.touchesEnded(touches, event)
        SKTouchPhase.Cancelled -> target.touchesCancelled(touches, event)
    }
}

/**
 * Updates [scene]'s tracking for [event] and returns the node to deliver it to plus its
 * persistent [SKTouch] — or `null` if there's nothing to deliver (no node hit, no tracked touch
 * for this pointer, or a "move" that didn't actually move).
 */
private fun trackTouch(
    scene: SKScene,
    event: SKTouchEvent,
    referenceNode: SKNode,
    point: Vector2,
): Pair<SKNode, SKTouch>? =
    if (event.phase == SKTouchPhase.Began) {
        beginTouch(scene, event.pointerId, referenceNode, point)
    } else {
        continueTouch(scene, event, point)
    }

private fun beginTouch(
    scene: SKScene,
    pointerId: Int,
    referenceNode: SKNode,
    point: Vector2,
): Pair<SKNode, SKTouch>? =
    hitTestInteractiveNode(scene, referenceNode, point)?.let { target ->
        val touch = SKTouch(pointerId, referenceNode, point)
        scene.activeTouchTargets[pointerId] = target
        scene.activeTouches[pointerId] = touch
        target to touch
    }

private fun continueTouch(
    scene: SKScene,
    event: SKTouchEvent,
    point: Vector2,
): Pair<SKNode, SKTouch>? {
    val target = scene.activeTouchTargets[event.pointerId]
    val touch = scene.activeTouches[event.pointerId]
    val stationary = event.phase == SKTouchPhase.Moved && point == touch?.referencePoint
    if (target == null || touch == null || stationary) return null
    touch.moveTo(point)
    touch.phase = event.phase
    return target to touch
}

/**
 * The frontmost (highest *effective* z-position -- this node's own [SKNode.zPosition] plus every
 * ancestor's, accumulated down the tree the same way [SKRenderCommandList.kt] does for draw
 * order, so a container's zPosition also wins it hit-test priority over its siblings even when
 * its children are left at the default `0`; ties broken by tree-traversal order)
 * [SKNode.isUserInteractionEnabled] node in [scene]'s tree whose [SKNode.containsLocalPoint]
 * contains [referencePoint] (expressed in [referenceNode]'s space). Skips hidden subtrees, like
 * rendering does. `null` if nothing matches.
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
        inheritedZPosition: Float,
    ) {
        order++
        val hidden = inheritedHidden || node.isHidden
        val zPosition = inheritedZPosition + node.zPosition
        if (!hidden && node.isUserInteractionEnabled) {
            val localPoint = node.convertFrom(referencePoint, referenceNode)
            if (node.containsLocalPoint(localPoint)) candidates += node to (zPosition to order)
        }
        for (child in node.children) visit(child, hidden, zPosition)
    }

    visit(scene, inheritedHidden = false, inheritedZPosition = 0f)
    return candidates.maxWithOrNull(compareBy({ it.second.first }, { it.second.second }))?.first
}
