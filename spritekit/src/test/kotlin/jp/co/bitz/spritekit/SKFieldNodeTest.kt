package jp.co.bitz.spritekit

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SKFieldNodeTest {
    private fun assertEquals(
        expected: Float,
        actual: Float,
        absoluteTolerance: Float = 1e-3f,
    ) {
        assertTrue(abs(expected - actual) <= absoluteTolerance, "expected $expected, was $actual")
    }

    @Test
    fun `a radial gravity field pulls a body towards its position`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        scene.physicsWorld.gravity = Vector2.Zero
        val field = SKFieldNode.radialGravityField().apply { strength = 10f }
        scene.addChild(field) // at the scene's origin

        val bodyNode =
            SKNode().apply {
                position = Vector2(10f, 0f)
                physicsBody = SKPhysicsBody.circleOfRadius(1f).apply { linearDamping = 0f }
            }
        scene.addChild(bodyNode)

        simulatePhysics(scene, 1.seconds)

        // Constant-strength (falloff 0) pull toward the field, i.e. in the -x direction.
        assertEquals(-10f, bodyNode.physicsBody!!.velocity.x)
        assertEquals(0f, bodyNode.physicsBody!!.velocity.y)
    }

    @Test
    fun `a negative-strength radial gravity field repels instead of attracts`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        scene.physicsWorld.gravity = Vector2.Zero
        val field = SKFieldNode.radialGravityField().apply { strength = -10f }
        scene.addChild(field)

        val bodyNode =
            SKNode().apply {
                position = Vector2(10f, 0f)
                physicsBody = SKPhysicsBody.circleOfRadius(1f).apply { linearDamping = 0f }
            }
        scene.addChild(bodyNode)

        simulatePhysics(scene, 1.seconds)

        assertTrue(bodyNode.physicsBody!!.velocity.x > 0f, "expected the body to be pushed away, not pulled in")
    }

    @Test
    fun `a linear gravity field accelerates bodies uniformly along its direction`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        scene.physicsWorld.gravity = Vector2.Zero
        val field = SKFieldNode.linearGravityField(Vector2(0f, -1f)).apply { strength = 5f }
        scene.addChild(field)

        val bodyNode = SKNode().apply { physicsBody = SKPhysicsBody.circleOfRadius(1f).apply { linearDamping = 0f } }
        scene.addChild(bodyNode)

        simulatePhysics(scene, 1.seconds)

        assertEquals(-5f, bodyNode.physicsBody!!.velocity.y)
    }

    @Test
    fun `a drag field decelerates a moving body proportionally to its velocity`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        scene.physicsWorld.gravity = Vector2.Zero
        val field = SKFieldNode.dragField().apply { strength = 0.5f }
        scene.addChild(field)

        val bodyNode =
            SKNode().apply {
                physicsBody =
                    SKPhysicsBody.circleOfRadius(1f).apply {
                        linearDamping = 0f
                        velocity = Vector2(10f, 0f)
                    }
            }
        scene.addChild(bodyNode)

        simulatePhysics(scene, 1.seconds)

        assertEquals(5f, bodyNode.physicsBody!!.velocity.x) // halved by strength 0.5 over one second
    }

    @Test
    fun `a velocity field overrides a body's velocity directly rather than accelerating it`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val field = SKFieldNode.velocityField(Vector2(3f, 4f)).apply { strength = 1f }
        scene.addChild(field)

        val bodyNode =
            SKNode().apply {
                physicsBody =
                    SKPhysicsBody.circleOfRadius(1f).apply {
                        linearDamping = 0f
                        velocity = Vector2(100f, 100f) // should be overridden, not just nudged
                    }
            }
        scene.addChild(bodyNode)

        simulatePhysics(scene, 1.seconds)

        assertEquals(Vector2(3f, 4f), bodyNode.physicsBody!!.velocity)
    }

    @Test
    fun `a disabled field has no effect`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        scene.physicsWorld.gravity = Vector2.Zero
        val field =
            SKFieldNode.linearGravityField(Vector2(0f, -1f)).apply {
                strength = 5f
                isEnabled = false
            }
        scene.addChild(field)

        val bodyNode = SKNode().apply { physicsBody = SKPhysicsBody.circleOfRadius(1f) }
        scene.addChild(bodyNode)

        simulatePhysics(scene, 1.seconds)

        assertEquals(Vector2.Zero, bodyNode.physicsBody!!.velocity)
    }

    @Test
    fun `a field whose categoryBitMask excludes a body's fieldBitMask has no effect on it`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        scene.physicsWorld.gravity = Vector2.Zero
        val field =
            SKFieldNode.linearGravityField(Vector2(0f, -1f)).apply {
                strength = 5f
                categoryBitMask = 1
            }
        scene.addChild(field)

        val bodyNode =
            SKNode().apply {
                physicsBody = SKPhysicsBody.circleOfRadius(1f).apply { fieldBitMask = 2 } // no overlap with category 1
            }
        scene.addChild(bodyNode)

        simulatePhysics(scene, 1.seconds)

        assertEquals(Vector2.Zero, bodyNode.physicsBody!!.velocity)
    }

    @Test
    fun `a field doesn't affect a non-dynamic body`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val field = SKFieldNode.linearGravityField(Vector2(0f, -1f)).apply { strength = 5f }
        scene.addChild(field)

        val bodyNode = SKNode().apply { physicsBody = SKPhysicsBody.circleOfRadius(1f).apply { isDynamic = false } }
        scene.addChild(bodyNode)

        simulatePhysics(scene, 1.seconds)

        assertEquals(Vector2.Zero, bodyNode.physicsBody!!.velocity)
    }
}
