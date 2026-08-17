package jp.co.bitz.spritekit

import android.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SKSpriteNodeTest {
    @Test
    fun `defaults are texture-less white and centered`() {
        val sprite = SKSpriteNode()

        assertNull(sprite.texture)
        assertEquals(Color.WHITE, sprite.color)
        assertEquals(0f, sprite.colorBlendFactor)
        assertEquals(SKBlendMode.Alpha, sprite.blendMode)
        assertEquals(Vector2(0.5f, 0.5f), sprite.anchorPoint)
        assertEquals(Vector2.Zero, sprite.size)
        assertNull(sprite.shader)
    }

    @Test
    fun `localQuadCorners is centered on the origin for the default anchorPoint`() {
        val sprite = SKSpriteNode(size = Vector2(10f, 4f))

        assertEquals(
            listOf(Vector2(-5f, -2f), Vector2(5f, -2f), Vector2(5f, 2f), Vector2(-5f, 2f)),
            sprite.localQuadCorners(),
        )
    }

    @Test
    fun `localQuadCorners shifts with a non-centered anchorPoint`() {
        val sprite = SKSpriteNode(size = Vector2(10f, 4f)).apply { anchorPoint = Vector2.Zero }

        assertEquals(
            listOf(Vector2(0f, 0f), Vector2(10f, 0f), Vector2(10f, 4f), Vector2(0f, 4f)),
            sprite.localQuadCorners(),
        )
    }

    @Test
    fun `calculateAccumulatedFrame reflects size and anchorPoint, offset by position`() {
        val sprite = SKSpriteNode(size = Vector2(10f, 4f)).apply { position = Vector2(100f, 200f) }
        val parent = SKNode()
        parent.addChild(sprite)

        assertEquals(Rect(95f, 198f, 105f, 202f), sprite.calculateAccumulatedFrame())
    }
}
