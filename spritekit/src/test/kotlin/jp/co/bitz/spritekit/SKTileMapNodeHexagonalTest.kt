package jp.co.bitz.spritekit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * [SKTileMapNode]'s [SKTileSetType.HEXAGONAL_POINTY]/[SKTileSetType.HEXAGONAL_FLAT] layouts:
 * pixel placement (`centerOfTile` and its inverses) and automapping's 6-neighbor adjacency.
 * [SKTileMapNodeTest] covers the (unaffected) [SKTileSetType.GRID] behavior.
 */
class SKTileMapNodeHexagonalTest {
    private fun tileSet(
        group: SKTileGroup,
        type: SKTileSetType,
    ): SKTileSet = SKTileSet(listOf(group), type = type)

    @Test
    fun `pointy-top centerOfTile shifts odd rows right by half a tile`() {
        val group = SKTileGroup(SKTileDefinition())
        val map =
            SKTileMapNode(
                tileSet(group, SKTileSetType.HEXAGONAL_POINTY),
                numberOfColumns = 2,
                numberOfRows = 2,
                tileSize = Vector2(10f, 10f),
            ).apply { anchorPoint = Vector2(0f, 0f) }

        // Even row (0): plain grid-style horizontal spacing.
        assertEquals(Vector2(5f, 5f), map.centerOfTile(0, 0))
        assertEquals(Vector2(15f, 5f), map.centerOfTile(1, 0))
        // Odd row (1): shifted right by half a tile width, and only 3/4 of a tile height above row 0.
        assertEquals(Vector2(10f, 12.5f), map.centerOfTile(0, 1))
        assertEquals(Vector2(20f, 12.5f), map.centerOfTile(1, 1))
    }

    @Test
    fun `flat-top centerOfTile shifts odd columns down by half a tile`() {
        val group = SKTileGroup(SKTileDefinition())
        val map =
            SKTileMapNode(
                tileSet(group, SKTileSetType.HEXAGONAL_FLAT),
                numberOfColumns = 2,
                numberOfRows = 2,
                tileSize = Vector2(10f, 10f),
            ).apply { anchorPoint = Vector2(0f, 0f) }

        // Even column (0): plain grid-style vertical spacing.
        assertEquals(Vector2(5f, 5f), map.centerOfTile(0, 0))
        assertEquals(Vector2(5f, 15f), map.centerOfTile(0, 1))
        // Odd column (1): shifted down by half a tile height, and only 3/4 of a tile width right of column 0.
        assertEquals(Vector2(12.5f, 10f), map.centerOfTile(1, 0))
        assertEquals(Vector2(12.5f, 20f), map.centerOfTile(1, 1))
    }

    @Test
    fun `tileColumnIndex and tileRowIndex invert centerOfTile for pointy-top, across many rows and columns`() {
        val group = SKTileGroup(SKTileDefinition())
        val map =
            SKTileMapNode(
                tileSet(group, SKTileSetType.HEXAGONAL_POINTY),
                numberOfColumns = 6,
                numberOfRows = 6,
                tileSize = Vector2(10f, 14f),
            )

        for (row in 0 until 6) {
            for (column in 0 until 6) {
                val center = map.centerOfTile(column, row)
                assertEquals(column, map.tileColumnIndex(center), "column round-trip at ($column, $row)")
                assertEquals(row, map.tileRowIndex(center), "row round-trip at ($column, $row)")
            }
        }
    }

    @Test
    fun `tileColumnIndex and tileRowIndex invert centerOfTile for flat-top, across many rows and columns`() {
        val group = SKTileGroup(SKTileDefinition())
        val map =
            SKTileMapNode(
                tileSet(group, SKTileSetType.HEXAGONAL_FLAT),
                numberOfColumns = 6,
                numberOfRows = 6,
                tileSize = Vector2(14f, 10f),
            )

        for (row in 0 until 6) {
            for (column in 0 until 6) {
                val center = map.centerOfTile(column, row)
                assertEquals(column, map.tileColumnIndex(center), "column round-trip at ($column, $row)")
                assertEquals(row, map.tileRowIndex(center), "row round-trip at ($column, $row)")
            }
        }
    }

    @Test
    fun `pointy-top localBounds accounts for the half-tile row offset and 3-4 row spacing`() {
        val group = SKTileGroup(SKTileDefinition())
        val map =
            SKTileMapNode(
                tileSet(group, SKTileSetType.HEXAGONAL_POINTY),
                numberOfColumns = 3,
                numberOfRows = 2,
                tileSize = Vector2(10f, 10f),
            )

        // width = (3 + 0.5) * 10 = 35; height = 10 + (2 - 1) * 0.75 * 10 = 17.5
        assertEquals(Rect(-17.5f, -8.75f, 17.5f, 8.75f), map.calculateAccumulatedFrame())
    }

    @Test
    fun `flat-top localBounds accounts for the half-tile column offset and 3-4 column spacing`() {
        val group = SKTileGroup(SKTileDefinition())
        val map =
            SKTileMapNode(
                tileSet(group, SKTileSetType.HEXAGONAL_FLAT),
                numberOfColumns = 2,
                numberOfRows = 3,
                tileSize = Vector2(10f, 10f),
            )

        // width = 10 + (2 - 1) * 0.75 * 10 = 17.5; height = (3 + 0.5) * 10 = 35
        assertEquals(Rect(-8.75f, -17.5f, 8.75f, 17.5f), map.calculateAccumulatedFrame())
    }

    @Test
    fun `pointy-top automapping treats only the 6 true hex neighbors -- odd row -- as adjacent`() {
        val isolatedDefinition = SKTileDefinition()
        val surroundedDefinition = SKTileDefinition()
        val hexAllAdjacency =
            SKTileAdjacencyMask.LEFT or SKTileAdjacencyMask.RIGHT or
                SKTileAdjacencyMask.UPPER_LEFT or SKTileAdjacencyMask.UPPER_RIGHT or
                SKTileAdjacencyMask.LOWER_LEFT or SKTileAdjacencyMask.LOWER_RIGHT
        val group =
            SKTileGroup(
                listOf(
                    SKTileGroupRule(listOf(isolatedDefinition), adjacency = SKTileAdjacencyMask.NONE),
                    SKTileGroupRule(listOf(surroundedDefinition), adjacency = hexAllAdjacency),
                ),
            )
        val map =
            SKTileMapNode(
                tileSet(group, SKTileSetType.HEXAGONAL_POINTY),
                numberOfColumns = 3,
                numberOfRows = 3,
                tileSize = Vector2(10f, 10f),
            ).apply { enableAutomapping = true }

        // (1, 1) sits on an odd row; its 6 true neighbors under the odd-r offset layout.
        val trueNeighbors = listOf(2 to 1, 2 to 2, 1 to 2, 0 to 1, 1 to 0, 2 to 0)
        for ((column, row) in trueNeighbors) map.setTileGroup(group, column, row)
        map.setTileGroup(group, 1, 1)

        assertSame(surroundedDefinition, map.tileDefinition(1, 1))
    }

    @Test
    fun `flat-top automapping treats only the 6 true hex neighbors -- odd column -- as adjacent`() {
        val isolatedDefinition = SKTileDefinition()
        val surroundedDefinition = SKTileDefinition()
        val hexAllAdjacency =
            SKTileAdjacencyMask.UP or SKTileAdjacencyMask.DOWN or
                SKTileAdjacencyMask.UPPER_LEFT or SKTileAdjacencyMask.UPPER_RIGHT or
                SKTileAdjacencyMask.LOWER_LEFT or SKTileAdjacencyMask.LOWER_RIGHT
        val group =
            SKTileGroup(
                listOf(
                    SKTileGroupRule(listOf(isolatedDefinition), adjacency = SKTileAdjacencyMask.NONE),
                    SKTileGroupRule(listOf(surroundedDefinition), adjacency = hexAllAdjacency),
                ),
            )
        val map =
            SKTileMapNode(
                tileSet(group, SKTileSetType.HEXAGONAL_FLAT),
                numberOfColumns = 3,
                numberOfRows = 3,
                tileSize = Vector2(10f, 10f),
            ).apply { enableAutomapping = true }

        // (1, 1) sits on an odd column; its 6 true neighbors under the odd-q offset layout.
        val trueNeighbors = listOf(1 to 2, 1 to 0, 2 to 2, 2 to 1, 0 to 1, 0 to 2)
        for ((column, row) in trueNeighbors) map.setTileGroup(group, column, row)
        map.setTileGroup(group, 1, 1)

        assertSame(surroundedDefinition, map.tileDefinition(1, 1))
    }

    @Test
    fun `an isolated hex tile with no same-group neighbors matches the no-adjacency rule`() {
        val isolatedDefinition = SKTileDefinition()
        val surroundedDefinition = SKTileDefinition()
        val group =
            SKTileGroup(
                listOf(
                    SKTileGroupRule(listOf(isolatedDefinition), adjacency = SKTileAdjacencyMask.NONE),
                    SKTileGroupRule(listOf(surroundedDefinition), adjacency = SKTileAdjacencyMask.ALL),
                ),
            )
        val map =
            SKTileMapNode(
                tileSet(group, SKTileSetType.HEXAGONAL_POINTY),
                numberOfColumns = 3,
                numberOfRows = 3,
                tileSize = Vector2(10f, 10f),
            ).apply { enableAutomapping = true }

        map.setTileGroup(group, 1, 1)

        assertSame(isolatedDefinition, map.tileDefinition(1, 1))
    }
}
