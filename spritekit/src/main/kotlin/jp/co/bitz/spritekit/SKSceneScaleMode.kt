package jp.co.bitz.spritekit

/** How an [SKScene] scales to fit its [SKView], mirroring Apple's `SKSceneScaleMode`. */
public enum class SKSceneScaleMode {
    /** Stretches each axis independently to exactly fill the view; doesn't preserve aspect ratio. */
    Fill,

    /** Uniformly scales to fill the view, cropping content that overflows; preserves aspect ratio. */
    AspectFill,

    /** Uniformly scales to fit entirely within the view, letterboxing if needed; preserves aspect ratio. */
    AspectFit,

    /** Doesn't scale; [SKScene.size] instead tracks the view's size directly. */
    ResizeFill,
}
