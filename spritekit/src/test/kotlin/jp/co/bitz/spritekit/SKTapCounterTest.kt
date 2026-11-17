package jp.co.bitz.spritekit

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Mirrors the tic-tac-toe sample's `SKTouchTapCountParityUITests` probe of Apple's
 * `UITouch.tapCount`, with stock-Android-like limits: 300 ms double-tap timeout, 100 px double-tap
 * slop, 16 px touch slop, 500 ms long-press timeout.
 */
class SKTapCounterTest {
    private val counter = SKTapCounter(300, 100f, 16f, 500)

    /** Taps pointer 0 at ([x], [y]) from [downMs] to [upMs], returning "b<n>,e<n>". */
    private fun tap(
        downMs: Long,
        upMs: Long = downMs + 50,
        x: Float = 200f,
        y: Float = 300f,
        pointerId: Int = 0,
    ): String {
        val began = counter.began(pointerId, x, y, downMs)
        val ended = counter.ended(pointerId, x, y, upMs)
        return "b$began,e$ended"
    }

    @Test
    fun `a single tap has tap count one`() {
        assertEquals("b1,e1", tap(0))
    }

    @Test
    fun `quick taps in one place count up`() {
        assertEquals(listOf("b1,e1", "b2,e2", "b3,e3"), listOf(tap(0), tap(150), tap(300)))
    }

    @Test
    fun `a quick tap close by continues the count`() {
        assertEquals(listOf("b1,e1", "b2,e2"), listOf(tap(0), tap(150, x = 210f, y = 310f)))
    }

    @Test
    fun `a pause restarts the count`() {
        assertEquals(listOf("b1,e1", "b1,e1"), listOf(tap(0), tap(1050)))
    }

    @Test
    fun `a quick tap far away restarts the count`() {
        assertEquals(listOf("b1,e1", "b1,e1"), listOf(tap(0), tap(150, x = 50f, y = 900f)))
    }

    @Test
    fun `a long press ends with tap count zero and the next touch starts over`() {
        assertEquals("b1,e0", tap(0, upMs = 1500))
        assertEquals("b1,e1", tap(1600))
    }

    @Test
    fun `a drag moves with its count and ends with zero`() {
        assertEquals(1, counter.began(0, 200f, 300f, 0))
        assertEquals(1, counter.moved(0, 205f, 300f))
        assertEquals(1, counter.moved(0, 200f, 500f))
        assertEquals(1, counter.moved(0, 200f, 300f)) // back where it started: still a drag
        assertEquals(0, counter.ended(0, 200f, 300f, 200))
        assertEquals("b1,e1", tap(250))
    }

    @Test
    fun `a wobble within touch slop is still a tap`() {
        counter.began(0, 200f, 300f, 0)
        counter.moved(0, 210f, 305f)
        assertEquals(1, counter.ended(0, 208f, 302f, 80))
    }

    @Test
    fun `each finger of a two finger tap counts on its own`() {
        val log = mutableListOf<Int>()
        for (round in 0 until 2) {
            val t = round * 150L
            log += counter.began(0, 100f, 300f, t)
            log += counter.began(1, 400f, 300f, t)
            log += counter.ended(0, 100f, 300f, t + 50)
            log += counter.ended(1, 400f, 300f, t + 50)
        }
        assertEquals(listOf(1, 1, 1, 1, 2, 2, 2, 2), log)
    }

    @Test
    fun `a cancelled touch keeps its count but never continues one`() {
        assertEquals("b1,e1", tap(0))
        assertEquals(2, counter.began(0, 200f, 300f, 100))
        assertEquals(2, counter.cancelled(0))
        assertEquals("b1,e1", tap(200))
    }
}
