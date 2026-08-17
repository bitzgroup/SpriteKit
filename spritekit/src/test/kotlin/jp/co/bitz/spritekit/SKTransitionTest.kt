package jp.co.bitz.spritekit

import android.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class SKTransitionTest {
    @Test
    fun `fade defaults to black`() {
        val transition = SKTransition.fade(1.seconds)

        assertEquals(Color.BLACK, transition.color)
    }

    @Test
    fun `fade accepts a custom color`() {
        val transition = SKTransition.fade(1.seconds, color = Color.RED)

        assertEquals(Color.RED, transition.color)
    }

    @Test
    fun `moveIn, push, and reveal carry the given direction`() {
        assertEquals(SKTransitionDirection.Up, SKTransition.moveIn(SKTransitionDirection.Up, 1.seconds).direction)
        assertEquals(SKTransitionDirection.Down, SKTransition.push(SKTransitionDirection.Down, 1.seconds).direction)
        assertEquals(SKTransitionDirection.Left, SKTransition.reveal(SKTransitionDirection.Left, 1.seconds).direction)
    }

    @Test
    fun `every factory carries the given duration`() {
        val duration = 2.5.seconds

        assertEquals(duration, SKTransition.fade(duration).duration)
        assertEquals(duration, SKTransition.crossFade(duration).duration)
        assertEquals(duration, SKTransition.moveIn(SKTransitionDirection.Right, duration).duration)
        assertEquals(duration, SKTransition.doorway(duration).duration)
        assertEquals(duration, SKTransition.flipHorizontal(duration).duration)
        assertEquals(duration, SKTransition.flipVertical(duration).duration)
    }
}
