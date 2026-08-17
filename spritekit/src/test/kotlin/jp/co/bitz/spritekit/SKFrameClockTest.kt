package jp.co.bitz.spritekit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class SKFrameClockTest {
    @Test
    fun `first tick returns zero`() {
        val clock = SKFrameClock()

        assertEquals(Duration.ZERO, clock.tick(nowNanos = 1_000_000_000L))
    }

    @Test
    fun `subsequent ticks return the elapsed duration since the previous tick`() {
        val clock = SKFrameClock()
        clock.tick(nowNanos = 0L)

        val delta = clock.tick(nowNanos = 16_000_000L) // 16ms later

        assertEquals(16.milliseconds, delta)
    }

    @Test
    fun `reset makes the next tick return zero again`() {
        val clock = SKFrameClock()
        clock.tick(nowNanos = 0L)
        clock.tick(nowNanos = 16_000_000L)

        clock.reset()

        assertEquals(Duration.ZERO, clock.tick(nowNanos = 32_000_000L))
    }
}
