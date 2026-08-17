package jp.co.bitz.spritekit

import kotlin.math.cos
import kotlin.math.sin

/**
 * A node in the scene graph — mirrors Apple's `SKNode`. Every node has a transform (position,
 * rotation, scale) relative to its parent, an optional list of children, and participates in
 * [SKView]'s per-frame update/render loop once it's part of a presented [SKScene]'s tree.
 *
 * All node state is confined to [SKView]'s render thread — see `docs/ARCHITECTURE.md`.
 */
public open class SKNode {
    /** Position relative to the parent's coordinate system. */
    public var position: Vector2 = Vector2.Zero

    /** Front-to-back ordering within the parent; higher values draw on top of lower ones. */
    public var zPosition: Float = 0f

    /** Rotation around the node's origin, in radians. Positive is counter-clockwise. */
    public var zRotation: Float = 0f

    /** Horizontal scale relative to the parent. */
    public var xScale: Float = 1f

    /** Vertical scale relative to the parent. */
    public var yScale: Float = 1f

    /** Opacity, from `0` (fully transparent) to `1` (fully opaque). Inherited by children. */
    public var alpha: Float = 1f

    /** When `true`, this node and its descendants are skipped when rendering. */
    public var isHidden: Boolean = false

    /**
     * When `true`, this node is skipped during action evaluation and physics simulation, but
     * still renders. Unlike [SKScene.isPaused] (inherited from here, now that [SKScene] is a
     * node), this only affects the individual node and its descendants, not the whole scene.
     */
    public var isPaused: Boolean = false

    /** An optional name used to find this node via [SKNode.childNode]/[SKNode.enumerateChildNodes]. */
    public var name: String? = null

    /** Arbitrary user data, `null` until the caller sets it — this library's `NSMutableDictionary` stand-in. */
    public var userData: MutableMap<String, Any?>? = null

    /** This node's parent, or `null` if it's a root (or not yet attached to one). */
    public var parent: SKNode? = null
        private set

    private val mutableChildren = mutableListOf<SKNode>()

    /** This node's direct children, in draw order (see [zPosition] for how they're actually sorted for rendering). */
    public val children: List<SKNode> get() = mutableChildren

    /** The nearest [SKScene] ancestor (including this node itself), or `null` if none is presented. */
    public val scene: SKScene?
        get() = generateSequence(this) { it.parent }.filterIsInstance<SKScene>().firstOrNull()

    /**
     * Adds [node] as a child of this node.
     *
     * @throws IllegalStateException if [node] already has a parent — matching Apple's
     *   `addChild(_:)` contract; call [removeFromParent] on it first.
     */
    public fun addChild(node: SKNode) {
        check(node.parent == null) { "node is already the child of another node" }
        node.parent = this
        mutableChildren.add(node)
    }

    /** Removes this node from its parent. No-op if it has none. */
    public fun removeFromParent() {
        parent?.mutableChildren?.remove(this)
        parent = null
    }

    /** Removes all of this node's children (each child's [parent] becomes `null`). */
    public fun removeAllChildren() {
        mutableChildren.forEach { it.parent = null }
        mutableChildren.clear()
    }

    /**
     * Returns the first direct child whose [name] equals [name], or `null` if none matches.
     *
     * Only searches direct children with an exact name match — Apple's `childNode(withName:)`
     * additionally supports a `/`/`//`/`*` search-pattern mini-language for recursive/wildcard
     * search, which isn't implemented; see `docs/API_COMPATIBILITY.md`.
     */
    public fun childNode(name: String): SKNode? = mutableChildren.firstOrNull { it.name == name }

    /** Calls [action] for every direct child whose [name] equals [name]. See [childNode]'s docs on search scope. */
    public fun enumerateChildNodes(
        name: String,
        action: (SKNode) -> Unit,
    ) {
        mutableChildren.filter { it.name == name }.forEach(action)
    }

    /**
     * Converts [point], expressed in this node's local coordinate space, into [node]'s local
     * coordinate space. Renamed from Apple's `convert(_:to:)` — Swift argument labels aren't
     * significant for Kotlin overload resolution, so `convert(point, to:)`/`convert(point,
     * from:)` would collide as plain Kotlin overloads (same rename pattern GameplayKit-for-Android
     * uses for `GKGraphNode.pathFrom`/`pathTo`; see `docs/API_COMPATIBILITY.md`).
     */
    public fun convertTo(
        point: Vector2,
        node: SKNode,
    ): Vector2 = node.pointFromRootSpace(pointToRootSpace(point))

    /** Converts [point], expressed in [node]'s local coordinate space, into this node's local coordinate space. */
    public fun convertFrom(
        point: Vector2,
        node: SKNode,
    ): Vector2 = pointFromRootSpace(node.pointToRootSpace(point))

    /**
     * The bounding box containing this node's own content (none, for a plain [SKNode] — see
     * [localBounds]) and all of its descendants'. Expressed in [parent]'s coordinate system, or —
     * if this node has no parent — in its own local coordinate system, since there's no parent
     * space to project into.
     */
    public fun calculateAccumulatedFrame(): Rect {
        var bounds = localBounds
        for (child in mutableChildren) {
            bounds = bounds.union(child.calculateAccumulatedFrame())
        }
        parent ?: return bounds
        return boundingRectOf(corners(bounds).map(::localToParent))
    }

    /** Whether this node's and [other]'s [calculateAccumulatedFrame]s overlap. */
    public fun intersects(other: SKNode): Boolean {
        val otherReferenceSpace = other.parent ?: other
        val otherCorners = corners(other.calculateAccumulatedFrame())
        val otherCornersInMySpace = otherCorners.map { convertFrom(it, otherReferenceSpace) }
        return calculateAccumulatedFrame().intersects(boundingRectOf(otherCornersInMySpace))
    }

    /**
     * This node's own local-space content bounds, before considering its transform or children —
     * a zero-size rect at the local origin for a plain [SKNode] (no visual content). Subclasses
     * with visual content (e.g. a future `SKSpriteNode`) override this.
     */
    protected open val localBounds: Rect get() = Rect.Zero

    /**
     * Transforms [point] from this node's local space into its parent's space, applying
     * position/rotation/scale.
     */
    private fun localToParent(point: Vector2): Vector2 {
        val scaledX = point.x * xScale
        val scaledY = point.y * yScale
        val cos = cos(zRotation)
        val sin = sin(zRotation)
        return Vector2(
            x = position.x + (scaledX * cos - scaledY * sin),
            y = position.y + (scaledX * sin + scaledY * cos),
        )
    }

    /**
     * Transforms [point] from this node's parent's space into this node's local space; the
     * inverse of [localToParent].
     */
    private fun parentToLocal(point: Vector2): Vector2 {
        val translated = Vector2(point.x - position.x, point.y - position.y)
        val cos = cos(zRotation)
        val sin = sin(zRotation)
        return Vector2(
            x = (translated.x * cos + translated.y * sin) / xScale,
            y = (-translated.x * sin + translated.y * cos) / yScale,
        )
    }

    /** Walks up from this node to its topmost ancestor, transforming [point] into that ancestor's local space. */
    private fun pointToRootSpace(point: Vector2): Vector2 {
        var result = point
        var node: SKNode = this
        while (true) {
            val nodeParent = node.parent ?: return result
            result = node.localToParent(result)
            node = nodeParent
        }
    }

    /**
     * The inverse of [pointToRootSpace]: [point] is in this node's topmost ancestor's space;
     * returns it in this node's space.
     */
    private fun pointFromRootSpace(point: Vector2): Vector2 {
        val ancestors = generateSequence(this) { it.parent }.takeWhile { it.parent != null }.toList()
        return ancestors.foldRight(point) { node, acc -> node.parentToLocal(acc) }
    }
}

private fun corners(rect: Rect): List<Vector2> =
    listOf(
        Vector2(rect.left, rect.top),
        Vector2(rect.right, rect.top),
        Vector2(rect.right, rect.bottom),
        Vector2(rect.left, rect.bottom),
    )

private fun boundingRectOf(points: List<Vector2>): Rect {
    val xs = points.map { it.x }
    val ys = points.map { it.y }
    return Rect(xs.min(), ys.min(), xs.max(), ys.max())
}
