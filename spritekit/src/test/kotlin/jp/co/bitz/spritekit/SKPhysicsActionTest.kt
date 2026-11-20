package jp.co.bitz.spritekit

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Mirrors the tic-tac-toe sample's `SKPhysicsActionParityTests` probes. */
class SKPhysicsActionTest {
    private val frame = (1.0 / 60.0).seconds

    private fun assertNear(
        expected: Float,
        actual: Float,
        tolerance: Float = 1e-3f,
    ) = assertTrue(abs(expected - actual) <= tolerance * maxOf(1f, abs(expected)), "expected $expected, was $actual")

    private fun bodyNode(at: Vector2 = Vector2(200f, 200f)): SKNode {
        val scene = SKScene(size = Vector2(400f, 400f))
        return SKNode().apply {
            position = at
            physicsBody = SKPhysicsBody.circleOfRadius(10f)
            scene.addChild(this)
        }
    }

    /** Runs [action] on [node] a 60th of a second at a time, returning [sample] after each frame. */
    private fun <T> frames(
        action: SKAction,
        node: SKNode,
        count: Int,
        sample: () -> T,
    ): List<T> {
        val state = SKActionState(action)
        return List(count) {
            stepAction(state, node, frame)
            sample()
        }
    }

    @Test
    fun `an impulse action spreads its impulse evenly over its duration`() {
        val node = bodyNode()
        val vs =
            frames(SKAction.applyImpulse(Vector2(10f, 0f), 0.2.seconds), node, 14) { node.physicsBody!!.velocity.x }
        // Apple: 59.6831 more each frame for 12 frames, then nothing — 10 / mass in all.
        assertNear(59.6831f, vs[0])
        assertNear(119.3662f, vs[1])
        assertNear(716.197f, vs[11])
        assertNear(716.197f, vs[13])
    }

    @Test
    fun `a force action pushes as the force would for its duration`() {
        val node = bodyNode()
        val vs = frames(SKAction.applyForce(Vector2(10f, 0f), 0.2.seconds), node, 12) { node.physicsBody!!.velocity.x }
        assertNear(11.9366f, vs[0]) // Apple: F / m / 60 a frame
        assertNear(143.239f, vs[11])
    }

    @Test
    fun `a timing mode shapes how the push builds up`() {
        val node = bodyNode()
        val action =
            SKAction.applyImpulse(
                Vector2(10f, 0f),
                0.2.seconds,
            ).apply { timingMode = SKActionTimingMode.EaseIn }
        val vs = frames(action, node, 12) { node.physicsBody!!.velocity.x }
        val t = 6f / 12f
        assertNear(716.197f * SKActionTimingMode.EaseIn.ease(t), vs[5])
        assertNear(716.197f, vs[11])
    }

    @Test
    fun `torque and angular impulse actions turn the body frame by frame`() {
        val torqued = bodyNode()
        val ws =
            frames(SKAction.applyTorque(0.001f, 0.2.seconds), torqued, 12) { torqued.physicsBody!!.angularVelocity }
        assertNear(0.53715f, ws[0]) // Apple: 0.5371 a frame
        assertNear(6.4458f, ws[11])

        val spun = bodyNode()
        val ss =
            frames(SKAction.applyAngularImpulse(0.001f, 0.2.seconds), spun, 12) { spun.physicsBody!!.angularVelocity }
        assertNear(2.6857f, ss[0]) // Apple: 2.6857 a frame
        assertNear(32.2289f, ss[11])
    }

    @Test
    fun `a force or impulse action at a point off the center of mass also turns the body`() {
        val forced = bodyNode(Vector2(250f, 50f))
        frames(SKAction.applyForce(Vector2(0f, 10f), Vector2(260f, 50f), 0.5.seconds), forced, 30) { }
        assertNear(358.0985f, forced.physicsBody!!.velocity.y) // Apple: 358.0985, ω 71.6195
        assertNear(71.6195f, forced.physicsBody!!.angularVelocity)

        val hit = bodyNode(Vector2(300f, 50f))
        frames(SKAction.applyImpulse(Vector2(0f, 10f), Vector2(310f, 50f), 0.5.seconds), hit, 30) { }
        assertNear(716.197f, hit.physicsBody!!.velocity.y) // Apple: 716.197, ω 143.239
        assertNear(143.239f, hit.physicsBody!!.angularVelocity)
    }

    @Test
    fun `an instant impulse action does nothing and a reversed one pushes the same way`() {
        val instant = bodyNode()
        frames(SKAction.applyImpulse(Vector2(10f, 0f), Duration.ZERO), instant, 2) { }
        assertEquals(Vector2.Zero, instant.physicsBody!!.velocity)

        val reversed = bodyNode()
        frames(SKAction.applyImpulse(Vector2(10f, 0f), 0.2.seconds).reversed(), reversed, 12) { }
        assertNear(716.197f, reversed.physicsBody!!.velocity.x)
    }

    @Test
    fun `mass actions change the mass and with it the density`() {
        val node = bodyNode()
        val body = node.physicsBody!!
        val start = body.mass
        val masses = frames(SKAction.changeMassTo(2f, 0.4.seconds), node, 24) { body.mass }
        assertNear(start + (2f - start) * 0.5f, masses[11])
        assertNear(2f, masses[23])
        assertNear(2f / body.area, body.density)

        body.mass = 3f
        frames(SKAction.changeMassBy(2f, 0.2.seconds), node, 12) { }
        assertNear(5f, body.mass) // Apple: 3 + 2
        frames(SKAction.changeMassBy(2f, 0.2.seconds).reversed(), node, 12) { }
        assertNear(5f, body.mass)
    }

    @Test
    fun `charge actions change the charge, and reversed ones don't`() {
        val node = bodyNode()
        val body = node.physicsBody!!
        assertEquals(0f, body.charge)
        frames(SKAction.changeChargeTo(3f, 0.4.seconds), node, 24) { }
        assertNear(3f, body.charge)
        frames(SKAction.changeChargeBy(2f, 0.4.seconds), node, 24) { }
        assertNear(5f, body.charge)
        frames(SKAction.changeChargeBy(2f, Duration.ZERO).reversed(), node, 1) { }
        frames(SKAction.changeChargeTo(7f, Duration.ZERO).reversed(), node, 1) { }
        assertNear(5f, body.charge) // Apple: reversed charge actions change nothing
    }

    @Test
    fun `strength actions set or multiply a field's strength`() {
        val field = SKFieldNode.radialGravityField()
        val strengths = frames(SKAction.strengthTo(5f, 0.4.seconds), field, 24) { field.strength }
        assertNear(3f, strengths[11])
        assertNear(5f, strengths[23])

        val multiplied = SKFieldNode.radialGravityField()
        val ms = frames(SKAction.strengthBy(3f, 0.4.seconds), multiplied, 24) { multiplied.strength }
        assertNear(2f, ms[11]) // Apple: 1.51 a quarter of the way, 3 at the end
        assertNear(3f, ms[23])

        val divided = SKFieldNode.radialGravityField()
        frames(SKAction.strengthBy(3f, Duration.ZERO).reversed(), divided, 1) { }
        assertNear(1f / 3f, divided.strength) // Apple: 0.3333
        frames(SKAction.strengthTo(3f, Duration.ZERO).reversed(), divided, 1) { }
        assertNear(1f / 3f, divided.strength) // Apple: unchanged
    }

    @Test
    fun `falloff actions leave the falloff as it is and never finish, as Apple's do`() {
        val field = SKFieldNode.radialGravityField().apply { falloff = 3f }
        val to = SKActionState(SKAction.falloffTo(1f, 0.2.seconds))
        val by = SKActionState(SKAction.falloffBy(2f, Duration.ZERO))
        val sequence = SKActionState(SKAction.sequence(listOf(SKAction.falloffTo(1f, 0.1.seconds), SKAction.hide())))
        repeat(60) {
            assertEquals(null, stepAction(to, field, frame))
            assertEquals(null, stepAction(by, field, frame))
            assertEquals(null, stepAction(sequence, field, frame))
        }
        assertEquals(3f, field.falloff)
        assertEquals(false, field.isHidden)
        assertEquals(null, stepAction(SKActionState(SKAction.falloffTo(1f, 0.1.seconds)), SKNode(), 1.seconds))
        assertEquals(0.3.seconds, SKAction.falloffTo(1f, 0.3.seconds).duration)
    }

    @Test
    fun `physics actions do nothing to a node without a body or field`() {
        val node = SKNode()
        val actions =
            listOf(
                SKAction.applyImpulse(Vector2(1f, 0f), 0.1.seconds),
                SKAction.applyTorque(1f, 0.1.seconds),
                SKAction.changeMassTo(1f, 0.1.seconds),
                SKAction.changeChargeBy(1f, 0.1.seconds),
                SKAction.strengthTo(1f, 0.1.seconds),
            )
        for (action in actions) assertEquals(Duration.ZERO, stepAction(SKActionState(action), node, 0.1.seconds))
    }
}
