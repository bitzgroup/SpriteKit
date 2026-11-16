package jp.co.bitz.spritekit

import kotlin.test.Test
import kotlin.test.assertEquals

class SKLabelLayoutTest {
    private val metrics = SKLabelMetrics(width = 20f, ascent = 8f, descent = 2f)

    @Test
    fun `Left-Baseline anchors the left edge at x=0 and the baseline at y=0`() {
        val corners =
            labelQuadCorners(metrics, SKLabelHorizontalAlignmentMode.Left, SKLabelVerticalAlignmentMode.Baseline)

        assertEquals(listOf(Vector2(0f, -2f), Vector2(20f, -2f), Vector2(20f, 8f), Vector2(0f, 8f)), corners)
    }

    @Test
    fun `Center-Center anchors the midpoint of both axes at the origin`() {
        val corners =
            labelQuadCorners(metrics, SKLabelHorizontalAlignmentMode.Center, SKLabelVerticalAlignmentMode.Center)

        assertEquals(listOf(Vector2(-10f, -5f), Vector2(10f, -5f), Vector2(10f, 5f), Vector2(-10f, 5f)), corners)
    }

    @Test
    fun `Right-Top anchors the right edge and the top of the text at the origin`() {
        val corners = labelQuadCorners(metrics, SKLabelHorizontalAlignmentMode.Right, SKLabelVerticalAlignmentMode.Top)

        assertEquals(listOf(Vector2(-20f, -10f), Vector2(0f, -10f), Vector2(0f, 0f), Vector2(-20f, 0f)), corners)
    }

    @Test
    fun `Left-Bottom anchors the bottom of the text at the origin`() {
        val corners =
            labelQuadCorners(metrics, SKLabelHorizontalAlignmentMode.Left, SKLabelVerticalAlignmentMode.Bottom)

        assertEquals(listOf(Vector2(0f, 0f), Vector2(20f, 0f), Vector2(20f, 10f), Vector2(0f, 10f)), corners)
    }

    // Apple, verified on iOS/tvOS simulators (tic-tac-toe's SKLabelMultilineParityTests): a
    // multi-line block aligns its bottom edge to the origin in baseline mode, as in bottom mode.
    @Test
    fun `a multi-line block aligns baseline like bottom and centers on its whole height`() {
        val metrics = multiLineMetrics(width = 40f, height = 50f)

        fun corners(mode: SKLabelVerticalAlignmentMode) =
            labelQuadCorners(metrics, SKLabelHorizontalAlignmentMode.Center, mode)

        assertEquals(0f, corners(SKLabelVerticalAlignmentMode.Baseline)[0].y)
        assertEquals(0f, corners(SKLabelVerticalAlignmentMode.Bottom)[0].y)
        assertEquals(-25f, corners(SKLabelVerticalAlignmentMode.Center)[0].y)
        assertEquals(0f, corners(SKLabelVerticalAlignmentMode.Top)[2].y)
        assertEquals(-20f, corners(SKLabelVerticalAlignmentMode.Top)[0].x)
    }

    @Test
    fun `a one-line label leaves the multi-line settings out of its cache key`() {
        val label = SKLabelNode(text = "").apply { preferredMaxLayoutWidth = 80f }
        assertEquals(1, label.numberOfLines)
        assertEquals(SKLineBreakMode.ByTruncatingTail, label.lineBreakMode)
    }
}
