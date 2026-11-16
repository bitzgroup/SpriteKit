package jp.co.bitz.spritekit

/**
 * How a multi-line [SKLabelNode] wraps and truncates its text — this library's stand-in for
 * Apple's `NSLineBreakMode`, which `SKLabelNode.lineBreakMode` takes. Only applies when
 * [SKLabelNode.numberOfLines] isn't `1`: a one-line label ignores it, as on Apple.
 */
public enum class SKLineBreakMode {
    /** Wraps at word boundaries. */
    ByWordWrapping,

    /**
     * Wraps at character boundaries. Deviation: Android's text layout has no character-wrapping
     * mode, so this wraps at word boundaries like [ByWordWrapping] (a word too long for a line
     * still breaks inside it).
     */
    ByCharWrapping,

    /** Wraps at word boundaries; text past the last line is cut off. */
    ByClipping,

    /**
     * Wraps, and marks text cut from the last line with an ellipsis at its start. Deviation:
     * Android's text layout only does this for a label limited to one line
     * ([SKLabelNode.numberOfLines] other than `1` can't express that, so in practice text past
     * the last line is cut off without an ellipsis); [ByTruncatingTail] works on any line.
     */
    ByTruncatingHead,

    /** Wraps, and marks text cut from the last line with an ellipsis at its end. Apple's default. */
    ByTruncatingTail,

    /**
     * Wraps, and marks text cut from the last line with an ellipsis in its middle. Same deviation
     * as [ByTruncatingHead].
     */
    ByTruncatingMiddle,
}
