package jp.co.bitz.spritekit

import kotlin.math.floor
import kotlin.time.Duration

/** Each of a tile's 8 neighbor directions paired with its [SKTileAdjacencyMask] bit and `(column, row)` offset. */
private val NEIGHBOR_OFFSETS =
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
 * A grid of tiles drawn from [tileSet] — mirrors Apple's `SKTileMapNode`. Column `0`/row `0` is
 * the bottom-left cell (matching this library's y-up convention); [anchorPoint] (like
 * [SKSpriteNode.anchorPoint]) is the normalized point within the whole grid that this node's own
 * [SKNode.position] refers to, defaulting to `(0.5, 0.5)` (centered).
 *
 * Only grid-shaped maps are supported (see `docs/API_COMPATIBILITY.md`); [numberOfColumns]/
 * [numberOfRows] are fixed at construction — Apple allows resizing a live map, this port doesn't.
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

    /** This map's full grid size, in this node's own local space. */
    private val mapSize: Vector2 get() = Vector2(numberOfColumns * tileSize.x, numberOfRows * tileSize.y)

    /** The center of tile ([column], [row]), in this node's own local space -- not bounds-checked, matching Apple. */
    public fun centerOfTile(
        column: Int,
        row: Int,
    ): Vector2 {
        val size = mapSize
        return Vector2(
            (column + 0.5f) * tileSize.x - anchorPoint.x * size.x,
            (row + 0.5f) * tileSize.y - anchorPoint.y * size.y,
        )
    }

    /**
     * The column index [fromPosition] (in this node's own local space) falls within -- not
     * bounds-checked, matching Apple.
     */
    public fun tileColumnIndex(fromPosition: Vector2): Int {
        val size = mapSize
        return floor((fromPosition.x + anchorPoint.x * size.x) / tileSize.x).toInt()
    }

    /**
     * The row index [fromPosition] (in this node's own local space) falls within -- not
     * bounds-checked, matching Apple.
     */
    public fun tileRowIndex(fromPosition: Vector2): Int {
        val size = mapSize
        return floor((fromPosition.y + anchorPoint.y * size.y) / tileSize.y).toInt()
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
     * Re-evaluates ([column], [row])'s own definition, then each of its 8 neighbors' -- placing
     * or clearing a tile can change what best fits any of them.
     */
    private fun remapCellAndNeighbors(
        column: Int,
        row: Int,
    ) {
        remapCell(column, row)
        for ((_, offset) in NEIGHBOR_OFFSETS) {
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
     * Which of ([column], [row])'s 8 neighbors currently belong to [group] too, as an
     * [SKTileAdjacencyMask] combination.
     */
    private fun neighborMask(
        column: Int,
        row: Int,
        group: SKTileGroup,
    ): Int {
        var mask = 0
        for ((bit, offset) in NEIGHBOR_OFFSETS) {
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
