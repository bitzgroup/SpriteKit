package jp.co.bitz.spritekit

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** Mirrors the tic-tac-toe sample's `SKPhysicsCompoundParityTests` probes. */
class SKPhysicsCompoundTest {
    private val frame = (1.0 / 60.0).seconds

    private fun assertNear(
        expected: Float,
        actual: Float,
        tolerance: Float = 1e-3f,
    ) = assertTrue(abs(expected - actual) <= tolerance * maxOf(1f, abs(expected)), "expected $expected, was $actual")

    private fun scene(gravity: Vector2 = Vector2.Zero) =
        SKScene(size = Vector2(400f, 400f)).apply { physicsWorld.gravity = gravity }

    private fun add(
        scene: SKScene,
        body: SKPhysicsBody,
        at: Vector2,
    ): SKNode =
        SKNode().apply {
            position = at
            physicsBody = body
            scene.addChild(this)
        }

    /** Where a ball of radius 5 dropped at [x] from y 250 ends up after 1.5 s over [floor] at (200, 100). */
    private fun drop(
        floor: SKPhysicsBody,
        x: Float,
    ): Vector2 {
        val scene = scene(Vector2(0f, -9.8f))
        add(scene, floor.apply { isDynamic = false }, Vector2(200f, 100f))
        val ball = add(scene, SKPhysicsBody.circleOfRadius(5f), Vector2(x, 250f))
        repeat(90) { simulatePhysics(scene, frame) }
        return ball.position
    }

    private val ell =
        listOf(
            Vector2(-60f, 0f),
            Vector2(60f, 0f),
            Vector2(60f, 20f),
            Vector2(-20f, 20f),
            Vector2(-20f, 60f),
            Vector2(-60f, 60f),
        )

    private fun bar(x: Float) = SKPhysicsBody.rectangleOf(Vector2(40f, 10f), center = Vector2(x, 0f))

    @Test
    fun `a compound's area sums its parts and it takes the first body's density, restitution and friction`() {
        val a =
            SKPhysicsBody.circleOfRadius(10f, center = Vector2(-20f, 0f)).apply {
                density = 2f
                restitution = 0.9f
                friction = 0.3f
                categoryBitMask = 4
                linearDamping = 0.5f
            }
        val b = SKPhysicsBody.rectangleOf(Vector2(20f, 20f), center = Vector2(20f, 0f)).apply { isDynamic = false }
        val compound = SKPhysicsBody.bodies(listOf(a, b))
        // Apple: area 0.0317404, mass 0.0634808, density 2, restitution 0.9; the rest defaults.
        assertNear(0.0317404f, compound.area)
        assertNear(0.0634808f, compound.mass)
        assertEquals(2f, compound.density)
        assertEquals(0.9f, compound.restitution)
        assertEquals(0.3f, compound.friction)
        assertEquals(0.1f, compound.linearDamping)
        assertEquals(-1, compound.categoryBitMask)
        assertTrue(compound.isDynamic)
        compound.density = 3f
        assertNear(0.0952212f, compound.mass)
    }

    @Test
    fun `edges in a compound add no area, a nested compound collides but adds no mass, and none has density 0`() {
        val withEdge =
            SKPhysicsBody.bodies(
                listOf(SKPhysicsBody.circleOfRadius(10f), SKPhysicsBody.edgeFrom(Vector2.Zero, Vector2(50f, 0f))),
            )
        assertNear(0.0139626f, withEdge.area)
        val nested =
            SKPhysicsBody.bodies(
                listOf(
                    SKPhysicsBody.circleOfRadius(5f, center = Vector2(30f, 0f)),
                    SKPhysicsBody.bodies(listOf(SKPhysicsBody.circleOfRadius(10f))),
                ),
            )
        assertNear(0.0034913f, nested.area) // Apple: the circle of radius 5 alone
        assertEquals(0f, SKPhysicsBody.bodies(emptyList()).density)

        val nestedFloor =
            SKPhysicsBody.bodies(
                listOf(
                    SKPhysicsBody.bodies(
                        listOf(SKPhysicsBody.rectangleOf(Vector2(60f, 10f), center = Vector2(50f, 0f))),
                    ),
                    SKPhysicsBody.circleOfRadius(5f, center = Vector2(-80f, 0f)),
                ),
            )
        assertNear(111.3f, drop(nestedFloor, 250f).y, 0.02f) // Apple: 111.31, resting on the nested part
    }

    @Test
    fun `every part collides and the gaps between them don't`() {
        fun bell() = SKPhysicsBody.bodies(listOf(bar(-60f), bar(60f)))
        assertNear(109.87f, drop(bell(), 140f).y, 0.02f) // Apple: 109.87
        assertNear(109.87f, drop(bell(), 260f).y, 0.02f)
        assertTrue(drop(bell(), 200f).y < 0f) // Apple: falls through
        val circleAndEdge =
            SKPhysicsBody.bodies(
                listOf(
                    SKPhysicsBody.circleOfRadius(10f, center = Vector2(-80f, 0f)),
                    SKPhysicsBody.edgeFrom(Vector2.Zero, Vector2(100f, 0f)),
                ),
            )
        assertNear(106.34f, drop(circleAndEdge, 250f).y, 0.02f) // Apple: 106.34, on the edge part
    }

    @Test
    fun `a compound turns about its parts' shared center of mass`() {
        val scene = scene()
        val dumbbell =
            SKPhysicsBody.bodies(
                listOf(
                    SKPhysicsBody.circleOfRadius(10f, center = Vector2(-20f, 0f)),
                    SKPhysicsBody.circleOfRadius(10f, center = Vector2(20f, 0f)),
                ),
            )
        add(scene, dumbbell, Vector2(200f, 200f))
        dumbbell.applyAngularImpulse(0.01f)
        assertNear(17.904924f, dumbbell.angularVelocity) // Apple
        dumbbell.applyImpulse(Vector2(0f, 1f), at = Vector2(220f, 200f))
        assertNear(35.809856f, dumbbell.velocity.y)
        assertNear(19.496473f, dumbbell.angularVelocity)

        val offCenter = SKPhysicsBody.bodies(listOf(SKPhysicsBody.circleOfRadius(10f, center = Vector2(30f, 0f))))
        add(scene, offCenter, Vector2(100f, 100f))
        offCenter.applyAngularImpulse(0.01f)
        assertNear(322.28888f, offCenter.angularVelocity) // Apple: about the circle's own center
    }

    @Test
    fun `point queries find a compound by its parts`() {
        val scene = scene()
        val body =
            SKPhysicsBody.bodies(
                listOf(
                    SKPhysicsBody.circleOfRadius(10f, center = Vector2(-30f, 0f)),
                    SKPhysicsBody.circleOfRadius(10f, center = Vector2(30f, 0f)),
                ),
            )
        add(scene, body, Vector2(200f, 200f))
        assertEquals(body, scene.physicsWorld.body(at = Vector2(230f, 200f)))
        assertNull(scene.physicsWorld.body(at = Vector2(200f, 200f)))
    }

    @Test
    fun `a concave polygon keeps its full area and collides in its notch`() {
        val l =
            listOf(
                Vector2(0f, 0f),
                Vector2(30f, 0f),
                Vector2(30f, 10f),
                Vector2(10f, 10f),
                Vector2(10f, 30f),
                Vector2(0f, 30f),
            )
        assertNear(0.0222222f, SKPhysicsBody.polygonFrom(l).area) // Apple: 500 square points
        assertNear(0.0222222f, SKPhysicsBody.polygonFrom(l.reversed()).area)
        assertNear(126.02f, drop(SKPhysicsBody.polygonFrom(ell), 230f).y, 0.02f) // Apple: 126.02
        assertNear(166.11f, drop(SKPhysicsBody.polygonFrom(ell), 160f).y, 0.02f) // Apple: 166.11
    }

    @Test
    fun `a concave polygon splits into few convex pieces and a ray enters it once`() {
        val shape = polygonShape(ell)
        assertTrue(shape is SKPhysicsShape.Compound && shape.parts.size == 2, "$shape")
        val scene = scene()
        add(scene, SKPhysicsBody.polygonFrom(ell), Vector2(200f, 100f))
        val hits = mutableListOf<Vector2>()
        scene.physicsWorld.enumerateBodies(
            alongRayStart = Vector2(100f, 110f),
            end = Vector2(300f, 110f),
        ) { _, point, _ ->
            hits += point
        }
        assertEquals(1, hits.size, "$hits")
        assertNear(140f, hits[0].x)
    }

    @Test
    fun `a full circle's flattened outline makes a circle, other curves and straight polygons don't`() {
        fun ring(
            count: Int,
            radius: Float,
            center: Vector2 = Vector2.Zero,
        ) = List(count) {
            val angle = 2 * PI * it / count
            center + Vector2((cos(angle) * radius).toFloat(), (sin(angle) * radius).toFloat())
        }
        val circle = assertNotNull(circleThrough(ring(16, 10f, Vector2(30f, 0f))))
        assertNear(10f, circle.radius, 0.01f)
        assertNear(30f, circle.center.x, 0.01f)
        val body = SKPhysicsBody(pathPolygonShape(ring(16, 10f)))
        assertNear(0.0139626f, body.area) // Apple: exactly a circle's
        assertNull(circleThrough(ring(12, 10f))) // a 12-gon of straight lines: Apple's area is 300
        assertNear(0.0133333f, SKPhysicsBody(pathPolygonShape(ring(12, 10f))).area)
        val halfDisc = ring(32, 10f).filter { it.y >= -1e-3f }
        assertNull(circleThrough(halfDisc))
    }
}
