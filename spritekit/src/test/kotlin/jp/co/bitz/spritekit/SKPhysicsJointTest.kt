package jp.co.bitz.spritekit

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SKPhysicsJointTest {
    private fun assertEquals(
        expected: Float,
        actual: Float,
        absoluteTolerance: Float = 1e-3f,
    ) {
        assertTrue(abs(expected - actual) <= absoluteTolerance, "expected $expected, was $actual")
    }

    /** A body that never physically collides with anything, so joint tests aren't confounded by collision response. */
    private fun nonCollidingBody(isDynamic: Boolean = true): SKPhysicsBody =
        SKPhysicsBody.circleOfRadius(1f).apply {
            this.isDynamic = isDynamic
            collisionBitMask = 0
        }

    @Test
    fun `add, remove, and removeAllJoints manage the joints list`() {
        val world = SKPhysicsWorld()
        val joint = SKPhysicsJointPin(SKPhysicsBody.circleOfRadius(1f), SKPhysicsBody.circleOfRadius(1f), Vector2.Zero)

        world.add(joint)
        assertEquals(listOf(joint), world.joints)

        world.remove(joint)
        assertTrue(world.joints.isEmpty())

        world.add(joint)
        world.removeAllJoints()
        assertTrue(world.joints.isEmpty())
    }

    @Test
    fun `a pin joint keeps a dynamic body anchored to a static one under gravity`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        scene.physicsWorld.gravity = Vector2(0f, -50f)

        val anchorNode = SKNode().apply { physicsBody = nonCollidingBody(isDynamic = false) }
        scene.addChild(anchorNode)
        val bobNode = SKNode().apply { physicsBody = nonCollidingBody() } // starts at the same position
        scene.addChild(bobNode)

        scene.physicsWorld.add(SKPhysicsJointPin(anchorNode.physicsBody!!, bobNode.physicsBody!!, Vector2.Zero))

        repeat(120) { simulatePhysics(scene, (1.0 / 60.0).seconds) }

        assertTrue(
            bobNode.position.length() < 1f,
            "expected the pin to hold the bob near the anchor, was at ${bobNode.position}",
        )
    }

    @Test
    fun `a fixed joint keeps a second body's rotation locked to the first's`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        scene.physicsWorld.gravity = Vector2.Zero

        val nodeA = SKNode().apply { physicsBody = nonCollidingBody(isDynamic = false) }
        scene.addChild(nodeA)
        val nodeB = SKNode().apply { physicsBody = nonCollidingBody() }
        scene.addChild(nodeB)

        scene.physicsWorld.add(SKPhysicsJointFixed(nodeA.physicsBody!!, nodeB.physicsBody!!, Vector2.Zero))
        simulatePhysics(scene, (1.0 / 60.0).seconds) // binds the joint's initial (zero) relative rotation

        nodeA.zRotation = 1f // simulate an external influence rotating nodeA
        simulatePhysics(scene, (1.0 / 60.0).seconds)

        assertEquals(nodeA.zRotation, nodeB.zRotation)
    }

    @Test
    fun `a spring joint pulls a stretched body back towards its rest length`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        scene.physicsWorld.gravity = Vector2.Zero

        val nodeA = SKNode().apply { physicsBody = nonCollidingBody(isDynamic = false) }
        scene.addChild(nodeA)
        val nodeB =
            SKNode().apply {
                position = Vector2(5f, 0f)
                physicsBody = nonCollidingBody().apply { linearDamping = 0.5f }
            }
        scene.addChild(nodeB)

        val spring = SKPhysicsJointSpring(nodeA.physicsBody!!, nodeB.physicsBody!!, Vector2.Zero, Vector2(5f, 0f))
        spring.frequency = 2f
        spring.damping = 1f
        scene.physicsWorld.add(spring)

        simulatePhysics(scene, (1.0 / 60.0).seconds) // binds restLength = 5 (the initial separation)

        nodeB.position = Vector2(10f, 0f) // stretch the spring well past its rest length
        repeat(120) { simulatePhysics(scene, (1.0 / 60.0).seconds) }

        assertTrue(nodeB.position.x < 8f, "expected the spring to pull the body back, was at ${nodeB.position}")
    }

    @Test
    fun `a sliding joint holds a body to its axis while gravity pulls it off-axis`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        scene.physicsWorld.gravity = Vector2(0f, -20f)

        val railNode = SKNode().apply { physicsBody = nonCollidingBody(isDynamic = false) }
        scene.addChild(railNode)
        val carNode = SKNode().apply { physicsBody = nonCollidingBody() }
        scene.addChild(carNode)

        // A horizontal rail: the car may slide left/right (x) but not drift vertically (y).
        scene.physicsWorld.add(
            SKPhysicsJointSliding(railNode.physicsBody!!, carNode.physicsBody!!, Vector2.Zero, Vector2(1f, 0f)),
        )

        repeat(120) { simulatePhysics(scene, (1.0 / 60.0).seconds) }

        assertTrue(
            abs(carNode.position.y) < 1f,
            "expected the car to stay near the rail's height, was at ${carNode.position}",
        )
    }

    @Test
    fun `a limit joint doesn't correct a body still within maxLength`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        scene.physicsWorld.gravity = Vector2.Zero

        val anchorNode = SKNode().apply { physicsBody = nonCollidingBody(isDynamic = false) }
        scene.addChild(anchorNode)
        val weightNode =
            SKNode().apply {
                position = Vector2(3f, 0f)
                physicsBody = nonCollidingBody()
            }
        scene.addChild(weightNode)

        val limit =
            SKPhysicsJointLimit(anchorNode.physicsBody!!, weightNode.physicsBody!!, Vector2.Zero, Vector2(3f, 0f))
        limit.maxLength = 5f
        scene.physicsWorld.add(limit)

        simulatePhysics(scene, (1.0 / 60.0).seconds)

        assertEquals(3f, weightNode.position.x)
    }

    @Test
    fun `a limit joint pulls a body back once it exceeds maxLength`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        scene.physicsWorld.gravity = Vector2.Zero

        val anchorNode = SKNode().apply { physicsBody = nonCollidingBody(isDynamic = false) }
        scene.addChild(anchorNode)
        val weightNode =
            SKNode().apply {
                position = Vector2(3f, 0f)
                physicsBody = nonCollidingBody()
            }
        scene.addChild(weightNode)

        val limit =
            SKPhysicsJointLimit(anchorNode.physicsBody!!, weightNode.physicsBody!!, Vector2.Zero, Vector2(3f, 0f))
        limit.maxLength = 5f
        scene.physicsWorld.add(limit)
        simulatePhysics(scene, (1.0 / 60.0).seconds) // binds anchors while still at their construction-time positions

        weightNode.position = Vector2(10f, 0f) // well past the 5-unit limit
        repeat(60) { simulatePhysics(scene, (1.0 / 60.0).seconds) }

        assertTrue(
            weightNode.position.x < 10f,
            "expected the limit to pull the body back, was at ${weightNode.position}",
        )
    }

    @Test
    fun `a limit joint's default maxLength is the initial distance between anchors`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val anchorNode = SKNode().apply { physicsBody = nonCollidingBody(isDynamic = false) }
        scene.addChild(anchorNode)
        val weightNode =
            SKNode().apply {
                position = Vector2(3f, 0f)
                physicsBody = nonCollidingBody()
            }
        scene.addChild(weightNode)

        val limit =
            SKPhysicsJointLimit(anchorNode.physicsBody!!, weightNode.physicsBody!!, Vector2.Zero, Vector2(3f, 0f))
        scene.physicsWorld.add(limit)

        simulatePhysics(scene, (1.0 / 60.0).seconds)

        assertEquals(3f, limit.maxLength)
    }

    @Test
    fun `a joint referencing a body no longer in the scene is silently skipped`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val nodeA = SKNode().apply { physicsBody = nonCollidingBody(isDynamic = false) }
        scene.addChild(nodeA)
        val bodyB = nonCollidingBody() // never attached to a node in the scene

        scene.physicsWorld.add(SKPhysicsJointPin(nodeA.physicsBody!!, bodyB, Vector2.Zero))

        simulatePhysics(scene, 1.seconds) // must not throw
    }
}
