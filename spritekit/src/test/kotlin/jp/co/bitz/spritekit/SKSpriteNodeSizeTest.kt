package jp.co.bitz.spritekit

import kotlin.test.Test
import kotlin.test.assertEquals

// Expectations recorded from Apple's SpriteKit on iOS/tvOS simulators — see the tic-tac-toe
// sample's SpriteKitParity probes (SKSpriteSizeParityTests, SKNodeHierarchyParityTests).
class SKSpriteNodeSizeTest {
    @Test
    fun `size includes the scale magnitude`() {
        val sprite = SKSpriteNode(size = Vector2(10f, 20f))
        sprite.xScale = 2f
        assertEquals(Vector2(20f, 20f), sprite.size)
        sprite.setScale(3f)
        assertEquals(Vector2(30f, 60f), sprite.size)
        sprite.xScale = -1f
        assertEquals(Vector2(10f, 60f), sprite.size)
    }

    @Test
    fun `assigning size stores it divided by the scale`() {
        val sprite = SKSpriteNode(size = Vector2(10f, 20f)).apply { xScale = 2f }
        sprite.size = Vector2(30f, 30f)
        assertEquals(Vector2(30f, 30f), sprite.size)
        assertEquals(2f, sprite.xScale)
        sprite.xScale = 1f
        assertEquals(Vector2(15f, 30f), sprite.size)
    }

    @Test
    fun `a zero scale keeps the unscaled size`() {
        val sprite = SKSpriteNode(size = Vector2(10f, 20f)).apply { xScale = 0f }
        assertEquals(Vector2(0f, 20f), sprite.size)
        sprite.xScale = 2f
        assertEquals(Vector2(20f, 20f), sprite.size)
    }

    @Test
    fun `the sprite still draws its unscaled size in its own space`() {
        val sprite = SKSpriteNode(size = Vector2(10f, 20f)).apply { xScale = 2f }
        val parent = SKNode()
        parent.addChild(sprite)
        assertEquals(Rect(-10f, -10f, 10f, 10f), sprite.frame)
    }

    @Test
    fun `scale to sets the scale for that size whatever the current scale or rotation`() {
        val sprite =
            SKSpriteNode(size = Vector2(10f, 20f)).apply {
                xScale = 3f
                zRotation = 1.5f
            }
        sprite.scale(Vector2(40f, 10f))
        assertEquals(4f, sprite.xScale)
        assertEquals(0.5f, sprite.yScale)
        assertEquals(Vector2(40f, 10f), sprite.size)
    }
}
