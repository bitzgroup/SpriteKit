package jp.co.bitz.spritekit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Expectations recorded from Apple's SKShapeNode(splinePoints:count:) on iOS/tvOS simulators —
// see the tic-tac-toe sample's SpriteKitParity probes (SKShapeNodeInitParityTests).
class SKCubicSegmentTest {
    private fun assertNear(
        expected: Vector2,
        actual: Vector2,
    ) {
        assertTrue(
            kotlin.math.abs(expected.x - actual.x) < 1e-3f && kotlin.math.abs(expected.y - actual.y) < 1e-3f,
            "expected $expected, was $actual",
        )
    }

    @Test
    fun `spline segments match Apple's Catmull-Rom construction, leading zero-length segment included`() {
        val segments =
            splineSegments(listOf(Vector2(0f, 0f), Vector2(10f, 10f), Vector2(20f, 0f), Vector2(30f, 10f)))
        val expected =
            listOf(
                Triple(Vector2(0f, 0f), Vector2(-1.667f, -1.667f), Vector2(0f, 0f)),
                Triple(Vector2(1.667f, 1.667f), Vector2(6.667f, 10f), Vector2(10f, 10f)),
                Triple(Vector2(13.333f, 10f), Vector2(16.667f, 0f), Vector2(20f, 0f)),
                Triple(Vector2(23.333f, 0f), Vector2(28.333f, 8.333f), Vector2(30f, 10f)),
            )
        assertEquals(expected.size, segments.size)
        segments.zip(expected).forEach { (segment, triple) ->
            assertNear(triple.first, segment.control1)
            assertNear(triple.second, segment.control2)
            assertNear(triple.third, segment.end)
        }
    }

    @Test
    fun `no points give no segments`() {
        assertEquals(emptyList(), splineSegments(emptyList()))
    }
}
