package jp.co.bitz.spritekit

import kotlin.math.floor
import kotlin.time.Duration

/** Each of a grid tile's 8 neighbor directions paired with its [SKTileAdjacencyMask] bit and `(column, row)` offset. */
private val GRID_NEIGHBOR_OFFSETS =
    listOf(
        SKTileAdjacencyMask.UP to (0 to 1),
        SKTileAdjacencyMask.UPPER_RIGHT to (1 to 1),
        SKTileAdjacencyMask.RIGHT to (1 to 0),
        SKTileAdjacencyMask.LOWER_RIGHT to (1 to -1),
        SKTileAdjacencyMask.DOWN to (0 to -1),
        SKTileAdjacencyMask.LOWER_LEFT to (-1 to -1),
        SKTileAdjacencyMask.LEFT to (-1 to 0),
        SKTileAdjacencyMask.UPPER_LEFT to (-1 to 1),
    )

/**
 * A pointy-top hex cell's 6 neighbor directions (no `UP`/`DOWN` -- see [SKTileSetType.HEXAGONAL_POINTY]),
 * for a cell on an even-numbered row (`row % 2 == 0`) in the "odd-r offset" layout.
 */
private val HEXAGONAL_POINTY_EVEN_ROW_OFFSETS =
    listOf(
        SKTileAdjacencyMask.RIGHT to (1 to 0),
        SKTileAdjacencyMask.UPPER_RIGHT to (0 to 1),
        SKTileAdjacencyMask.UPPER_LEFT to (-1 to 1),
        SKTileAdjacencyMask.LEFT to (-1 to 0),
        SKTileAdjacencyMask.LOWER_LEFT to (-1 to -1),
        SKTileAdjacencyMask.LOWER_RIGHT to (0 to -1),
    )

/** Same as [HEXAGONAL_POINTY_EVEN_ROW_OFFSETS], for a cell on an odd-numbered row (`row % 2 != 0`). */
private val HEXAGONAL_POINTY_ODD_ROW_OFFSETS =
    listOf(
        SKTileAdjacencyMask.RIGHT to (1 to 0),
        SKTileAdjacencyMask.UPPER_RIGHT to (1 to 1),
        SKTileAdjacencyMask.UPPER_LEFT to (0 to 1),
        SKTileAdjacencyMask.LEFT to (-1 to 0),
        SKTileAdjacencyMask.LOWER_LEFT to (0 to -1),
        SKTileAdjacencyMask.LOWER_RIGHT to (1 to -1),
    )

/**
 * A flat-top hex cell's 6 neighbor directions (no `LEFT`/`RIGHT` -- see [SKTileSetType.HEXAGONAL_FLAT]),
 * for a cell on an even-numbered column (`column % 2 == 0`) in the "odd-q offset" layout.
 */
private val HEXAGONAL_FLAT_EVEN_COLUMN_OFFSETS =
    listOf(
        SKTileAdjacencyMask.UP to (0 to 1),
        SKTileAdjacencyMask.DOWN to (0 to -1),
        SKTileAdjacencyMask.UPPER_RIGHT to (1 to 0),
        SKTileAdjacencyMask.LOWER_RIGHT to (1 to -1),
        SKTileAdjacencyMask.LOWER_LEFT to (-1 to -1),
        SKTileAdjacencyMask.UPPER_LEFT to (-1 to 0),
    )

/** Same as [HEXAGONAL_FLAT_EVEN_COLUMN_OFFSETS], for a cell on an odd-numbered column (`column % 2 != 0`). */
private val HEXAGONAL_FLAT_ODD_COLUMN_OFFSETS =
    listOf(
        SKTileAdjacencyMask.UP to (0 to 1),
        SKTileAdjacencyMask.DOWN to (0 to -1),
        SKTileAdjacencyMask.UPPER_RIGHT to (1 to 1),
        SKTileAdjacencyMask.LOWER_RIGHT to (1 to 0),
        SKTileAdjacencyMask.LOWER_LEFT to (-1 to 0),
        SKTileAdjacencyMask.UPPER_LEFT to (-1 to 1),
    )

/**
 * [type]'s neighbor-direction table for a cell at ([column], [row]) -- for the two hexagonal
 * types this depends on the cell's own row/column parity (adjacent rows/columns are offset from
 * each other by half a tile), unlike [SKTileSetType.GRID] where it's fixed.
 */
private fun neighborOffsets(
    type: SKTileSetType,
    column: Int,
    row: Int,
): List<Pair<Int, Pair<Int, Int>>> =
    when (type) {
        SKTileSetType.GRID -> GRID_NEIGHBOR_OFFSETS
        SKTileSetType.HEXAGONAL_POINTY ->
            if (row % 2 == 0) HEXAGONAL_POINTY_EVEN_ROW_OFFSETS else HEXAGONAL_POINTY_ODD_ROW_OFFSETS
        SKTileSetType.HEXAGONAL_FLAT ->
            if (column % 2 == 0) HEXAGONAL_FLAT_EVEN_COLUMN_OFFSETS else HEXAGONAL_FLAT_ODD_COLUMN_OFFSETS
    }

/** How much closer together adjacent hex rows (pointy-top) or columns (flat-top) sit than a full tile. */
private const val HEX_LINE_SPACING_FACTOR = 0.75f

/**
 * A grid of tiles drawn from [tileSet] — mirrors Apple's `SKTileMapNode`. Column `0`/row `0` is
 * the bottom-left cell (matching this library's y-up convention); [anchorPoint] (like
 * [SKSpriteNode.anchorPoint]) is the normalized point within the whole grid that this node's own
 * [SKNode.position] refers to, defaulting to `(0.5, 0.5)` (centered).
 *
 * Laid out per [tileSet]'s [SKTileSet.type] — a plain grid, or pointy-/flat-top hexagons (see
 * [SKTileSetType]); [numberOfColumns]/[numberOfRows] are fixed at construction — Apple allows
 * resizing a live map, this port doesn't.
 *
 * Rendered by contributing one quad [SKRenderCommand] per non-empty cell — the same triangle-list
 * shape [SKSpriteNode]/[SKEmitterNode] particles already produce, so [SKSceneRenderer] needed no
 * changes to support tile maps. [SKView]'s frame loop advances each map's own animation clock
 * (`stepTileMaps`, see `SKTileMapSimulation.kt`) so [SKTileDefinition]s with more than one texture
 * animate.
 */
public class SKTileMapNode(
    public var tileSet: SKTileSet,
    public val numberOfColumns: Int,
    public val numberOfRows: Int,
    /** Each cell's footprint, in this node's own local space. Defaults to [SKTileSet.defaultTileSize]. */
    public val tileSize: Vector2 = tileSet.defaultTileSize,
    fillWith: SKTileGroup? = null,
) : SKNode() {
    init {
        require(numberOfColumns > 0) { "numberOfColumns must be positive" }
        require(numberOfRows > 0) { "numberOfRows must be positive" }
    }

    /**
     * The point within the whole grid (normalized `0..1` on each axis) that [SKNode.position]
     * refers to. Defaults to `(0.5, 0.5)`.
     */
    public var anchorPoint: Vector2 = Vector2(0.5f, 0.5f)

    /**
     * When `true`, [setTileGroup] (the 3-argument overload) picks each placed tile's
     * [SKTileDefinition] by matching its actual neighbor configuration against its group's
     * [SKTileGroupRule]s, and re-evaluates already-tiled neighbors too — Apple's auto-tiling.
     * When `false` (the default), a placed tile always uses its group's first rule. See
     * `docs/API_COMPATIBILITY.md` for how ties/imperfect matches are resolved — this is
     * *contract-conformant, not bit-identical* with Apple's own (undocumented) matching algorithm.
     */
    public var enableAutomapping: Boolean = false

    private val groups: Array<SKTileGroup?> = arrayOfNulls(numberOfColumns * numberOfRows)
    private val definitions: Array<SKTileDefinition?> = arrayOfNulls(numberOfColumns * numberOfRows)

    /** This map's own animation clock, advanced by `stepTileMaps` -- see `SKTileMapSimulation.kt`. */
    internal var elapsedTime: Duration = Duration.ZERO

    init {
        if (fillWith != null) {
            for (row in 0 until numberOfRows) {
                for (column in 0 until numberOfColumns) setTileGroup(fillWith, column, row)
            }
        }
    }

    private fun inBounds(
        column: Int,
        row: Int,
    ): Boolean = column in 0 until numberOfColumns && row in 0 until numberOfRows

    private fun index(
        column: Int,
        row: Int,
    ): Int = row * numberOfColumns + column

    /** The group currently placed at ([column], [row]), or `null` if empty or out of bounds. */
    public fun tileGroup(
        column: Int,
        row: Int,
    ): SKTileGroup? = if (inBounds(column, row)) groups[index(column, row)] else null

    /** The specific definition currently rendering at ([column], [row]), or `null` if empty or out of bounds. */
    public fun tileDefinition(
        column: Int,
        row: Int,
    ): SKTileDefinition? = if (inBounds(column, row)) definitions[index(column, row)] else null

    /**
     * Places [tileGroup] at ([column], [row]) (`null` clears it), choosing a [SKTileDefinition]
     * per [enableAutomapping]'s rule-matching behavior. A no-op if ([column], [row]) is out of
     * bounds. See [setTileGroup] (4-argument overload) to set an exact definition directly,
     * bypassing rule matching entirely.
     */
    public fun setTileGroup(
        tileGroup: SKTileGroup?,
        column: Int,
        row: Int,
    ) {
        if (!inBounds(column, row)) return
        groups[index(column, row)] = tileGroup
        if (tileGroup == null) {
            definitions[index(column, row)] = null
        } else if (!enableAutomapping) {
            definitions[index(column, row)] = tileGroup.rules.first().tileDefinitions.random()
        }
        if (enableAutomapping) remapCellAndNeighbors(column, row)
    }

    /**
     * Places [tileGroup] and exactly [tileDefinition] at ([column], [row]), bypassing
     * [enableAutomapping]'s rule matching (and not re-evaluating neighbors either) — Apple's
     * `setTileGroup(_:andTileDefinition:forColumn:row:)`. A no-op if out of bounds.
     */
    public fun setTileGroup(
        tileGroup: SKTileGroup?,
        tileDefinition: SKTileDefinition?,
        column: Int,
        row: Int,
    ) {
        if (!inBounds(column, row)) return
        groups[index(column, row)] = tileGroup
        definitions[index(column, row)] = tileDefinition
    }

    /**
     * This map's full layout size, in this node's own local space. For the hexagonal types this
     * is the tightest box containing every cell, which — because alternating rows/columns
     * overlap by [HEX_LINE_SPACING_FACTOR] of a tile and are offset by half a tile from their
     * neighbors — is *not* simply `numberOfColumns * numberOfRows` tiles' worth of space.
     */
    private val mapSize: Vector2
        get() =
            when (tileSet.type) {
                SKTileSetType.GRID -> Vector2(numberOfColumns * tileSize.x, numberOfRows * tileSize.y)
                SKTileSetType.HEXAGONAL_POINTY ->
                    Vector2(
                        (numberOfColumns + 0.5f) * tileSize.x,
                        tileSize.y + (numberOfRows - 1) * HEX_LINE_SPACING_FACTOR * tileSize.y,
                    )
                SKTileSetType.HEXAGONAL_FLAT ->
                    Vector2(
                        tileSize.x + (numberOfColumns - 1) * HEX_LINE_SPACING_FACTOR * tileSize.x,
                        (numberOfRows + 0.5f) * tileSize.y,
                    )
            }

    /** The center of tile ([column], [row]), in this node's own local space -- not bounds-checked, matching Apple. */
    public fun centerOfTile(
        column: Int,
        row: Int,
    ): Vector2 {
        val size = mapSize
        val beforeAnchor =
            when (tileSet.type) {
                SKTileSetType.GRID -> Vector2((column + 0.5f) * tileSize.x, (row + 0.5f) * tileSize.y)
                SKTileSetType.HEXAGONAL_POINTY -> {
                    val rowShift = if (row % 2 == 0) 0f else 0.5f * tileSize.x
                    Vector2(
                        (column + 0.5f) * tileSize.x + rowShift,
                        (row * HEX_LINE_SPACING_FACTOR + 0.5f) * tileSize.y,
                    )
                }
                SKTileSetType.HEXAGONAL_FLAT -> {
                    val columnShift = if (column % 2 == 0) 0f else 0.5f * tileSize.y
                    Vector2(
                        (column * HEX_LINE_SPACING_FACTOR + 0.5f) * tileSize.x,
                        (row + 0.5f) * tileSize.y + columnShift,
                    )
                }
            }
        return Vector2(beforeAnchor.x - anchorPoint.x * size.x, beforeAnchor.y - anchorPoint.y * size.y)
    }

    /**
     * The column index [fromPosition] (in this node's own local space) falls within -- not
     * bounds-checked, matching Apple. For the hexagonal types, this is the column of whichever
     * cell's [centerOfTile] is nearest [fromPosition] (equivalent to the grid case's simple
     * division, since a regular hexagonal tiling's cells are exactly their centers' Voronoi
     * regions).
     */
    public fun tileColumnIndex(fromPosition: Vector2): Int =
        if (tileSet.type == SKTileSetType.GRID) {
            val size = mapSize
            floor((fromPosition.x + anchorPoint.x * size.x) / tileSize.x).toInt()
        } else {
            nearestTile(fromPosition).first
        }

    /**
     * The row index [fromPosition] (in this node's own local space) falls within -- not
     * bounds-checked, matching Apple. See [tileColumnIndex] for the hexagonal-type approach.
     */
    public fun tileRowIndex(fromPosition: Vector2): Int =
        if (tileSet.type == SKTileSetType.GRID) {
            val size = mapSize
            floor((fromPosition.y + anchorPoint.y * size.y) / tileSize.y).toInt()
        } else {
            nearestTile(fromPosition).second
        }

    /**
     * A rough (column, row) guess for whichever hexagonal cell [localPosition] falls within,
     * cheap to compute but not always exact right at a cell boundary -- see [nearestTile].
     */
    private fun roughHexGuess(localPosition: Vector2): Pair<Int, Int> {
        val size = mapSize
        val beforeAnchor = Vector2(localPosition.x + anchorPoint.x * size.x, localPosition.y + anchorPoint.y * size.y)
        return when (tileSet.type) {
            SKTileSetType.HEXAGONAL_POINTY -> {
                val rowSpacing = HEX_LINE_SPACING_FACTOR * tileSize.y
                floor(beforeAnchor.x / tileSize.x).toInt() to floor(beforeAnchor.y / rowSpacing).toInt()
            }
            SKTileSetType.HEXAGONAL_FLAT -> {
                val columnSpacing = HEX_LINE_SPACING_FACTOR * tileSize.x
                floor(beforeAnchor.x / columnSpacing).toInt() to floor(beforeAnchor.y / tileSize.y).toInt()
            }
            SKTileSetType.GRID -> error("nearestTile is only used for the hexagonal types")
        }
    }

    /**
     * The hexagonal cell whose [centerOfTile] is closest to [localPosition] -- a small local
     * search around [roughHexGuess] rather than a closed-form formula, since a placed hex tile is
     * exactly the Voronoi region of its own center. The search window is generous enough that
     * [roughHexGuess]'s occasional imprecision near a cell boundary never misses the true nearest
     * cell.
     */
    private fun nearestTile(localPosition: Vector2): Pair<Int, Int> {
        val (roughColumn, roughRow) = roughHexGuess(localPosition)
        var best = roughColumn to roughRow
        var bestDistanceSquared = Float.MAX_VALUE
        for (deltaRow in -2..2) {
            for (deltaColumn in -2..2) {
                val candidate = (roughColumn + deltaColumn) to (roughRow + deltaRow)
                val center = centerOfTile(candidate.first, candidate.second)
                val dx = center.x - localPosition.x
                val dy = center.y - localPosition.y
                val distanceSquared = dx * dx + dy * dy
                if (distanceSquared < bestDistanceSquared) {
                    bestDistanceSquared = distanceSquared
                    best = candidate
                }
            }
        }
        return best
    }

    override val localBounds: Rect
        get() {
            val size = mapSize
            return Rect(
                left = 0f - anchorPoint.x * size.x,
                top = 0f - anchorPoint.y * size.y,
                right = size.x * (1f - anchorPoint.x),
                bottom = size.y * (1f - anchorPoint.y),
            )
        }

    /**
     * Re-evaluates ([column], [row])'s own definition, then each of its neighbors' (8 for
     * [SKTileSetType.GRID], 6 for the hexagonal types) -- placing or clearing a tile can change
     * what best fits any of them.
     */
    private fun remapCellAndNeighbors(
        column: Int,
        row: Int,
    ) {
        remapCell(column, row)
        for ((_, offset) in neighborOffsets(tileSet.type, column, row)) {
            val (dx, dy) = offset
            remapCell(column + dx, row + dy)
        }
    }

    /**
     * Re-picks ([column], [row])'s [SKTileDefinition] from its group's best-matching
     * [SKTileGroupRule], if it has a group at all.
     */
    private fun remapCell(
        column: Int,
        row: Int,
    ) {
        val group = tileGroup(column, row) ?: return
        val rule = bestRule(group, neighborMask(column, row, group))
        definitions[index(column, row)] = rule.tileDefinitions.random()
    }

    /**
     * Which of ([column], [row])'s neighbors currently belong to [group] too, as an
     * [SKTileAdjacencyMask] combination.
     */
    private fun neighborMask(
        column: Int,
        row: Int,
        group: SKTileGroup,
    ): Int {
        var mask = 0
        for ((bit, offset) in neighborOffsets(tileSet.type, column, row)) {
            val (dx, dy) = offset
            if (tileGroup(column + dx, row + dy) === group) mask = mask or bit
        }
        return mask
    }

    /**
     * [group]'s rule whose [SKTileGroupRule.adjacency] shares the most bits with [neighborMask]
     * -- an exact match wins outright; see this class's [enableAutomapping] docs.
     */
    private fun bestRule(
        group: SKTileGroup,
        neighborMask: Int,
    ): SKTileGroupRule = group.rules.maxBy { rule -> 8 - (rule.adjacency xor neighborMask).countOneBits() }
}
