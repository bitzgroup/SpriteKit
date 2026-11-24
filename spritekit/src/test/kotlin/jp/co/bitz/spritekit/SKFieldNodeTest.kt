package jp.co.bitz.spritekit

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SKFieldNodeTest {
    private fun assertEquals(
        expected: Float,
        actual: Float,
        absoluteTolerance: Float = 1e-3f,
    ) {
        assertTrue(abs(expected - actual) <= absoluteTolerance, "expected $expected, was $actual")
    }

    @Test
    fun `a radial gravity field pulls a body towards its position`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        scene.physicsWorld.gravity = Vector2.Zero
        val field =
            SKFieldNode.radialGravityField().apply {
                strength = 10f
                falloff = 0f
            }
        scene.addChild(field) // at the scene's origin

        val bodyNode =
            SKNode().apply {
                position = Vector2(1000f, 0f) // far enough not to reach the field within the second
                physicsBody = SKPhysicsBody.circleOfRadius(1f).apply { linearDamping = 0f }
            }
        scene.addChild(bodyNode)

        simulatePhysics(scene, 1.seconds)

        // Constant-strength (falloff 0) pull toward the field, i.e. in the -x direction:
        // strength 10 is 10 m/s², 1500 points/s².
        assertEquals(-1500f, bodyNode.physicsBody!!.velocity.x, 0.05f)
        assertEquals(0f, bodyNode.physicsBody!!.velocity.y)
    }

    @Test
    fun `a negative-strength radial gravity field repels instead of attracts`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        scene.physicsWorld.gravity = Vector2.Zero
        val field = SKFieldNode.radialGravityField().apply { strength = -10f }
        scene.addChild(field)

        val bodyNode =
            SKNode().apply {
                position = Vector2(10f, 0f)
                physicsBody = SKPhysicsBody.circleOfRadius(1f).apply { linearDamping = 0f }
            }
        scene.addChild(bodyNode)

        simulatePhysics(scene, 1.seconds)

        assertTrue(bodyNode.physicsBody!!.velocity.x > 0f, "expected the body to be pushed away, not pulled in")
    }

    @Test
    fun `a linear gravity field accelerates bodies uniformly along its direction`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        scene.physicsWorld.gravity = Vector2.Zero
        val field = SKFieldNode.linearGravityField(Vector2(0f, -1f)).apply { strength = 5f }
        scene.addChild(field)

        val bodyNode = SKNode().apply { physicsBody = SKPhysicsBody.circleOfRadius(1f).apply { linearDamping = 0f } }
        scene.addChild(bodyNode)

        simulatePhysics(scene, 1.seconds)

        assertEquals(-750f, bodyNode.physicsBody!!.velocity.y, 0.05f) // 5 m/s²
    }

    @Test
    fun `fields start with Apple's defaults`() {
        val radial = SKFieldNode.radialGravityField()
        assertEquals(2f, radial.falloff)
        assertEquals(0.0045776367f, radial.minimumRadius, 1e-9f)
        assertEquals(1f, radial.strength)
        for (field in listOf(
            SKFieldNode.linearGravityField(Vector2(0f, -1f)),
            SKFieldNode.dragField(),
            SKFieldNode.velocityField(Vector2(1f, 0f)),
        )) {
            assertEquals(0f, field.falloff)
            assertEquals(0.0045776367f, field.minimumRadius, 1e-9f)
        }
    }

    // Recorded on iOS/tvOS simulators: a gravity field accelerates at strength × 150 points/s²
    // divided by (distance in meters of 150 points)^falloff, the distance never below minimumRadius.
    private fun firstStepVelocity(
        field: SKFieldNode,
        distance: Float,
    ): Vector2 {
        val scene = SKScene(size = Vector2(400f, 400f))
        scene.physicsWorld.gravity = Vector2.Zero
        scene.addChild(field)
        val body = SKPhysicsBody.circleOfRadius(2f).apply { linearDamping = 0f }
        scene.addChild(SKNode().apply { position = Vector2(distance, 0f) }.also { it.physicsBody = body })
        applyFieldForces(scene, listOf(SKPhysicsEntry(scene.children.last(), body)), dt = 1f / 60f)
        return body.velocity
    }

    @Test
    fun `a radial field's default falloff of 2 is inverse-square in meters`() {
        // 2.5 points/s a frame (150 points/s²) one meter away; 4× at half the distance, ¼ at twice.
        assertEquals(-2.5f, firstStepVelocity(SKFieldNode.radialGravityField(), 150f).x)
        assertEquals(-10f, firstStepVelocity(SKFieldNode.radialGravityField(), 75f).x)
        assertEquals(-0.625f, firstStepVelocity(SKFieldNode.radialGravityField(), 300f).x)
        val linear = SKFieldNode.radialGravityField().apply { falloff = 1f }
        assertEquals(-1.25f, firstStepVelocity(linear, 300f).x)
    }

    @Test
    fun `minimumRadius is in points and caps how strong falloff makes a field`() {
        val field = SKFieldNode.radialGravityField().apply { minimumRadius = 50f }
        // At 30 points the field acts as if 50 points (1/3 m) away: 2.5 × 9.
        assertEquals(-22.5f, firstStepVelocity(field, 30f).x)
    }

    @Test
    fun `a linear field's vector isn't normalized and falloff applies to it too`() {
        val field = SKFieldNode.linearGravityField(Vector2(-2f, 0f)).apply { strength = 3f }
        assertEquals(-15f, firstStepVelocity(field, 150f).x) // 2 × 3 m/s², 900 points/s²
        val fallingOff = SKFieldNode.linearGravityField(Vector2(-1f, 0f)).apply { falloff = 2f }
        assertEquals(-62.5f, firstStepVelocity(fallingOff, 30f).x) // 2.5 / 0.2²
    }

    @Test
    fun `a drag field decelerates a moving body proportionally to its velocity`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        scene.physicsWorld.gravity = Vector2.Zero
        val field = SKFieldNode.dragField().apply { strength = 0.5f }
        scene.addChild(field)

        val bodyNode =
            SKNode().apply {
                physicsBody =
                    SKPhysicsBody.circleOfRadius(1f).apply {
                        linearDamping = 0f
                        velocity = Vector2(10f, 0f)
                    }
            }
        scene.addChild(bodyNode)

        simulatePhysics(scene, 1.seconds)

        // Strength 0.5 takes off half the velocity a second, applied over 120 substeps:
        // 10 × (1 − 0.5/120)^120.
        assertEquals(6.0590f, bodyNode.physicsBody!!.velocity.x, 1e-3f)
    }

    @Test
    fun `a velocity field overrides a body's velocity directly rather than accelerating it`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val field = SKFieldNode.velocityField(Vector2(3f, 4f)).apply { strength = 1f }
        scene.addChild(field)

        val bodyNode =
            SKNode().apply {
                physicsBody =
                    SKPhysicsBody.circleOfRadius(1f).apply {
                        linearDamping = 0f
                        velocity = Vector2(100f, 100f) // should be overridden, not just nudged
                    }
            }
        scene.addChild(bodyNode)

        simulatePhysics(scene, 1.seconds)

        assertEquals(Vector2(3f, 4f), bodyNode.physicsBody!!.velocity)
    }

    @Test
    fun `a disabled field has no effect`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        scene.physicsWorld.gravity = Vector2.Zero
        val field =
            SKFieldNode.linearGravityField(Vector2(0f, -1f)).apply {
                strength = 5f
                isEnabled = false
            }
        scene.addChild(field)

        val bodyNode = SKNode().apply { physicsBody = SKPhysicsBody.circleOfRadius(1f) }
        scene.addChild(bodyNode)

        simulatePhysics(scene, 1.seconds)

        assertEquals(Vector2.Zero, bodyNode.physicsBody!!.velocity)
    }

    @Test
    fun `a field whose categoryBitMask excludes a body's fieldBitMask has no effect on it`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        scene.physicsWorld.gravity = Vector2.Zero
        val field =
            SKFieldNode.linearGravityField(Vector2(0f, -1f)).apply {
                strength = 5f
                categoryBitMask = 1
            }
        scene.addChild(field)

        val bodyNode =
            SKNode().apply {
                physicsBody = SKPhysicsBody.circleOfRadius(1f).apply { fieldBitMask = 2 } // no overlap with category 1
            }
        scene.addChild(bodyNode)

        simulatePhysics(scene, 1.seconds)

        assertEquals(Vector2.Zero, bodyNode.physicsBody!!.velocity)
    }

    @Test
    fun `a field doesn't affect a non-dynamic body`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val field = SKFieldNode.linearGravityField(Vector2(0f, -1f)).apply { strength = 5f }
        scene.addChild(field)

        val bodyNode = SKNode().apply { physicsBody = SKPhysicsBody.circleOfRadius(1f).apply { isDynamic = false } }
        scene.addChild(bodyNode)

        simulatePhysics(scene, 1.seconds)

        assertEquals(Vector2.Zero, bodyNode.physicsBody!!.velocity)
    }
}
