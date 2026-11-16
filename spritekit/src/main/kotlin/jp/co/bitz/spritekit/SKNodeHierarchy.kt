package jp.co.bitz.spritekit

/**
 * Removes each of [nodes] that is a child of this node — Apple's `removeChildren(in:)`. Nodes that
 * aren't children of this node are ignored.
 */
public fun SKNode.removeChildren(nodes: List<SKNode>) {
    nodes.filter { it.parent === this }.forEach { it.removeFromParent() }
}

/**
 * Whether [parent] is an ancestor of this node — its parent, its parent's parent, and so on —
 * Apple's `inParentHierarchy(_:)`. `false` for the node itself.
 */
public fun SKNode.inParentHierarchy(parent: SKNode): Boolean =
    generateSequence(this.parent) { it.parent }.any { it === parent }

/**
 * Moves this node to [parent], appending it to [parent]'s children, while keeping it at the same
 * place — Apple's `move(toParent:)`. Only [SKNode.position] is converted; [SKNode.zRotation] and
 * the scale keep their local values, as on Apple (verified on iOS/tvOS simulators). The position is
 * carried through every ancestor's transform on both sides, each tree's topmost node included
 * (for a scene, whose own transform is normally the identity, that's scene space) — so moving
 * between two separate trees treats the space above their roots as shared, and a node with no
 * parent has its position taken as already being in that space.
 */
public fun SKNode.moveToParent(parent: SKNode) {
    val sharedPosition =
        generateSequence(this.parent) {
            it.parent
        }.fold(position) { point, node -> node.localToParent(point) }
    removeFromParent()
    position =
        generateSequence(parent) {
            it.parent
        }.toList().foldRight(sharedPosition) { node, point -> node.parentToLocal(point) }
    parent.addChild(this)
}
