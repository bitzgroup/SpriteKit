package jp.co.bitz.spritekit

/**
 * Bitmask flags describing which of a tile's 8 neighbors belong to the same [SKTileGroup] —
 * mirrors Apple's `SKTileAdjacencyMask`. [SKTileMapNode]'s automapping (see
 * [SKTileMapNode.enableAutomapping]) computes one of these for each placed tile and matches it
 * against each candidate [SKTileGroupRule.adjacency] in that tile's group to pick a fitting
 * [SKTileDefinition].
 */
public object SKTileAdjacencyMask {
    public const val UP: Int = 1 shl 0
    public const val UPPER_RIGHT: Int = 1 shl 1
    public const val RIGHT: Int = 1 shl 2
    public const val LOWER_RIGHT: Int = 1 shl 3
    public const val DOWN: Int = 1 shl 4
    public const val LOWER_LEFT: Int = 1 shl 5
    public const val LEFT: Int = 1 shl 6
    public const val UPPER_LEFT: Int = 1 shl 7

    /** [UP]/[DOWN]/[LEFT]/[RIGHT] only, no corners. */
    public const val ALL_EDGES: Int = UP or DOWN or LEFT or RIGHT

    /** Every one of the 8 directions. */
    public const val ALL: Int = ALL_EDGES or UPPER_RIGHT or LOWER_RIGHT or LOWER_LEFT or UPPER_LEFT

    /** No neighbors share this tile's group — the rule for a fully isolated tile. */
    public const val NONE: Int = 0
}

/**
 * One candidate appearance for a tile within an [SKTileGroup]: [tileDefinitions] (one is picked at
 * random when more than one, for visual variety among tiles satisfying the same adjacency) apply
 * when a placed tile's actual neighbor configuration best matches [adjacency] (an
 * [SKTileAdjacencyMask] combination) — mirrors Apple's `SKTileGroupRule`.
 */
public class SKTileGroupRule(
    public val tileDefinitions: List<SKTileDefinition>,
    public val adjacency: Int = SKTileAdjacencyMask.ALL,
) {
    init {
        require(tileDefinitions.isNotEmpty()) { "tileDefinitions must not be empty" }
    }

    /** An identifying name, purely for the caller's own bookkeeping. */
    public var name: String? = null
}
