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
        val circle = SKPhysicsShape.Circle(radius = 300f) // 2 m

        assertEquals(PI.toFloat() * 4f, circle.area(), 1e-5f)
    }

    @Test
    fun `a circle's mass is density times area, and its inertia matches the solid-disk formula`() {
        val circle = SKPhysicsShape.Circle(radius = 300f) // 2 m
        val density = 3f

        val properties = circle.massProperties(density)

        val expectedMass = density * PI.toFloat() * 4f
        assertEquals(expectedMass, properties.mass, 1e-4f)
        assertEquals(expectedMass * 4f / 2f, properties.inertia, 1e-4f) // mass * r^2 / 2, r in meters
    }

    @Test
    fun `an off-center circle's inertia is about its own center, which is its center of mass`() {
        val onCenter = SKPhysicsShape.Circle(radius = 150f)
        val offCenter = SKPhysicsShape.Circle(radius = 150f, center = Vector2(450f, 600f))

        assertEquals(onCenter.massProperties(1f).inertia, offCenter.massProperties(1f).inertia, 1e-5f)
        assertEquals(Vector2(450f, 600f), offCenter.massProperties(1f).centroid)
    }

    @Test
    fun `an off-center polygon's inertia is about its centroid`() {
        fun square(at: Vector2) =
            SKPhysicsShape.Polygon(
                listOf(Vector2(-75f, -75f), Vector2(75f, -75f), Vector2(75f, 75f), Vector2(-75f, 75f)).map { it + at },
            )
        val centered = square(Vector2.Zero).massProperties(1f)
        val moved = square(Vector2(300f, -150f)).massProperties(1f)

        assertEquals(centered.inertia, moved.inertia, 1e-4f)
        assertEquals(300f, moved.centroid.x, 1e-2f)
        assertEquals(-150f, moved.centroid.y, 1e-2f)
    }

    @Test
    fun `a square's area matches width times height`() {
        val square =
            SKPhysicsShape.Polygon(
                listOf(Vector2(-150f, -150f), Vector2(150f, -150f), Vector2(150f, 150f), Vector2(-150f, 150f)),
            )

        assertEquals(4f, square.area(), 1e-5f) // 2 m × 2 m
    }

    @Test
    fun `a square's inertia matches the known analytic formula mass times width squared plus height squared`() {
        // In meters (150 points each), as Apple measures mass and inertia.
        val width = 4f
        val height = 2f
        val points = 150f
        val square =
            SKPhysicsShape.Polygon(
                listOf(
                    Vector2(-width / 2f, -height / 2f) * points,
                    Vector2(width / 2f, -height / 2f) * points,
                    Vector2(width / 2f, height / 2f) * points,
                    Vector2(-width / 2f, height / 2f) * points,
                ),
            )
        val density = 5f

        val properties = square.massProperties(density)

        val expectedMass = density * width * height
        assertEquals(expectedMass, properties.mass, 1e-4f)
        assertEquals(expectedMass * (width * width + height * height) / 12f, properties.inertia, 1e-3f)
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
