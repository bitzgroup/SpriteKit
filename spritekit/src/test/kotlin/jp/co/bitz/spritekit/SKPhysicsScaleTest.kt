package jp.co.bitz.spritekit

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** Mirrors tic-tac-toe's `SKPhysicsScaleParityTests` (Apple's behavior, recorded on iOS/tvOS simulators). */
class SKPhysicsScaleTest {
    private val root = SKScene(size = Vector2(400f, 400f)).apply { physicsWorld.gravity = Vector2.Zero }

    private fun node(
        scale: Float = 1f,
        body: SKPhysicsBody? = SKPhysicsBody.circleOfRadius(10f),
    ): SKNode =
        SKNode().apply {
            position = Vector2(200f, 200f)
            setScale(scale)
            body?.isDynamic = false
            physicsBody = body
            root.addChild(this)
        }

    /** How far the outline's first vertex is from the node, along the scene's x axis. */
    private fun xExtent(node: SKNode): Float = assertNotNull(physicsBodyOutline(node)).first().x - node.position.x

    private fun assertNear(
        expected: Float,
        actual: Float,
    ) = assertTrue(abs(expected - actual) < 1e-3f, "expected $expected, was $actual")

    @Test
    fun `a node's own scale counts only from when its body was attached`() {
        val node = node(scale = 2f)
        assertNear(10f, xExtent(node))
        node.setScale(4f)
        assertNear(20f, xExtent(node))
        node.setScale(1f)
        assertNear(5f, xExtent(node))
    }

    @Test
    fun `scaling a node after attaching its body scales the body`() {
        val node = node()
        node.setScale(2f)
        assertNear(20f, xExtent(node))
        assertNotNull(root.physicsWorld.body(at = Vector2(219f, 200f)))
    }

    @Test
    fun `an ancestor's scale counts in full`() {
        val parent =
            SKNode().apply {
                position = Vector2(200f, 200f)
                setScale(2f)
                root.addChild(this)
            }
        val child =
            SKNode().apply {
                physicsBody = SKPhysicsBody.circleOfRadius(10f).apply { isDynamic = false }
                parent.addChild(this)
            }
        assertNear(20f, assertNotNull(physicsBodyOutline(child)).first().x - 200f)
    }

    @Test
    fun `a body moved to another node takes that node's scale as its own`() {
        val first = node()
        val second = node(scale = 3f, body = null)
        second.physicsBody = first.physicsBody
        assertNear(10f, xExtent(second))
    }

    @Test
    fun `a circle under a non-uniform scale keeps the mean square of the two stretches`() {
        val node = node()
        node.xScale = 3f
        assertNear(10f * sqrt(5f), xExtent(node))
    }

    @Test
    fun `a rectangle stretches along each axis`() {
        val node = node(body = SKPhysicsBody.rectangleOf(Vector2(20f, 20f)))
        node.xScale = 2f
        assertNotNull(root.physicsWorld.body(at = Vector2(219f, 200f)))
        assertNull(root.physicsWorld.body(at = Vector2(200f, 211f)))
    }

    @Test
    fun `collisions use the scaled shape`() {
        val target = node()
        target.setScale(2f)
        val ball =
            SKNode().apply {
                position = Vector2(176f, 200f) // 24 from the target: inside its 20 + 5, outside 10 + 5
                physicsBody = SKPhysicsBody.circleOfRadius(5f)
                root.addChild(this)
            }
        simulatePhysics(root, 1.seconds)
        assertEquals(listOf(target.physicsBody!!), ball.physicsBody!!.allContactedBodies())
    }
}
