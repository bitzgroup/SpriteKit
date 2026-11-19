package jp.co.bitz.spritekit

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** Mirrors the tic-tac-toe sample's `SKPhysicsAngularParityTests` probes. */
class SKPhysicsAngularTest {
    private val frame = (1.0 / 60.0).seconds

    private fun assertNear(
        expected: Float,
        actual: Float,
        tolerance: Float = 1e-3f,
    ) = assertTrue(abs(expected - actual) <= tolerance * maxOf(1f, abs(expected)), "expected $expected, was $actual")

    private fun scene(gravity: Vector2 = Vector2.Zero) =
        SKScene(size = Vector2(400f, 400f)).apply { physicsWorld.gravity = gravity }

    private fun body(
        scene: SKNode,
        body: SKPhysicsBody,
        at: Vector2,
    ): SKNode =
        SKNode().apply {
            position = at
            physicsBody = body
            scene.addChild(this)
        }

    @Test
    fun `an impulse off the center of mass turns the body by r cross J over inertia, lengths in meters`() {
        val scene = scene()
        val b = body(scene, SKPhysicsBody.circleOfRadius(10f), Vector2(200f, 200f)).physicsBody!!
        b.applyImpulse(Vector2(0f, 10f), at = Vector2(210f, 200f))
        // Apple: v 716.197, ω 143.239.
        assertNear(716.197f, b.velocity.y)
        assertNear(143.239f, b.angularVelocity)
    }

    @Test
    fun `the point is in scene coordinates`() {
        val scene = scene()
        val holder =
            SKNode().apply {
                position = Vector2(50f, 0f)
                zRotation = (PI / 2).toFloat()
                scene.addChild(this)
            }
        val b = body(holder, SKPhysicsBody.circleOfRadius(10f), Vector2.Zero).physicsBody!!
        b.applyImpulse(Vector2(0f, 10f), at = Vector2(60f, 0f))
        assertNear(143.239f, b.angularVelocity)
    }

    @Test
    fun `the lever arm runs from the center of mass, not the node's origin`() {
        val scene = scene()
        val b =
            body(
                scene,
                SKPhysicsBody.circleOfRadius(10f, center = Vector2(20f, 0f)),
                Vector2(100f, 300f),
            ).physicsBody!!
        b.applyImpulse(Vector2(0f, 10f), at = Vector2(100f, 300f))
        assertNear(716.197f, b.velocity.y)
        assertNear(-286.479f, b.angularVelocity)

        val spun =
            body(
                scene,
                SKPhysicsBody.circleOfRadius(10f, center = Vector2(20f, 0f)),
                Vector2(300f, 100f),
            ).physicsBody!!
        spun.applyAngularImpulse(1f)
        assertNear(32228.6f, spun.angularVelocity) // 1 / the inertia about the circle's own center
    }

    @Test
    fun `a force off the center of mass turns the body for one step`() {
        val scene = scene()
        val b =
            body(scene, SKPhysicsBody.circleOfRadius(10f), Vector2(200f, 200f)).physicsBody!!.apply {
                linearDamping = 0f
                angularDamping = 0f
            }
        b.applyForce(Vector2(0f, 10f), at = Vector2(210f, 200f))
        simulatePhysics(scene, frame)
        assertNear(0.2f, b.angularVelocity / b.velocity.y) // Apple: 0.2
        val after = b.angularVelocity
        simulatePhysics(scene, frame)
        assertNear(after, b.angularVelocity)
    }

    @Test
    fun `a body turns about its center of mass, swinging an off-center node around it`() {
        val scene = scene()
        val node = body(scene, SKPhysicsBody.circleOfRadius(10f, center = Vector2(20f, 0f)), Vector2(300f, 100f))
        node.physicsBody!!.apply {
            angularDamping = 0f
            angularVelocity = 2f
        }
        repeat(30) { simulatePhysics(scene, frame) }
        val angle = node.zRotation
        assertNear(1f, angle, 1e-3f)
        val center = node.position + Vector2(20f * cos(angle), 20f * sin(angle))
        assertNear(320f, center.x)
        assertNear(100f, center.y)
    }

    @Test
    fun `a body turns at most a quarter turn a substep`() {
        val scene = scene()
        val b = body(scene, SKPhysicsBody.circleOfRadius(10f), Vector2(200f, 200f)).physicsBody!!
        b.applyAngularImpulse(1f)
        simulatePhysics(scene, frame)
        assertNear((PI * 60).toFloat(), b.angularVelocity) // Apple: 188.4956, π/2 per 1/120 s
    }

    @Test
    fun `a frame runs as substeps of about a 120th of a second`() {
        assertEquals(2, physicsSubsteps(1f / 60f))
        assertEquals(6, physicsSubsteps(1f / 20f))
        assertEquals(1, physicsSubsteps(1f / 90f))
        assertEquals(1, physicsSubsteps(0.001f))

        // Apple, at 20 frames a second: a body falling at 61.25 points/s from y 298.4687 is at
        // 293.2625, falling at 134.75, a frame later -- six 1/120-second steps.
        val scene = scene(Vector2(0f, -9.8f))
        val node = body(scene, SKPhysicsBody.circleOfRadius(5f).apply { linearDamping = 0f }, Vector2(200f, 298.4687f))
        node.physicsBody!!.velocity = Vector2(0f, -61.25f)
        simulatePhysics(scene, 0.05.seconds)
        assertNear(-134.75f, node.physicsBody!!.velocity.y)
        assertNear(293.2625f, node.position.y)
    }

    @Test
    fun `a body moves at most 300 points a substep`() {
        val scene = scene()
        val fast =
            body(
                scene,
                SKPhysicsBody.circleOfRadius(5f).apply { linearDamping = 0f },
                Vector2(200f, 200f),
            ).physicsBody!!
        fast.velocity = Vector2(100000f, 0f)
        val slower =
            body(
                scene,
                SKPhysicsBody.circleOfRadius(5f).apply {
                    linearDamping = 0f
                },
                Vector2(200f, 100f),
            ).physicsBody!!
        slower.velocity = Vector2(30000f, 0f)
        simulatePhysics(scene, frame)
        assertNear(36000f, fast.velocity.x) // Apple: 36000
        assertNear(30000f, slower.velocity.x)
    }

    @Test
    fun `restitution applies at any speed`() {
        for (speed in listOf(50f, 140f, 400f)) {
            val scene = scene()
            body(scene, SKPhysicsBody.edgeFrom(Vector2(300f, 0f), Vector2(300f, 400f)), Vector2.Zero)
            val ball =
                body(
                    scene,
                    SKPhysicsBody.circleOfRadius(5f).apply {
                        restitution = 1f
                        linearDamping = 0f
                        friction = 0f
                    },
                    Vector2(300f - 6f - speed * 0.05f, 30f),
                ).physicsBody!!
            ball.velocity = Vector2(speed, 0f)
            repeat(30) { simulatePhysics(scene, frame) }
            assertNear(-speed, ball.velocity.x, 0.01f) // Apple: straight back at every speed
        }
    }

    @Test
    fun `a box settles the same way at any frame rate`() {
        for (framesPerSecond in listOf(60, 20, 5)) {
            val scene = scene(Vector2(0f, -9.8f))
            body(scene, SKPhysicsBody.edgeFrom(Vector2(0f, 50f), Vector2(400f, 50f)), Vector2.Zero)
            val box = body(scene, SKPhysicsBody.rectangleOf(Vector2(40f, 40f)), Vector2(100f, 120f))
            box.zRotation = 0.3f
            repeat(framesPerSecond * 2) { simulatePhysics(scene, (1.0 / framesPerSecond).seconds) }
            // Apple, at 60 and at 20 frames a second alike: flat, at rest, at y 72.27.
            assertTrue(abs(box.zRotation) < 0.01f, "$framesPerSecond fps: rotation ${box.zRotation}")
            assertTrue(box.position.y in 69f..72.5f, "$framesPerSecond fps: y ${box.position.y}")
        }
    }

    @Test
    fun `a box landing on its corner turns flat`() {
        val scene = scene(Vector2(0f, -9.8f))
        body(scene, SKPhysicsBody.edgeFrom(Vector2(0f, 50f), Vector2(400f, 50f)), Vector2.Zero)
        val box = body(scene, SKPhysicsBody.rectangleOf(Vector2(40f, 40f)), Vector2(100f, 120f))
        box.zRotation = 0.3f
        repeat(120) { simulatePhysics(scene, frame) }
        // Apple: flat (rotation 0.00008) and at rest, its center 20-odd points above the floor.
        assertTrue(abs(box.zRotation) < 0.01f, "rotation ${box.zRotation}")
        assertTrue(abs(box.physicsBody!!.angularVelocity) < 0.05f, "ω ${box.physicsBody!!.angularVelocity}")
        assertTrue(box.position.y in 69f..72.5f, "y ${box.position.y}")
    }

    @Test
    fun `a ball sliding along the floor starts rolling`() {
        val scene = scene(Vector2(0f, -9.8f))
        body(scene, SKPhysicsBody.edgeFrom(Vector2(0f, 50f), Vector2(400f, 50f)), Vector2.Zero)
        val ball = body(scene, SKPhysicsBody.circleOfRadius(10f), Vector2(250f, 60f)).physicsBody!!
        ball.velocity = Vector2(100f, 0f)
        repeat(18) { simulatePhysics(scene, frame) }
        // Apple, 0.3 s in: v 63.5, ω -6.46 — rolling (ω ≈ -v/r), at about two thirds of the speed.
        assertTrue(ball.velocity.x in 60f..68f, "v ${ball.velocity.x}")
        assertNear(-ball.velocity.x / 10f, ball.angularVelocity, 0.03f)
    }

    @Test
    fun `a box resting flat on an edge touches it at both corners`() {
        val box =
            SKWorldShape.Polygon(
                listOf(Vector2(-20f, -0.5f), Vector2(20f, -0.5f), Vector2(20f, 39.5f), Vector2(-20f, 39.5f)),
            )
        val floor = SKWorldShape.EdgeChain(listOf(Vector2(-100f, 0f), Vector2(100f, 0f)), closed = false)
        val manifold = narrowPhase(box, floor)!!
        assertNear(0f, manifold.normal.x)
        assertNear(-1f, manifold.normal.y)
        assertNear(0.5f, manifold.penetration)
        val xs = manifold.points.map { it.x }.sorted()
        assertNear(-20f, xs.first())
        assertNear(20f, xs.last())
        assertNear(0f, manifold.point.x)
    }
}
