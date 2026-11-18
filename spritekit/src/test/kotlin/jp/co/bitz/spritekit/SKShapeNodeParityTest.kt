package jp.co.bitz.spritekit

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Apple's `SKShapeNode` defaults, frame and `lineLength`, as recorded on iOS/tvOS simulators. */
class SKShapeNodeParityTest {
    private val square =
        SKFlattenedContour(
            listOf(Vector2(0f, 0f), Vector2(40f, 0f), Vector2(40f, 40f), Vector2(0f, 40f)),
            closed = true,
        )
    private val horizontalLine = SKFlattenedContour(listOf(Vector2(0f, 0f), Vector2(40f, 0f)), closed = false)

    private fun assertRect(
        expected: Rect,
        actual: Rect?,
    ) {
        checkNotNull(actual)
        val close = { a: Float, b: Float -> abs(a - b) < 0.001f }
        assertTrue(
            close(expected.left, actual.left) && close(expected.top, actual.top) &&
                close(expected.right, actual.right) && close(expected.bottom, actual.bottom),
            "expected $expected, was $actual",
        )
    }

    @Test
    fun `defaults match Apple`() {
        val shape = SKShapeNode()
        assertEquals(1f, shape.lineWidth)
        assertTrue(shape.isAntialiased)
        assertEquals(SKBlendMode.Alpha, shape.blendMode)
        assertEquals(0f, shape.glowWidth)
        assertEquals(0f, shape.lineLength) // no path
    }

    @Test
    fun `a stroked square's frame grows by the full line width`() {
        // Apple: a 40-point square at lineWidth 3 without antialiasing is 43 wide
        assertRect(Rect(-1.5f, -1.5f, 41.5f, 41.5f), strokedBounds(listOf(square), 3f, LineCap.Butt))
    }

    @Test
    fun `antialiasing pads a stroked frame by 0_85 points a side`() {
        // Apple: the same square antialiased is 44.7 wide
        val width = 3f + ANTIALIASING_FRAME_PADDING * 2f
        assertRect(Rect(-2.35f, -2.35f, 42.35f, 42.35f), strokedBounds(listOf(square), width, LineCap.Butt))
    }

    @Test
    fun `butt caps don't lengthen an open line's frame`() {
        // Apple: a 40-point horizontal line at lineWidth 2 without antialiasing is 40 x 2
        assertRect(Rect(0f, -1f, 40f, 1f), strokedBounds(listOf(horizontalLine), 2f, LineCap.Butt))
    }

    @Test
    fun `a sharp corner's miter isn't counted, as on Apple`() {
        // Apple: (0,0)-(20,40)-(40,0) at lineWidth 4 tops out at about 40.89, well below the miter tip
        val peak = SKFlattenedContour(listOf(Vector2(0f, 0f), Vector2(20f, 40f), Vector2(40f, 0f)), closed = false)
        val bounds = checkNotNull(strokedBounds(listOf(peak), 4f, LineCap.Butt))
        assertTrue(abs(bounds.bottom - 40.894f) < 0.01f, "top was ${bounds.bottom}")
        assertTrue(abs(bounds.left + 1.789f) < 0.01f, "left was ${bounds.left}")
    }

    @Test
    fun `nothing to stroke gives no stroked bounds`() {
        assertNull(strokedBounds(listOf(SKFlattenedContour(listOf(Vector2(5f, 5f)), closed = false)), 3f, LineCap.Butt))
    }

    @Test
    fun `contour length sums segments and a closed contour's closing segment`() {
        assertEquals(5f, contourLength(SKFlattenedContour(listOf(Vector2(0f, 0f), Vector2(3f, 4f)), closed = false)))
        assertEquals(160f, contourLength(square))
        val openL = SKFlattenedContour(listOf(Vector2(0f, 0f), Vector2(10f, 0f), Vector2(10f, 10f)), closed = false)
        assertEquals(20f, contourLength(openL))
        assertEquals(20f + kotlin.math.sqrt(200f), contourLength(openL.copy(closed = true)))
        assertEquals(0f, contourLength(SKFlattenedContour(listOf(Vector2(1f, 1f)), closed = false)))
    }
}
