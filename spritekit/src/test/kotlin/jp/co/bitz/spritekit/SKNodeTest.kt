package jp.co.bitz.spritekit

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A node with fixed local content bounds, for exercising [SKNode.calculateAccumulatedFrame]/[SKNode.intersects]. */
private class FixedBoundsNode(private val bounds: Rect) : SKNode() {
    override val localBounds: Rect get() = bounds
}

private fun assertVector2Equals(
    expected: Vector2,
    actual: Vector2,
    tolerance: Float = 1e-4f,
) {
    assertTrue(kotlin.math.abs(expected.x - actual.x) < tolerance, "expected x=${expected.x}, was ${actual.x}")
    assertTrue(kotlin.math.abs(expected.y - actual.y) < tolerance, "expected y=${expected.y}, was ${actual.y}")
}

class SKNodeTest {
    @Test
    fun `addChild sets parent and appends to children`() {
        val parent = SKNode()
        val child = SKNode()

        parent.addChild(child)

        assertEquals(parent, child.parent)
        assertEquals(listOf(child), parent.children)
    }

    @Test
    fun `addChild throws if the node already has a parent`() {
        val firstParent = SKNode()
        val secondParent = SKNode()
        val child = SKNode()
        firstParent.addChild(child)

        assertFailsWith<IllegalStateException> { secondParent.addChild(child) }
    }

    @Test
    fun `removeFromParent clears parent and removes from the old parent's children`() {
        val parent = SKNode()
        val child = SKNode()
        parent.addChild(child)

        child.removeFromParent()

        assertNull(child.parent)
        assertTrue(parent.children.isEmpty())
    }

    @Test
    fun `removeFromParent on a root node is a no-op`() {
        val node = SKNode()

        node.removeFromParent() // must not throw

        assertNull(node.parent)
    }

    @Test
    fun `removeAllChildren detaches every child`() {
        val parent = SKNode()
        val childA = SKNode()
        val childB = SKNode()
        parent.addChild(childA)
        parent.addChild(childB)

        parent.removeAllChildren()

        assertTrue(parent.children.isEmpty())
        assertNull(childA.parent)
        assertNull(childB.parent)
    }

    @Test
    fun `childNode finds the first direct child with a matching name`() {
        val parent = SKNode()
        val target = SKNode().apply { name = "target" }
        parent.addChild(SKNode().apply { name = "other" })
        parent.addChild(target)

        assertEquals(target, parent.childNode("target"))
        assertNull(parent.childNode("missing"))
    }

    @Test
    fun `childNode does not search grandchildren`() {
        val parent = SKNode()
        val child = SKNode()
        val grandchild = SKNode().apply { name = "target" }
        parent.addChild(child)
        child.addChild(grandchild)

        assertNull(parent.childNode("target"))
    }

    @Test
    fun `enumerateChildNodes calls action for every matching direct child`() {
        val parent = SKNode()
        repeat(3) { parent.addChild(SKNode().apply { name = "enemy" }) }
        parent.addChild(SKNode().apply { name = "player" })
        val matched = mutableListOf<SKNode>()

        parent.enumerateChildNodes("enemy") { matched += it }

        assertEquals(3, matched.size)
    }

    @Test
    fun `scene walks up to the nearest SKScene ancestor`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val layer = SKNode()
        val leaf = SKNode()
        scene.addChild(layer)
        layer.addChild(leaf)

        assertEquals(scene, leaf.scene)
        assertEquals(scene, scene.scene) // a scene is its own nearest SKScene ancestor
        assertNull(SKNode().scene) // unattached node, no scene
    }

    @Test
    fun `convert translates a point through a chain of positioned parents`() {
        val root = SKNode().apply { position = Vector2(10f, 20f) }
        val child = SKNode().apply { position = Vector2(1f, 2f) }
        root.addChild(child)

        val worldPoint = child.convertTo(Vector2(5f, 5f), root)

        assertVector2Equals(Vector2(6f, 7f), worldPoint) // child's (5,5) is (1+5, 2+5) in root's space
    }

    @Test
    fun `convert to and from are inverses`() {
        val root = SKNode().apply { position = Vector2(3f, -4f) }
        val child =
            SKNode().apply {
                position = Vector2(2f, 2f)
                zRotation = (PI / 4).toFloat()
                xScale = 2f
                yScale = 0.5f
            }
        root.addChild(child)
        val original = Vector2(7f, -3f)

        val roundTripped = child.convertFrom(child.convertTo(original, root), root)

        assertVector2Equals(original, roundTripped)
    }

    @Test
    fun `convert accounts for rotation`() {
        val root = SKNode()
        val child = SKNode().apply { zRotation = (PI / 2).toFloat() } // 90 degrees counter-clockwise
        root.addChild(child)

        val worldPoint = child.convertTo(Vector2(1f, 0f), root)

        assertVector2Equals(Vector2(0f, 1f), worldPoint)
    }

    @Test
    fun `convert accounts for scale`() {
        val root = SKNode()
        val child =
            SKNode().apply {
                xScale = 2f
                yScale = 3f
            }
        root.addChild(child)

        val worldPoint = child.convertTo(Vector2(1f, 1f), root)

        assertVector2Equals(Vector2(2f, 3f), worldPoint)
    }

    @Test
    fun `calculateAccumulatedFrame for a leaf node with no content is a degenerate rect at its position`() {
        val node = SKNode().apply { position = Vector2(5f, 6f) }
        val parent = SKNode()
        parent.addChild(node)

        val frame = node.calculateAccumulatedFrame()

        assertEquals(Rect(5f, 6f, 5f, 6f), frame)
    }

    @Test
    fun `calculateAccumulatedFrame unions children`() {
        val parent = SKNode()
        parent.addChild(SKNode().apply { position = Vector2(-10f, 0f) })
        parent.addChild(SKNode().apply { position = Vector2(10f, 5f) })

        val frame = parent.calculateAccumulatedFrame()

        assertEquals(Rect(-10f, 0f, 10f, 5f), frame)
    }

    @Test
    fun `calculateAccumulatedFrame on a rootless node skips its own transform`() {
        val node = FixedBoundsNode(Rect(-1f, -1f, 1f, 1f)).apply { position = Vector2(100f, 100f) }

        val frame = node.calculateAccumulatedFrame()

        assertEquals(Rect(-1f, -1f, 1f, 1f), frame) // position not applied: no parent space to project into
    }

    @Test
    fun `intersects is true for overlapping content and false for disjoint content`() {
        val parent = SKNode()
        val a = FixedBoundsNode(Rect(-1f, -1f, 1f, 1f)).apply { position = Vector2(0f, 0f) }
        val b = FixedBoundsNode(Rect(-1f, -1f, 1f, 1f)).apply { position = Vector2(1f, 0f) }
        val c = FixedBoundsNode(Rect(-1f, -1f, 1f, 1f)).apply { position = Vector2(10f, 10f) }
        parent.addChild(a)
        parent.addChild(b)
        parent.addChild(c)

        assertTrue(a.intersects(b))
        assertFalse(a.intersects(c))
    }
}
