package jp.co.bitz.spritekit

import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class SKActionExecutionTest {
    private fun assertEquals(
        expected: Float,
        actual: Float,
        absoluteTolerance: Float,
    ) {
        assertTrue(abs(expected - actual) <= absoluteTolerance, "expected $expected, was $actual")
    }

    @Test
    fun `a leaf action mid-flight returns null and reflects partial progress`() {
        val node = SKNode().apply { position = Vector2(0f, 0f) }
        val state = SKActionState(SKAction.moveTo(Vector2(10f, 0f), 2.seconds))

        val leftover = stepAction(state, node, 1.seconds)

        assertNull(leftover)
        assertEquals(Vector2(5f, 0f), node.position)
    }

    @Test
    fun `a leaf action that finishes exactly on time returns zero leftover`() {
        val node = SKNode()
        val state = SKActionState(SKAction.moveTo(Vector2(10f, 0f), 2.seconds))

        val leftover = stepAction(state, node, 2.seconds)

        assertEquals(Duration.ZERO, leftover)
        assertEquals(Vector2(10f, 0f), node.position)
    }

    @Test
    fun `a leaf action that overshoots its duration reports the overflow as leftover`() {
        val node = SKNode()
        val state = SKActionState(SKAction.moveTo(Vector2(10f, 0f), 2.seconds))

        val leftover = stepAction(state, node, 3.seconds)

        assertEquals(1.seconds, leftover)
        assertEquals(Vector2(10f, 0f), node.position) // clamped to the target, not overshot
    }

    @Test
    fun `a zero-duration action applies immediately and consumes no time`() {
        val node = SKNode()
        val state = SKActionState(SKAction.moveTo(Vector2(10f, 0f), Duration.ZERO))

        val leftover = stepAction(state, node, 5.seconds)

        assertEquals(5.seconds, leftover)
        assertEquals(Vector2(10f, 0f), node.position)
    }

    @Test
    fun `stepping an already-finished action is a no-op and returns the full available time`() {
        val node = SKNode()
        val state = SKActionState(SKAction.moveTo(Vector2(10f, 0f), 1.seconds))
        stepAction(state, node, 1.seconds)

        node.position = Vector2(999f, 999f) // simulate something else moving the node afterward
        val leftover = stepAction(state, node, 1.seconds)

        assertEquals(1.seconds, leftover)
        assertEquals(Vector2(999f, 999f), node.position) // untouched by the finished action
    }

    @Test
    fun `a sequence runs its children in order, carrying leftover time into the next one within the same frame`() {
        val node = SKNode()
        val action =
            SKAction.sequence(
                listOf(
                    SKAction.moveTo(Vector2(10f, 0f), 1.seconds),
                    SKAction.moveTo(Vector2(10f, 10f), 1.seconds),
                ),
            )
        val state = SKActionState(action)

        val leftover = stepAction(state, node, 1.5.seconds)

        assertNull(leftover) // second child still running
        assertEquals(Vector2(10f, 5f), node.position) // 0.5s into the second child
    }

    @Test
    fun `a sequence reports the correct total duration`() {
        val action =
            SKAction.sequence(
                listOf(SKAction.wait(1.seconds), SKAction.wait(2.seconds)),
            )

        assertEquals(3.seconds, action.duration)
    }

    @Test
    fun `a group finishes only once every child has, and short children stop rather than repeat`() {
        val node = SKNode()
        val action =
            SKAction.group(
                listOf(
                    SKAction.fadeAlphaTo(0f, 1.seconds),
                    SKAction.moveTo(Vector2(10f, 0f), 2.seconds),
                ),
            )
        val state = SKActionState(action)

        assertNull(stepAction(state, node, 1.seconds)) // fade done, move at the halfway point
        assertEquals(0f, node.alpha)
        assertEquals(Vector2(5f, 0f), node.position)

        val finalLeftover = stepAction(state, node, 1.seconds)
        assertEquals(Duration.ZERO, finalLeftover)
        assertEquals(Vector2(10f, 0f), node.position)
    }

    @Test
    fun `repeat runs the child action the requested number of times`() {
        val node = SKNode()
        val action = SKAction.repeat(SKAction.moveBy(Vector2(1f, 0f), 1.seconds), count = 3)
        val state = SKActionState(action)

        val leftover = stepAction(state, node, 3.seconds)

        assertEquals(Duration.ZERO, leftover)
        assertEquals(Vector2(3f, 0f), node.position)
    }

    @Test
    fun `repeatForever never finishes`() {
        val node = SKNode()
        val action = SKAction.repeatForever(SKAction.moveBy(Vector2(1f, 0f), 1.seconds))
        val state = SKActionState(action)

        val leftover = stepAction(state, node, 10.5.seconds)

        assertNull(leftover)
        // 10 full iterations (+10 in x) plus 0.5s into the 11th (a fresh moveBy each time, so +0.5 more).
        assertEquals(Vector2(10.5f, 0f), node.position)
    }

    @Test
    fun `a run block action executes exactly once`() {
        val node = SKNode()
        var callCount = 0
        val state = SKActionState(SKAction.run { callCount++ })

        stepAction(state, node, Duration.ZERO)
        stepAction(state, node, 1.seconds) // already finished -- must not run again

        assertEquals(1, callCount)
    }

    @Test
    fun `removeFromParent removes the node when the action runs`() {
        val parent = SKNode()
        val child = SKNode()
        parent.addChild(child)
        val state = SKActionState(SKAction.removeFromParent())

        stepAction(state, child, Duration.ZERO)

        assertNull(child.parent)
        assertTrue(parent.children.isEmpty())
    }

    @Test
    fun `shortestAngleDelta takes the direct path when it's already the short way around`() {
        val delta = shortestAngleDelta(from = 0.1f, to = 3.0f) // 2.9 rad forward, under PI: already shortest

        assertEquals(2.9f, delta, absoluteTolerance = 1e-4f)
    }

    @Test
    fun `shortestAngleDelta wraps around the +-PI boundary rather than taking the long way`() {
        // Going forward from 0 to PI+0.1 is longer than wrapping backward by PI-0.1.
        val delta = shortestAngleDelta(from = 0f, to = PI.toFloat() + 0.1f)

        assertEquals(0.1f - PI.toFloat(), delta, absoluteTolerance = 1e-4f)
    }

    @Test
    fun `rotateTo drives zRotation via shortestAngleDelta`() {
        val node = SKNode().apply { zRotation = 0f }
        val state = SKActionState(SKAction.rotateTo(PI.toFloat() + 0.1f, 1.seconds))

        stepAction(state, node, 1.seconds)

        assertEquals(0.1f - PI.toFloat(), node.zRotation, absoluteTolerance = 1e-4f)
    }

    @Test
    fun `resizeTo and colorize are no-ops on a plain SKNode`() {
        val node = SKNode().apply { position = Vector2.Zero }
        val resizeState = SKActionState(SKAction.resizeTo(Vector2(50f, 50f), 1.seconds))
        val colorizeState = SKActionState(SKAction.colorize(0xFFFF0000.toInt(), 1f, 1.seconds))

        stepAction(resizeState, node, 1.seconds) // must not throw
        stepAction(colorizeState, node, 1.seconds) // must not throw

        assertEquals(Vector2.Zero, node.position) // nothing else on the plain node was disturbed
    }

    @Test
    fun `resizeTo and colorize animate an SKSpriteNode`() {
        val sprite = SKSpriteNode(size = Vector2(10f, 10f), color = 0xFF000000.toInt())
        val resizeState = SKActionState(SKAction.resizeTo(Vector2(20f, 30f), 1.seconds))
        val colorizeState = SKActionState(SKAction.colorize(0xFFFFFFFF.toInt(), 1f, 1.seconds))

        stepAction(resizeState, sprite, 1.seconds)
        stepAction(colorizeState, sprite, 1.seconds)

        assertEquals(Vector2(20f, 30f), sprite.size)
        assertEquals(0xFFFFFFFF.toInt(), sprite.color)
        assertEquals(1f, sprite.colorBlendFactor)
    }

    @Test
    fun `customAction receives the raw elapsed time, not the eased progress`() {
        val node = SKNode()
        val observed = mutableListOf<Duration>()
        val state = SKActionState(SKAction.customAction(2.seconds) { _, elapsed -> observed += elapsed })

        stepAction(state, node, 1.seconds)
        stepAction(state, node, 1.seconds)

        assertEquals(listOf(1.seconds, 2.seconds), observed)
    }

    @Test
    fun `animationFrameIndex picks the frame for the given elapsed time, clamped to the last one`() {
        assertEquals(0, animationFrameIndex(0.seconds, timePerFrame = 1.seconds, textureCount = 3))
        assertEquals(0, animationFrameIndex(0.9.seconds, timePerFrame = 1.seconds, textureCount = 3))
        assertEquals(1, animationFrameIndex(1.seconds, timePerFrame = 1.seconds, textureCount = 3))
        assertEquals(2, animationFrameIndex(2.5.seconds, timePerFrame = 1.seconds, textureCount = 3))
        assertEquals(2, animationFrameIndex(100.seconds, timePerFrame = 1.seconds, textureCount = 3)) // clamped
    }

    @Test
    fun `easing curves map the midpoint as expected`() {
        assertEquals(0.5f, SKActionTimingMode.Linear.ease(0.5f))
        assertEquals(0.25f, SKActionTimingMode.EaseIn.ease(0.5f))
        assertEquals(0.75f, SKActionTimingMode.EaseOut.ease(0.5f))
        assertEquals(0.5f, SKActionTimingMode.EaseInEaseOut.ease(0.5f))
    }

    @Test
    fun `a custom timingFunction overrides timingMode`() {
        val node = SKNode()
        val action = SKAction.moveTo(Vector2(10f, 0f), 1.seconds).apply { timingFunction = { 1f } }
        val state = SKActionState(action)

        stepAction(state, node, 0.1.seconds) // barely started, but the override always reports "done"

        assertEquals(Vector2(10f, 0f), node.position)
    }

    @Test
    fun `speed scales how fast an action's time passes`() {
        val node = SKNode()
        val action = SKAction.moveTo(Vector2(10f, 0f), 2.seconds).apply { speed = 2f }
        val state = SKActionState(action)

        stepAction(state, node, 1.seconds) // 1s of wall time, but 2s of action time at 2x speed

        assertEquals(Vector2(10f, 0f), node.position)
    }

    @Test
    fun `reversed negates relative actions but leaves absolute ones unchanged`() {
        assertEquals(
            SKActionKind.MoveBy(Vector2(-5f, 2f)),
            SKAction.moveBy(Vector2(5f, -2f), 1.seconds).reversed().kind,
        )
        assertEquals(
            SKActionKind.MoveTo(Vector2(5f, -2f)),
            SKAction.moveTo(Vector2(5f, -2f), 1.seconds).reversed().kind,
        )
    }

    @Test
    fun `reversed on a sequence reverses both the order and each child`() {
        val a = SKAction.moveBy(Vector2(1f, 0f), 1.seconds)
        val b = SKAction.moveBy(Vector2(0f, 1f), 1.seconds)

        val reversedKind = SKAction.sequence(listOf(a, b)).reversed().kind as SKActionKind.Sequence

        assertEquals(SKActionKind.MoveBy(Vector2(0f, -1f)), reversedKind.actions[0].kind)
        assertEquals(SKActionKind.MoveBy(Vector2(-1f, 0f)), reversedKind.actions[1].kind)
    }

    @Test
    fun `hasActions, action(forKey), and finished-action cleanup via SKNode run and stepActions`() {
        val node = SKNode()
        assertFalse(node.hasActions())

        val action = SKAction.wait(1.seconds)
        node.run(action, withKey = "wait")
        assertTrue(node.hasActions())
        assertEquals(action, node.action("wait"))
        assertNull(node.action("missing"))

        node.stepActions(1.seconds)
        assertFalse(node.hasActions()) // the wait finished and was removed
        assertNull(node.action("wait"))
    }

    @Test
    fun `run with a key replaces any existing action under that key`() {
        val node = SKNode()
        node.run(SKAction.wait(5.seconds), withKey = "key")
        node.run(SKAction.wait(1.seconds), withKey = "key") // replaces the 5s wait

        node.stepActions(1.seconds)

        assertFalse(node.hasActions()) // only the second (1s) action was running, and it just finished
    }

    @Test
    fun `an isPaused node's own actions and its descendants' are skipped`() {
        val parent = SKNode().apply { isPaused = true }
        val child = SKNode()
        parent.addChild(child)
        var ran = false
        parent.run(SKAction.run { ran = true })
        child.run(SKAction.run { ran = true })

        parent.stepActions(1.seconds)

        assertFalse(ran)
        assertTrue(parent.hasActions())
        assertTrue(child.hasActions())
    }

    @Test
    fun `a completion block runs once, when the action finishes`() {
        val node = SKNode()
        var completions = 0
        node.run(SKAction.wait(1.seconds)) { completions++ }

        node.stepActions(0.5.seconds)
        assertEquals(0, completions)

        node.stepActions(0.5.seconds)
        assertEquals(1, completions)
    }
}
