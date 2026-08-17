package jp.co.bitz.spritekit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SKPhysicsContactTest {
    private class RecordingDelegate : SKPhysicsContactDelegate {
        val began = mutableListOf<SKPhysicsContact>()
        val ended = mutableListOf<SKPhysicsContact>()

        override fun didBegin(contact: SKPhysicsContact) {
            began += contact
        }

        override fun didEnd(contact: SKPhysicsContact) {
            ended += contact
        }
    }

    /** A static sensor body -- no physical collision response, only contact-test bitmasks set. */
    private fun sensorBody(
        radius: Float,
        category: Int,
        contactTest: Int,
    ): SKPhysicsBody =
        SKPhysicsBody.circleOfRadius(radius).apply {
            isDynamic = false
            categoryBitMask = category
            collisionBitMask = 0
            contactTestBitMask = contactTest
        }

    @Test
    fun `a sensor pair reports contact without any physical separation`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val delegate = RecordingDelegate()
        scene.physicsWorld.contactDelegate = delegate
        scene.physicsWorld.gravity = Vector2.Zero

        val nodeA = SKNode().apply { physicsBody = sensorBody(radius = 5f, category = 1, contactTest = 2) }
        scene.addChild(nodeA)
        val nodeB =
            SKNode().apply {
                position = Vector2(1f, 0f) // well inside nodeA's radius -- overlapping
                physicsBody = sensorBody(radius = 1f, category = 2, contactTest = 1)
            }
        scene.addChild(nodeB)

        simulatePhysics(scene, 1.seconds)

        assertEquals(1, delegate.began.size)
        assertEquals(Vector2(1f, 0f), nodeB.position) // never pushed apart
        assertEquals(0f, delegate.began.single().collisionImpulse)
    }

    @Test
    fun `didBegin fires once while bodies stay touching, then didEnd fires once they separate`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val delegate = RecordingDelegate()
        scene.physicsWorld.contactDelegate = delegate
        scene.physicsWorld.gravity = Vector2.Zero

        val nodeA = SKNode().apply { physicsBody = sensorBody(radius = 5f, category = 1, contactTest = 2) }
        scene.addChild(nodeA)
        val nodeB =
            SKNode().apply {
                position = Vector2(1f, 0f)
                physicsBody = sensorBody(radius = 1f, category = 2, contactTest = 1)
            }
        scene.addChild(nodeB)

        simulatePhysics(scene, 1.seconds)
        simulatePhysics(scene, 1.seconds)
        assertEquals(1, delegate.began.size)
        assertEquals(0, delegate.ended.size)

        nodeB.position = Vector2(100f, 100f)
        simulatePhysics(scene, 1.seconds)

        assertEquals(1, delegate.began.size)
        assertEquals(1, delegate.ended.size)
    }

    @Test
    fun `no contact notification when neither body's contactTestBitMask matches the other's category`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val delegate = RecordingDelegate()
        scene.physicsWorld.contactDelegate = delegate
        scene.physicsWorld.gravity = Vector2.Zero

        val nodeA = SKNode().apply { physicsBody = SKPhysicsBody.circleOfRadius(5f).apply { isDynamic = false } }
        scene.addChild(nodeA)
        val nodeB =
            SKNode().apply {
                position = Vector2(1f, 0f)
                // Default contactTestBitMask is 0 on both sides -- overlapping, but never reported.
                physicsBody = SKPhysicsBody.circleOfRadius(1f).apply { isDynamic = false }
            }
        scene.addChild(nodeB)

        simulatePhysics(scene, 1.seconds)

        assertTrue(delegate.began.isEmpty())
    }

    @Test
    fun `didEnd fires when a touching body leaves the scene`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val delegate = RecordingDelegate()
        scene.physicsWorld.contactDelegate = delegate
        scene.physicsWorld.gravity = Vector2.Zero

        val nodeA = SKNode().apply { physicsBody = sensorBody(radius = 5f, category = 1, contactTest = 2) }
        scene.addChild(nodeA)
        val nodeB =
            SKNode().apply {
                position = Vector2(1f, 0f)
                physicsBody = sensorBody(radius = 1f, category = 2, contactTest = 1)
            }
        scene.addChild(nodeB)

        simulatePhysics(scene, 1.seconds)
        nodeB.removeFromParent()
        simulatePhysics(scene, 1.seconds)

        assertEquals(1, delegate.began.size)
        assertEquals(1, delegate.ended.size)
    }

    @Test
    fun `contact is still reported for a pair whose collisionBitMask also lets them physically collide`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val delegate = RecordingDelegate()
        scene.physicsWorld.contactDelegate = delegate
        scene.physicsWorld.gravity = Vector2.Zero

        val nodeA =
            SKNode().apply {
                physicsBody =
                    SKPhysicsBody.circleOfRadius(1f).apply {
                        isDynamic = false
                        categoryBitMask = 1
                        contactTestBitMask = 2
                    }
            }
        scene.addChild(nodeA)
        val nodeB =
            SKNode().apply {
                position = Vector2(0.5f, 0f)
                physicsBody =
                    SKPhysicsBody.circleOfRadius(1f).apply {
                        categoryBitMask = 2
                        contactTestBitMask = 1
                    }
            }
        scene.addChild(nodeB)

        simulatePhysics(scene, 1.seconds)

        assertEquals(1, delegate.began.size)
    }
}
