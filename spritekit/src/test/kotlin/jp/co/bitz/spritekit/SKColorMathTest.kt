package jp.co.bitz.spritekit

import kotlin.test.Test
import kotlin.test.assertContentEquals

class SKColorMathTest {
    private val translucentRed = (0x80 shl 24) or (0xFF shl 16)

    @Test
    fun opaqueViewIgnoresTheBackgroundAlpha() {
        assertContentEquals(
            floatArrayOf(1f, 0f, 0f, 1f),
            backgroundClearColor(translucentRed, allowsTransparency = false),
        )
        assertContentEquals(floatArrayOf(0f, 0f, 0f, 1f), backgroundClearColor(0, allowsTransparency = false))
    }

    @Test
    fun transparentViewKeepsTheAlphaAndPremultipliesTheColor() {
        val alpha = 0x80 / 255f
        assertContentEquals(
            floatArrayOf(alpha, 0f, 0f, alpha),
            backgroundClearColor(translucentRed, allowsTransparency = true),
        )
    }

    @Test
    fun aFullyTransparentBackgroundClearsToZero() {
        val transparentWhite = 0x00FFFFFF
        assertContentEquals(
            floatArrayOf(0f, 0f, 0f, 0f),
            backgroundClearColor(transparentWhite, allowsTransparency = true),
        )
    }
}
