package jp.co.bitz.spritekit

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** Mirrors tic-tac-toe's `SKPhysicsBodyParityTests` (Apple's behavior, recorded on iOS/tvOS simulators). */
class SKPhysicsBodyBookkeepingTest {
    private val scene = SKScene(size = Vector2(400f, 400f)).apply { physicsWorld.gravity = Vector2.Zero }
    private val step = (1.0 / 60.0).seconds

    private fun add(
        name: String,
        body: SKPhysicsBody,
        at: Vector2,
    ): SKNode =
        SKNode().also {
            it.name = name
            it.position = at
            it.physicsBody = body
            scene.addChild(it)
        }

    private fun names(body: SKPhysicsBody) = body.allContactedBodies().map { it.node?.name }

    @Test
    fun `area is in square meters of 150 points and mass is density times area`() {
        val circle = SKPhysicsBody.circleOfRadius(10f)
        assertEquals(PI.toFloat() * 100f / 22500f, circle.area, 1e-7f)
        assertEquals(circle.area, circle.mass, 1e-7f)
        val rect = SKPhysicsBody.rectangleOf(Vector2(20f, 10f))
        rect.mass = 1f
        assertEquals(112.5f, rect.density, 1e-3f)
        assertEquals(0f, SKPhysicsBody.edgeLoopFrom(Rect(0f, 0f, 10f, 10f)).area)
    }

    @Test
    fun `an impulse changes velocity by impulse over mass and an angular one uses inertia in meters`() {
        val circle = add("c", SKPhysicsBody.circleOfRadius(10f), Vector2(100f, 300f)).physicsBody!!
        circle.applyImpulse(Vector2(1f, 0f))
        assertEquals(1f / circle.mass, circle.velocity.x, 0.01f)
        circle.applyAngularImpulse(1f)
        val radius = 10f / 150f
        assertEquals(1f, circle.angularVelocity * circle.mass * radius * radius / 2f, 1e-3f)
    }

    @Test
    fun `node is the node carrying the body and null otherwise`() {
        val body = SKPhysicsBody.circleOfRadius(10f)
        assertNull(body.node)
        val node = SKNode()
        node.physicsBody = body
        assertSame(node, body.node)
        val other = SKNode()
        other.physicsBody = body
        assertSame(other, body.node)
        assertNull(node.physicsBody)
        other.physicsBody = null
        assertNull(body.node)
    }

    @Test
    fun `isResting is never set by the simulation`() {
        scene.physicsWorld.gravity = Vector2(0f, -9.8f)
        add("floor", SKPhysicsBody.edgeFrom(Vector2(0f, 50f), Vector2(400f, 50f)), Vector2.Zero)
        val ball =
            add(
                "ball",
                SKPhysicsBody.circleOfRadius(10f).apply { restitution = 0f },
                Vector2(200f, 60f),
            ).physicsBody!!
        repeat(120) { simulatePhysics(scene, step) }
        assertFalse(ball.isResting)
    }

    @Test
    fun `setting isResting stops the body until the next step or a velocity clears it`() {
        val detached = SKPhysicsBody.circleOfRadius(10f)
        detached.isResting = true
        assertTrue(detached.isResting)

        val node = add("k", SKPhysicsBody.circleOfRadius(10f).apply { linearDamping = 0f }, Vector2(200f, 300f))
        val body = node.physicsBody!!
        body.velocity = Vector2(50f, 0f)
        body.isResting = true
        assertTrue(body.isResting)
        assertEquals(Vector2.Zero, body.velocity)
        repeat(10) { simulatePhysics(scene, step) }
        assertFalse(body.isResting)
        assertEquals(200f, node.position.x)

        body.isResting = true
        body.velocity = Vector2(30f, 0f)
        assertFalse(body.isResting)
    }

    @Test
    fun `joints lists the world's joints on the body until removed or its node leaves`() {
        val a = add("a", SKPhysicsBody.circleOfRadius(10f), Vector2(100f, 100f)).physicsBody!!
        val bNode = add("b", SKPhysicsBody.circleOfRadius(10f), Vector2(150f, 100f))
        val b = bNode.physicsBody!!
        val joint = SKPhysicsJointPin(a, b, Vector2(125f, 100f))
        assertTrue(a.joints.isEmpty())
        scene.physicsWorld.add(joint)
        assertEquals(listOf<SKPhysicsJoint>(joint), a.joints)
        assertEquals(listOf<SKPhysicsJoint>(joint), b.joints)
        scene.physicsWorld.remove(joint)
        assertTrue(a.joints.isEmpty())
        scene.physicsWorld.add(joint)
        bNode.removeFromParent()
        assertTrue(a.joints.isEmpty())
    }

    @Test
    fun `adding a joint between bodies in the scene fixes its anchors at once`() {
        val aNode = add("a", SKPhysicsBody.circleOfRadius(10f), Vector2(100f, 100f))
        val bNode = add("b", SKPhysicsBody.circleOfRadius(10f), Vector2(150f, 100f))
        val joint = SKPhysicsJointPin(aNode.physicsBody!!, bNode.physicsBody!!, Vector2(125f, 100f))
        scene.physicsWorld.add(joint)
        aNode.position = Vector2(0f, 0f) // moving afterwards must not move the anchor on the body
        assertEquals(Vector2(25f, 0f), joint.localAnchorA)
    }

    @Test
    fun `allContactedBodies lists touching pairs that collide either way or test for contact`() {
        add("floor", SKPhysicsBody.edgeFrom(Vector2(0f, 50f), Vector2(400f, 50f)), Vector2.Zero)
        val ball = add("ball", SKPhysicsBody.circleOfRadius(10f), Vector2(100f, 59.5f)).physicsBody!!
        assertTrue(ball.allContactedBodies().isEmpty()) // nothing until a step runs

        fun ghost(
            name: String,
            x: Float,
            y: Float,
        ) = add(name, SKPhysicsBody.circleOfRadius(10f).apply { collisionBitMask = 0 }, Vector2(x, y)).physicsBody!!
        val quiet = ghost("quiet1", 300f, 300f)
        ghost("quiet2", 305f, 300f)
        val sensor = ghost("sensor", 300f, 200f).apply { contactTestBitMask = -1 }
        val target = ghost("target", 305f, 200f)
        val wall =
            add(
                "wall",
                SKPhysicsBody.circleOfRadius(10f).apply { isDynamic = false },
                Vector2(200f, 300f),
            ).physicsBody!!
        val pusher = ghost("pusher", 205f, 300f)
        simulatePhysics(scene, step)

        assertEquals(listOf("floor"), names(ball))
        assertTrue(quiet.allContactedBodies().isEmpty())
        assertEquals(listOf("target"), names(sensor))
        assertEquals(listOf("sensor"), names(target))
        assertEquals(listOf("pusher"), names(wall))
        assertEquals(listOf("wall"), names(pusher))
    }
}
