package jp.co.bitz.spritekit

import kotlin.math.sqrt

/**
 * [shape], transformed from [node]'s local space into [scene]'s space. As on Apple's (verified on
 * iOS/tvOS simulators), the node's own scale counts only relative to its scale when the body was
 * attached ([SKPhysicsBody.attachedScale]); its ancestors' scale counts in full. A circle stays a
 * circle: under a non-uniform scale its radius is `r·√((sx² + sy²)/2)`, where `sx` and `sy` are
 * how far the transform stretches its local x and y axes.
 */
internal fun worldShape(
    node: SKNode,
    scene: SKScene,
    shape: SKPhysicsShape,
): SKWorldShape {
    val attached = node.physicsBody?.attachedScale ?: Vector2(1f, 1f)
    val toScene = { point: Vector2 -> node.convertTo(unscaled(point, attached), scene) }
    return worldShape(shape, toScene)
}

private fun worldShape(
    shape: SKPhysicsShape,
    toScene: (Vector2) -> Vector2,
): SKWorldShape =
    when (shape) {
        is SKPhysicsShape.Circle -> {
            val center = toScene(shape.center)
            val xAxis = toScene(shape.center + Vector2(shape.radius, 0f)) - center
            val yAxis = toScene(shape.center + Vector2(0f, shape.radius)) - center
            SKWorldShape.Circle(center, sqrt((xAxis.lengthSquared() + yAxis.lengthSquared()) / 2f))
        }
        is SKPhysicsShape.Polygon -> SKWorldShape.Polygon(shape.vertices.map(toScene))
        is SKPhysicsShape.EdgeChain -> SKWorldShape.EdgeChain(shape.vertices.map(toScene), shape.closed)
        is SKPhysicsShape.Compound -> SKWorldShape.Compound(shape.parts.map { worldShape(it, toScene) })
    }

/** [point] divided by [scale], so that a node's scale multiplies it by only its change since then. */
internal fun unscaled(
    point: Vector2,
    scale: Vector2,
): Vector2 = Vector2(point.x / scale.x.orOne(), point.y / scale.y.orOne())

private fun Float.orOne(): Float = if (this == 0f) 1f else this
