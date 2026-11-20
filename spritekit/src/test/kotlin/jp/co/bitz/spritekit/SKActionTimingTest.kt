package jp.co.bitz.spritekit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Mirrors the tic-tac-toe sample's `SKActionTimingParityTests` probes. */
class SKActionTimingTest {
    /** How long one run of [action] takes, stepped a millisecond at a time. */
    private fun runLength(action: SKAction): Duration {
        val state = SKActionState(action)
        var elapsed = Duration.ZERO
        while (stepAction(state, SKNode(), 1.milliseconds) == null) elapsed += 1.milliseconds
        return elapsed
    }

    @Test
    fun `a ranged wait reports its base duration and draws a new length on every run`() {
        val wait = SKAction.wait(300.milliseconds, withRange = 400.milliseconds)
        assertEquals(300.milliseconds, wait.duration)
        val lengths = List(40) { runLength(wait) }
        assertTrue(lengths.all { it in 99.milliseconds..500.milliseconds }, "$lengths")
        assertTrue(lengths.toSet().size > 10, "$lengths")
    }

    @Test
    fun `a ranged wait draws again on each pass of a repeat`() {
        val stamps = mutableListOf<Duration>()
        var now = Duration.ZERO
        val action =
            SKAction.repeat(
                SKAction.sequence(
                    listOf(
                        SKAction.wait(300.milliseconds, withRange = 400.milliseconds),
                        SKAction.run { stamps += now },
                    ),
                ),
                count = 8,
            )
        val state = SKActionState(action)
        while (stepAction(state, SKNode(), 1.milliseconds) == null) now += 1.milliseconds
        val gaps = stamps.zipWithNext { a, b -> b - a }
        assertTrue(gaps.toSet().size > 3, "$gaps")
    }

    @Test
    fun `a group hands on the time its last action didn't need`() {
        val plain = SKNode()
        val grouped = SKNode()
        val move = SKAction.moveBy(Vector2(1000f, 0f), 1.seconds)
        val plainState = SKActionState(SKAction.sequence(listOf(SKAction.wait(105.milliseconds), move)))
        val groupedState =
            SKActionState(
                SKAction.sequence(
                    listOf(
                        SKAction.group(listOf(SKAction.wait(105.milliseconds), SKAction.wait(50.milliseconds))),
                        move,
                    ),
                ),
            )
        repeat(8) {
            stepAction(plainState, plain, (1.0 / 60).seconds)
            stepAction(groupedState, grouped, (1.0 / 60).seconds)
        }
        // Apple: both nodes at 28.33 two frames after the 105 ms wait ends.
        assertEquals(28.333f, grouped.position.x, 0.01f)
        assertEquals(plain.position.x, grouped.position.x, 1e-3f)
    }

    @Test
    fun `a group's leftover is that of the action finishing last`() {
        val state =
            SKActionState(
                SKAction.group(
                    listOf(
                        SKAction.wait(50.milliseconds),
                        SKAction.sequence(listOf(SKAction.wait(30.milliseconds), SKAction.wait(75.milliseconds))),
                    ),
                ),
            )
        assertNull(stepAction(state, SKNode(), 100.milliseconds))
        assertEquals(15.milliseconds, stepAction(state, SKNode(), 20.milliseconds))
    }
}
