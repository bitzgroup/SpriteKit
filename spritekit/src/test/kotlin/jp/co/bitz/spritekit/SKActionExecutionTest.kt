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

    // Expectations below were recorded from Apple's SpriteKit on iOS/tvOS simulators — see the
    // tic-tac-toe sample's SpriteKitParity probes (SKActionParityTests).

    @Test
    fun `rotateTo interpolates the long way by default and ends exactly on the target`() {
        val node = SKNode().apply { zRotation = 0.1f }
        val state = SKActionState(SKAction.rotateTo(6f, 1.seconds))

        stepAction(state, node, 0.5.seconds)
        assertEquals(3.05f, node.zRotation, absoluteTolerance = 1e-4f)
        stepAction(state, node, 0.5.seconds)
        assertEquals(6f, node.zRotation, absoluteTolerance = 1e-6f)
    }

    @Test
    fun `rotateTo with shortestUnitArc turns the short way and still ends exactly on the target`() {
        val node = SKNode().apply { zRotation = 0.1f }
        val state = SKActionState(SKAction.rotateTo(6f, 1.seconds, shortestUnitArc = true))

        stepAction(state, node, 0.5.seconds)
        // Halfway along the short arc, expressed relative to the target (6 < value < 0.1 + 2π).
        assertTrue(node.zRotation > 6f && node.zRotation < 6.4f, "was ${node.zRotation}")
        stepAction(state, node, 0.5.seconds)
        assertEquals(6f, node.zRotation, absoluteTolerance = 1e-6f)

        val far = SKNode().apply { zRotation = 7f }
        stepAction(SKActionState(SKAction.rotateTo(0.5f, 1.seconds, shortestUnitArc = true)), far, 1.seconds)
        assertEquals(0.5f, far.zRotation, absoluteTolerance = 1e-6f)
    }

    @Test
    fun `scaleBy multiplies the current scale and its reverse divides`() {
        val node =
            SKNode().apply {
                xScale = 2f
                yScale = 3f
            }
        stepAction(SKActionState(SKAction.scaleBy(2f, 1.seconds)), node, 1.seconds)
        assertEquals(4f, node.xScale, absoluteTolerance = 1e-5f)
        assertEquals(6f, node.yScale, absoluteTolerance = 1e-5f)

        val axes =
            SKNode().apply {
                xScale = 2f
                yScale = 3f
            }
        stepAction(SKActionState(SKAction.scaleBy(2f, 0.5f, 1.seconds)), axes, 1.seconds)
        assertEquals(4f, axes.xScale, absoluteTolerance = 1e-5f)
        assertEquals(1.5f, axes.yScale, absoluteTolerance = 1e-5f)

        val reversed =
            SKNode().apply {
                xScale = 2f
                yScale = 3f
            }
        stepAction(SKActionState(SKAction.scaleBy(2f, 1.seconds).reversed()), reversed, 1.seconds)
        assertEquals(1f, reversed.xScale, absoluteTolerance = 1e-5f)
        assertEquals(1.5f, reversed.yScale, absoluteTolerance = 1e-5f)
    }

    @Test
    fun `single-axis moves and scales leave the other axis alone`() {
        val node =
            SKNode().apply {
                position = Vector2(1f, 2f)
                xScale = 2f
                yScale = 3f
            }
        stepAction(SKActionState(SKAction.moveToY(9f, 1.seconds)), node, 1.seconds)
        stepAction(SKActionState(SKAction.scaleXTo(5f, 1.seconds)), node, 1.seconds)
        assertEquals(Vector2(1f, 9f), node.position)
        assertEquals(5f, node.xScale)
        assertEquals(3f, node.yScale)

        stepAction(SKActionState(SKAction.moveToX(4f, 1.seconds)), node, 1.seconds)
        stepAction(SKActionState(SKAction.scaleYTo(7f, 1.seconds)), node, 1.seconds)
        assertEquals(Vector2(4f, 9f), node.position)
        assertEquals(5f, node.xScale)
        assertEquals(7f, node.yScale)
    }

    @Test
    fun `resizeToWidth and resizeToHeight change one dimension each`() {
        val sprite = SKSpriteNode(size = Vector2(10f, 20f))
        stepAction(SKActionState(SKAction.resizeToWidth(30f, 1.seconds)), sprite, 1.seconds)
        assertEquals(Vector2(30f, 20f), sprite.size)
        stepAction(SKActionState(SKAction.resizeToHeight(40f, 1.seconds)), sprite, 1.seconds)
        assertEquals(Vector2(30f, 40f), sprite.size)
    }

    @Test
    fun `scaleTo a size scales a sprite to it whatever its current scale and ignores other nodes`() {
        val sprite = SKSpriteNode(size = Vector2(10f, 20f)).apply { xScale = 2f }
        stepAction(SKActionState(SKAction.scaleTo(Vector2(40f, 10f), 1.seconds)), sprite, 1.seconds)
        assertEquals(4f, sprite.xScale, absoluteTolerance = 1e-5f)
        assertEquals(0.5f, sprite.yScale, absoluteTolerance = 1e-5f)
        assertEquals(Vector2(40f, 10f), sprite.size)

        val plain = SKNode()
        stepAction(SKActionState(SKAction.scaleTo(Vector2(40f, 10f), 1.seconds)), plain, 1.seconds)
        assertEquals(1f, plain.xScale)
        assertEquals(1f, plain.yScale)
    }

    @Test
    fun `speedTo sets and speedBy multiplies the node's speed, reversed dividing`() {
        val node = SKNode()
        stepAction(SKActionState(SKAction.speedTo(3f, 1.seconds)), node, 1.seconds)
        assertEquals(3f, node.speed, absoluteTolerance = 1e-5f)
        stepAction(SKActionState(SKAction.speedBy(0.5f, 1.seconds)), node, 1.seconds)
        assertEquals(1.5f, node.speed, absoluteTolerance = 1e-5f)
        stepAction(SKActionState(SKAction.speedBy(0.5f, 1.seconds).reversed()), node, 1.seconds)
        assertEquals(3f, node.speed, absoluteTolerance = 1e-5f)
    }

    @Test
    fun `a leaf action's duration can be reassigned but a sequence's reassignment doesn't change its run`() {
        val move = SKAction.moveBy(Vector2(10f, 0f), 5.seconds).apply { duration = 1.seconds }
        val node = SKNode()
        assertTrue(stepAction(SKActionState(move), node, 1.seconds) != null)
        assertEquals(Vector2(10f, 0f), node.position)

        val sequence =
            SKAction.sequence(
                listOf(SKAction.moveBy(Vector2(10f, 0f), 1.seconds), SKAction.moveBy(Vector2(10f, 0f), 1.seconds)),
            )
        assertEquals(2.seconds, sequence.duration)
        sequence.duration = 0.1.seconds
        val other = SKNode()
        val state = SKActionState(sequence)
        assertNull(stepAction(state, other, 1.seconds))
        assertTrue(stepAction(state, other, 1.seconds) != null)
        assertEquals(Vector2(20f, 0f), other.position)
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
    fun `colorize animates an SKLabelNode, as on Apple`() {
        val label = SKLabelNode("M").apply { color = 0xFF000000.toInt() }
        val state = SKActionState(SKAction.colorize(0xFFFFFFFF.toInt(), 1f, 2.seconds))

        stepAction(state, label, 1.seconds)
        assertEquals(0.5f, label.colorBlendFactor)
        stepAction(state, label, 1.seconds)

        assertEquals(0xFFFFFFFF.toInt(), label.color)
        assertEquals(1f, label.colorBlendFactor)
    }

    @Test
    fun `an SKLabelNode's color, colorBlendFactor and blendMode default as on Apple`() {
        val label = SKLabelNode("M")

        assertEquals(0xFFFFFFFF.toInt(), label.color)
        assertEquals(0f, label.colorBlendFactor)
        assertEquals(SKBlendMode.Alpha, label.blendMode)
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
    fun `run onChildWithName starts the action on the first direct child with that name`() {
        val parent = SKNode()
        val first = SKNode().apply { name = "face" }
        val second = SKNode().apply { name = "face" }
        parent.addChild(first)
        parent.addChild(second)

        parent.run(SKAction.run(SKAction.hide(), onChildWithName = "face"))
        parent.stepActions(0.seconds)
        first.stepActions(0.seconds)

        assertTrue(first.isHidden)
        assertFalse(second.isHidden)
    }

    @Test
    fun `run onChildWithName takes no time itself and ignores a missing child`() {
        val parent = SKNode()
        var ranAfter = false
        parent.run(
            SKAction.sequence(
                listOf(
                    SKAction.run(SKAction.wait(5.seconds), onChildWithName = "missing"),
                    SKAction.run { ranAfter = true },
                ),
            ),
        )

        parent.stepActions(0.seconds)

        assertTrue(ranAfter)
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

    // `follow` touches real android.graphics.Path/PathMeasure APIs, so — like `flattenPath` —
    // it isn't covered by unit tests here; see applyFollow's KDoc and docs/ROADMAP.md's testing
    // notes.
}
