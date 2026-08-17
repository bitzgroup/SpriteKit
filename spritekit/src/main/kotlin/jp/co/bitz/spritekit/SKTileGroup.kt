package jp.co.bitz.spritekit

/**
 * A named "terrain" a [SKTileMapNode] cell can be set to (e.g. grass, water) — mirrors Apple's
 * `SKTileGroup`. [rules] are matched against a placed tile's actual neighbor configuration by
 * [SKTileMapNode]'s automapping (see [SKTileMapNode.enableAutomapping]) to pick which
 * [SKTileDefinition] renders there.
 */
public class SKTileGroup(
    public val rules: List<SKTileGroupRule>,
) {
    init {
        require(rules.isNotEmpty()) { "rules must not be empty" }
    }

    /**
     * A group with a single rule matching [SKTileAdjacencyMask.ALL] — Apple's
     * `SKTileGroup(tileDefinition:)`, for a non-auto-tiling group that always renders the same way
     * regardless of its neighbors.
     */
    public constructor(tileDefinition: SKTileDefinition) : this(listOf(SKTileGroupRule(listOf(tileDefinition))))

    /** An identifying name, purely for the caller's own bookkeeping. */
    public var name: String? = null
}
