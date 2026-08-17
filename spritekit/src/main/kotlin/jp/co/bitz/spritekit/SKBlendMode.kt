package jp.co.bitz.spritekit

/**
 * How a sprite's pixels combine with what's already drawn, mirroring Apple's `SKBlendMode`.
 * Implemented via standard `glBlendFunc`/`glBlendEquation` combinations — *contract-conformant,
 * not bit-identical*, since Apple's own blending isn't independently documented beyond its
 * observable effect (the same framing GameplayKit-for-Android uses for its own undocumented
 * internals). `.multiplyX2` is not implemented — see `docs/API_COMPATIBILITY.md`.
 */
public enum class SKBlendMode {
    /** Standard alpha compositing (source-over). This library's default, matching Apple's. */
    Alpha,

    /** Adds the source color to the destination, scaled by source alpha. */
    Add,

    /** Subtracts the source color from the destination, scaled by source alpha. */
    Subtract,

    /** Multiplies the source and destination colors. */
    Multiply,

    /** Inverse-multiplies: lightens the destination based on the source color. */
    Screen,

    /** Overwrites the destination with the source, ignoring alpha. */
    Replace,
}
