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
    /**
     * The exact pixel size of a region cut out by [SKTextureAtlas], which [textureRect] only
     * approximates in floats.
     */
    private val regionPixelSize: Vector2? = null,
) {
    /** Wraps [bitmap] as a full (`0..1`) texture. */
    public constructor(bitmap: Bitmap) : this(bitmap, Rect(0f, 0f, 1f, 1f), SKTextureGpuState())

    /**
     * Loads the image [imageNamed] from the host app's `assets/` folder — Android's stand-in for
     * the main bundle Apple's `SKTexture(imageNamed:)` reads from — at its pixel size. The name
     * may leave out the extension (`"hero"` finds `hero.png`, `.jpg`, `.jpeg` or `.webp`) and may
     * include a subfolder (`"sprites/hero"`).
     *
     * As on Apple, a missing image doesn't fail: it logs a warning and gives a 128×128
     * placeholder. Deviation: Apple's `@2x`/`@3x` variants and asset catalogs have no
     * counterpart; the file is used at its own pixel size. Each call decodes the file again —
     * share the texture to share the bitmap.
     */
    public constructor(imageNamed: String) : this(loadNamedImage(imageNamed))

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

    /**
     * This texture's size — Apple's `size()`: its bitmap's pixel dimensions, scaled by
     * [textureRect] for a sub-region texture (the same thing Apple reports for a texture made from
     * a `CGImage`, whose size is likewise its pixel dimensions).
     */
    public fun size(): Vector2 =
        regionPixelSize ?: Vector2(
            bitmap.width * (textureRect.right - textureRect.left),
            bitmap.height * (textureRect.bottom - textureRect.top),
        )

    /** How this texture is sampled when scaled. Defaults to [SKTextureFilteringMode.Linear]. */
    public var filteringMode: SKTextureFilteringMode = SKTextureFilteringMode.Linear

    /**
     * Calls [completionHandler] once this texture is ready to draw — Apple's `preload`. As on
     * Apple, it's called on a background thread. Deviation: the image is already decoded, and it
     * reaches the GPU on the render thread the first time it's drawn, so there's nothing left to
     * do first.
     */
    public fun preload(completionHandler: () -> Unit) {
        skPreload(completionHandler)
    }

    public companion object {
        /** Calls [completionHandler] once all of [textures] are ready, on a background thread — see [preload]. */
        @Suppress("UnusedParameter") // Every texture is ready once created; see [preload].
        public fun preload(
            textures: List<SKTexture>,
            completionHandler: () -> Unit,
        ) {
            skPreload(completionHandler)
        }
    }
}
