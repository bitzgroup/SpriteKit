package jp.co.bitz.spritekit

import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SKPhysicsShapeTest {
    private fun assertEquals(
        expected: Float,
        actual: Float,
        absoluteTolerance: Float = 1e-4f,
    ) {
        assertTrue(abs(expected - actual) <= absoluteTolerance, "expected $expected, was $actual")
    }

    @Test
    fun `a circle's area matches pi r squared`() {
        val circle = SKPhysicsShape.Circle(radius = 2f)

        assertEquals(PI.toFloat() * 4f, circle.area())
    }

    @Test
    fun `a circle's mass is density times area, and its inertia matches the solid-disk formula`() {
        val circle = SKPhysicsShape.Circle(radius = 2f)
        val density = 3f

        val properties = circle.massProperties(density)

        val expectedMass = density * PI.toFloat() * 4f
        assertEquals(expectedMass, properties.mass)
        assertEquals(expectedMass * 4f / 2f, properties.inertia) // mass * r^2 / 2
    }

    @Test
    fun `an off-center circle's inertia is shifted by the parallel axis theorem`() {
        val onCenter = SKPhysicsShape.Circle(radius = 1f)
        val offCenter = SKPhysicsShape.Circle(radius = 1f, center = Vector2(3f, 4f)) // distance 5 from origin
        val density = 1f

        val onCenterInertia = onCenter.massProperties(density).inertia
        val offCenterInertia = offCenter.massProperties(density).inertia
        val mass = offCenter.massProperties(density).mass

        assertEquals(onCenterInertia + mass * 25f, offCenterInertia) // + mass * d^2
    }

    @Test
    fun `a square's area matches width times height`() {
        val square =
            SKPhysicsShape.Polygon(
                listOf(Vector2(-1f, -1f), Vector2(1f, -1f), Vector2(1f, 1f), Vector2(-1f, 1f)),
            )

        assertEquals(4f, square.area())
    }

    @Test
    fun `a square's inertia matches the known analytic formula mass times width squared plus height squared`() {
        val width = 4f
        val height = 2f
        val square =
            SKPhysicsShape.Polygon(
                listOf(
                    Vector2(-width / 2f, -height / 2f),
                    Vector2(width / 2f, -height / 2f),
                    Vector2(width / 2f, height / 2f),
                    Vector2(-width / 2f, height / 2f),
                ),
            )
        val density = 5f

        val properties = square.massProperties(density)

        val expectedMass = density * width * height
        assertEquals(expectedMass, properties.mass)
        assertEquals(expectedMass * (width * width + height * height) / 12f, properties.inertia)
    }

    @Test
    fun `an edge chain has no area, mass, or inertia`() {
        val chain = SKPhysicsShape.EdgeChain(listOf(Vector2(0f, 0f), Vector2(10f, 0f)), closed = false)

        assertEquals(0f, chain.area())
        val properties = chain.massProperties(density = 5f)
        assertEquals(0f, properties.mass)
        assertEquals(0f, properties.inertia)
    }
}
