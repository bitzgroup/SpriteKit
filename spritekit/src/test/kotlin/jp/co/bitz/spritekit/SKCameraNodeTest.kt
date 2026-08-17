package jp.co.bitz.spritekit

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SKCameraNodeTest {
    @Test
    fun `containsNode is false when the camera isn't part of a scene`() {
        val camera = SKCameraNode()
        val node = SKSpriteNode(size = Vector2(10f, 10f))

        assertFalse(camera.containsNode(node))
    }

    @Test
    fun `containsNode is true for a node within the scene's default viewport`() {
        val scene = SKScene(size = Vector2(100f, 100f)) // anchorPoint (0,0): visible area is (0,0)-(100,100)
        val camera = SKCameraNode()
        scene.addChild(camera)
        scene.camera = camera
        val node = SKSpriteNode(size = Vector2(10f, 10f)).apply { position = Vector2(50f, 50f) }
        scene.addChild(node)

        assertTrue(camera.containsNode(node))
    }

    @Test
    fun `containsNode is false for a node well outside the scene's viewport`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val camera = SKCameraNode()
        scene.addChild(camera)
        scene.camera = camera
        val node = SKSpriteNode(size = Vector2(10f, 10f)).apply { position = Vector2(1000f, 1000f) }
        scene.addChild(node)

        assertFalse(camera.containsNode(node))
    }

    @Test
    fun `containsNode accounts for the camera's own position`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val camera = SKCameraNode().apply { position = Vector2(500f, 500f) }
        scene.addChild(camera)
        scene.camera = camera
        // Near the scene's own origin, but far from where the camera has moved to.
        val node = SKSpriteNode(size = Vector2(10f, 10f)).apply { position = Vector2(10f, 10f) }
        scene.addChild(node)

        assertFalse(camera.containsNode(node))
    }
}
