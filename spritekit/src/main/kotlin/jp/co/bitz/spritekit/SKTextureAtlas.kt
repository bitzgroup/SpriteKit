package jp.co.bitz.spritekit

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.util.Log
import java.io.File

/** The pixels repeated around each packed image so linear filtering never reaches a neighbor. */
private const val ATLAS_BORDER = 1

/** The named textures an [SKTextureAtlas] holds, keyed as given. */
internal class SKAtlasContents(
    val textures: Map<String, SKTexture>,
)

/**
 * A single packed bitmap holding multiple named sub-textures — mirrors Apple's `SKTextureAtlas`.
 *
 * Deviation: Apple packs an atlas folder at Xcode build time; there's no equivalent Android build
 * step, so this packs at runtime instead, from an `assets/<name>.atlas/` folder ([named]), from
 * images in memory or on disk ([dictionary]) or from bitmaps ([pack]) — see
 * `docs/API_COMPATIBILITY.md`. The packing layout itself is computed by [packTextureAtlas], kept
 * separately unit-testable from the actual bitmap compositing here.
 */
public class SKTextureAtlas internal constructor(
    contents: SKAtlasContents,
) {
    private val textures = contents.textures

    /**
     * Loads and packs the images in the host app's `assets/<named>.atlas/` folder — Android's
     * stand-in for the `.atlas` folder Apple's `SKTextureAtlas(named:)` reads from the main bundle.
     * The `.atlas` extension may be included or left out. A missing or empty folder gives an empty
     * atlas and logs a warning, as Apple does.
     *
     * Each image is named by its file name, extension included (`"hero.png"`), as Apple's
     * [textureNames] are; [textureNamed] finds it with or without the extension.
     */
    public constructor(named: String) : this(loadAtlasNamed(named))

    /**
     * Packs [dictionary]'s images into an atlas, keyed as given — Apple's
     * `SKTextureAtlas(dictionary:)`. Each value is a [Bitmap], a [File], a [Uri] (anything a
     * `ContentResolver` opens) or a `String` file path; a relative path is read from the host
     * app's `assets/`, the way [SKTexture]`(imageNamed:)` reads it. An image that can't be read
     * becomes the 128×128 missing-image placeholder.
     *
     * @throws IllegalArgumentException if a value is of any other type.
     */
    public constructor(dictionary: Map<String, Any>) : this(packAtlas(dictionary.mapValues { atlasImage(it.value) }))

    /** All names this atlas holds, as they were given (with their extensions for a [named] atlas). */
    public val textureNames: Set<String> get() = textures.keys

    /**
     * The texture named [name], given with or without its image extension (`"hero"` finds
     * `"hero.png"`, and `"hero.png"` finds `"hero"`). As on Apple, an unknown name doesn't fail:
     * it logs a warning and gives the 128×128 missing-image placeholder.
     */
    public fun textureNamed(name: String): SKTexture {
        val key = atlasLookupKey(name)
        return textures[name]
            ?: textures.entries.firstOrNull { atlasLookupKey(it.key) == key }?.value
            ?: SKTexture(missingImagePlaceholder()).also {
                Log.w("SpriteKit", "SKTextureAtlas: no texture named '$name'; using a placeholder.")
            }
    }

    /**
     * Calls [completionHandler] once this atlas is ready to draw — Apple's `preload`. As on Apple,
     * it's called on a background thread, so hop back to the thread you need. Deviation: the
     * images are already decoded when the atlas is created, and textures reach the GPU on the
     * render thread the first time they're drawn, so there's nothing left to do first.
     */
    public fun preload(completionHandler: () -> Unit) {
        skPreload(completionHandler)
    }

    public companion object {
        /** Packs [images] (name to source bitmap) into a single atlas no wider than [maxWidth]. */
        public fun pack(
            images: Map<String, Bitmap>,
            maxWidth: Int = 2048,
        ): SKTextureAtlas = SKTextureAtlas(packAtlas(images, maxWidth))

        /** Calls [completionHandler] once all of [atlases] are ready, on a background thread — see [preload]. */
        @Suppress("UnusedParameter") // Every atlas is ready once created; see [preload].
        public fun preloadTextureAtlases(
            atlases: List<SKTextureAtlas>,
            completionHandler: () -> Unit,
        ) {
            skPreload(completionHandler)
        }

        /**
         * Loads the atlases [atlasNames] on a background thread, then calls [completionHandler]
         * there with them, in order — Apple's `preloadTextureAtlasesNamed(_:withCompletionHandler:)`.
         * As on Apple, a missing atlas is passed as an empty one, not as an error; the error is
         * only for a failure to load at all (no `Context` yet, say), with no atlases.
         */
        public fun preloadTextureAtlasesNamed(
            atlasNames: List<String>,
            completionHandler: (error: Throwable?, atlases: List<SKTextureAtlas>) -> Unit,
        ) {
            skPreloadExecutor.execute {
                val loaded = runCatching { atlasNames.map { SKTextureAtlas(named = it) } }
                completionHandler(loaded.exceptionOrNull(), loaded.getOrDefault(emptyList()))
            }
        }
    }
}

/**
 * The key [SKTextureAtlas.textureNamed] matches names by: the name without a trailing image
 * extension, so a name finds its texture with or without one.
 */
internal fun atlasLookupKey(name: String): String {
    val extension = name.substringAfterLast('.', "")
    return if (extension.lowercase() in imageExtensions) name.dropLast(extension.length + 1) else name
}

/** The `assets/` folder [SKTextureAtlas] (named:) reads for [name], with or without `.atlas`. */
internal fun atlasFolder(name: String): String = if (name.endsWith(".atlas")) name else "$name.atlas"

/** Which of an atlas folder's [files] are images, in a stable order. */
internal fun atlasImageFiles(files: List<String>): List<String> =
    files.filter { it.substringAfterLast('.', "").lowercase() in imageExtensions }.sorted()

private fun loadAtlasNamed(name: String): SKAtlasContents {
    val folder = atlasFolder(name)
    val assets = SKBundle.requireContext().assets
    val files = atlasImageFiles(assets.list(folder)?.toList().orEmpty())
    if (files.isEmpty()) Log.w("SpriteKit", "SKTextureAtlas: no images in assets/$folder/; the atlas is empty.")
    val options = BitmapFactory.Options().apply { inScaled = false }
    val images =
        files.associateWith { file ->
            runCatching { assets.open("$folder/$file").use { BitmapFactory.decodeStream(it, null, options) } }
                .getOrNull() ?: missingImagePlaceholder()
        }
    return packAtlas(images)
}

private fun atlasImage(value: Any): Bitmap =
    when (value) {
        is Bitmap -> value
        is File -> decodeFileOrPlaceholder(value)
        is String -> if (value.startsWith("/")) decodeFileOrPlaceholder(File(value)) else loadNamedImage(value)
        is Uri ->
            runCatching {
                SKBundle.requireContext().contentResolver.openInputStream(value)?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull() ?: missingImagePlaceholder()
        else -> throw IllegalArgumentException(
            "SKTextureAtlas(dictionary:) takes Bitmap, File, Uri or String values, not ${value::class.java.name}",
        )
    }

private fun decodeFileOrPlaceholder(file: File): Bitmap =
    BitmapFactory.decodeFile(file.path) ?: missingImagePlaceholder().also {
        Log.w("SpriteKit", "SKTextureAtlas: can't read an image at '${file.path}'; using a placeholder.")
    }

private fun packAtlas(
    images: Map<String, Bitmap>,
    maxWidth: Int = 2048,
): SKAtlasContents {
    if (images.isEmpty()) return SKAtlasContents(emptyMap())
    val entries = images.map { (name, bitmap) -> SKAtlasEntry(name, bitmap.width, bitmap.height) }
    val layout = packTextureAtlas(entries, maxWidth, ATLAS_BORDER)

    val atlasBitmap = Bitmap.createBitmap(layout.width, layout.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(atlasBitmap)
    // The root SKTexture's SKTextureGpuState is shared by every region below, so the
    // whole atlas bitmap is uploaded to the GPU exactly once.
    val atlasTexture = SKTexture(atlasBitmap)

    val placed = layout.placements.associateBy { it.name }
    val textures =
        images.keys.associateWith { name ->
            val placement = placed.getValue(name)
            drawWithRepeatedEdges(canvas, images.getValue(name), placement.x, placement.y)
            // Top-of-Rect = top-of-bitmap (row 0), matching how the sprite renderer
            // assigns UV coordinates to quad corners — see docs/ARCHITECTURE.md.
            val uvRect =
                Rect(
                    left = placement.x / layout.width.toFloat(),
                    top = placement.y / layout.height.toFloat(),
                    right = (placement.x + placement.width) / layout.width.toFloat(),
                    bottom = (placement.y + placement.height) / layout.height.toFloat(),
                )
            SKTexture(
                atlasBitmap,
                uvRect,
                atlasTexture.gpuState,
                Vector2(placement.width.toFloat(), placement.height.toFloat()),
            )
        }
    return SKAtlasContents(textures)
}

/**
 * Draws [image] at ([x], [y]) and repeats its outermost pixels [ATLAS_BORDER] pixels outward, so
 * sampling just past its edge reads its own edge color rather than a neighbor's.
 */
private fun drawWithRepeatedEdges(
    canvas: Canvas,
    image: Bitmap,
    x: Int,
    y: Int,
) {
    val w = image.width
    val h = image.height
    val b = ATLAS_BORDER

    fun copy(
        src: android.graphics.Rect,
        dst: android.graphics.Rect,
    ) = canvas.drawBitmap(image, src, dst, null)
    copy(android.graphics.Rect(0, 0, w, h), android.graphics.Rect(x, y, x + w, y + h))
    // Edges: the outermost row or column, stretched across the border.
    copy(android.graphics.Rect(0, 0, w, 1), android.graphics.Rect(x, y - b, x + w, y))
    copy(android.graphics.Rect(0, h - 1, w, h), android.graphics.Rect(x, y + h, x + w, y + h + b))
    copy(android.graphics.Rect(0, 0, 1, h), android.graphics.Rect(x - b, y, x, y + h))
    copy(android.graphics.Rect(w - 1, 0, w, h), android.graphics.Rect(x + w, y, x + w + b, y + h))
    // Corners: the corner pixel.
    copy(android.graphics.Rect(0, 0, 1, 1), android.graphics.Rect(x - b, y - b, x, y))
    copy(android.graphics.Rect(w - 1, 0, w, 1), android.graphics.Rect(x + w, y - b, x + w + b, y))
    copy(android.graphics.Rect(0, h - 1, 1, h), android.graphics.Rect(x - b, y + h, x, y + h + b))
    copy(android.graphics.Rect(w - 1, h - 1, w, h), android.graphics.Rect(x + w, y + h, x + w + b, y + h + b))
}
