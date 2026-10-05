package jp.co.bitz.spritekit

import kotlin.math.PI
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

        // 2 segments (the closing edge back to the start is omitted) + a miter join at the one
        // interior corner (bevel triangle + miter tip); butt caps add nothing.
        assertEquals(12 + 6, open.size)
        // 3 segments, all edges stroked including the closing one + a miter join at every corner.
        assertEquals(18 + 18, closed.size)
    }

    @Test
    fun `a square cap extends each end of an open stroke by half the line width`() {
        val line = listOf(Vector2(0f, 0f), Vector2(10f, 0f))

        val result = triangulateStroke(line, lineWidth = 2f, closed = false, lineCap = LineCap.Square)

        assertEquals(-1f, result.minOf { it.x })
        assertEquals(11f, result.maxOf { it.x })
        assertEquals(2f * 12f, totalArea(result), 0.001f) // a 12 x 2 rectangle
    }

    @Test
    fun `a round cap adds a half disc past each end of an open stroke`() {
        val line = listOf(Vector2(0f, 0f), Vector2(10f, 0f))

        val result = triangulateStroke(line, lineWidth = 2f, closed = false, lineCap = LineCap.Round)

        assertEquals(-1f, result.minOf { it.x }, 0.001f)
        assertEquals(11f, result.maxOf { it.x }, 0.001f)
        // The 10 x 2 ribbon plus two half discs of radius 1 (a polygonal approximation, slightly smaller).
        val area = totalArea(result)
        assertTrue(area > 20f + PI.toFloat() * 0.95f && area <= 20f + PI.toFloat(), "area was $area")
    }

    @Test
    fun `a butt cap is the default and adds nothing past the endpoints`() {
        val line = listOf(Vector2(0f, 0f), Vector2(10f, 0f))

        val result = triangulateStroke(line, lineWidth = 2f, closed = false)

        assertEquals(0f, result.minOf { it.x })
        assertEquals(10f, result.maxOf { it.x })
    }

    @Test
    fun `a miter join extends the outer corner of a right angle to a sharp point`() {
        val corner = listOf(Vector2(0f, 0f), Vector2(10f, 0f), Vector2(10f, 10f))

        val result = triangulateStroke(corner, lineWidth = 2f, closed = false)

        // The outer corner of the turn at (10, 0) is (11, -1).
        assertTrue(result.any { abs(it.x - 11f) < 0.001f && abs(it.y + 1f) < 0.001f })
    }

    @Test
    fun `a bevel join cuts the outer corner off instead`() {
        val corner = listOf(Vector2(0f, 0f), Vector2(10f, 0f), Vector2(10f, 10f))

        val result = triangulateStroke(corner, lineWidth = 2f, closed = false, lineJoin = LineJoin.Bevel)

        assertTrue(result.none { abs(it.x - 11f) < 0.001f && abs(it.y + 1f) < 0.001f })
        assertEquals(12 + 3, result.size) // two segment quads + one bevel triangle
    }

    @Test
    fun `a round join fills the outer corner with an arc`() {
        val corner = listOf(Vector2(0f, 0f), Vector2(10f, 0f), Vector2(10f, 10f))

        val result = triangulateStroke(corner, lineWidth = 2f, closed = false, lineJoin = LineJoin.Round)

        assertTrue(result.size > 12 + 3)
        assertTrue(result.all { Vector2(it.x - 10f, it.y).length() <= 1.001f || it.y >= -1.001f })
    }

    @Test
    fun `a miter join past the miter limit falls back to a bevel`() {
        // A very sharp turn: the miter would be far longer than 10 line widths.
        val spike = listOf(Vector2(0f, 0f), Vector2(10f, 0f), Vector2(0f, 0.2f))

        val miter = triangulateStroke(spike, lineWidth = 2f, closed = false)
        val bevel = triangulateStroke(spike, lineWidth = 2f, closed = false, lineJoin = LineJoin.Bevel)

        assertEquals(bevel, miter)
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
