package jp.co.bitz.spritekit

import kotlin.test.Test
import kotlin.test.assertEquals

class SKSceneSizeTest {
    private class RecordingScene(size: Vector2) : SKScene(size) {
        val oldSizes = mutableListOf<Vector2>()

        override fun didChangeSize(oldSize: Vector2) {
            oldSizes += oldSize
        }
    }

    @Test
    fun `assigning a different size calls didChangeSize with the previous size`() {
        val scene = RecordingScene(Vector2(100f, 50f))

        scene.size = Vector2(200f, 80f)

        assertEquals(listOf(Vector2(100f, 50f)), scene.oldSizes)
        assertEquals(Vector2(200f, 80f), scene.size)
    }

    @Test
    fun `assigning the same size doesn't call didChangeSize`() {
        val scene = RecordingScene(Vector2(100f, 50f))

        scene.size = Vector2(100f, 50f)

        assertEquals(emptyList(), scene.oldSizes)
    }

    @Test
    fun `constructing a scene doesn't call didChangeSize`() {
        val scene = RecordingScene(Vector2(100f, 50f))

        assertEquals(emptyList(), scene.oldSizes)
    }
}
