package jp.co.bitz.spritekit

/** Where an [SKLabelNode]'s text sits vertically relative to [SKNode.position]. */
public enum class SKLabelVerticalAlignmentMode {
    /** [SKNode.position] is the text's baseline. This library's default, matching Apple's. */
    Baseline,

    /** Centered on [SKNode.position]. */
    Center,

    /** [SKNode.position] is the top of the text. */
    Top,

    /** [SKNode.position] is the bottom of the text. */
    Bottom,
}
