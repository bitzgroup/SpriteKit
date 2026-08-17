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

    private fun assertEquals(
        expected: Float,
        actual: Float,
        absoluteTolerance: Float,
    ) {
        assertTrue(kotlin.math.abs(expected - actual) <= absoluteTolerance, "expected $expected, was $actual")
    }
}
