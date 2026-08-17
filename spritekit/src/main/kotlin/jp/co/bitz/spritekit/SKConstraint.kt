package jp.co.bitz.spritekit

/**
 * A restriction on a node's position/rotation, applied once per frame after physics simulation —
 * mirrors Apple's `SKConstraint`. Attach via [SKNode.constraints].
 *
 * All built-in constraint kinds are *contract-conformant, not bit-identical* with Apple's own
 * (undocumented) constraint-solving internals — see `docs/API_COMPATIBILITY.md`.
 */
public class SKConstraint internal constructor(
    internal val kind: SKConstraintKind,
) {
    /** When `false`, this constraint is skipped. Defaults to `true`. */
    public var enabled: Boolean = true

    public companion object {
        /** Keeps [SKNode.position]'s x component within [range] (measured in the node's parent's coordinate space). */
        public fun positionX(range: SKRange): SKConstraint = SKConstraint(SKConstraintKind.PositionX(range))

        /** Keeps [SKNode.position]'s y component within [range] (measured in the node's parent's coordinate space). */
        public fun positionY(range: SKRange): SKConstraint = SKConstraint(SKConstraintKind.PositionY(range))

        /** Keeps [SKNode.position] within [x]/[y] — equivalent to combining [positionX] and [positionY]. */
        public fun position(
            x: SKRange,
            y: SKRange,
        ): SKConstraint = SKConstraint(SKConstraintKind.Position(x, y))

        /** Keeps [SKNode.zRotation] within [range]. */
        public fun zRotation(range: SKRange): SKConstraint = SKConstraint(SKConstraintKind.ZRotation(range))

        /** Keeps this node's distance from [to] within [range] (measured in the node's parent's coordinate space). */
        public fun distance(
            range: SKRange,
            to: SKNode,
        ): SKConstraint = SKConstraint(SKConstraintKind.Distance(range, to))

        /**
         * Rotates this node to face [to], allowing its actual rotation to deviate from that by
         * [offset] (defaults to exactly `0`, i.e. always face [to] precisely).
         */
        public fun orient(
            to: SKNode,
            offset: SKRange = SKRange.constant(0f),
        ): SKConstraint = SKConstraint(SKConstraintKind.Orient(to, offset))
    }
}
