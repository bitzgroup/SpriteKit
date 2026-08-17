package jp.co.bitz.spritekit

import android.graphics.Bitmap
import android.graphics.Canvas

/**
 * A single packed bitmap holding multiple named sub-textures — mirrors Apple's `SKTextureAtlas`.
 *
 * Deviation: Apple auto-packs an atlas from a folder of images at Xcode build time; there's no
 * equivalent Android build step, so [pack] packs a set of in-memory [Bitmap]s into one atlas at
 * runtime instead — see `docs/API_COMPATIBILITY.md`. The packing layout itself is computed by
 * [packTextureAtlas], kept separately unit-testable from the actual bitmap compositing here.
 */
public class SKTextureAtlas private constructor(
    private val regions: Map<String, SKTexture>,
) {
    /** All names this atlas was packed with. */
    public val textureNames: Set<String> get() = regions.keys

    /** The named texture, or `null` if [name] wasn't one this atlas was [pack]ed with. */
    public fun textureNamed(name: String): SKTexture? = regions[name]

    public companion object {
        /** Packs [images] (name to source bitmap) into a single atlas no wider than [maxWidth]. */
        public fun pack(
            images: Map<String, Bitmap>,
            maxWidth: Int = 2048,
        ): SKTextureAtlas {
            val entries = images.map { (name, bitmap) -> SKAtlasEntry(name, bitmap.width, bitmap.height) }
            val layout = packTextureAtlas(entries, maxWidth)

            val atlasBitmap = Bitmap.createBitmap(layout.width, layout.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(atlasBitmap)
            // The root SKTexture's SKTextureGpuState is shared by every region below, so the
            // whole atlas bitmap is uploaded to the GPU exactly once.
            val atlasTexture = SKTexture(atlasBitmap)

            val regions =
                layout.placements.associate { placement ->
                    canvas.drawBitmap(
                        images.getValue(placement.name),
                        placement.x.toFloat(),
                        placement.y.toFloat(),
                        null,
                    )
                    // Top-of-Rect = top-of-bitmap (row 0), matching how the sprite renderer
                    // assigns UV coordinates to quad corners — see docs/ARCHITECTURE.md.
                    val uvRect =
                        Rect(
                            left = placement.x / layout.width.toFloat(),
                            top = placement.y / layout.height.toFloat(),
                            right = (placement.x + placement.width) / layout.width.toFloat(),
                            bottom = (placement.y + placement.height) / layout.height.toFloat(),
                        )
                    placement.name to SKTexture(uvRect, atlasTexture)
                }

            return SKTextureAtlas(regions)
        }
    }
}
