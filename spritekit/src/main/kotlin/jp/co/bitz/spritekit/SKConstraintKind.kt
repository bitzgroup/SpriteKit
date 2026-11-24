package jp.co.bitz.spritekit

/** What an [SKConstraint] actually restricts — evaluated by [applyConstraint]. Not part of the public API. */
internal sealed class SKConstraintKind {
    data class PositionX(val range: SKRange) : SKConstraintKind()

    data class PositionY(val range: SKRange) : SKConstraintKind()

    data class Position(val x: SKRange, val y: SKRange) : SKConstraintKind()

    data class ZRotation(val range: SKRange) : SKConstraintKind()

    data class Distance(val range: SKRange, val target: SKConstraintTarget) : SKConstraintKind()

    data class Orient(val target: SKConstraintTarget, val offset: SKRange) : SKConstraintKind()
}

/** Where a [SKConstraintKind.Distance]/[SKConstraintKind.Orient] constraint measures from. */
internal sealed class SKConstraintTarget {
    /** [node]'s origin. */
    data class Node(val node: SKNode) : SKConstraintTarget()

    /** [point] in [space]'s coordinates, or in the constrained node's parent's when [space] is `null`. */
    data class Point(val point: Vector2, val space: SKNode?) : SKConstraintTarget()
}
