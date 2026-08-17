package jp.co.bitz.spritekit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SKRenderCommandListTest {
    @Test
    fun `an untextured sprite produces a command with a null texture and its world-space quad`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val sprite = SKSpriteNode(size = Vector2(4f, 2f)).apply { position = Vector2(10f, 20f) }
        scene.addChild(sprite)

        val commands = buildRenderCommands(scene)

        assertEquals(1, commands.size)
        val command = commands.single()
        assertNull(command.texture)
        assertEquals(
            listOf(
                Vector2(8f, 19f),
                Vector2(12f, 19f),
                Vector2(12f, 21f),
                Vector2(8f, 19f),
                Vector2(12f, 21f),
                Vector2(8f, 21f),
            ),
            command.vertices.map { it.position },
        )
    }

    @Test
    fun `a hidden sprite is excluded`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        scene.addChild(SKSpriteNode(size = Vector2(4f, 2f)).apply { isHidden = true })

        assertTrue(buildRenderCommands(scene).isEmpty())
    }

    @Test
    fun `a sprite under a hidden ancestor is excluded`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val layer = SKNode().apply { isHidden = true }
        scene.addChild(layer)
        layer.addChild(SKSpriteNode(size = Vector2(4f, 2f)))

        assertTrue(buildRenderCommands(scene).isEmpty())
    }

    @Test
    fun `a fully transparent sprite is excluded`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        scene.addChild(SKSpriteNode(size = Vector2(4f, 2f)).apply { alpha = 0f })

        assertTrue(buildRenderCommands(scene).isEmpty())
    }

    @Test
    fun `alpha accumulates multiplicatively down the tree`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val layer = SKNode().apply { alpha = 0.5f }
        scene.addChild(layer)
        layer.addChild(SKSpriteNode(size = Vector2(4f, 2f)).apply { alpha = 0.5f })

        val command = buildRenderCommands(scene).single()

        assertEquals(0.25f, command.color.a)
    }

    @Test
    fun `colorBlendFactor of zero leaves the vertex color multiplier at white`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        scene.addChild(SKSpriteNode(size = Vector2(4f, 2f), color = 0xFF00FF00.toInt()).apply { colorBlendFactor = 0f })

        val color = buildRenderCommands(scene).single().color

        assertEquals(SKVertexColor(1f, 1f, 1f, 1f), color)
    }

    @Test
    fun `colorBlendFactor of one fully replaces the vertex color multiplier with the sprite's color`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        scene.addChild(SKSpriteNode(size = Vector2(4f, 2f), color = 0xFF00FF00.toInt()).apply { colorBlendFactor = 1f })

        val color = buildRenderCommands(scene).single().color

        assertEquals(SKVertexColor(0f, 1f, 0f, 1f), color)
    }

    @Test
    fun `commands are sorted by zPosition ascending`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val front =
            SKSpriteNode(size = Vector2(1f, 1f), color = 0xFFFF0000.toInt()).apply {
                zPosition = 10f
                colorBlendFactor = 1f
            }
        val back =
            SKSpriteNode(size = Vector2(1f, 1f), color = 0xFF0000FF.toInt()).apply {
                zPosition = -10f
                colorBlendFactor = 1f
            }
        scene.addChild(front) // added first, but its higher zPosition should still sort it last
        scene.addChild(back)

        val commands = buildRenderCommands(scene)

        assertEquals(2, commands.size)
        assertEquals(1f, commands[0].color.b) // back (blue, zPosition -10) drawn first
        assertEquals(1f, commands[1].color.r) // front (red, zPosition 10) drawn last
    }

    @Test
    fun `zPosition ties are broken by tree traversal order`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val first = SKSpriteNode(size = Vector2(1f, 1f), color = 0xFFFF0000.toInt()).apply { colorBlendFactor = 1f }
        val second = SKSpriteNode(size = Vector2(1f, 1f), color = 0xFF0000FF.toInt()).apply { colorBlendFactor = 1f }
        scene.addChild(first) // added first -> earlier tree order
        scene.addChild(second)

        val commands = buildRenderCommands(scene)

        assertEquals(1f, commands[0].color.r) // red sprite (first) drawn before blue (second)
        assertEquals(1f, commands[1].color.b)
    }

    @Test
    fun `non-sprite nodes produce no command but their sprite descendants still appear`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val layer = SKNode()
        scene.addChild(layer)
        layer.addChild(SKSpriteNode(size = Vector2(1f, 1f)))

        assertEquals(1, buildRenderCommands(scene).size)
    }

    @Test
    fun `an empty-text label produces no command`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        scene.addChild(SKLabelNode(text = ""))

        assertTrue(buildRenderCommands(scene).isEmpty())
    }

    @Test
    fun `a shape node with no path produces no command`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        scene.addChild(SKShapeNode())

        assertTrue(buildRenderCommands(scene).isEmpty())
    }

    @Test
    fun `positions are relative to the scene's camera when one is set`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val camera = SKCameraNode().apply { position = Vector2(100f, 100f) }
        scene.addChild(camera)
        scene.camera = camera
        val sprite =
            SKSpriteNode(
                size = Vector2(4f, 2f),
            ).apply { position = Vector2(100f, 100f) } // coincides with the camera

        scene.addChild(sprite)
        val command = buildRenderCommands(scene).single()

        assertEquals(
            listOf(
                Vector2(-2f, -1f),
                Vector2(2f, -1f),
                Vector2(2f, 1f),
                Vector2(-2f, -1f),
                Vector2(2f, 1f),
                Vector2(-2f, 1f),
            ),
            command.vertices.map { it.position },
        )
    }

    @Test
    fun `a crop node with no maskNode produces no commands for its subtree`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val crop = SKCropNode()
        scene.addChild(crop)
        crop.addChild(SKSpriteNode(size = Vector2(4f, 2f)))

        assertTrue(buildRenderCommands(scene).isEmpty())
    }

    @Test
    fun `a crop node's maskNode is not rendered a second time as ordinary content`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val crop = SKCropNode()
        scene.addChild(crop)
        val mask = SKSpriteNode(size = Vector2(10f, 10f))
        crop.maskNode = mask
        crop.addChild(mask)

        assertTrue(buildRenderCommands(scene).isEmpty())
    }

    @Test
    fun `a crop node clips its descendants' commands to the maskNode's bounding box`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val crop = SKCropNode()
        scene.addChild(crop)
        val mask = SKSpriteNode(size = Vector2(10f, 10f)) // default anchorPoint (0.5,0.5) -> bounds (-5,-5)-(5,5)
        crop.maskNode = mask
        crop.addChild(mask)
        crop.addChild(SKSpriteNode(size = Vector2(2f, 2f)))

        val command = buildRenderCommands(scene).single() // only the plain sprite -- the mask itself isn't rendered

        assertEquals(Rect(-5f, -5f, 5f, 5f), command.clipRect)
    }

    @Test
    fun `nested crop nodes intersect their clip rects`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val outer = SKCropNode()
        scene.addChild(outer)
        val outerMask = SKSpriteNode(size = Vector2(20f, 20f)) // bounds (-10,-10)-(10,10)
        outer.maskNode = outerMask
        outer.addChild(outerMask)

        val inner = SKCropNode()
        outer.addChild(inner)
        val innerMask = SKSpriteNode(size = Vector2(6f, 6f)) // bounds (-3,-3)-(3,3), fully inside outer's
        inner.maskNode = innerMask
        inner.addChild(innerMask)
        inner.addChild(SKSpriteNode(size = Vector2(1f, 1f)))

        val command = buildRenderCommands(scene).single()

        assertEquals(Rect(-3f, -3f, 3f, 3f), command.clipRect) // narrowed to the inner (smaller) mask
    }

    @Test
    fun `nested crop nodes whose masks don't overlap render nothing`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val outer = SKCropNode()
        scene.addChild(outer)
        val outerMask = SKSpriteNode(size = Vector2(10f, 10f)).apply { position = Vector2(-50f, -50f) }
        outer.maskNode = outerMask
        outer.addChild(outerMask)

        val inner = SKCropNode()
        outer.addChild(inner)
        // Nowhere near outerMask's bounds -- the two masks don't overlap at all.
        val innerMask = SKSpriteNode(size = Vector2(10f, 10f)).apply { position = Vector2(50f, 50f) }
        inner.maskNode = innerMask
        inner.addChild(innerMask)
        inner.addChild(SKSpriteNode(size = Vector2(1f, 1f)))

        assertTrue(buildRenderCommands(scene).isEmpty())
    }

    @Test
    fun `an emitter node produces one untextured render command per living particle`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val emitter =
            SKEmitterNode().apply {
                particleBirthRate = 3f
                numParticlesToEmit = 3
            }
        scene.addChild(emitter)
        stepEmitters(scene, 1.seconds)

        val commands = buildRenderCommands(scene)

        assertEquals(3, commands.size)
        assertTrue(commands.all { it.texture == null })
    }

    @Test
    fun `an emitter with no particles produces no commands`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        scene.addChild(SKEmitterNode())

        assertTrue(buildRenderCommands(scene).isEmpty())
    }

    @Test
    fun `a particle's quad is centered on the emitter position plus the particle's own offset`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val emitter =
            SKEmitterNode().apply {
                position = Vector2(10f, 20f)
                particleSize = Vector2(4f, 4f)
                particleBirthRate = 1f
                numParticlesToEmit = 1
            }
        scene.addChild(emitter)
        stepEmitters(scene, 1.seconds) // spawns one particle at the emitter's local origin

        val command = buildRenderCommands(scene).single()

        // An unrotated 4x4 quad centered on the emitter's (10, 20) position.
        assertEquals(
            setOf(Vector2(8f, 18f), Vector2(12f, 18f), Vector2(12f, 22f), Vector2(8f, 22f)),
            command.vertices.map { it.position }.toSet(),
        )
    }
}
