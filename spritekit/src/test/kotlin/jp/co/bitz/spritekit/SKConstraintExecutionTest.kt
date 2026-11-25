package jp.co.bitz.spritekit

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SKConstraintExecutionTest {
    @Test
    fun `positionX clamps only the x component`() {
        val node = SKNode().apply { position = Vector2(100f, 50f) }

        applyConstraint(node, SKConstraint.positionX(SKRange.of(0f, 10f)))

        assertEquals(Vector2(10f, 50f), node.position)
    }

    @Test
    fun `positionY clamps only the y component`() {
        val node = SKNode().apply { position = Vector2(100f, 50f) }

        applyConstraint(node, SKConstraint.positionY(SKRange.of(0f, 10f)))

        assertEquals(Vector2(100f, 10f), node.position)
    }

    @Test
    fun `position clamps both components independently`() {
        val node = SKNode().apply { position = Vector2(-5f, 100f) }

        applyConstraint(node, SKConstraint.position(SKRange.of(0f, 10f), SKRange.of(0f, 50f)))

        assertEquals(Vector2(0f, 50f), node.position)
    }

    @Test
    fun `a value already within range is left untouched`() {
        val node = SKNode().apply { position = Vector2(5f, 5f) }

        applyConstraint(node, SKConstraint.position(SKRange.of(0f, 10f), SKRange.of(0f, 10f)))

        assertEquals(Vector2(5f, 5f), node.position)
    }

    @Test
    fun `zRotation clamps rotation`() {
        val node = SKNode().apply { zRotation = 5f }

        applyConstraint(node, SKConstraint.zRotation(SKRange.of(-1f, 1f)))

        assertEquals(1f, node.zRotation)
    }

    @Test
    fun `a disabled constraint is a no-op`() {
        val node = SKNode().apply { position = Vector2(100f, 100f) }
        val constraint = SKConstraint.positionX(SKRange.of(0f, 10f)).apply { enabled = false }

        applyConstraint(node, constraint)

        assertEquals(Vector2(100f, 100f), node.position)
    }

    @Test
    fun `distance pulls the node toward the target when too far, preserving direction`() {
        val parent = SKNode()
        val target = SKNode().apply { position = Vector2(0f, 0f) }
        val node = SKNode().apply { position = Vector2(10f, 0f) }
        parent.addChild(target)
        parent.addChild(node)

        applyConstraint(node, SKConstraint.distance(SKRange.atMost(5f), to = target))

        assertEquals(Vector2(5f, 0f), node.position)
    }

    @Test
    fun `distance pushes the node away from the target when too close`() {
        val parent = SKNode()
        val target = SKNode().apply { position = Vector2(0f, 0f) }
        val node = SKNode().apply { position = Vector2(1f, 0f) }
        parent.addChild(target)
        parent.addChild(node)

        applyConstraint(node, SKConstraint.distance(SKRange.atLeast(5f), to = target))

        assertEquals(Vector2(5f, 0f), node.position)
    }

    @Test
    fun `distance within range is left untouched`() {
        val parent = SKNode()
        val target = SKNode().apply { position = Vector2(0f, 0f) }
        val node = SKNode().apply { position = Vector2(3f, 0f) }
        parent.addChild(target)
        parent.addChild(node)

        applyConstraint(node, SKConstraint.distance(SKRange.of(0f, 5f), to = target))

        assertEquals(Vector2(3f, 0f), node.position)
    }

    @Test
    fun `distance is a no-op when the node and target coincide`() {
        val parent = SKNode()
        val target = SKNode().apply { position = Vector2(2f, 2f) }
        val node = SKNode().apply { position = Vector2(2f, 2f) }
        parent.addChild(target)
        parent.addChild(node)

        applyConstraint(node, SKConstraint.distance(SKRange.atLeast(5f), to = target)) // must not throw or NaN

        assertEquals(Vector2(2f, 2f), node.position)
    }

    @Test
    fun `orient rotates the node to face the target`() {
        val parent = SKNode()
        val target = SKNode().apply { position = Vector2(1f, 0f) }
        val node =
            SKNode().apply {
                position = Vector2(0f, 0f)
                zRotation = 0f
            }
        parent.addChild(target)
        parent.addChild(node)

        applyConstraint(node, SKConstraint.orient(to = target))

        assertEquals(0f, node.zRotation, 1e-4f)
    }

    @Test
    fun `orient allows deviation within the offset range`() {
        val parent = SKNode()
        val target = SKNode().apply { position = Vector2(1f, 0f) }
        // Already facing "up" (PI/2), 90 degrees off from facing the target directly (0 rad).
        val node =
            SKNode().apply {
                position = Vector2(0f, 0f)
                zRotation = (PI / 2).toFloat()
            }
        parent.addChild(target)
        parent.addChild(node)

        // Allow up to PI/4 of deviation -- expect the rotation to be pulled in only partway, to
        // the edge of the allowed offset, not all the way to directly facing the target.
        applyConstraint(node, SKConstraint.orient(to = target, offset = SKRange.of(0f, (PI / 4).toFloat())))

        assertEquals((PI / 4).toFloat(), node.zRotation, 1e-4f)
    }

    // The cases below mirror what the sample app's SKConstraintParityTests recorded on Apple's
    // SpriteKit: a child at the origin of a parent placed at (100, 0) in a root node.

    private fun childOfOffsetParent(): Triple<SKNode, SKNode, SKNode> {
        val root = SKNode()
        val parent = SKNode().apply { position = Vector2(100f, 0f) }
        val child = SKNode()
        root.addChild(parent)
        parent.addChild(child)
        return Triple(root, parent, child)
    }

    private fun assertEquals(
        expected: Vector2,
        actual: Vector2,
        absoluteTolerance: Float,
    ) {
        assertEquals(expected.x, actual.x, absoluteTolerance)
        assertEquals(expected.y, actual.y, absoluteTolerance)
    }

    @Test
    fun `referenceNode defaults to null`() {
        assertEquals(null, SKConstraint.positionX(SKRange.of(0f, 10f)).referenceNode)
    }

    @Test
    fun `position ranges are measured in the referenceNode's coordinates`() {
        val (root, _, child) = childOfOffsetParent()

        applyConstraint(child, SKConstraint.positionX(SKRange.of(0f, 10f)).apply { referenceNode = root })

        assertEquals(Vector2(-90f, 0f), child.position, 1e-4f)
    }

    @Test
    fun `a scaled referenceNode scales the range`() {
        val (root, _, child) = childOfOffsetParent()
        val reference =
            SKNode().apply {
                position = Vector2(50f, 0f)
                setScale(2f)
            }
        root.addChild(reference)

        applyConstraint(child, SKConstraint.positionX(SKRange.of(0f, 10f)).apply { referenceNode = reference })

        assertEquals(Vector2(-30f, 0f), child.position, 1e-4f)
    }

    @Test
    fun `a rotated referenceNode turns the axis the range applies along`() {
        val (root, _, child) = childOfOffsetParent()
        child.position = Vector2(0f, 30f)
        val reference = SKNode().apply { zRotation = (PI / 2).toFloat() }
        root.addChild(reference)

        applyConstraint(child, SKConstraint.positionX(SKRange.of(0f, 10f)).apply { referenceNode = reference })

        assertEquals(Vector2(0f, 10f), child.position, 1e-4f)
    }

    @Test
    fun `a zRotation range is measured relative to the referenceNode's rotation`() {
        val (root, _, child) = childOfOffsetParent()
        val reference = SKNode().apply { zRotation = 1f }
        root.addChild(reference)

        applyConstraint(child, SKConstraint.zRotation(SKRange.of(0.2f, 0.3f)).apply { referenceNode = reference })

        assertEquals(1.2f, child.zRotation, 1e-4f)
    }

    @Test
    fun `distance to a point measures from a point in the parent's coordinates`() {
        val (_, _, child) = childOfOffsetParent()

        applyConstraint(child, SKConstraint.distance(SKRange.atMost(20f), to = Vector2(0f, 100f)))

        assertEquals(Vector2(0f, 80f), child.position, 1e-4f)
    }

    @Test
    fun `distance to a point in another node converts it from that node`() {
        val (root, _, child) = childOfOffsetParent()
        val other =
            SKNode().apply {
                position = Vector2(0f, 50f)
                setScale(2f)
            }
        root.addChild(other)

        applyConstraint(child, SKConstraint.distance(SKRange.constant(0f), to = Vector2(10f, 10f), node = other))

        assertEquals(Vector2(-80f, 70f), child.position, 1e-4f)
    }

    @Test
    fun `distance is measured in the referenceNode's coordinates`() {
        val root = SKNode()
        val parent = SKNode().apply { setScale(2f) }
        val target = SKNode()
        root.addChild(parent)
        root.addChild(target)
        val inParent = SKNode().apply { position = Vector2(100f, 0f) }
        val inRoot = SKNode().apply { position = Vector2(100f, 0f) }
        parent.addChild(inParent)
        parent.addChild(inRoot)

        applyConstraint(inParent, SKConstraint.distance(SKRange.atMost(20f), to = target))
        applyConstraint(
            inRoot,
            SKConstraint.distance(SKRange.atMost(20f), to = target).apply { referenceNode = root },
        )

        assertEquals(Vector2(20f, 0f), inParent.position, 1e-4f)
        assertEquals(Vector2(10f, 0f), inRoot.position, 1e-4f)
    }

    @Test
    fun `with a referenceNode, a point target is still in the parent's coordinates`() {
        val (root, parent, child) = childOfOffsetParent()
        parent.setScale(2f)

        applyConstraint(
            child,
            SKConstraint.distance(SKRange.atMost(20f), to = Vector2(0f, 100f)).apply { referenceNode = root },
        )

        assertEquals(Vector2(0f, 90f), child.position, 1e-4f)
    }

    @Test
    fun `orient faces a point in the parent's or another node's coordinates`() {
        val (root, _, child) = childOfOffsetParent()
        applyConstraint(child, SKConstraint.orient(to = Vector2(0f, 10f)))
        assertEquals((PI / 2).toFloat(), child.zRotation, 1e-4f)

        applyConstraint(child, SKConstraint.orient(to = Vector2(0f, 10f), node = root))
        assertEquals(kotlin.math.atan2(10f, -100f), child.zRotation, 1e-4f)
    }

    @Test
    fun `orient's offset doesn't depend on the referenceNode`() {
        val (root, parent, child) = childOfOffsetParent()
        parent.zRotation = 1f
        val offset = SKRange.of(0.1f, 0.2f)

        applyConstraint(
            child,
            SKConstraint.orient(to = Vector2(0f, 10f), offset = offset).apply { referenceNode = root },
        )

        assertEquals((PI / 2).toFloat() + 0.1f, child.zRotation, 1e-4f)
    }

    private fun assertEquals(
        expected: Float,
        actual: Float,
        absoluteTolerance: Float,
    ) {
        assertTrue(kotlin.math.abs(expected - actual) <= absoluteTolerance, "expected $expected, was $actual")
    }
}
