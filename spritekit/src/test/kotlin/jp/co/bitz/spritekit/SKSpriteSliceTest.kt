package jp.co.bitz.spritekit

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Mirrors the `centerRect` cases in tic-tac-toe's `SKRenderPixelParityTests` (Apple's behavior,
 * recorded on iOS/tvOS simulators), on a 3×3 texture whose middle third stretches.
 */
class SKSpriteSliceTest {
    private val third = 1f / 3f
    private val center = Rect(third, third, 2 * third, 2 * third)
    private val texture = Vector2(3f, 3f)

    private fun xEdges(slices: List<SKSpriteSlice>) = slices.flatMap { s -> s.corners.map { it.x } }.distinct().sorted()

    private fun yEdges(slices: List<SKSpriteSlice>) = slices.flatMap { s -> s.corners.map { it.y } }.distinct().sorted()

    @Test
    fun `growing through size keeps the border at its texture size`() {
        val slices = nineSlices(Rect(-15f, -15f, 15f, 15f), center, texture, Vector2(1f, 1f), FULL_UNIT_RECT)
        assertEquals(9, slices.size)
        assertEquals(listOf(-15f, -14f, 14f, 15f), xEdges(slices))
        assertEquals(listOf(-15f, -14f, 14f, 15f), yEdges(slices))
    }

    @Test
    fun `growing through scale keeps the border at its texture size on screen`() {
        val slices = nineSlices(Rect(-1.5f, -1.5f, 1.5f, 1.5f), center, texture, Vector2(10f, -10f), FULL_UNIT_RECT)
        assertEquals(listOf(-1.5f, -1.4f, 1.4f, 1.5f), xEdges(slices).map { Math.round(it * 10) / 10f })
    }

    @Test
    fun `a sprite smaller than its borders shrinks them and drops the center`() {
        val slices = nineSlices(Rect(0f, 0f, 1f, 1f), center, texture, Vector2(1f, 1f), FULL_UNIT_RECT)
        assertEquals(4, slices.size)
        assertEquals(listOf(0f, 0.5f, 1f), xEdges(slices))
    }

    @Test
    fun `cells sample the matching part of the texture with v running down from the top`() {
        val slices = nineSlices(Rect(0f, 0f, 30f, 30f), center, texture, Vector2(1f, 1f), FULL_UNIT_RECT)
        assertRectEquals(Rect(0f, 2 * third, third, 1f), slices.first().uv)
        assertRectEquals(Rect(2 * third, 0f, 1f, third), slices.last().uv)
    }

    @Test
    fun `cells stay inside a sub-rect texture's region`() {
        val region = Rect(0.5f, 0f, 1f, 0.5f)
        val slices = nineSlices(Rect(0f, 0f, 30f, 30f), center, texture, Vector2(1f, 1f), region)
        assertEquals(0.5f, slices.minOf { it.uv.left })
        assertEquals(1f, slices.maxOf { it.uv.right })
        assertEquals(0f, slices.minOf { it.uv.top })
        assertEquals(0.5f, slices.maxOf { it.uv.bottom })
    }

    private fun assertRectEquals(
        expected: Rect,
        actual: Rect,
    ) {
        val tolerance = 1e-5f
        assertEquals(expected.left, actual.left, tolerance)
        assertEquals(expected.top, actual.top, tolerance)
        assertEquals(expected.right, actual.right, tolerance)
        assertEquals(expected.bottom, actual.bottom, tolerance)
    }
}
