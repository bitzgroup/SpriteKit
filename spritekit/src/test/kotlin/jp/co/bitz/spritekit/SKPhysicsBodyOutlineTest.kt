package jp.co.bitz.spritekit

import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Expected outlines are the obstacle vertices Apple's framework gives for the same bodies. */
class SKPhysicsBodyOutlineTest {
    private val root = SKScene(size = Vector2(100f, 100f))

    private fun inScene(
        body: SKPhysicsBody?,
        position: Vector2 = Vector2.Zero,
        rotation: Float = 0f,
        scale: Float = 1f,
    ): SKNode =
        SKNode().apply {
            physicsBody = body
            this.position = position
            zRotation = rotation
            xScale = scale
            yScale = scale
            root.addChild(this)
        }

    private fun assertOutline(
        expected: List<Vector2>,
        actual: List<Vector2>?,
    ) {
        assertNotNull(actual)
        assertEquals(expected.size, actual.size, "vertex count of $actual")
        for ((e, a) in expected.zip(actual)) {
            assertTrue(abs(e.x - a.x) < 1e-3f && abs(e.y - a.y) < 1e-3f, "expected $expected, was $actual")
        }
    }

    private fun v(
        x: Float,
        y: Float,
    ) = Vector2(x, y)

    @Test
    fun `a rectangle goes through the node's transform`() {
        val node = inScene(SKPhysicsBody.rectangleOf(v(10f, 20f), v(5f, 0f)), v(1f, 2f), PI.toFloat() / 2f)

        assertOutline(listOf(v(11f, 2f), v(11f, 12f), v(-9f, 12f), v(-9f, 2f)), physicsBodyOutline(node))
    }

    @Test
    fun `a polygon keeps its first vertex and turns counterclockwise`() {
        val ccw = inScene(SKPhysicsBody.polygonFrom(listOf(v(10f, 0f), v(0f, 10f), v(0f, 0f))))
        val cw = inScene(SKPhysicsBody.polygonFrom(listOf(v(0f, 0f), v(0f, 10f), v(10f, 0f))))

        assertOutline(listOf(v(10f, 0f), v(0f, 10f), v(0f, 0f)), physicsBodyOutline(ccw))
        assertOutline(listOf(v(0f, 0f), v(10f, 0f), v(0f, 10f)), physicsBodyOutline(cw))
    }

    @Test
    fun `a circle is an octagon at scene-space angles`() {
        val node = inScene(SKPhysicsBody.circleOfRadius(10f, v(5f, 0f)), v(100f, 0f), PI.toFloat() / 2f)

        val d = 7.0711f
        assertOutline(
            listOf(
                v(110f, 5f),
                v(100f + d, 5f + d),
                v(100f, 15f),
                v(100f - d, 5f + d),
                v(90f, 5f),
                v(100f - d, 5f - d),
                v(100f, -5f),
                v(100f + d, 5f - d),
            ),
            physicsBodyOutline(node),
        )
    }

    @Test
    fun `a uniform scale scales the outline`() {
        val node = inScene(SKPhysicsBody.circleOfRadius(10f), scale = 2f)

        assertEquals(v(20f, 0f), physicsBodyOutline(node)?.first())
    }

    @Test
    fun `edge chains keep their order and a loop repeats its first vertex`() {
        val loop = inScene(SKPhysicsBody.edgeLoopFrom(Rect(-5f, 3f, 5f, 23f)))

        assertOutline(
            listOf(v(-5f, 3f), v(5f, 3f), v(5f, 23f), v(-5f, 23f), v(-5f, 3f)),
            physicsBodyOutline(loop),
        )
    }

    @Test
    fun `a single edge, several parts and a concave polygon have no outline`() {
        val edge = inScene(SKPhysicsBody.edgeFrom(v(0f, 0f), v(10f, 0f)))
        val parts =
            inScene(
                SKPhysicsBody.bodies(
                    listOf(SKPhysicsBody.rectangleOf(v(2f, 2f)), SKPhysicsBody.circleOfRadius(1f, v(10f, 0f))),
                ),
            )
        val concave =
            inScene(SKPhysicsBody.polygonFrom(listOf(v(0f, 0f), v(10f, 0f), v(10f, 10f), v(5f, 3f), v(0f, 10f))))

        assertNull(physicsBodyOutline(edge))
        assertNull(physicsBodyOutline(parts))
        assertNull(physicsBodyOutline(concave))
    }

    @Test
    fun `a body of one part has that part's outline`() {
        val node =
            inScene(SKPhysicsBody.bodies(listOf(SKPhysicsBody.bodies(listOf(SKPhysicsBody.rectangleOf(v(10f, 20f)))))))

        assertOutline(listOf(v(-5f, -10f), v(5f, -10f), v(5f, 10f), v(-5f, 10f)), physicsBodyOutline(node))
    }

    @Test
    fun `an outline goes through the node's ancestors`() {
        val parent = SKNode().apply { position = v(1000f, 0f) }
        root.addChild(parent)
        val child = SKNode().apply { physicsBody = SKPhysicsBody.rectangleOf(v(4f, 4f)) }
        parent.addChild(child)

        assertOutline(listOf(v(998f, -2f), v(1002f, -2f), v(1002f, 2f), v(998f, 2f)), physicsBodyOutline(child))
    }

    @Test
    fun `no body or no scene means no outline`() {
        assertNull(physicsBodyOutline(inScene(null)))
        assertNull(physicsBodyOutline(SKNode().apply { physicsBody = SKPhysicsBody.rectangleOf(v(2f, 2f)) }))
    }
}
