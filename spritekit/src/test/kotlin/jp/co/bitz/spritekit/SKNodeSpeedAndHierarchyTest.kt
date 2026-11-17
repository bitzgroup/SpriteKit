package jp.co.bitz.spritekit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

// Expectations recorded from Apple's SpriteKit on iOS/tvOS simulators — see the tic-tac-toe
// sample's SpriteKitParity probes (SKActionParityTests, SKNodeHierarchyParityTests).
class SKNodeSpeedAndHierarchyTest {
    private fun named(name: String) = SKNode().apply { this.name = name }

    @Test
    fun `a node's speed scales its own and its descendants' actions`() {
        val parent = SKNode().apply { speed = 2f }
        val child = SKNode()
        parent.addChild(child)
        child.run(SKAction.moveBy(Vector2(10f, 0f), 1.seconds))

        parent.stepActions(0.25.seconds)

        assertEquals(5f, child.position.x, 1e-4f)
    }

    @Test
    fun `a node with zero speed runs nothing but keeps its actions`() {
        val node = SKNode().apply { speed = 0f }
        node.run(SKAction.moveBy(Vector2(5f, 0f), 0.seconds))

        node.stepActions(1.seconds)

        assertEquals(Vector2.Zero, node.position)
        assertTrue(node.hasActions())
    }

    @Test
    fun `insertChild puts the node at that index, including the end, and rejects an index past it`() {
        val parent = SKNode()
        parent.addChild(named("a"))
        parent.addChild(named("b"))
        parent.insertChild(named("c"), 1)
        parent.insertChild(named("d"), 3)
        assertEquals(listOf("a", "c", "b", "d"), parent.children.map { it.name })
        assertFailsWith<IndexOutOfBoundsException> { parent.insertChild(named("e"), 5) }
    }

    @Test
    fun `removeChildren removes only actual children`() {
        val parent = SKNode()
        val a = named("a")
        parent.addChild(a)
        parent.addChild(named("b"))
        parent.removeChildren(listOf(a, SKNode()))
        assertEquals(listOf("b"), parent.children.map { it.name })
        assertNull(a.parent)
    }

    @Test
    fun `inParentHierarchy is true for any ancestor but not the node itself`() {
        val root = SKNode()
        val child = SKNode()
        val grandchild = SKNode()
        root.addChild(child)
        child.addChild(grandchild)
        assertTrue(child.inParentHierarchy(root))
        assertTrue(grandchild.inParentHierarchy(root))
        assertFalse(root.inParentHierarchy(child))
        assertFalse(child.inParentHierarchy(child))
    }

    @Test
    fun `moveToParent keeps the root-space position but not rotation or scale`() {
        val root = SKNode()
        val from =
            SKNode().apply {
                position = Vector2(100f, 50f)
                zRotation = (Math.PI / 2).toFloat()
                xScale = 2f
                yScale = 3f
            }
        val to =
            SKNode().apply {
                position = Vector2(-20f, 10f)
                xScale = 0.5f
            }
        root.addChild(from)
        root.addChild(to)
        to.addChild(SKNode())
        val node =
            SKNode().apply {
                position = Vector2(10f, 0f)
                zRotation = 0.3f
                xScale = 1.5f
            }
        from.addChild(node)

        node.moveToParent(to)

        assertSame(to, node.parent)
        assertSame(node, to.children.last())
        assertEquals(240f, node.position.x, 1e-3f)
        assertEquals(60f, node.position.y, 1e-3f)
        assertEquals(0.3f, node.zRotation)
        assertEquals(1.5f, node.xScale)
    }

    @Test
    fun `moveToParent treats each tree's root space as the shared space`() {
        val target =
            SKNode().apply {
                position = Vector2(-20f, 10f)
                xScale = 0.5f
            }
        val orphan = SKNode().apply { position = Vector2(5f, 6f) }
        orphan.moveToParent(target)
        assertEquals(Vector2(50f, -4f), orphan.position)

        val otherRoot = SKNode().apply { position = Vector2(10f, 10f) }
        val inOtherTree = SKNode().apply { position = Vector2(3f, 4f) }
        otherRoot.addChild(inOtherTree)
        val unrooted = SKNode().apply { position = Vector2(1f, 1f) }
        inOtherTree.moveToParent(unrooted)
        assertEquals(Vector2(12f, 13f), inOtherTree.position)
    }
}
