package jp.co.bitz.spritekit

/**
 * A named palette of [SKTileGroup]s an [SKTileMapNode] draws its tiles from — mirrors Apple's
 * `SKTileSet`. Configured programmatically (no `.sks` tile-set archive format to parse, and no
 * bundled/built-in tile sets — see `docs/API_COMPATIBILITY.md`).
 *
 * Only grid-shaped maps are supported — Apple's `SKTileSetType`/isometric/hexagonal variants
 * aren't, so there's no corresponding property here; see `docs/API_COMPATIBILITY.md`.
 */
public class SKTileSet(
    public val tileGroups: List<SKTileGroup>,
) {
    /** An identifying name, purely for the caller's own bookkeeping. */
    public var name: String? = null

    /** The group new/out-of-bounds-reset tiles fall back to. `null` (the default) leaves a tile empty instead. */
    public var defaultTileGroup: SKTileGroup? = null

    /** The size new tiles default to when a map's own [SKTileMapNode.tileSize] isn't specified some other way. */
    public var defaultTileSize: Vector2 = Vector2(32f, 32f)
}
