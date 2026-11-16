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
        val scene = SKScene(size = Vector2(100f, 100f)) // with a camera, visible area is (-50,-50)-(50,50)
        val camera = SKCameraNode()
        scene.addChild(camera)
        scene.camera = camera
        val node = SKSpriteNode(size = Vector2(10f, 10f)).apply { position = Vector2(20f, 20f) }
        scene.addChild(node)

        assertTrue(camera.containsNode(node))
    }

    @Test
    fun `containsNode ignores the scene's anchorPoint once a camera is active`() {
        val scene = SKScene(size = Vector2(100f, 100f)).apply { anchorPoint = Vector2(1f, 1f) }
        val camera = SKCameraNode()
        scene.addChild(camera)
        scene.camera = camera
        // Well within (-50,-50)-(50,50) (the camera-centered viewport), but outside (-100,-100)-
        // (0,0) (what the buggy anchorPoint-offset formula would have used instead).
        val node = SKSpriteNode(size = Vector2(10f, 10f)).apply { position = Vector2(20f, 20f) }
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
