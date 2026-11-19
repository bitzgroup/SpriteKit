package jp.co.bitz.spritekit

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/**
 * Mirrors the tic-tac-toe sample's `SKTextureAtlasParityTests` probes, as far as they don't need
 * real bitmaps: name lookup, the `assets/` folder read, the packing border and where preloads call back.
 */
class SKTextureAtlasTest {
    @Test
    fun `a name finds its texture with or without an image extension`() {
        assertEquals("red", atlasLookupKey("red.png"))
        assertEquals("red", atlasLookupKey("red"))
        assertEquals("two", atlasLookupKey("two.PNG"))
        assertEquals("photo", atlasLookupKey("photo.jpeg"))
        assertEquals("v1.2", atlasLookupKey("v1.2"))
    }

    @Test
    fun `a named atlas reads the atlas folder, with or without its extension, and only its images`() {
        assertEquals("ParityAtlas.atlas", atlasFolder("ParityAtlas"))
        assertEquals("ParityAtlas.atlas", atlasFolder("ParityAtlas.atlas"))
        assertEquals(
            listOf("blue.png", "framed.png", "red.png"),
            atlasImageFiles(listOf("red.png", "notes.txt", "framed.png", "blue.png", "sub")),
        )
    }

    @Test
    fun `a border keeps each image clear of its neighbors and of the atlas edge`() {
        val layout = packTextureAtlas(listOf(SKAtlasEntry("a", 4, 2), SKAtlasEntry("b", 3, 5)), border = 1)
        val b = layout.placements.first { it.name == "b" }
        val a = layout.placements.first { it.name == "a" }
        assertEquals(SKAtlasPlacement("b", 1, 1, 3, 5), b)
        assertEquals(SKAtlasPlacement("a", 6, 1, 4, 2), a)
        assertEquals(11, layout.width)
        assertEquals(7, layout.height)
    }

    @Test
    fun `a border wraps shelves by the bordered size`() {
        val layout =
            packTextureAtlas(listOf(SKAtlasEntry("a", 4, 4), SKAtlasEntry("b", 4, 4)), maxWidth = 11, border = 1)
        assertEquals(1, layout.placements.first { it.name == "a" }.y)
        assertEquals(7, layout.placements.first { it.name == "b" }.y)
    }

    @Test
    fun `preloads call back once, off the calling thread`() {
        val latch = CountDownLatch(1)
        var thread: Thread? = null
        skPreload({
            thread = Thread.currentThread()
            latch.countDown()
        })
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertNotSame(Thread.currentThread(), thread)
    }
}
