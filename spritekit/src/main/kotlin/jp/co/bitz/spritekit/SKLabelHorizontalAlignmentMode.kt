package jp.co.bitz.spritekit

/** Where an [SKLabelNode]'s text sits horizontally relative to [SKNode.position]. */
public enum class SKLabelHorizontalAlignmentMode {
    /** Centered on [SKNode.position]. This library's default, matching Apple's. */
    Center,

    /** [SKNode.position] is the text's left edge. */
    Left,

    /** [SKNode.position] is the text's right edge. */
    Right,
}
