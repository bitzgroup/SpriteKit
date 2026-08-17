package jp.co.bitz.spritekit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RectTest {
    @Test
    fun `intersection of overlapping rects is their shared region`() {
        val a = Rect(0f, 0f, 10f, 10f)
        val b = Rect(5f, 5f, 15f, 15f)

        assertEquals(Rect(5f, 5f, 10f, 10f), a.intersection(b))
    }

    @Test
    fun `intersection is symmetric`() {
        val a = Rect(0f, 0f, 10f, 10f)
        val b = Rect(5f, 5f, 15f, 15f)

        assertEquals(a.intersection(b), b.intersection(a))
    }

    @Test
    fun `intersection of disjoint rects is null`() {
        val a = Rect(0f, 0f, 10f, 10f)
        val b = Rect(20f, 20f, 30f, 30f)

        assertNull(a.intersection(b))
    }

    @Test
    fun `intersection of rects that only touch at an edge is null`() {
        val a = Rect(0f, 0f, 10f, 10f)
        val b = Rect(10f, 0f, 20f, 10f)

        assertNull(a.intersection(b))
    }

    @Test
    fun `intersection of one rect fully containing another is the smaller one`() {
        val outer = Rect(0f, 0f, 100f, 100f)
        val inner = Rect(10f, 10f, 20f, 20f)

        assertEquals(inner, outer.intersection(inner))
    }
}
