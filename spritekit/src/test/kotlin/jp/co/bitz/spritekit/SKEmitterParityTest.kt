package jp.co.bitz.spritekit

import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * What an emitter draws, against what Apple's draws — the expected values were recorded on
 * iOS/tvOS simulators by rendering one still particle and reading its pixels back.
 */
class SKEmitterParityTest {
    private val scene = SKScene(size = Vector2(100f, 100f))

    /** An emitter at the scene's center that emits one still, white, 10-point particle living [lifetime] seconds. */
    private fun emitter(lifetime: Float = 2f): SKEmitterNode =
        SKEmitterNode().apply {
            particleSize = Vector2(10f, 10f)
            particleBirthRate = 1000f
            numParticlesToEmit = 1
            particleLifetime = lifetime
            emissionAngle = 0f
            particleColor = WHITE
            position = Vector2(50f, 50f)
        }

    /** Steps [scene]'s emitters through [seconds] in 1/120 s frames, so motion integrates closely. */
    private fun run(
        seconds: Float,
        scene: SKScene = this.scene,
    ) {
        repeat((seconds * FRAMES_PER_SECOND).toInt()) { stepEmitters(scene, (1000.0 / FRAMES_PER_SECOND).milliseconds) }
    }

    /** Adds [emitter] to the scene, emits its particle and ages it [seconds]. */
    private fun aged(
        seconds: Float,
        emitter: SKEmitterNode,
    ): SKEmitterNode {
        if (emitter.parent == null) scene.addChild(emitter)
        stepEmitters(scene, 1.milliseconds)
        run(seconds)
        return emitter
    }

    private fun sequence(
        values: List<Float>,
        times: List<Float> = listOf(0f, 1f),
    ) = SKKeyframeSequence(values, times, SKKeyframeInterpolator.FloatValues)

    private fun single(): SKRenderCommand = buildRenderCommands(scene).single()

    private fun bounds(command: SKRenderCommand): List<Float> {
        val xs = command.vertices.map { it.position.x }
        val ys = command.vertices.map { it.position.y }
        return listOf(xs.min(), xs.max(), ys.min(), ys.max())
    }

    private fun assertBounds(
        expected: List<Float>,
        command: SKRenderCommand,
    ) {
        val actual = bounds(command)
        assertTrue(expected.zip(actual).all { (e, a) -> abs(e - a) <= 1e-3f }, "expected $expected, was $actual")
    }

    /** Apple's premultiplied pixel color, which is what the screen shows over black. */
    private fun assertPremultiplied(
        expected: List<Float>,
        command: SKRenderCommand,
    ) {
        val color = command.color
        val actual = listOf(color.r * color.a, color.g * color.a, color.b * color.a)
        assertTrue(expected.zip(actual).all { (e, a) -> abs(e - a) <= 0.01f }, "expected $expected, was $actual")
    }

    @Test
    fun `defaults match Apple's`() {
        val emitter = SKEmitterNode()

        assertEquals((PI / 2).toFloat(), emitter.emissionAngle)
        assertEquals(0f, emitter.particleLifetime)
        assertEquals(0x00FFFFFF, emitter.particleColor)
        assertEquals(0f, emitter.particleColorBlendFactor)
        assertEquals(SKParticleRenderOrder.OldestLast, emitter.particleRenderOrder)
        assertNull(emitter.targetNode)
        assertNull(emitter.particleAction)
        assertNull(emitter.particleAlphaSequence)
        assertNull(emitter.particleScaleSequence)
        assertNull(emitter.particleColorBlendFactorSequence)
        assertNull(emitter.particleColorSequence)
    }

    @Test
    fun `a particle with no lifetime counts as emitted but is never drawn`() {
        val emitter = aged(0.5f, emitter(lifetime = 0f))

        assertEquals(1, emitter.totalEmitted)
        assertTrue(buildRenderCommands(scene).isEmpty())
    }

    @Test
    fun `sequences are sampled by the fraction of the particle's lifetime`() {
        for ((age, brightness) in listOf(0.5f to 0.75f, 1f to 0.5f, 1.5f to 0.25f)) {
            val emitter = aged(age, emitter().apply { particleAlphaSequence = sequence(listOf(1f, 0f)) })
            assertPremultiplied(listOf(brightness, brightness, brightness), single())
            emitter.removeFromParent()
        }
        aged(0.5f, emitter().apply { particleAlphaSequence = sequence(listOf(1f, 0f), listOf(0f, 2f)) })
        assertPremultiplied(listOf(0.875f, 0.875f, 0.875f), single())
    }

    @Test
    fun `an alpha sequence replaces the alpha and its speed`() {
        val plain =
            aged(
                0.5f,
                emitter().apply {
                    particleAlpha = 0.5f
                    particleAlphaSpeed = -0.2f
                },
            )
        assertPremultiplied(listOf(0.4f, 0.4f, 0.4f), single())
        plain.removeFromParent()

        aged(
            0.5f,
            emitter().apply {
                particleAlpha = 0.5f
                particleAlphaSpeed = -0.2f
                particleAlphaSequence = sequence(listOf(1f, 0f))
            },
        )
        assertPremultiplied(listOf(0.75f, 0.75f, 0.75f), single())
    }

    @Test
    fun `a scale sequence replaces the scale and its speed`() {
        for ((age, size) in listOf(0.5f to 15f, 1f to 20f, 1.5f to 25f)) {
            val emitter =
                aged(
                    age,
                    emitter().apply {
                        particleScale = 3f
                        particleScaleSpeed = 1f
                        particleScaleSequence = sequence(listOf(1f, 3f))
                    },
                )
            assertBounds(listOf(50 - size / 2, 50 + size / 2, 50 - size / 2, 50 + size / 2), single())
            emitter.removeFromParent()
        }
        val steps = sequence(listOf(1f, 3f)).apply { interpolationMode = SKInterpolationMode.Step }
        aged(0.9f, emitter().apply { particleScaleSequence = steps })
        assertBounds(listOf(45f, 55f, 45f, 55f), single())
    }

    @Test
    fun `a color blend factor sequence replaces the blend factor`() {
        aged(
            1f,
            emitter().apply {
                particleColor = RED
                particleColorBlendFactorSequence = sequence(listOf(0f, 1f))
            },
        )
        assertPremultiplied(listOf(1f, 0.5f, 0.5f), single())
    }

    @Test
    fun `the blend factor mixes color and alpha toward the particle color`() {
        val mixed =
            aged(
                0.5f,
                emitter().apply {
                    particleColor = HALF_RED
                    particleColorBlendFactor = 0.5f
                },
            )
        assertPremultiplied(listOf(0.75f, 0.375f, 0.375f), single())
        mixed.removeFromParent()

        val unblended = aged(0.5f, emitter().apply { particleColor = HALF_RED })
        assertPremultiplied(listOf(1f, 1f, 1f), single())
        unblended.removeFromParent()

        aged(
            0.5f,
            emitter().apply {
                particleColor = SKEmitterNode().particleColor
                particleColorBlendFactor = 1f
            },
        )
        assertEquals(0f, single().color.a)
    }

    @Test
    fun `a color sequence replaces only the color`() {
        val colors = SKKeyframeSequence(listOf(HALF_RED), listOf(0f), SKKeyframeInterpolator.ColorValues)
        val unblended = aged(0.5f, emitter().apply { particleColorSequence = colors })
        assertPremultiplied(listOf(1f, 1f, 1f), single())
        unblended.removeFromParent()

        aged(
            0.5f,
            emitter().apply {
                particleColorBlendFactor = 0.5f
                particleColorSequence = colors
            },
        )
        assertPremultiplied(listOf(0.75f, 0.375f, 0.375f), single())
    }

    @Test
    fun `without a target particles move with the emitter`() {
        val emitter = aged(0.1f, emitter())
        emitter.position = Vector2(20f, 50f)

        assertBounds(listOf(15f, 25f, 45f, 55f), single())
    }

    @Test
    fun `a target keeps particles where they were born`() {
        val emitter = aged(0.1f, emitter().apply { targetNode = this@SKEmitterParityTest.scene })
        emitter.position = Vector2(20f, 50f)
        assertBounds(listOf(45f, 55f, 45f, 55f), single())

        run(0.2f)
        assertBounds(listOf(45f, 55f, 45f, 55f), single())
    }

    @Test
    fun `particles move with their target and take its alpha, not the emitter's`() {
        val target = SKNode().apply { position = Vector2(10f, 0f) }
        scene.addChild(target)
        val emitter = aged(0.1f, emitter().apply { targetNode = target })
        assertSame(target, emitter.particles.single().space)
        assertBounds(listOf(45f, 55f, 45f, 55f), single())

        target.position = Vector2(-20f, 0f)
        assertBounds(listOf(15f, 25f, 45f, 55f), single())
        target.alpha = 0.5f
        assertPremultiplied(listOf(0.5f, 0.5f, 0.5f), single())

        target.alpha = 1f
        emitter.alpha = 0.5f
        assertPremultiplied(listOf(1f, 1f, 1f), single())
        emitter.isHidden = true
        assertPremultiplied(listOf(1f, 1f, 1f), single())
        emitter.removeFromParent()
        assertTrue(buildRenderCommands(scene).isEmpty())
    }

    /** An emitter rotated a quarter turn and scaled 2×, emitting one 4-point particle. */
    private fun turnedEmitter(): SKEmitterNode =
        emitter().apply {
            zRotation = (PI / 2).toFloat()
            setScale(2f)
            particleSize = Vector2(4f, 4f)
        }

    @Test
    fun `without a target the emitter transforms motion and size`() {
        val moving = aged(1f, turnedEmitter().apply { particleSpeed = 10f })
        // Along the emitter's +x — the scene's +y — at twice the speed, and twice the size.
        assertBounds(listOf(46f, 54f, 66f, 74f), single())
        moving.removeFromParent()

        aged(
            1f,
            turnedEmitter().apply {
                setScale(1f)
                xAcceleration = 20f
            },
        )
        val center = bounds(single()).let { Vector2((it[0] + it[1]) / 2, (it[2] + it[3]) / 2) }
        assertTrue(abs(center.x - 50f) < 1e-3f && abs(center.y - 60f) < 0.5f, "was $center")
    }

    @Test
    fun `with a target motion and size are in the target's coordinates`() {
        val moving =
            aged(
                1f,
                turnedEmitter().apply {
                    targetNode = this@SKEmitterParityTest.scene
                    particleSpeed = 10f
                },
            )
        // The emission angle, speed and size ignore the emitter's rotation and scale.
        assertBounds(listOf(58f, 62f, 48f, 52f), single())
        moving.removeFromParent()

        aged(
            1f,
            turnedEmitter().apply {
                targetNode = this@SKEmitterParityTest.scene
                setScale(1f)
                xAcceleration = 20f
            },
        )
        val center = bounds(single()).let { Vector2((it[0] + it[1]) / 2, (it[2] + it[3]) / 2) }
        assertTrue(abs(center.x - 60f) < 0.5f && abs(center.y - 50f) < 1e-3f, "was $center")
    }

    @Test
    fun `the newest particle is drawn on top in every render order, whatever the z-position speed`() {
        for (order in SKParticleRenderOrder.entries) {
            for (zSpeed in listOf(0f, 1f, -1f)) {
                val scene = SKScene(size = Vector2(100f, 100f))
                val emitter =
                    SKEmitterNode().apply {
                        particleSize = Vector2(10f, 10f)
                        particleBirthRate = 1f
                        numParticlesToEmit = 2
                        particleLifetime = 10f
                        particleSpeed = 4f
                        emissionAngle = 0f
                        particleRenderOrder = order
                        particleZPositionSpeed = zSpeed
                    }
                scene.addChild(emitter)
                run(2.5f, scene)

                val commands = buildRenderCommands(scene)
                val (first, last) = commands.map { bounds(it)[0] }
                assertTrue(first > last, "older (further right) first, newer on top: $first, $last")
            }
        }
    }

    @Test
    fun `the z-position orders particles against other nodes but its speed does not`() {
        val cases = listOf(Triple(0f, 0f, false), Triple(1f, 0f, true), Triple(0f, 2f, false), Triple(1f, -2f, true))
        for ((z, speed, onTop) in cases) {
            val scene = SKScene(size = Vector2(100f, 100f))
            val sprite = SKSpriteNode(size = Vector2(40f, 40f)).apply { zPosition = 0.5f }
            scene.addChild(sprite)
            val emitter =
                emitter().apply {
                    particleZPosition = z
                    particleZPositionSpeed = speed
                }
            scene.addChild(emitter)
            stepEmitters(scene, 1.milliseconds)
            stepEmitters(scene, 0.5.seconds)

            // The particle is the 10-point quad; drawn last means drawn on top of the 40-point sprite.
            val drawnLast = buildRenderCommands(scene).last()
            assertEquals(onTop, bounds(drawnLast).let { it[1] - it[0] } < 20f, "z $z, speed $speed")
        }
    }

    @Test
    fun `a particle action changes nothing drawn`() {
        aged(1f, emitter().apply { particleAction = SKAction.moveBy(Vector2(20f, 0f), 0.3.seconds) })

        assertBounds(listOf(45f, 55f, 45f, 55f), single())
    }

    @Test
    fun `a copy keeps the sequences, target, render order and action`() {
        val target = SKNode()
        val alpha = sequence(listOf(1f, 0f))
        val action = SKAction.wait(1.seconds)
        val copy =
            emitter()
                .apply {
                    particleAlphaSequence = alpha
                    targetNode = target
                    particleRenderOrder = SKParticleRenderOrder.DontCare
                    particleAction = action
                }.copy()

        assertSame(alpha, copy.particleAlphaSequence)
        assertSame(target, copy.targetNode)
        assertEquals(SKParticleRenderOrder.DontCare, copy.particleRenderOrder)
        assertSame(action, copy.particleAction)
    }

    private companion object {
        const val FRAMES_PER_SECOND = 120
        const val WHITE = 0xFFFFFFFF.toInt()
        const val RED = 0xFFFF0000.toInt()
        const val HALF_RED = 0x80FF0000.toInt()
    }
}
