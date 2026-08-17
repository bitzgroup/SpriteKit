package jp.co.bitz.spritekit

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SKPhysicsSimulationTest {
    private fun assertEquals(
        expected: Float,
        actual: Float,
        absoluteTolerance: Float = 1e-3f,
    ) {
        assertTrue(abs(expected - actual) <= absoluteTolerance, "expected $expected, was $actual")
    }

    @Test
    fun `gravity accelerates a dynamic body's velocity and position each step`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        scene.physicsWorld.gravity = Vector2(0f, -10f)
        val node =
            SKNode().apply {
                position = Vector2(0f, 100f)
                physicsBody = SKPhysicsBody.circleOfRadius(1f).apply { linearDamping = 0f }
            }
        scene.addChild(node)

        simulatePhysics(scene, 1.seconds)

        // Semi-implicit Euler: velocity integrates first, then position uses the new velocity.
        assertEquals(-10f, node.physicsBody!!.velocity.y)
        assertEquals(90f, node.position.y)
    }

    @Test
    fun `a dynamic body resting on a static floor doesn't fall through it`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        scene.physicsWorld.gravity = Vector2(0f, -10f)
        val floorNode = SKNode().apply { physicsBody = SKPhysicsBody.edgeFrom(Vector2(-50f, 0f), Vector2(50f, 0f)) }
        scene.addChild(floorNode)
        val ballNode =
            SKNode().apply {
                position = Vector2(0f, 1f) // resting exactly on the floor, body radius 1
                physicsBody = SKPhysicsBody.circleOfRadius(1f)
            }
        scene.addChild(ballNode)

        repeat(60) { simulatePhysics(scene, (1.0 / 60.0).seconds) }

        assertTrue(
            ballNode.position.y >= 0.9f,
            "expected the ball to stay near the floor, was at ${ballNode.position.y}",
        )
    }

    @Test
    fun `bodies whose collision masks exclude each other pass through uncorrected`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        scene.physicsWorld.gravity = Vector2(0f, -10f)
        val floorNode =
            SKNode().apply {
                physicsBody =
                    SKPhysicsBody.edgeFrom(Vector2(-50f, 0f), Vector2(50f, 0f)).apply {
                        categoryBitMask = 1
                        collisionBitMask = 0
                    }
            }
        scene.addChild(floorNode)
        val ballNode =
            SKNode().apply {
                position = Vector2(0f, 0.5f) // already overlapping the floor
                physicsBody =
                    SKPhysicsBody.circleOfRadius(1f).apply {
                        categoryBitMask = 2
                        collisionBitMask = 0
                    }
            }
        scene.addChild(ballNode)

        simulatePhysics(scene, 1.seconds)

        assertTrue(
            ballNode.position.y < 0f,
            "expected the ball to fall straight through, was at ${ballNode.position.y}",
        )
    }

    @Test
    fun `an isPaused node's physics body doesn't simulate`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        scene.physicsWorld.gravity = Vector2(0f, -10f)
        val node =
            SKNode().apply {
                position = Vector2(0f, 100f)
                isPaused = true
                physicsBody = SKPhysicsBody.circleOfRadius(1f)
            }
        scene.addChild(node)

        simulatePhysics(scene, 1.seconds)

        assertEquals(100f, node.position.y)
        assertEquals(0f, node.physicsBody!!.velocity.y)
    }

    @Test
    fun `a pinned body doesn't translate even under gravity`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        scene.physicsWorld.gravity = Vector2(0f, -10f)
        val node =
            SKNode().apply {
                position = Vector2(0f, 100f)
                physicsBody = SKPhysicsBody.circleOfRadius(1f).apply { pinned = true }
            }
        scene.addChild(node)

        simulatePhysics(scene, 1.seconds)

        assertEquals(100f, node.position.y)
    }

    @Test
    fun `physicsWorld speed of zero freezes the simulation`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        scene.physicsWorld.gravity = Vector2(0f, -10f)
        scene.physicsWorld.speed = 0f
        val node =
            SKNode().apply {
                position = Vector2(0f, 100f)
                physicsBody = SKPhysicsBody.circleOfRadius(1f)
            }
        scene.addChild(node)

        simulatePhysics(scene, 1.seconds)

        assertEquals(100f, node.position.y)
        assertEquals(0f, node.physicsBody!!.velocity.y)
    }

    @Test
    fun `a node with no physicsBody is unaffected`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        scene.physicsWorld.gravity = Vector2(0f, -10f)
        val node = SKNode().apply { position = Vector2(5f, 5f) }
        scene.addChild(node)

        simulatePhysics(scene, 1.seconds)

        assertEquals(5f, node.position.y)
    }
}
