package jp.co.bitz.spritekit

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SKTransitionLayersTest {
    private fun assertEquals(
        expected: Float,
        actual: Float,
        absoluteTolerance: Float = 1e-4f,
    ) {
        assertTrue(abs(expected - actual) <= absoluteTolerance, "expected $expected, was $actual")
    }

    // Component-wise, tolerance-based -- Vector2's exact (data class) equality is sensitive to
    // signed zero (-0.0f != 0.0f), which arithmetic like `motion * t` legitimately produces at
    // t=0/t=1 without anything being wrong.
    private fun assertEquals(
        expected: Vector2,
        actual: Vector2,
        absoluteTolerance: Float = 1e-4f,
    ) {
        assertEquals(expected.x, actual.x, absoluteTolerance)
        assertEquals(expected.y, actual.y, absoluteTolerance)
    }

    @Test
    fun `directionVector points one full viewport's worth in each direction`() {
        assertEquals(Vector2(200f, 0f), directionVector(SKTransitionDirection.Right, 200, 100))
        assertEquals(Vector2(-200f, 0f), directionVector(SKTransitionDirection.Left, 200, 100))
        assertEquals(Vector2(0f, 100f), directionVector(SKTransitionDirection.Up, 200, 100))
        assertEquals(Vector2(0f, -100f), directionVector(SKTransitionDirection.Down, 200, 100))
    }

    @Test
    fun `fade starts fully on the outgoing scene and ends fully on the incoming one`() {
        val transition = SKTransition.fade(1.seconds)

        val start = transitionLayers(transition, 0f, 200, 100).single()
        assertEquals(false, start.useToScene) // outgoing
        assertEquals(1f, start.alpha)

        val end = transitionLayers(transition, 1f, 200, 100).single()
        assertEquals(true, end.useToScene)
        assertEquals(1f, end.alpha)
    }

    @Test
    fun `fade crosses from the outgoing to the incoming scene exactly at the midpoint`() {
        val transition = SKTransition.fade(1.seconds)

        val justBefore = transitionLayers(transition, 0.49f, 200, 100).single()
        assertEquals(false, justBefore.useToScene)

        val justAfter = transitionLayers(transition, 0.51f, 200, 100).single()
        assertEquals(true, justAfter.useToScene)
    }

    @Test
    fun `crossFade draws the outgoing scene opaque, then the incoming scene fading in on top`() {
        val transition = SKTransition.crossFade(1.seconds)

        val layers = transitionLayers(transition, 0.25f, 200, 100)

        assertEquals(2, layers.size)
        assertEquals(false, layers[0].useToScene)
        assertEquals(1f, layers[0].alpha)
        assertTrue(layers[0].clearFirst)
        assertEquals(true, layers[1].useToScene)
        assertEquals(0.25f, layers[1].alpha)
        assertTrue(!layers[1].clearFirst)
    }

    @Test
    fun `moveIn keeps the outgoing scene stationary while the incoming scene slides fully into place`() {
        val transition = SKTransition.moveIn(SKTransitionDirection.Right, 1.seconds)

        val start = transitionLayers(transition, 0f, 200, 100)
        assertEquals(Vector2.Zero, start[0].viewportOffset) // outgoing, stationary
        assertEquals(Vector2(-200f, 0f), start[1].viewportOffset) // incoming, off-screen

        val end = transitionLayers(transition, 1f, 200, 100)
        assertEquals(Vector2.Zero, end[1].viewportOffset) // incoming, arrived
    }

    @Test
    fun `push moves both scenes together, exactly one screen apart at all times`() {
        val transition = SKTransition.push(SKTransitionDirection.Left, 1.seconds)

        for (t in listOf(0f, 0.3f, 0.7f, 1f)) {
            val layers = transitionLayers(transition, t, 200, 100)
            val outgoing = layers[0].viewportOffset
            val incoming = layers[1].viewportOffset
            assertEquals(200f, abs(outgoing.x - incoming.x))
        }
        // At t=1 the outgoing scene has fully exited and the incoming scene has fully arrived.
        val end = transitionLayers(transition, 1f, 200, 100)
        assertEquals(Vector2(-200f, 0f), end[0].viewportOffset)
        assertEquals(Vector2.Zero, end[1].viewportOffset)
    }

    @Test
    fun `reveal keeps the incoming scene stationary underneath while the outgoing scene slides away`() {
        val transition = SKTransition.reveal(SKTransitionDirection.Down, 1.seconds)

        val layers = transitionLayers(transition, 0.5f, 200, 100)

        assertEquals(true, layers[0].useToScene)
        assertEquals(Vector2.Zero, layers[0].viewportOffset)
        assertEquals(false, layers[1].useToScene)
        assertEquals(Vector2(0f, -50f), layers[1].viewportOffset) // half travelled, downward
    }

    @Test
    fun `doorway splits the outgoing scene into two panels sliding apart from the incoming scene beneath`() {
        val transition = SKTransition.doorway(1.seconds)

        val layers = transitionLayers(transition, 0.5f, 200, 100)

        assertEquals(3, layers.size)
        assertEquals(true, layers[0].useToScene) // incoming, base layer
        val left = layers[1]
        val right = layers[2]
        assertEquals(SKTransitionSplit.Left, left.split)
        assertEquals(SKTransitionSplit.Right, right.split)
        assertTrue(left.viewportOffset.x < 0f) // slides left
        assertTrue(right.viewportOffset.x > 0f) // slides right
    }

    @Test
    fun `doorway panels are fully back in place at progress 0`() {
        val transition = SKTransition.doorway(1.seconds)

        val layers = transitionLayers(transition, 0f, 200, 100)

        assertEquals(Vector2.Zero, layers[1].viewportOffset)
        assertEquals(Vector2.Zero, layers[2].viewportOffset)
    }

    @Test
    fun `flipHorizontal squashes the outgoing scene to nothing, then grows the incoming scene back out`() {
        val transition = SKTransition.flipHorizontal(1.seconds)

        val start = transitionLayers(transition, 0f, 200, 100).single()
        assertEquals(false, start.useToScene)
        assertEquals(200f, start.viewportSize!!.x)

        val midpoint = transitionLayers(transition, 0.5f, 200, 100).single()
        assertEquals(0f, midpoint.viewportSize!!.x)

        val end = transitionLayers(transition, 1f, 200, 100).single()
        assertEquals(true, end.useToScene)
        assertEquals(200f, end.viewportSize!!.x)
    }

    @Test
    fun `flipHorizontal keeps the squashed scene vertically centered and full-height`() {
        val transition = SKTransition.flipHorizontal(1.seconds)

        val layer = transitionLayers(transition, 0.25f, 200, 100).single()

        assertEquals(100f, layer.viewportSize!!.y) // full height, unaffected
        assertEquals(0f, layer.viewportOffset.y) // no vertical offset needed
        assertTrue(layer.viewportOffset.x > 0f) // horizontally re-centered as it narrows
    }

    @Test
    fun `flipVertical squashes vertically instead of horizontally`() {
        val transition = SKTransition.flipVertical(1.seconds)

        val layer = transitionLayers(transition, 0.25f, 200, 100).single()

        assertEquals(200f, layer.viewportSize!!.x) // full width, unaffected
        assertTrue(layer.viewportSize!!.y < 100f)
    }

    @Test
    fun `progress is clamped to 0 to 1`() {
        val transition = SKTransition.fade(1.seconds)

        val beforeStart = transitionLayers(transition, -1f, 200, 100).single()
        val afterEnd = transitionLayers(transition, 2f, 200, 100).single()

        assertEquals(false, beforeStart.useToScene)
        assertEquals(1f, beforeStart.alpha)
        assertEquals(true, afterEnd.useToScene)
        assertEquals(1f, afterEnd.alpha)
    }
}
