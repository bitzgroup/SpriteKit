package jp.co.bitz.spritekit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SKTextureAtlasPackerTest {
    @Test
    fun `packs a single entry flush against the origin`() {
        val layout = packTextureAtlas(listOf(SKAtlasEntry("a", 32, 16)))

        assertEquals(listOf(SKAtlasPlacement("a", 0, 0, 32, 16)), layout.placements)
        assertEquals(32, layout.width)
        assertEquals(16, layout.height)
    }

    @Test
    fun `packs entries onto the same shelf when they fit within maxWidth`() {
        val entries = listOf(SKAtlasEntry("a", 32, 16), SKAtlasEntry("b", 16, 16))

        val layout = packTextureAtlas(entries, maxWidth = 128)

        assertEquals(2, layout.placements.size)
        assertEquals(0, layout.placements.first { it.name == "a" }.y)
        assertEquals(0, layout.placements.first { it.name == "b" }.y) // same shelf
        assertEquals(48, layout.width) // 32 + 16
        assertEquals(16, layout.height)
    }

    @Test
    fun `wraps onto a new shelf when an entry would exceed maxWidth`() {
        val entries = listOf(SKAtlasEntry("a", 60, 20), SKAtlasEntry("b", 60, 10))

        val layout = packTextureAtlas(entries, maxWidth = 100)

        val placementA = layout.placements.first { it.name == "a" }
        val placementB = layout.placements.first { it.name == "b" }
        assertEquals(0, placementA.y)
        assertEquals(20, placementB.y) // wrapped below the first (tallest) shelf
        assertEquals(60, layout.width)
        assertEquals(30, layout.height) // 20 + 10
    }

    @Test
    fun `no two placements overlap, for a variety of entry sizes`() {
        val entries = (1..12).map { SKAtlasEntry("entry$it", width = 8 + it * 3, height = 40 - it * 2) }

        val layout = packTextureAtlas(entries, maxWidth = 256)

        for (i in layout.placements.indices) {
            for (j in i + 1 until layout.placements.size) {
                assertTrue(
                    !overlaps(layout.placements[i], layout.placements[j]),
                    "expected no overlap between entries $i and $j",
                )
            }
        }
    }

    private fun overlaps(
        a: SKAtlasPlacement,
        b: SKAtlasPlacement,
    ): Boolean = a.x < b.x + b.width && b.x < a.x + a.width && a.y < b.y + b.height && b.y < a.y + a.height
}
