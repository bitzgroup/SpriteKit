package jp.co.bitz.spritekit

/**
 * How an [SKTileMapNode] arranges the cells of a tile set spatially — mirrors Apple's
 * `SKTileSetType`. Selects both the pixel layout [SKTileMapNode.centerOfTile] (and its inverses,
 * [SKTileMapNode.tileColumnIndex]/[SKTileMapNode.tileRowIndex]) use, and which
 * [SKTileAdjacencyMask] directions are meaningful for [SKTileMapNode.enableAutomapping] (see each
 * case's docs).
 *
 * Apple's `.isometric` case isn't implemented — no isometric layout math exists in this port; see
 * `docs/API_COMPATIBILITY.md`.
 */
public enum class SKTileSetType {
    /**
     * A plain rectangular grid — [SKTileMapNode]'s original (and, until this type existed, only)
     * layout. Every neighbor bit in [SKTileAdjacencyMask] is meaningful.
     */
    GRID,

    /**
     * Regular hexagons with a vertex pointing straight up, arranged in horizontal rows; odd rows
     * (`row % 2 != 0`) are shifted right by half a tile width relative to even rows (the
     * "odd-r offset" convention). Adjacent same-row cells touch along a vertical edge, so a cell
     * has no `UP`/`DOWN` neighbor — only [SKTileAdjacencyMask.LEFT]/[SKTileAdjacencyMask.RIGHT]
     * and the four corner bits are meaningful for automapping.
     */
    HEXAGONAL_POINTY,

    /**
     * Regular hexagons with a flat edge pointing straight up, arranged in vertical columns; odd
     * columns (`column % 2 != 0`) are shifted down by half a tile height relative to even columns
     * (the "odd-q offset" convention). Adjacent same-column cells touch along a horizontal edge,
     * so a cell has no `LEFT`/`RIGHT` neighbor — only [SKTileAdjacencyMask.UP]/
     * [SKTileAdjacencyMask.DOWN] and the four corner bits are meaningful for automapping.
     */
    HEXAGONAL_FLAT,
}
