package jp.co.bitz.spritekit

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The expected values are Apple's, recorded on iOS/tvOS simulators. */
class SKKeyframeSequenceTest {
    private fun sequence(
        values: List<Float>,
        times: List<Float>,
        mode: SKInterpolationMode = SKInterpolationMode.Linear,
        repeat: SKRepeatMode = SKRepeatMode.Clamp,
    ): SKKeyframeSequence<Float> =
        SKKeyframeSequence(values, times, SKKeyframeInterpolator.FloatValues).apply {
            interpolationMode = mode
            repeatMode = repeat
        }

    private fun assertSamples(
        sequence: SKKeyframeSequence<Float>,
        vararg expected: Pair<Float, Float>,
    ) {
        for ((time, value) in expected) {
            val actual = sequence.sample(time) ?: error("no value at $time")
            assertTrue(abs(value - actual) <= 1e-4f, "at $time: expected $value, was $actual")
        }
    }

    private fun keyframes(sequence: SKKeyframeSequence<Float>): List<Pair<Float, Float>> =
        (0 until sequence.count()).map { sequence.getKeyframeTime(it) to sequence.getKeyframeValue(it) }

    private val fourKeyframes = listOf(0f, 10f, 0f, 40f)
    private val fourTimes = listOf(0.2f, 0.4f, 0.6f, 1.0f)

    @Test
    fun `defaults are linear and clamp`() {
        val sequence = sequence(listOf(0f, 10f), listOf(0f, 1f))

        assertEquals(SKInterpolationMode.Linear, sequence.interpolationMode)
        assertEquals(SKRepeatMode.Clamp, sequence.repeatMode)
        assertEquals(2, sequence.count())
    }

    @Test
    fun `an empty sequence samples null`() {
        val sequence = SKKeyframeSequence(4, SKKeyframeInterpolator.FloatValues)

        assertEquals(0, sequence.count())
        assertNull(sequence.sample(0.5f))
    }

    @Test
    fun `mismatched keyframeValues and times sizes throw`() {
        assertFailsWith<IllegalArgumentException> { sequence(listOf(0f, 1f), listOf(0f)) }
    }

    @Test
    fun `a single keyframe is its value everywhere`() {
        for (mode in SKInterpolationMode.entries) {
            for (repeat in SKRepeatMode.entries) {
                assertSamples(
                    sequence(listOf(5f), listOf(0.5f), mode, repeat),
                    0f to 5f,
                    0.5f to 5f,
                    1f to 5f,
                    2f to 5f,
                )
            }
        }
    }

    @Test
    fun `linear interpolates and holds the end values`() {
        assertSamples(
            sequence(fourKeyframes, fourTimes),
            -1.5f to 0f,
            0f to 0f,
            0.2f to 0f,
            0.25f to 2.5f,
            0.3f to 5f,
            0.4f to 10f,
            0.45f to 7.5f,
            0.6f to 0f,
            0.7f to 10f,
            0.9f to 30f,
            1.0f to 40f,
            1.5f to 40f,
            3.7f to 40f,
        )
    }

    @Test
    fun `step holds the latest keyframe reached`() {
        assertSamples(
            sequence(fourKeyframes, fourTimes, SKInterpolationMode.Step),
            -0.5f to 0f,
            0.35f to 0f,
            0.4f to 10f,
            0.55f to 10f,
            0.6f to 0f,
            0.9f to 0f,
            1.0f to 40f,
            2.0f to 40f,
        )
    }

    @Test
    fun `spline with two keyframes is a smoothstep`() {
        assertSamples(
            sequence(listOf(0f, 10f), listOf(0f, 1f), SKInterpolationMode.Spline),
            0.1f to 0.28f,
            0.2f to 1.04f,
            0.5f to 5f,
            0.9f to 9.72f,
        )
    }

    @Test
    fun `spline with even spacing is Catmull-Rom with flat ends`() {
        assertSamples(
            sequence(listOf(0f, 1f, 3f, 6f), listOf(0f, 1f, 2f, 3f), SKInterpolationMode.Spline),
            0.125f to 0.022461f,
            0.5f to 0.3125f,
            1.5f to 1.875f,
            2.5f to 4.8125f,
            2.875f to 5.905273f,
        )
    }

    @Test
    fun `spline tangents weight each side by the other side's gap`() {
        assertSamples(
            sequence(fourKeyframes, fourTimes, SKInterpolationMode.Spline),
            0.25f to 1.5625f,
            0.3f to 5f,
            0.45f to 8.125f,
            0.5f to 4.166667f,
            0.575f to -0.208333f,
            0.7f to 7.187497f,
            0.8f to 20.833334f,
            0.9f to 34.062496f,
            1.5f to 40f,
        )
        assertSamples(
            sequence(listOf(0f, 10f, 0f), listOf(0f, 0.1f, 1f), SKInterpolationMode.Spline),
            0.05f to 4f,
            0.2f to 10.359397f,
            0.55f to 6f,
            0.9f to 0.43073f,
        )
    }

    @Test
    fun `loop wraps the time by the last keyframe's time`() {
        assertSamples(
            sequence(fourKeyframes, fourTimes, repeat = SKRepeatMode.Loop),
            -1.5f to 5f,
            -0.5f to 5f,
            -0.1f to 30f,
            1.0f to 0f,
            1.1f to 0f,
            1.3f to 5f,
            1.8f to 20f,
            2.0f to 0f,
            3.7f to 10f,
        )
        assertSamples(
            sequence(fourKeyframes, fourTimes, SKInterpolationMode.Step, SKRepeatMode.Loop),
            1.0f to 0f,
            1.5f to 10f,
            1.8f to 0f,
        )
        assertSamples(
            sequence(listOf(0f, 1f, 3f, 6f), listOf(0f, 1f, 2f, 3f), SKInterpolationMode.Spline, SKRepeatMode.Loop),
            -1f to 3f,
            -0.5f to 4.8125f,
            3.5f to 0.3125f,
            4f to 1f,
            6.25f to 0.085938f,
        )
    }

    @Test
    fun `times are not sorted`() {
        val unsorted = sequence(listOf(0f, 10f, 20f), listOf(0f, 1f, 0.5f))
        assertEquals(listOf(0f to 0f, 1f to 10f, 0.5f to 20f), keyframes(unsorted))
        assertSamples(unsorted, 0.25f to 2.5f, 0.5f to 5f, 0.75f to 5f, 1f to 5f)

        assertSamples(sequence(listOf(0f, 10f, 5f), listOf(2f, 1f, 0.5f)), 0f to 0f, 0.5f to 0f, 1f to 0f, 2f to 0f)
    }

    @Test
    fun `equal times take the later keyframe`() {
        assertSamples(
            sequence(listOf(0f, 10f, 20f), listOf(0f, 0.5f, 0.5f)),
            0.25f to 5f,
            0.49f to 9.8f,
            0.5f to 20f,
            0.75f to 20f,
        )
    }

    @Test
    fun `editing keeps the keyframes in their order`() {
        val sequence = sequence(listOf(0f, 10f), listOf(0f, 1f))
        sequence.addKeyframeValue(5f, 0.5f)
        assertEquals(listOf(0f to 0f, 1f to 10f, 0.5f to 5f), keyframes(sequence))
        assertSamples(sequence, 0.25f to 2.5f, 0.5f to 5f, 0.75f to 5f)

        sequence.setKeyframeTime(2f, 0)
        assertEquals(listOf(2f to 0f, 1f to 10f, 0.5f to 5f), keyframes(sequence))

        sequence.setKeyframeValue(100f, 1)
        sequence.setKeyframeValue(7f, 0.1f, 2)
        assertEquals(listOf(2f to 0f, 1f to 100f, 0.1f to 7f), keyframes(sequence))

        sequence.removeKeyframe(0)
        assertEquals(listOf(1f to 100f, 0.1f to 7f), keyframes(sequence))
        sequence.removeLastKeyframe()
        assertEquals(listOf(1f to 100f), keyframes(sequence))
        sequence.removeLastKeyframe()
        assertEquals(0, sequence.count())
        assertNull(sequence.sample(0f))
        sequence.removeLastKeyframe()
        assertEquals(0, sequence.count())
    }

    private fun assertColor(
        expected: List<Float>,
        argb: Int?,
    ) {
        requireNotNull(argb)
        val actual = listOf(redOf(argb), greenOf(argb), blueOf(argb), alphaOf(argb))
        for ((e, a) in expected.zip(actual)) {
            assertTrue(abs(e - a) <= 1f / 255f, "expected $expected, was $actual")
        }
    }

    @Test
    fun `colors mix each unpremultiplied channel`() {
        val red = 0xFFFF0000.toInt()
        val clearBlue = 0x000000FF
        val colors = SKKeyframeSequence(listOf(red, clearBlue), listOf(0f, 1f), SKKeyframeInterpolator.ColorValues)

        assertColor(listOf(0.75f, 0f, 0.25f, 0.75f), colors.sample(0.25f))
        assertColor(listOf(0.5f, 0f, 0.5f, 0.5f), colors.sample(0.5f))
        colors.interpolationMode = SKInterpolationMode.Spline
        assertColor(listOf(0.84375f, 0f, 0.15625f, 0.84375f), colors.sample(0.25f))

        val whiteToRed =
            SKKeyframeSequence(listOf(0xFFFFFFFF.toInt(), red), listOf(0f, 1f), SKKeyframeInterpolator.ColorValues)
        assertColor(listOf(1f, 0.5f, 0.5f, 1f), whiteToRed.sample(0.5f))
    }

    @Test
    fun `a color overshooting on a spline is clamped`() {
        val colors =
            SKKeyframeSequence(
                listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt()),
                listOf(0f, 0.1f, 1f),
                SKKeyframeInterpolator.ColorValues,
            )
        colors.interpolationMode = SKInterpolationMode.Spline

        assertColor(listOf(1f, 1f, 1f, 1f), colors.sample(0.2f))
    }
}
