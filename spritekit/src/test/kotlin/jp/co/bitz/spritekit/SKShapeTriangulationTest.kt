package jp.co.bitz.spritekit

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SKShapeTriangulationTest {
    @Test
    fun `triangulateFill on fewer than 3 points is empty`() {
        assertTrue(triangulateFill(listOf(Vector2.Zero, Vector2(1f, 0f))).isEmpty())
    }

    @Test
    fun `triangulateFill on a triangle returns exactly that triangle`() {
        val triangle = listOf(Vector2(0f, 0f), Vector2(4f, 0f), Vector2(0f, 3f))

        val result = triangulateFill(triangle)

        assertEquals(3, result.size)
        assertEquals(6f, totalArea(result), absoluteTolerance = 1e-4f)
    }

    @Test
    fun `triangulateFill on a CCW square covers its full area with two triangles`() {
        val square = listOf(Vector2(0f, 0f), Vector2(10f, 0f), Vector2(10f, 10f), Vector2(0f, 10f))

        val result = triangulateFill(square)

        assertEquals(6, result.size) // 2 triangles
        assertEquals(100f, totalArea(result), absoluteTolerance = 1e-3f)
    }

    @Test
    fun `triangulateFill on a CW square (opposite winding) still covers its full area`() {
        val square = listOf(Vector2(0f, 0f), Vector2(0f, 10f), Vector2(10f, 10f), Vector2(10f, 0f))

        val result = triangulateFill(square)

        assertEquals(100f, totalArea(result), absoluteTolerance = 1e-3f)
    }

    @Test
    fun `triangulateFill on a concave L-shape covers its full area with no degenerate triangles`() {
        // An L-shape: a 10x10 square with a 5x5 notch cut out of its top-right corner.
        val lShape =
            listOf(
                Vector2(0f, 0f),
                Vector2(10f, 0f),
                Vector2(10f, 5f),
                Vector2(5f, 5f),
                Vector2(5f, 10f),
                Vector2(0f, 10f),
            )

        val result = triangulateFill(lShape)

        assertEquals(12, result.size) // 4 triangles
        assertEquals(75f, totalArea(result), absoluteTolerance = 1e-3f) // 100 - 25
        for (i in result.indices step 3) {
            assertTrue(
                triangleArea(result[i], result[i + 1], result[i + 2]) > 1e-4f,
                "triangle at $i must not be degenerate",
            )
        }
    }

    @Test
    fun `triangulateStroke on fewer than 2 points is empty`() {
        assertTrue(triangulateStroke(listOf(Vector2.Zero), lineWidth = 2f, closed = false).isEmpty())
    }

    @Test
    fun `triangulateStroke with a non-positive lineWidth is empty`() {
        val line = listOf(Vector2(0f, 0f), Vector2(10f, 0f))

        assertTrue(triangulateStroke(line, lineWidth = 0f, closed = false).isEmpty())
    }

    @Test
    fun `triangulateStroke on a single open horizontal segment forms the expected ribbon`() {
        val line = listOf(Vector2(0f, 0f), Vector2(10f, 0f))

        val result = triangulateStroke(line, lineWidth = 2f, closed = false)

        assertEquals(
            listOf(
                Vector2(0f, 1f),
                Vector2(0f, -1f),
                Vector2(10f, 1f),
                Vector2(0f, -1f),
                Vector2(10f, -1f),
                Vector2(10f, 1f),
            ),
            result,
        )
    }

    @Test
    fun `triangulateStroke on a closed triangle strokes every edge including the closing one`() {
        val triangle = listOf(Vector2(0f, 0f), Vector2(10f, 0f), Vector2(0f, 10f))

        val open = triangulateStroke(triangle, lineWidth = 1f, closed = false)
        val closed = triangulateStroke(triangle, lineWidth = 1f, closed = true)

        assertEquals(12, open.size) // 2 segments (the closing edge back to the start is omitted)
        assertEquals(18, closed.size) // 3 segments, all edges stroked including the closing one
    }

    private fun totalArea(triangles: List<Vector2>): Float {
        var sum = 0f
        for (i in triangles.indices step 3) {
            sum += triangleArea(triangles[i], triangles[i + 1], triangles[i + 2])
        }
        return sum
    }

    private fun triangleArea(
        a: Vector2,
        b: Vector2,
        c: Vector2,
    ): Float = abs((b.x - a.x) * (c.y - a.y) - (c.x - a.x) * (b.y - a.y)) / 2f

    private fun assertEquals(
        expected: Float,
        actual: Float,
        absoluteTolerance: Float,
    ) {
        assertTrue(abs(expected - actual) <= absoluteTolerance, "expected $expected, was $actual")
    }
}
