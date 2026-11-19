package jp.co.bitz.spritekit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Mirrors the tic-tac-toe sample's `SKViewDelegateParityTests` probes. */
class SKViewDelegateTest {
    @Test
    fun `a frame runs without a delegate, or when the delegate says to render`() {
        assertEquals(SKFrameStep.Run, skFrameStep(viewPaused = false, shouldRender = null))
        assertEquals(SKFrameStep.Run, skFrameStep(viewPaused = false, shouldRender = { true }))
    }

    @Test
    fun `a frame is held when the delegate says not to render`() {
        assertEquals(SKFrameStep.Hold, skFrameStep(viewPaused = false, shouldRender = { false }))
    }

    @Test
    fun `the delegate isn't asked while the view is paused`() {
        var asked = false
        val step =
            skFrameStep(viewPaused = true, shouldRender = {
                asked = true
                false
            })
        assertEquals(SKFrameStep.Run, step)
        assertEquals(false, asked)
    }

    @Test
    fun `held frames leave the clock alone, so the next frame advances by all of the skipped time`() {
        val clock = SKFrameClock()
        val frame = 16_666_667L
        assertEquals(Duration.ZERO, clock.tick(0))
        // Frames 1-24 are held: the clock isn't ticked. Frame 25 runs.
        val delta = clock.tick(25 * frame)
        assertEquals(25 * frame / 1_000_000.0, delta.inWholeNanoseconds / 1_000_000.0, 0.001)
        assertEquals(true, delta > 400.milliseconds)
    }
}
