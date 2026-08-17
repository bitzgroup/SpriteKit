package jp.co.bitz.spritekit

import kotlin.test.Test
import kotlin.test.assertEquals

class SKSceneProjectionTest {
    @Test
    fun `Fill always uses the scene's own size, regardless of the view's aspect ratio`() {
        val projection =
            computeSceneProjection(
                sceneSize = Vector2(100f, 100f),
                anchorPoint = Vector2.Zero,
                scaleMode = SKSceneScaleMode.Fill,
                viewWidth = 400,
                viewHeight = 100,
            )

        assertEquals(SKSceneProjection(left = 0f, right = 100f, bottom = 0f, top = 100f), projection)
    }

    @Test
    fun `ResizeFill behaves like Fill`() {
        val projection =
            computeSceneProjection(
                sceneSize = Vector2(320f, 180f),
                anchorPoint = Vector2.Zero,
                scaleMode = SKSceneScaleMode.ResizeFill,
                viewWidth = 640,
                viewHeight = 480,
            )

        assertEquals(SKSceneProjection(left = 0f, right = 320f, bottom = 0f, top = 180f), projection)
    }

    @Test
    fun `anchorPoint offsets the projection rect within the projected size`() {
        val projection =
            computeSceneProjection(
                sceneSize = Vector2(100f, 100f),
                anchorPoint = Vector2(0.5f, 0.5f),
                scaleMode = SKSceneScaleMode.Fill,
                viewWidth = 100,
                viewHeight = 100,
            )

        assertEquals(SKSceneProjection(left = -50f, right = 50f, bottom = -50f, top = 50f), projection)
    }

    @Test
    fun `AspectFit widens the projection to letterbox a view wider than the scene`() {
        val projection =
            computeSceneProjection(
                sceneSize = Vector2(100f, 100f),
                anchorPoint = Vector2.Zero,
                scaleMode = SKSceneScaleMode.AspectFit,
                viewWidth = 200,
                viewHeight = 100,
            )

        // The whole 100x100 scene fits (height is the limiting axis); width grows to 200 to
        // match the view's aspect ratio, so extra background shows on the sides.
        assertEquals(SKSceneProjection(left = 0f, right = 200f, bottom = 0f, top = 100f), projection)
    }

    @Test
    fun `AspectFill shrinks the projection to crop a view wider than the scene`() {
        val projection =
            computeSceneProjection(
                sceneSize = Vector2(100f, 100f),
                anchorPoint = Vector2.Zero,
                scaleMode = SKSceneScaleMode.AspectFill,
                viewWidth = 200,
                viewHeight = 100,
            )

        // The view's full width is used (the limiting axis); height shrinks to 50, so only the
        // bottom half of the 100-tall scene is visible (cropped).
        assertEquals(SKSceneProjection(left = 0f, right = 100f, bottom = 0f, top = 50f), projection)
    }
}
