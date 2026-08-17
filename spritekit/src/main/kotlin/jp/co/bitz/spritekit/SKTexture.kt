package jp.co.bitz.spritekit

import android.graphics.Bitmap

/**
 * GPU-side state for a texture's underlying bitmap — one instance per distinct [Bitmap], shared
 * by an [SKTexture] and every sub-rect [SKTexture] carved from it (e.g. via [SKTextureAtlas]), so
 * they all reference the same uploaded GL texture. Not part of this library's public API — see
 * `docs/ARCHITECTURE.md`'s "GPU resource lifecycle and context loss" section.
 */
internal class SKTextureGpuState {
    var glTextureId: Int = 0
    var uploadedGeneration: Int = -1
}

/**
 * An image sampled by [SKSpriteNode] (and, in later phases, particles/tile maps) — mirrors
 * Apple's `SKTexture`.
 *
 * Wraps a [Bitmap] as this library's persistent CPU-side descriptor; the GPU-side texture handle
 * is uploaded lazily on first use and re-uploaded after `EGLContext` loss, tracked via
 * [SKResourceRegistry.generation] rather than the [SKReloadableResource] callback mechanism —
 * see `docs/ARCHITECTURE.md`.
 */
public class SKTexture internal constructor(
    internal val bitmap: Bitmap,
    /** This texture's region within [bitmap], normalized `0..1` on each axis — the full bitmap by default. */
    public val textureRect: Rect = Rect(0f, 0f, 1f, 1f),
    internal val gpuState: SKTextureGpuState = SKTextureGpuState(),
) {
    /** Wraps [bitmap] as a full (`0..1`) texture. */
    public constructor(bitmap: Bitmap) : this(bitmap, Rect(0f, 0f, 1f, 1f), SKTextureGpuState())

    /**
     * A sub-region of [texture], expressed as [rect] — normalized `0..1` coordinates within
     * [texture]'s own underlying bitmap — mirrors Apple's `SKTexture(rect:in:)`. Shares
     * [texture]'s GPU state, so both reference the same uploaded GL texture (this is how
     * [SKTextureAtlas] regions avoid uploading the packed atlas bitmap once per region).
     *
     * Deviation: [rect] is always interpreted relative to [texture]'s underlying bitmap, even if
     * [texture] is itself already a sub-rect — this library doesn't compose nested sub-rects, an
     * edge case [SKTextureAtlas] (this constructor's only real use case) never hits.
     */
    public constructor(rect: Rect, texture: SKTexture) : this(texture.bitmap, rect, texture.gpuState)

    /** How this texture is sampled when scaled. Defaults to [SKTextureFilteringMode.Linear]. */
    public var filteringMode: SKTextureFilteringMode = SKTextureFilteringMode.Linear
}
