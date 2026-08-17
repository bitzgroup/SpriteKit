package jp.co.bitz.spritekit

import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Applies [constraint] to [node] (a no-op if [SKConstraint.enabled] is `false`).
 *
 * Pure Kotlin, reusing [SKNode.convertTo] (from Phase 2) for [SKConstraintKind.Distance]/
 * [SKConstraintKind.Orient]'s cross-node position lookups — no OpenGL/Android dependency, so
 * this is unit-testable independent of a live GL context.
 */
internal fun applyConstraint(
    node: SKNode,
    constraint: SKConstraint,
) {
    if (!constraint.enabled) return
    when (val kind = constraint.kind) {
        is SKConstraintKind.PositionX -> node.position = Vector2(kind.range.clamp(node.position.x), node.position.y)
        is SKConstraintKind.PositionY -> node.position = Vector2(node.position.x, kind.range.clamp(node.position.y))
        is SKConstraintKind.Position ->
            node.position = Vector2(kind.x.clamp(node.position.x), kind.y.clamp(node.position.y))
        is SKConstraintKind.ZRotation -> node.zRotation = kind.range.clamp(node.zRotation)
        is SKConstraintKind.Distance -> applyDistanceConstraint(node, kind)
        is SKConstraintKind.Orient -> applyOrientConstraint(node, kind)
    }
}

private fun applyDistanceConstraint(
    node: SKNode,
    kind: SKConstraintKind.Distance,
) {
    val referenceSpace = node.parent ?: node
    val targetPosition = kind.target.convertTo(Vector2.Zero, referenceSpace)
    val offset = node.position - targetPosition
    val currentDistance = hypot(offset.x, offset.y)
    if (currentDistance == 0f) return // nothing meaningful to constrain -- avoids a divide-by-zero below
    val clampedDistance = kind.range.clamp(currentDistance)
    if (clampedDistance == currentDistance) return
    val direction = offset * (1f / currentDistance)
    node.position = targetPosition + direction * clampedDistance
}

private fun applyOrientConstraint(
    node: SKNode,
    kind: SKConstraintKind.Orient,
) {
    val referenceSpace = node.parent ?: node
    val targetPosition = kind.target.convertTo(Vector2.Zero, referenceSpace)
    val direction = targetPosition - node.position
    if (direction.x == 0f && direction.y == 0f) return
    val angleToTarget = atan2(direction.y, direction.x)
    val currentOffset = node.zRotation - angleToTarget
    node.zRotation = angleToTarget + kind.offset.clamp(currentOffset)
}
