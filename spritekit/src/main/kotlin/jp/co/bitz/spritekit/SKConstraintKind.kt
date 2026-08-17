package jp.co.bitz.spritekit

/** What an [SKConstraint] actually restricts — evaluated by [applyConstraint]. Not part of the public API. */
internal sealed class SKConstraintKind {
    data class PositionX(val range: SKRange) : SKConstraintKind()

    data class PositionY(val range: SKRange) : SKConstraintKind()

    data class Position(val x: SKRange, val y: SKRange) : SKConstraintKind()

    data class ZRotation(val range: SKRange) : SKConstraintKind()

    data class Distance(val range: SKRange, val target: SKNode) : SKConstraintKind()

    data class Orient(val target: SKNode, val offset: SKRange) : SKConstraintKind()
}
