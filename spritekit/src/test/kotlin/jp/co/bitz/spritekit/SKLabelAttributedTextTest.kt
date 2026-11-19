package jp.co.bitz.spritekit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * Mirrors the tic-tac-toe sample's `SKLabelAttributedTextParityTests` probes. Plain `String`s stand
 * in for styled text here: snapshotting a `Spanned` and rendering need an Android runtime.
 */
class SKLabelAttributedTextTest {
    @Test
    fun `attributed text is unset by default, and setting it leaves text and the font alone`() {
        val label = SKLabelNode("Plain")
        assertNull(label.attributedText)
        label.attributedText = "Hello"
        assertEquals("Hello", label.attributedText)
        assertEquals("Plain", label.text)
        assertEquals(32f, label.fontSize)
    }

    @Test
    fun `setting text doesn't clear attributed text`() {
        val label = SKLabelNode(attributedText = "Hello")
        assertEquals("", label.text)
        label.text = "Other"
        assertEquals("Hello", label.attributedText)
    }

    @Test
    fun `while attributed text is set, text and the font don't change what is drawn`() {
        fun key(
            text: String,
            fontSize: Float,
            attributed: CharSequence?,
        ) = labelCacheKey(text, "Courier", fontSize, 0, lineLayout = null, attributedText = attributed)
        assertEquals(key("Plain", 20f, "Hello"), key("Other", 60f, "Hello"))
        assertNotEquals(key("Plain", 20f, "Hello"), key("Plain", 20f, "Hi"))
        // Without it, they do.
        assertNotEquals(key("Plain", 20f, null), key("Other", 20f, null))
        assertNotEquals(key("Plain", 20f, null), key("Plain", 60f, null))
    }

    @Test
    fun `the line layout still applies to attributed text`() {
        val layout = SKLabelLineLayout(0, 80f, SKLineBreakMode.ByWordWrapping, SKLabelHorizontalAlignmentMode.Center)
        assertNotEquals(
            labelCacheKey("", null, 0f, 0, lineLayout = null, attributedText = "Hello"),
            labelCacheKey("", null, 0f, 0, lineLayout = layout, attributedText = "Hello"),
        )
    }
}
