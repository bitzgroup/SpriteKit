package jp.co.bitz.spritekit

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [triangulateAntialiasedStroke] against the edge profiles recorded on iOS/tvOS simulators
 * (tic-tac-toe's `SKShapeNodeStyleParityTests`): sampled at pixel centers, a white outline's alpha
 * is 255/162/12 at 0.5/1.5/2.5 pixels from a 4-pixel line's center, the same at any screen scale.
 */
class SKStrokeMeshTest {
    private val horizontal = listOf(Vector2(-40f, 0f), Vector2(40f, 0f))

    @Test
    fun `a wide outline fades over 1_7 pixels across each edge, as Apple's does`() {
        val mesh = stroke(horizontal, lineWidth = 4f)

        assertAlpha(1f, mesh, Vector2(0.3f, 0.5f))
        assertAlpha(162 / 255f, mesh, Vector2(0.3f, 1.5f))
        assertAlpha(12 / 255f, mesh, Vector2(0.3f, -2.5f))
        assertAlpha(0f, mesh, Vector2(0.3f, 3.5f))
    }

    @Test
    fun `an off-center edge matches Apple's quarter-pixel samples`() {
        // Apple's 4-point line at y = 0.25 on a 1x screen: rows 0.75/1.75 and 2.25 from its center.
        val mesh = stroke(horizontal, lineWidth = 4f)

        assertAlpha(199 / 255f, mesh, Vector2(0.3f, 1.25f))
        assertAlpha(124 / 255f, mesh, Vector2(0.3f, 1.75f))
        assertAlpha(49 / 255f, mesh, Vector2(0.3f, 2.25f))
    }

    @Test
    fun `the fade is sized in pixels, not points`() {
        // A 4-point line on a 3x screen is 12 pixels wide: its edge sits at 6 pixels = 2 points.
        val mesh = stroke(horizontal, lineWidth = 4f, pixelSize = 1f / 3f)

        assertAlpha(1f, mesh, Vector2(0.3f, 4.5f / 3f))
        assertAlpha(162 / 255f, mesh, Vector2(0.3f, 5.5f / 3f))
        assertAlpha(12 / 255f, mesh, Vector2(0.3f, 6.5f / 3f))
    }

    @Test
    fun `a thin outline is drawn fainter and about two and a half pixels wide`() {
        val mesh = stroke(horizontal, lineWidth = 0.5f)

        val near = alphaAt(mesh, Vector2(0.3f, 0.5f))
        val far = alphaAt(mesh, Vector2(0.3f, 1.5f))
        assertTrue(near in 0.6f..0.85f, "near: $near")
        assertTrue(far in 0f..0.25f, "far: $far")
        // About as much ink as Apple's (0.69 to 0.85 on the two nearest rows).
        assertEquals(1.83f, 2f * (near + far), absoluteTolerance = 0.05f)
    }

    @Test
    fun `a butt end fades the same way along the line`() {
        val mesh = stroke(horizontal, lineWidth = 4f)

        assertAlpha(1f, mesh, Vector2(38.5f, 0.3f))
        assertAlpha(162 / 255f, mesh, Vector2(39.5f, 0.3f))
        assertAlpha(12 / 255f, mesh, Vector2(40.5f, 0.3f))
        assertAlpha(0f, mesh, Vector2(41.5f, 0.3f))
    }

    @Test
    fun `square and round ends fade around their own outline`() {
        val square = stroke(horizontal, lineWidth = 4f, lineCap = LineCap.Square)
        assertAlpha(162 / 255f, square, Vector2(41.5f, 0.3f))
        assertAlpha(12 / 255f, square, Vector2(42.5f, 0.3f))
        assertAlpha(162 / 255f, square, Vector2(41f, 1.5f))

        val round = stroke(horizontal, lineWidth = 4f, lineCap = LineCap.Round)
        assertAlpha(1f, round, Vector2(40.5f, 0.1f))
        assertAlpha(162 / 255f, round, Vector2(41.5f, 0.1f), tolerance = 0.03f)
        assertAlpha(12 / 255f, round, Vector2(42.5f, 0.1f), tolerance = 0.03f)
    }

    @Test
    fun `a closed outline fades around its mitered corners`() {
        val square = listOf(Vector2(0f, 0f), Vector2(20f, 0f), Vector2(20f, 20f), Vector2(0f, 20f))
        val mesh = stroke(square, lineWidth = 4f, closed = true)

        assertAlpha(1f, mesh, Vector2(10.3f, 0.5f))
        assertAlpha(12 / 255f, mesh, Vector2(10.3f, -2.5f))
        assertAlpha(12 / 255f, mesh, Vector2(-2.5f, 10.3f))
        // Diagonally outside the corner (just off the bisector, where two fringe triangles meet):
        // the fade follows both edges out to the miter tip.
        assertAlpha(162 / 255f, mesh, Vector2(21.5f, 21.45f))
        assertAlpha(0f, mesh, Vector2(23.5f, 23.5f))
    }

    @Test
    fun `round and bevel joins fade around the outside of a turn`() {
        val corner = listOf(Vector2(0f, 0f), Vector2(20f, 0f), Vector2(20f, 20f))
        // 2.5 pixels from the corner, off the bisector, where two fringe triangles meet.
        val angle = -PI.toFloat() / 4f + 0.1f
        val round = stroke(corner, lineWidth = 4f, lineJoin = LineJoin.Round)
        assertAlpha(12 / 255f, round, Vector2(20f + 2.5f * cos(angle), 2.5f * sin(angle)), tolerance = 0.03f)

        val bevel = stroke(corner, lineWidth = 4f, lineJoin = LineJoin.Bevel)
        assertAlpha(1f, bevel, Vector2(20.2f, -0.2f))
        assertAlpha(0f, bevel, Vector2(21.4f, -1.4f))
    }

    @Test
    fun `every vertex is fully opaque or fully clear`() {
        val mesh = stroke(listOf(Vector2(0f, 0f), Vector2(20f, 0f), Vector2(20f, 20f)), lineWidth = 3f)

        assertEquals(mesh.vertices.size, mesh.alphas.size)
        assertEquals(0, mesh.vertices.size % 3)
        assertTrue(mesh.alphas.all { it == 0f || it == 1f })
    }

    @Test
    fun `nothing to draw yields an empty mesh`() {
        assertTrue(stroke(horizontal, lineWidth = 0f).vertices.isEmpty())
        assertTrue(stroke(listOf(Vector2.Zero), lineWidth = 4f).vertices.isEmpty())
    }

    @Test
    fun `arcDirections steps the shorter way, or through the given direction`() {
        val up = Vector2(0f, 1f)
        val right = Vector2(1f, 0f)
        val quarter = arcDirections(right, up)
        assertEquals(ROUND_SEGMENTS_PER_HALF_CIRCLE / 2 + 1, quarter.size)
        assertTrue(quarter.all { it.x >= -1e-5f && it.y >= -1e-5f })

        val half = arcDirections(up, -up, through = -right)
        assertTrue(half.all { it.x <= 1e-5f })
    }

    @Suppress("LongParameterList")
    private fun stroke(
        contour: List<Vector2>,
        lineWidth: Float,
        closed: Boolean = false,
        lineCap: LineCap = LineCap.Butt,
        lineJoin: LineJoin = LineJoin.Miter,
        pixelSize: Float = 1f,
    ): SKStrokeMesh =
        triangulateAntialiasedStroke(contour, lineWidth, closed, lineCap, lineJoin, DEFAULT_MITER_LIMIT, pixelSize)

    private fun assertAlpha(
        expected: Float,
        mesh: SKStrokeMesh,
        point: Vector2,
        tolerance: Float = 0.02f,
    ) = assertEquals(expected, alphaAt(mesh, point), absoluteTolerance = tolerance, "alpha at $point")

    /** The alpha a white outline leaves at [point] once every triangle covering it is blended over the last. */
    private fun alphaAt(
        mesh: SKStrokeMesh,
        point: Vector2,
    ): Float {
        var transparency = 1f
        for (i in mesh.vertices.indices step 3) {
            val weights = barycentric(point, mesh.vertices[i], mesh.vertices[i + 1], mesh.vertices[i + 2]) ?: continue
            val alpha = weights[0] * mesh.alphas[i] + weights[1] * mesh.alphas[i + 1] + weights[2] * mesh.alphas[i + 2]
            transparency *= 1f - alpha
        }
        return 1f - transparency
    }

    /** [p]'s barycentric weights in triangle [a]/[b]/[c], or `null` outside it (or if it's degenerate). */
    private fun barycentric(
        p: Vector2,
        a: Vector2,
        b: Vector2,
        c: Vector2,
    ): FloatArray? {
        val det = (b.y - c.y) * (a.x - c.x) + (c.x - b.x) * (a.y - c.y)
        if (det == 0f) return null
        val wa = ((b.y - c.y) * (p.x - c.x) + (c.x - b.x) * (p.y - c.y)) / det
        val wb = ((c.y - a.y) * (p.x - c.x) + (a.x - c.x) * (p.y - c.y)) / det
        val wc = 1f - wa - wb
        val epsilon = -1e-6f
        return if (wa >= epsilon && wb >= epsilon && wc >= epsilon) floatArrayOf(wa, wb, wc) else null
    }
}
