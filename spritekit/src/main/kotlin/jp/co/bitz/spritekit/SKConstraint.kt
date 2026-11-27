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

    /**
     * The node whose coordinate system this constraint's ranges are measured in, or `null` (the
     * default) for the constrained node's parent's. Position and distance ranges are measured in
     * this node's coordinates; a [zRotation] range is measured relative to this node's rotation.
     * [orient]'s facing and offset don't depend on it. Apple's rotation results with a reference
     * node don't always follow this; see `docs/API_COMPATIBILITY.md`.
     */
    public var referenceNode: SKNode? = null

    public companion object {
        /** Keeps [SKNode.position]'s x component within [range]. */
        public fun positionX(range: SKRange): SKConstraint = SKConstraint(SKConstraintKind.PositionX(range))

        /** Keeps [SKNode.position]'s y component within [range]. */
        public fun positionY(range: SKRange): SKConstraint = SKConstraint(SKConstraintKind.PositionY(range))

        /** Keeps [SKNode.position] within [x]/[y] — equivalent to combining [positionX] and [positionY]. */
        public fun position(
            x: SKRange,
            y: SKRange,
        ): SKConstraint = SKConstraint(SKConstraintKind.Position(x, y))

        /** Keeps [SKNode.zRotation] within [range]. */
        public fun zRotation(range: SKRange): SKConstraint = SKConstraint(SKConstraintKind.ZRotation(range))

        /** Keeps this node's distance from [to]'s origin within [range]. */
        public fun distance(
            range: SKRange,
            to: SKNode,
        ): SKConstraint = SKConstraint(SKConstraintKind.Distance(range, SKConstraintTarget.Node(to)))

        /** Keeps this node's distance from [to], a point in its parent's coordinates, within [range]. */
        public fun distance(
            range: SKRange,
            to: Vector2,
        ): SKConstraint = SKConstraint(SKConstraintKind.Distance(range, SKConstraintTarget.Point(to, null)))

        /**
         * Keeps this node's distance from [to], a point in [node]'s coordinates, within [range] —
         * Apple's `distance(_:to:in:)`, with `in` renamed because it's a Kotlin keyword.
         */
        public fun distance(
            range: SKRange,
            to: Vector2,
            node: SKNode,
        ): SKConstraint = SKConstraint(SKConstraintKind.Distance(range, SKConstraintTarget.Point(to, node)))

        /**
         * Rotates this node to face [to]'s origin, allowing its actual rotation to deviate from that
         * by [offset] (defaults to exactly `0`, i.e. always face [to] precisely).
         */
        public fun orient(
            to: SKNode,
            offset: SKRange = SKRange.constant(0f),
        ): SKConstraint = SKConstraint(SKConstraintKind.Orient(SKConstraintTarget.Node(to), offset))

        /** Rotates this node to face [to], a point in its parent's coordinates, within [offset]. */
        public fun orient(
            to: Vector2,
            offset: SKRange = SKRange.constant(0f),
        ): SKConstraint = SKConstraint(SKConstraintKind.Orient(SKConstraintTarget.Point(to, null), offset))

        /**
         * Rotates this node to face [to], a point in [node]'s coordinates, within [offset] —
         * Apple's `orient(to:in:offset:)`, with `in` renamed because it's a Kotlin keyword.
         */
        public fun orient(
            to: Vector2,
            node: SKNode,
            offset: SKRange = SKRange.constant(0f),
        ): SKConstraint = SKConstraint(SKConstraintKind.Orient(SKConstraintTarget.Point(to, node), offset))
    }
}
