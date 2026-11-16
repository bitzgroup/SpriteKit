package jp.co.bitz.spritekit

import kotlin.test.Test
import kotlin.test.assertEquals

class SKTextureLoadingTest {
    @Test
    fun `a name without an extension tries the image extensions first`() {
        assertEquals(
            listOf("hero.png", "hero.jpg", "hero.jpeg", "hero.webp", "hero"),
            namedImageCandidates("hero"),
        )
    }

    @Test
    fun `a name with an extension is tried as is first`() {
        assertEquals("hero.png", namedImageCandidates("hero.png").first())
        assertEquals("sprites/hero.png", namedImageCandidates("sprites/hero").first())
    }

    @Test
    fun `a dot in a folder name is not taken as an extension`() {
        assertEquals("v1.0/hero.png", namedImageCandidates("v1.0/hero").first())
    }

    @Test
    fun `the missing image placeholder is Apple's 128 points`() {
        assertEquals(128, MISSING_IMAGE_SIZE)
    }
}
