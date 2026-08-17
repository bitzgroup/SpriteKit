package jp.co.bitz.spritekit

/** How a texture is sampled when magnified/minified, mirroring Apple's `SKTextureFilteringMode`. */
public enum class SKTextureFilteringMode {
    /** `GL_NEAREST` — blocky, pixelated scaling; suits pixel-art. */
    Nearest,

    /** `GL_LINEAR` — smoothed scaling. This library's default, matching Apple's. */
    Linear,
}
