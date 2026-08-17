package jp.co.bitz.spritekit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
}
