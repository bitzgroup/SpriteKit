package jp.co.bitz.spritekit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SKViewCoordinatesTest {
    private fun assertClose(
        expected: Vector2,
        actual: Vector2,
    ) {
        val close = kotlin.math.abs(expected.x - actual.x) < 1e-3f && kotlin.math.abs(expected.y - actual.y) < 1e-3f
        assertTrue(close, "expected $expected, was $actual")
    }

    @Test
    fun `fill scene maps view corners to scene corners, flipping y`() {
        val scene = SKScene(Vector2(200f, 100f))

        assertClose(Vector2(0f, 100f), viewPointToScene(scene, Vector2(0f, 0f), 400, 200))
        assertClose(Vector2(200f, 0f), viewPointToScene(scene, Vector2(400f, 200f), 400, 200))
        assertClose(Vector2(100f, 50f), viewPointToScene(scene, Vector2(200f, 100f), 400, 200))
    }

    @Test
    fun `aspectFit letterboxes around the centered scene`() {
        val scene = SKScene(Vector2(100f, 100f)).apply { scaleMode = SKSceneScaleMode.AspectFit }

        // 400x200 view: the 100x100 scene is scaled to 200x200 and centered, with 100 px bars
        // left and right; the view's left edge is scene x = -50.
        assertClose(Vector2(-50f, 100f), viewPointToScene(scene, Vector2(0f, 0f), 400, 200))
        assertClose(Vector2(50f, 50f), viewPointToScene(scene, Vector2(200f, 100f), 400, 200))
    }

    @Test
    fun `camera position, scale and the scene's own space are all accounted for`() {
        val scene = SKScene(Vector2(200f, 200f))
        val camera =
            SKCameraNode().apply {
                position = Vector2(1000f, 500f)
                setScale(2f)
            }
        scene.addChild(camera)
        scene.camera = camera

        // The view's center is the camera's position; a camera scaled 2x shows twice as much.
        assertClose(Vector2(1000f, 500f), viewPointToScene(scene, Vector2(100f, 100f), 200, 200))
        assertClose(Vector2(800f, 700f), viewPointToScene(scene, Vector2(0f, 0f), 200, 200))
    }

    @Test
    fun `scenePointToView inverts viewPointToScene`() {
        val scene =
            SKScene(Vector2(300f, 200f)).apply {
                scaleMode = SKSceneScaleMode.AspectFill
                anchorPoint = Vector2(0.5f, 0.5f)
            }
        val camera = SKCameraNode().apply { position = Vector2(30f, -40f) }
        scene.addChild(camera)
        scene.camera = camera

        for (viewPoint in listOf(Vector2(0f, 0f), Vector2(123f, 456f), Vector2(640f, 480f))) {
            val scenePoint = viewPointToScene(scene, viewPoint, 640, 480)
            assertClose(viewPoint, scenePointToView(scene, scenePoint, 640, 480))
        }
    }

    @Test
    fun `conversion is the identity before the view has a size`() {
        val scene = SKScene(Vector2(100f, 100f))

        assertEquals(Vector2(3f, 4f), viewPointToScene(scene, Vector2(3f, 4f), 0, 0))
        assertEquals(Vector2(3f, 4f), scenePointToView(scene, Vector2(3f, 4f), 0, 0))
    }
}
