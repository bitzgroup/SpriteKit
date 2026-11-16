package jp.co.bitz.spritekit

import kotlin.test.Test
import kotlin.test.assertEquals

class SKFontNameTest {
    @Test
    fun `a bold suffix is read as weight 700 on the family before it`() {
        assertEquals(SKFontName("Helvetica", 700, italic = false), parseFontName("Helvetica-Bold"))
    }

    @Test
    fun `weight and italic combine in one suffix`() {
        assertEquals(SKFontName("HelveticaNeue", 300, italic = true), parseFontName("HelveticaNeue-LightItalic"))
        assertEquals(SKFontName("AvenirNext", 700, italic = true), parseFontName("AvenirNext-BoldItalic"))
    }

    @Test
    fun `SemiBold is not mistaken for Bold`() {
        assertEquals(600, parseFontName("AvenirNext-SemiBold").weight)
        assertEquals(600, parseFontName("AvenirNext-DemiBold").weight)
    }

    @Test
    fun `a name without a style suffix is the family at regular weight`() {
        assertEquals(SKFontName("Chalkduster", 400, italic = false), parseFontName("Chalkduster"))
    }

    @Test
    fun `Android family names keep their dashes`() {
        assertEquals(SKFontName("sans-serif", 400, italic = false), parseFontName("sans-serif"))
        assertEquals(SKFontName("sans-serif-condensed", 400, italic = false), parseFontName("sans-serif-condensed"))
    }

    @Test
    fun `an Android family name with a weight suffix still resolves that weight`() {
        assertEquals(SKFontName("sans-serif", 500, italic = false), parseFontName("sans-serif-medium"))
    }

    @Test
    fun `Apple monospaced families map to Android's monospace`() {
        assertEquals(SKFontName("monospace", 400, italic = false), parseFontName("Menlo"))
        assertEquals(SKFontName("monospace", 700, italic = false), parseFontName("Menlo-Bold"))
        assertEquals(SKFontName("monospace", 400, italic = false), parseFontName("Courier New"))
    }
}
