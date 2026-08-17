package jp.co.bitz.spritekit

/** One image to be packed into an atlas, identified by [name] and its pixel dimensions. */
internal data class SKAtlasEntry(
    val name: String,
    val width: Int,
    val height: Int,
)

/** Where [name]'s image was placed within the packed atlas, in pixel coordinates from its top-left corner. */
internal data class SKAtlasPlacement(
    val name: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

/** The result of [packTextureAtlas]: the packed atlas's overall pixel size and every entry's placement within it. */
internal data class SKAtlasLayout(
    val width: Int,
    val height: Int,
    val placements: List<SKAtlasPlacement>,
)

/**
 * Packs [entries] into a single atlas no wider than [maxWidth], using a classic shelf/next-fit
 * algorithm: entries are sorted tallest-first, then placed left-to-right, wrapping onto a new
 * "shelf" (row) whenever the current one would exceed [maxWidth]. Simple and not
 * space-optimal — Apple auto-packs atlases with an undocumented (Xcode build-time) algorithm this
 * library has no access to replicate, so this is a *contract-conformant, not bit-identical*
 * runtime alternative; see `docs/API_COMPATIBILITY.md`.
 *
 * Pure Kotlin — no [android.graphics.Bitmap] dependency — so it's unit-testable without an
 * Android runtime; [SKTextureAtlas] does the actual bitmap compositing this layout describes.
 */
internal fun packTextureAtlas(
    entries: List<SKAtlasEntry>,
    maxWidth: Int = 2048,
): SKAtlasLayout {
    val placements = mutableListOf<SKAtlasPlacement>()
    var cursorX = 0
    var shelfY = 0
    var shelfHeight = 0
    var atlasWidth = 0

    for (entry in entries.sortedByDescending { it.height }) {
        if (cursorX > 0 && cursorX + entry.width > maxWidth) {
            shelfY += shelfHeight
            cursorX = 0
            shelfHeight = 0
        }
        placements += SKAtlasPlacement(entry.name, cursorX, shelfY, entry.width, entry.height)
        cursorX += entry.width
        shelfHeight = maxOf(shelfHeight, entry.height)
        atlasWidth = maxOf(atlasWidth, cursorX)
    }

    return SKAtlasLayout(atlasWidth, shelfY + shelfHeight, placements)
}
