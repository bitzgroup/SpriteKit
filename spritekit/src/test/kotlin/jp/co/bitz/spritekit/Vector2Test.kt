package jp.co.bitz.spritekit

import kotlin.test.Test
import kotlin.test.assertEquals

class Vector2Test {
    @Test
    fun `width and height read a size-valued Vector2 like CGSize`() {
        val size = Vector2(1080f, 1920f)

        assertEquals(1080f, size.width)
        assertEquals(1920f, size.height)
    }
}
