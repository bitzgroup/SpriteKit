package jp.co.bitz.spritekit

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SKKeyframeSequenceTest {
    private fun assertEquals(
        expected: Float,
        actual: Float,
        absoluteTolerance: Float = 1e-4f,
    ) {
        assertTrue(abs(expected - actual) <= absoluteTolerance, "expected $expected, was $actual")
    }

    private fun lerp(
        from: Float,
        to: Float,
        fraction: Float,
    ): Float = from + (to - from) * fraction

    @Test
    fun `sampling before the first keyframe returns the first value`() {
        val sequence = SKKeyframeSequence(listOf(0f, 10f), listOf(0.25f, 0.75f))

        assertEquals(0f, sequence.sample(0f, ::lerp))
        assertEquals(0f, sequence.sample(0.25f, ::lerp))
    }

    @Test
    fun `sampling after the last keyframe returns the last value`() {
        val sequence = SKKeyframeSequence(listOf(0f, 10f), listOf(0.25f, 0.75f))

        assertEquals(10f, sequence.sample(0.75f, ::lerp))
        assertEquals(10f, sequence.sample(1f, ::lerp))
    }

    @Test
    fun `sampling between two keyframes interpolates by fraction`() {
        val sequence = SKKeyframeSequence(listOf(0f, 10f), listOf(0f, 1f))

        assertEquals(5f, sequence.sample(0.5f, ::lerp))
        assertEquals(2.5f, sequence.sample(0.25f, ::lerp))
    }

    @Test
    fun `three keyframes interpolate within the correct surrounding segment`() {
        val sequence = SKKeyframeSequence(listOf(0f, 100f, 0f), listOf(0f, 0.5f, 1f))

        assertEquals(100f, sequence.sample(0.5f, ::lerp))
        assertEquals(50f, sequence.sample(0.25f, ::lerp))
        assertEquals(50f, sequence.sample(0.75f, ::lerp))
    }

    @Test
    fun `a single keyframe always returns that value`() {
        val sequence = SKKeyframeSequence(listOf(42f), listOf(0.5f))

        assertEquals(42f, sequence.sample(0f, ::lerp))
        assertEquals(42f, sequence.sample(1f, ::lerp))
    }

    @Test
    fun `time is clamped to 0 to 1 before sampling`() {
        val sequence = SKKeyframeSequence(listOf(0f, 10f), listOf(0f, 1f))

        assertEquals(0f, sequence.sample(-1f, ::lerp))
        assertEquals(10f, sequence.sample(2f, ::lerp))
    }

    @Test
    fun `mismatched keyframeValues and times sizes throw`() {
        assertFailsWith<IllegalArgumentException> { SKKeyframeSequence(listOf(0f, 1f), listOf(0f)) }
    }

    @Test
    fun `an empty sequence throws`() {
        assertFailsWith<IllegalArgumentException> { SKKeyframeSequence(emptyList<Float>(), emptyList()) }
    }

    @Test
    fun `works with a non-Float type given a matching interpolator, like packed ARGB colors`() {
        val sequence = SKKeyframeSequence(listOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt()), listOf(0f, 1f))

        val midpoint = sequence.sample(0.5f, ::lerpColor)

        // 127/255, not exactly 0.5 -- lerpColor quantizes each channel back to an 8-bit int.
        assertEquals(0.5f, redOf(midpoint), absoluteTolerance = 0.01f)
    }
}
