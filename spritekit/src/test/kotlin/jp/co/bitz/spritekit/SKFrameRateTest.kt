package jp.co.bitz.spritekit

import kotlin.test.Test
import kotlin.test.assertEquals

/** Mirrors the tic-tac-toe sample's `SKViewFrameRateParityTests` probe (a 60 Hz display). */
class SKFrameRateTest {
    private fun framesPerSecond(
        preferred: Int,
        refresh: Float = 60f,
    ): Float = refresh / skFrameInterval(preferred, refresh)

    @Test
    fun `a divisor of the display rate runs at exactly that rate`() {
        assertEquals(60f, framesPerSecond(60))
        assertEquals(30f, framesPerSecond(30))
        assertEquals(20f, framesPerSecond(20))
        assertEquals(15f, framesPerSecond(15))
        assertEquals(1f, framesPerSecond(1))
    }

    @Test
    fun `any other rate runs at the nearest whole fraction of the display rate, the higher on a tie`() {
        assertEquals(60f, framesPerSecond(50))
        assertEquals(60f, framesPerSecond(46))
        assertEquals(30f, framesPerSecond(40))
        assertEquals(30f, framesPerSecond(26))
        assertEquals(30f, framesPerSecond(25)) // tie between 30 and 20
        assertEquals(20f, framesPerSecond(23))
        assertEquals(20f, framesPerSecond(18))
        assertEquals(15f, framesPerSecond(17))
        assertEquals(15f, framesPerSecond(14))
        assertEquals(12f, framesPerSecond(13))
        assertEquals(12f, framesPerSecond(11)) // tie between 12 and 10
    }

    @Test
    fun `every rate from 11 to 60 matches Apple's except 24 and 45`() {
        // Apple's measured rate at 60 Hz for each requested rate (iOS/tvOS simulators).
        fun apple(fps: Int): Float =
            when (fps) {
                in 46..60 -> 60f
                in 24..45 -> 30f
                in 18..23 -> 20f
                in 14..17 -> 15f
                else -> 12f
            }
        val mismatches = (11..60).filter { framesPerSecond(it) != apple(it) }
        assertEquals(listOf(24, 45), mismatches)
    }

    @Test
    fun `a negative rate or one above the display's runs every refresh`() {
        assertEquals(60f, framesPerSecond(-5))
        assertEquals(60f, framesPerSecond(120))
        assertEquals(60f, framesPerSecond(1000))
    }

    @Test
    fun `zero runs once a second`() {
        assertEquals(1f, framesPerSecond(0))
    }

    @Test
    fun `a faster display divides down the same way`() {
        assertEquals(60f, framesPerSecond(60, refresh = 120f))
        assertEquals(30f, framesPerSecond(30, refresh = 120f))
        assertEquals(120f, framesPerSecond(120, refresh = 120f))
        assertEquals(1, skFrameInterval(60, 0f)) // no display yet: assume 60 Hz
    }

    @Test
    fun `a display rate a little off a whole number is taken as that whole number`() {
        assertEquals(skFrameInterval(45, 60f), skFrameInterval(45, 60.000004f))
        assertEquals(skFrameInterval(45, 60f), skFrameInterval(45, 59.94f))
        assertEquals(1, skFrameInterval(45, 60.000004f))
    }
}
