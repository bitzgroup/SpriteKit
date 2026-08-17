package jp.co.bitz.spritekit

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SKPhysicsBodyTest {
    private fun assertEquals(
        expected: Float,
        actual: Float,
        absoluteTolerance: Float = 1e-4f,
    ) {
        assertTrue(abs(expected - actual) <= absoluteTolerance, "expected $expected, was $actual")
    }

    @Test
    fun `mass defaults from density and the body's shape area`() {
        val body = SKPhysicsBody.rectangleOf(Vector2(2f, 3f)) // area 6, default density 1

        assertEquals(6f, body.mass)
    }

    @Test
    fun `setting mass back-computes density rather than being stored directly`() {
        val body = SKPhysicsBody.rectangleOf(Vector2(2f, 3f)) // area 6

        body.mass = 12f

        assertEquals(2f, body.density)
        assertEquals(12f, body.mass)
    }

    @Test
    fun `setting mass on a zero-area edge body is a no-op`() {
        val body = SKPhysicsBody.edgeFrom(Vector2(0f, 0f), Vector2(10f, 0f))

        body.mass = 50f

        assertEquals(0f, body.mass)
    }

    @Test
    fun `circleOfRadius and rectangleOf produce shapes with the expected area`() {
        val circle = SKPhysicsBody.circleOfRadius(2f)
        val rectangle = SKPhysicsBody.rectangleOf(Vector2(4f, 5f))

        assertEquals(Math.PI.toFloat() * 4f, circle.shape.area())
        assertEquals(20f, rectangle.shape.area())
    }

    @Test
    fun `edgeLoopFrom and edgeFrom bodies default to non-dynamic`() {
        val loop = SKPhysicsBody.edgeLoopFrom(Rect(-5f, -5f, 5f, 5f))
        val edge = SKPhysicsBody.edgeFrom(Vector2(0f, 0f), Vector2(10f, 0f))

        assertFalse(loop.isDynamic)
        assertFalse(edge.isDynamic)
    }

    @Test
    fun `a dynamic body has a nonzero inverse mass, a non-dynamic one has zero`() {
        val dynamic = SKPhysicsBody.circleOfRadius(1f)
        val static = SKPhysicsBody.edgeFrom(Vector2(0f, 0f), Vector2(1f, 0f))

        assertTrue(dynamic.inverseMass > 0f)
        assertEquals(0f, static.inverseMass)
    }

    @Test
    fun `a body with rotation disallowed has zero inverse inertia`() {
        val body = SKPhysicsBody.circleOfRadius(1f).apply { allowsRotation = false }

        assertEquals(0f, body.inverseInertia)
    }

    @Test
    fun `applyImpulse changes velocity by impulse over mass`() {
        val body = SKPhysicsBody.circleOfRadius(1f) // mass = density(1) * area(pi)

        body.applyImpulse(Vector2(body.mass * 2f, 0f))

        assertEquals(2f, body.velocity.x)
        assertEquals(0f, body.velocity.y)
    }

    @Test
    fun `applyAngularImpulse changes angular velocity by impulse over inertia`() {
        val body = SKPhysicsBody.circleOfRadius(1f)

        body.applyAngularImpulse(body.inertia * 3f)

        assertEquals(3f, body.angularVelocity)
    }

    @Test
    fun `applyForce and applyTorque accumulate until cleared`() {
        val body = SKPhysicsBody.circleOfRadius(1f)

        body.applyForce(Vector2(1f, 2f))
        body.applyForce(Vector2(3f, 4f))
        body.applyTorque(5f)
        body.applyTorque(1f)

        assertEquals(Vector2(4f, 6f), body.forceAccumulator)
        assertEquals(6f, body.torqueAccumulator)

        body.clearAccumulators()

        assertEquals(Vector2.Zero, body.forceAccumulator)
        assertEquals(0f, body.torqueAccumulator)
    }
}
