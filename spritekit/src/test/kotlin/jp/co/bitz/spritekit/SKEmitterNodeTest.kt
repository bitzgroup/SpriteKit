package jp.co.bitz.spritekit

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SKEmitterNodeTest {
    private fun assertEquals(
        expected: Float,
        actual: Float,
        absoluteTolerance: Float = 1e-3f,
    ) {
        assertTrue(abs(expected - actual) <= absoluteTolerance, "expected $expected, was $actual")
    }

    @Test
    fun `particleBirthRate emits particles at the expected rate`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val emitter = SKEmitterNode().apply { particleBirthRate = 10f }
        scene.addChild(emitter)

        stepEmitters(scene, 1.seconds)

        assertEquals(10, emitter.particles.size)
    }

    @Test
    fun `numParticlesToEmit caps total emission even across multiple steps`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val emitter =
            SKEmitterNode().apply {
                particleBirthRate = 10f
                numParticlesToEmit = 5
                particleLifetime = 100f
            }
        scene.addChild(emitter)

        stepEmitters(scene, 1.seconds)
        assertEquals(5, emitter.particles.size)
        assertEquals(5, emitter.totalEmitted)

        stepEmitters(scene, 1.seconds) // no more should be emitted

        assertEquals(5, emitter.particles.size)
    }

    @Test
    fun `a particle is removed once it exceeds its lifetime`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val emitter =
            SKEmitterNode().apply {
                particleBirthRate = 1f
                numParticlesToEmit = 1
                particleLifetime = 2f
            }
        scene.addChild(emitter)

        stepEmitters(scene, 1.seconds) // emits the one particle, age 0
        assertEquals(1, emitter.particles.size)

        stepEmitters(scene, 1.seconds) // age -> 1s, still under the 2s lifetime
        assertEquals(1, emitter.particles.size)

        stepEmitters(scene, 2.seconds) // age -> 3s, past the 2s lifetime
        assertTrue(emitter.particles.isEmpty())
    }

    @Test
    fun `an isPaused emitter doesn't step`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val emitter =
            SKEmitterNode().apply {
                particleBirthRate = 10f
                isPaused = true
            }
        scene.addChild(emitter)

        stepEmitters(scene, 1.seconds)

        assertTrue(emitter.particles.isEmpty())
    }

    @Test
    fun `xAcceleration and yAcceleration accelerate particles over their lifetime`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val emitter =
            SKEmitterNode().apply {
                particleBirthRate = 1f
                numParticlesToEmit = 1
                yAcceleration = -10f
                particleLifetime = 100f
            }
        scene.addChild(emitter)

        stepEmitters(scene, 1.seconds) // spawns the particle this step, before acceleration applies
        val particle = emitter.particles.single()
        assertEquals(0f, particle.velocity.y)

        stepEmitters(scene, 1.seconds) // ages it under yAcceleration

        assertEquals(-10f, particle.velocity.y)
        assertEquals(-10f, particle.position.y)
    }

    @Test
    fun `resetSimulation clears particles and emission counters`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val emitter = SKEmitterNode().apply { particleBirthRate = 5f }
        scene.addChild(emitter)
        stepEmitters(scene, 1.seconds)
        assertTrue(emitter.particles.isNotEmpty())

        emitter.resetSimulation()

        assertTrue(emitter.particles.isEmpty())
        assertEquals(0, emitter.totalEmitted)
    }

    @Test
    fun `advanceSimulationTime emits and ages particles without needing a scene`() {
        val emitter =
            SKEmitterNode().apply {
                particleBirthRate = 10f
                particleLifetime = 100f
            }

        emitter.advanceSimulationTime(1f)

        assertTrue(emitter.particles.isNotEmpty())
        assertTrue(emitter.particles.all { it.age > 0f })
    }

    @Test
    fun `a matching field accelerates particles opted in via fieldBitMask`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val field = SKFieldNode.linearGravityField(Vector2(0f, -1f)).apply { strength = 20f }
        scene.addChild(field)
        val emitter =
            SKEmitterNode().apply {
                particleBirthRate = 1f
                numParticlesToEmit = 1
                particleLifetime = 100f
                fieldBitMask = -1
            }
        scene.addChild(emitter)

        stepEmitters(scene, 1.seconds) // spawns the particle
        stepEmitters(scene, 1.seconds) // ages it under the field's acceleration

        assertEquals(-20f, emitter.particles.single().velocity.y)
    }

    @Test
    fun `fieldBitMask defaults to 0, so fields don't affect particles unless opted in`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val field = SKFieldNode.linearGravityField(Vector2(0f, -1f)).apply { strength = 20f }
        scene.addChild(field)
        val emitter =
            SKEmitterNode().apply {
                particleBirthRate = 1f
                numParticlesToEmit = 1
                particleLifetime = 100f
            }
        scene.addChild(emitter)

        stepEmitters(scene, 1.seconds)
        stepEmitters(scene, 1.seconds)

        assertEquals(0f, emitter.particles.single().velocity.y)
    }
}
