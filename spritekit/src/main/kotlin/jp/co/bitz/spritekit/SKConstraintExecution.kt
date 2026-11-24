package jp.co.bitz.spritekit

import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Applies [constraint] to [node] (a no-op if [SKConstraint.enabled] is `false`).
 *
 * Pure Kotlin, reusing [SKNode.convertTo]/[SKNode.convertFrom] to move between the node's parent's
 * coordinates and [SKConstraint.referenceNode]'s — no OpenGL/Android dependency, so this is
 * unit-testable independent of a live GL context.
 */
internal fun applyConstraint(
    node: SKNode,
    constraint: SKConstraint,
) {
    if (!constraint.enabled) return
    val space = ConstraintSpace(node, constraint.referenceNode)
    when (val kind = constraint.kind) {
        is SKConstraintKind.PositionX ->
            space.updatePosition { Vector2(kind.range.clamp(it.x), it.y) }
        is SKConstraintKind.PositionY ->
            space.updatePosition { Vector2(it.x, kind.range.clamp(it.y)) }
        is SKConstraintKind.Position ->
            space.updatePosition { Vector2(kind.x.clamp(it.x), kind.y.clamp(it.y)) }
        is SKConstraintKind.ZRotation -> {
            val offset = space.rotationOffset()
            node.zRotation = kind.range.clamp(node.zRotation + offset) - offset
        }
        is SKConstraintKind.Distance -> applyDistanceConstraint(space, kind)
        is SKConstraintKind.Orient -> applyOrientConstraint(node, kind)
    }
}

/**
 * The coordinate system a constraint is measured in: [reference]'s when set, otherwise the
 * node's parent's. Without a parent there's no space to convert from, so positions are used as-is.
 */
private class ConstraintSpace(
    val node: SKNode,
    reference: SKNode?,
) {
    private val parent = node.parent

    /** The reference node, when it differs from the parent (otherwise no conversion is needed). */
    private val reference = reference?.takeIf { parent != null && it !== parent }

    /** The coordinate system [SKConstraintTarget.Node] origins are converted into. */
    val measuringNode: SKNode = this.reference ?: parent ?: node

    fun fromParent(point: Vector2): Vector2 = reference?.let { parent?.convertTo(point, it) } ?: point

    fun toParent(point: Vector2): Vector2 = reference?.let { parent?.convertFrom(point, it) } ?: point

    inline fun updatePosition(transform: (Vector2) -> Vector2) {
        val current = fromParent(node.position)
        val updated = transform(current)
        if (updated != current) node.position = toParent(updated)
    }

    /** The angle to add to a rotation in the parent's coordinates to express it in [reference]'s. */
    fun rotationOffset(): Float {
        if (reference == null) return 0f
        val axis = fromParent(Vector2(1f, 0f)) - fromParent(Vector2.Zero)
        return atan2(axis.y, axis.x)
    }

    fun locate(target: SKConstraintTarget): Vector2 =
        when (target) {
            is SKConstraintTarget.Node -> target.node.convertTo(Vector2.Zero, measuringNode)
            is SKConstraintTarget.Point ->
                target.space?.convertTo(target.point, measuringNode) ?: fromParent(target.point)
        }
}

private fun applyDistanceConstraint(
    space: ConstraintSpace,
    kind: SKConstraintKind.Distance,
) {
    val targetPosition = space.locate(kind.target)
    space.updatePosition { position ->
        val offset = position - targetPosition
        val currentDistance = hypot(offset.x, offset.y)
        // A node exactly on the target has no direction to move along, so it's left where it is.
        if (currentDistance == 0f) return@updatePosition position
        val clampedDistance = kind.range.clamp(currentDistance)
        if (clampedDistance == currentDistance) return@updatePosition position
        targetPosition + offset * (clampedDistance / currentDistance)
    }
}

private fun applyOrientConstraint(
    node: SKNode,
    kind: SKConstraintKind.Orient,
) {
    // Facing a point is the same in any rotated coordinate system, so this measures in the
    // parent's regardless of the constraint's referenceNode.
    val targetPosition = ConstraintSpace(node, null).locate(kind.target)
    val direction = targetPosition - node.position
    if (direction.x == 0f && direction.y == 0f) return
    val angleToTarget = atan2(direction.y, direction.x)
    val currentOffset = node.zRotation - angleToTarget
    node.zRotation = angleToTarget + kind.offset.clamp(currentOffset)
}
