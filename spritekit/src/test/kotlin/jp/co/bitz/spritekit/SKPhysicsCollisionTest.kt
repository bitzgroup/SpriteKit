package jp.co.bitz.spritekit

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SKPhysicsCollisionTest {
    private fun assertEquals(
        expected: Float,
        actual: Float,
        absoluteTolerance: Float = 1e-4f,
    ) {
        assertTrue(abs(expected - actual) <= absoluteTolerance, "expected $expected, was $actual")
    }

    private fun square(
        halfExtent: Float,
        center: Vector2 = Vector2.Zero,
    ): SKWorldShape.Polygon =
        SKWorldShape.Polygon(
            listOf(
                Vector2(center.x - halfExtent, center.y - halfExtent),
                Vector2(center.x + halfExtent, center.y - halfExtent),
                Vector2(center.x + halfExtent, center.y + halfExtent),
                Vector2(center.x - halfExtent, center.y + halfExtent),
            ),
        )

    @Test
    fun `broadPhaseOverlap is true only when the two shapes' bounding boxes overlap`() {
        val near = SKWorldShape.Circle(Vector2(1f, 0f), 1f)
        val far = SKWorldShape.Circle(Vector2(100f, 0f), 1f)
        val origin = SKWorldShape.Circle(Vector2.Zero, 1f)

        assertTrue(broadPhaseOverlap(origin, near))
        assertFalse(broadPhaseOverlap(origin, far))
    }

    @Test
    fun `two circles far apart don't collide`() {
        val a = SKWorldShape.Circle(Vector2(0f, 0f), 1f)
        val b = SKWorldShape.Circle(Vector2(10f, 0f), 1f)

        assertNull(narrowPhase(a, b))
    }

    @Test
    fun `two overlapping circles produce a manifold pointing from the first towards the second`() {
        val a = SKWorldShape.Circle(Vector2(0f, 0f), 3f)
        val b = SKWorldShape.Circle(Vector2(4f, 0f), 2f) // centers 4 apart, radii sum 5 -> penetration 1

        val manifold = assertNotNull(narrowPhase(a, b))

        assertEquals(1f, manifold.penetration)
        assertEquals(Vector2(1f, 0f), manifold.normal)
    }

    @Test
    fun `circle vs circle is symmetric -- swapping the arguments flips the normal`() {
        val a = SKWorldShape.Circle(Vector2(0f, 0f), 3f)
        val b = SKWorldShape.Circle(Vector2(4f, 0f), 2f)

        val forward = assertNotNull(narrowPhase(a, b))
        val backward = assertNotNull(narrowPhase(b, a))

        assertEquals(forward.penetration, backward.penetration)
        assertEquals(-forward.normal.x, backward.normal.x)
    }

    @Test
    fun `a circle outside a square doesn't collide`() {
        val circle = SKWorldShape.Circle(Vector2(10f, 0f), 1f)
        val poly = square(2f)

        assertNull(narrowPhase(circle, poly))
    }

    @Test
    fun `a circle touching a square's edge from outside collides with a normal pointing towards the square`() {
        val poly = square(2f) // spans (-2,-2) to (2,2)
        val circle = SKWorldShape.Circle(Vector2(3f, 0f), 2f) // overlaps the right edge by 1

        val manifold = assertNotNull(narrowPhase(circle, poly))

        assertEquals(1f, manifold.penetration)
        assertEquals(Vector2(-1f, 0f), manifold.normal) // the square is to the circle's left
    }

    @Test
    fun `a circle whose center is inside a square is pushed out along the nearest edge`() {
        val poly = square(2f) // spans (-2,-2) to (2,2)
        val circle = SKWorldShape.Circle(Vector2(1.9f, 0f), 1f) // center just inside, near the right edge

        val manifold = assertNotNull(narrowPhase(circle, poly))

        // Resolution moves the circle along -normal, so this normal pushes it out to the right.
        assertEquals(Vector2(-1f, 0f), manifold.normal)
        assertTrue(manifold.penetration > 0f)
    }

    @Test
    fun `two overlapping squares produce a manifold along the shallowest penetration axis`() {
        val a = square(2f) // spans (-2,-2) to (2,2)
        val b = square(2f, center = Vector2(3f, 0f)) // spans (1,-2) to (5,2) -- overlap of 1 on the x axis

        val manifold = assertNotNull(narrowPhase(a, b))

        assertEquals(1f, manifold.penetration)
        assertEquals(Vector2(1f, 0f), manifold.normal)
    }

    @Test
    fun `two squares far apart don't collide`() {
        val a = square(1f)
        val b = square(1f, center = Vector2(100f, 0f))

        assertNull(narrowPhase(a, b))
    }

    @Test
    fun `a circle resting on an edge chain floor collides with a vertical normal`() {
        val floor = SKWorldShape.EdgeChain(listOf(Vector2(-10f, 0f), Vector2(10f, 0f)), closed = false)
        val circle = SKWorldShape.Circle(Vector2(0f, 0.5f), 1f) // overlaps the floor by 0.5

        val manifold = assertNotNull(narrowPhase(circle, floor))

        assertEquals(0.5f, manifold.penetration)
        assertEquals(1f, abs(manifold.normal.y)) // straight up or down, off the horizontal floor
    }

    @Test
    fun `a circle far from every segment of an edge chain doesn't collide`() {
        val floor = SKWorldShape.EdgeChain(listOf(Vector2(-10f, 0f), Vector2(10f, 0f)), closed = false)
        val circle = SKWorldShape.Circle(Vector2(0f, 100f), 1f)

        assertNull(narrowPhase(circle, floor))
    }

    @Test
    fun `a square overlapping an edge chain's segment collides`() {
        val floor = SKWorldShape.EdgeChain(listOf(Vector2(-10f, 0f), Vector2(10f, 0f)), closed = false)
        val poly = square(1f, center = Vector2(0f, 0.5f)) // spans y -0.5..1.5, overlaps the floor by 0.5

        val manifold = assertNotNull(narrowPhase(poly, floor))

        assertTrue(manifold.penetration > 0f)
    }

    @Test
    fun `two edge chains never collide with each other`() {
        val a = SKWorldShape.EdgeChain(listOf(Vector2(-10f, 0f), Vector2(10f, 0f)), closed = false)
        val b = SKWorldShape.EdgeChain(listOf(Vector2(0f, -5f), Vector2(0f, 5f)), closed = false)

        assertNull(narrowPhase(a, b))
    }
}
