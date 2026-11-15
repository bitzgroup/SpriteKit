package jp.co.bitz.spritekit

// Apple's `nodes(at:)`/`atPoint(_:)`. Top-level extensions (rather than [SKNode] members) only to
// keep that class's member count under this codebase's `TooManyFunctions` budget, like
// `convertAllTo`; call them as `node.nodes(point)`/`node.atPoint(point)` all the same.

/**
 * Every descendant (not this node) whose [SKNode.calculateAccumulatedFrame] contains [point] —
 * given in this node's coordinate system — frontmost first: Apple's `nodes(at:)`. "Frontmost" is
 * draw order: higher effective [SKNode.zPosition] (own plus every ancestor's) first, ties broken so
 * that a child is in front of its parent and a later sibling in front of an earlier one.
 *
 * Matching Apple's SpriteKit (verified on macOS): [SKNode.isHidden] nodes and nodes with an
 * [SKNode.alpha] of `0` are skipped along with their whole subtree,
 * [SKNode.isUserInteractionEnabled] plays no part, the frame test includes all four edges (so a
 * plain [SKNode] is found exactly at its own position), and a node is found wherever its
 * accumulated frame reaches, even in the gaps between its children.
 */
public fun SKNode.nodes(point: Vector2): List<SKNode> {
    val root = this
    val hits = mutableListOf<Triple<SKNode, Float, Int>>()
    var order = 0

    fun visit(
        node: SKNode,
        inheritedZPosition: Float,
    ) {
        val pointInParentSpace = node.convertFrom(point, root)
        for (child in node.children) {
            order++
            if (child.isHidden || child.alpha <= 0f) continue
            val zPosition = inheritedZPosition + child.zPosition
            val bounds = child.calculateAccumulatedFrame()
            val inside =
                pointInParentSpace.x in bounds.left..bounds.right &&
                    pointInParentSpace.y in bounds.top..bounds.bottom
            if (inside) hits += Triple(child, zPosition, order)
            visit(child, zPosition)
        }
    }

    visit(root, 0f)
    val frontmostFirst = compareByDescending<Triple<SKNode, Float, Int>> { it.second }.thenByDescending { it.third }
    return hits.sortedWith(frontmostFirst).map { it.first }
}

/**
 * The frontmost descendant at [point] (in this node's coordinate system) as [nodes] finds them, or
 * this node itself if there's none — Apple's `atPoint(_:)`.
 */
public fun SKNode.atPoint(point: Vector2): SKNode = nodes(point).firstOrNull() ?: this
