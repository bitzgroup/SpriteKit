package jp.co.bitz.spritekit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SKTileMapNodeTest {
    private fun tileSet(group: SKTileGroup): SKTileSet = SKTileSet(listOf(group))

    @Test
    fun `a new map is empty everywhere`() {
        val group = SKTileGroup(SKTileDefinition())
        val map = SKTileMapNode(tileSet(group), numberOfColumns = 2, numberOfRows = 2, tileSize = Vector2(10f, 10f))

        assertNull(map.tileGroup(0, 0))
        assertNull(map.tileDefinition(0, 0))
    }

    @Test
    fun `tileGroup and tileDefinition return null for out-of-bounds coordinates`() {
        val group = SKTileGroup(SKTileDefinition())
        val map = SKTileMapNode(tileSet(group), numberOfColumns = 2, numberOfRows = 2, tileSize = Vector2(10f, 10f))

        assertNull(map.tileGroup(-1, 0))
        assertNull(map.tileGroup(0, 2))
        assertNull(map.tileDefinition(2, 2))
    }

    @Test
    fun `setTileGroup on an out-of-bounds cell is a no-op`() {
        val group = SKTileGroup(SKTileDefinition())
        val map = SKTileMapNode(tileSet(group), numberOfColumns = 2, numberOfRows = 2, tileSize = Vector2(10f, 10f))

        map.setTileGroup(group, 5, 5) // must not throw

        assertNull(map.tileGroup(5, 5))
    }

    @Test
    fun `fillWith places the given group in every cell`() {
        val group = SKTileGroup(SKTileDefinition())
        val map =
            SKTileMapNode(
                tileSet(group),
                numberOfColumns = 2,
                numberOfRows = 2,
                tileSize = Vector2(10f, 10f),
                fillWith = group,
            )

        for (row in 0 until 2) {
            for (column in 0 until 2) {
                assertSame(group, map.tileGroup(column, row))
            }
        }
    }

    @Test
    fun `without automapping, a placed tile always uses its group's first rule`() {
        val firstRuleDefinition = SKTileDefinition()
        val secondRuleDefinition = SKTileDefinition()
        val group =
            SKTileGroup(
                listOf(
                    SKTileGroupRule(listOf(firstRuleDefinition), adjacency = SKTileAdjacencyMask.NONE),
                    SKTileGroupRule(listOf(secondRuleDefinition), adjacency = SKTileAdjacencyMask.ALL),
                ),
            )
        val map = SKTileMapNode(tileSet(group), numberOfColumns = 1, numberOfRows = 1, tileSize = Vector2(10f, 10f))

        map.setTileGroup(group, 0, 0)

        assertSame(firstRuleDefinition, map.tileDefinition(0, 0))
    }

    @Test
    fun `setTileGroup with null clears a cell's group and definition`() {
        val group = SKTileGroup(SKTileDefinition())
        val map = SKTileMapNode(tileSet(group), numberOfColumns = 1, numberOfRows = 1, tileSize = Vector2(10f, 10f))
        map.setTileGroup(group, 0, 0)

        map.setTileGroup(null, 0, 0)

        assertNull(map.tileGroup(0, 0))
        assertNull(map.tileDefinition(0, 0))
    }

    @Test
    fun `the 4-argument setTileGroup sets an exact definition, bypassing rule matching`() {
        val group = SKTileGroup(SKTileDefinition())
        val customDefinition = SKTileDefinition().apply { name = "custom" }
        val map =
            SKTileMapNode(tileSet(group), numberOfColumns = 1, numberOfRows = 1, tileSize = Vector2(10f, 10f)).apply {
                enableAutomapping = true
            }

        map.setTileGroup(group, customDefinition, 0, 0)

        assertSame(group, map.tileGroup(0, 0))
        assertSame(customDefinition, map.tileDefinition(0, 0))
    }

    @Test
    fun `automapping picks the rule matching no same-group neighbors for a lone tile`() {
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
            SKTileMapNode(tileSet(group), numberOfColumns = 3, numberOfRows = 3, tileSize = Vector2(10f, 10f)).apply {
                enableAutomapping = true
            }

        map.setTileGroup(group, 1, 1) // the only tile in the whole map -- no same-group neighbors

        assertSame(isolatedDefinition, map.tileDefinition(1, 1))
    }

    @Test
    fun `automapping picks the all-adjacency rule once a tile is fully surrounded`() {
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
            SKTileMapNode(tileSet(group), numberOfColumns = 3, numberOfRows = 3, tileSize = Vector2(10f, 10f)).apply {
                enableAutomapping = true
            }

        for (row in 0 until 3) {
            for (column in 0 until 3) map.setTileGroup(group, column, row)
        }

        // The center tile ends up re-evaluated after every one of its 8 neighbors is placed.
        assertSame(surroundedDefinition, map.tileDefinition(1, 1))
    }

    @Test
    fun `clearing a tile updates its former neighbor's automapping`() {
        val isolatedDefinition = SKTileDefinition()
        val pairedDefinition = SKTileDefinition()
        val group =
            SKTileGroup(
                listOf(
                    SKTileGroupRule(listOf(isolatedDefinition), adjacency = SKTileAdjacencyMask.NONE),
                    SKTileGroupRule(listOf(pairedDefinition), adjacency = SKTileAdjacencyMask.RIGHT),
                ),
            )
        val map =
            SKTileMapNode(tileSet(group), numberOfColumns = 2, numberOfRows = 1, tileSize = Vector2(10f, 10f)).apply {
                enableAutomapping = true
            }
        map.setTileGroup(group, 0, 0)
        map.setTileGroup(group, 1, 0)
        assertSame(pairedDefinition, map.tileDefinition(0, 0)) // has a RIGHT neighbor of the same group

        map.setTileGroup(null, 1, 0) // remove that neighbor

        assertSame(isolatedDefinition, map.tileDefinition(0, 0)) // back to having none
    }

    @Test
    fun `centerOfTile is relative to the default centered anchorPoint`() {
        val group = SKTileGroup(SKTileDefinition())
        val map = SKTileMapNode(tileSet(group), numberOfColumns = 2, numberOfRows = 2, tileSize = Vector2(10f, 10f))

        assertEquals(Vector2(-5f, -5f), map.centerOfTile(0, 0))
        assertEquals(Vector2(5f, 5f), map.centerOfTile(1, 1))
    }

    @Test
    fun `tileColumnIndex and tileRowIndex invert centerOfTile`() {
        val group = SKTileGroup(SKTileDefinition())
        val map = SKTileMapNode(tileSet(group), numberOfColumns = 4, numberOfRows = 3, tileSize = Vector2(10f, 10f))

        val center = map.centerOfTile(2, 1)

        assertEquals(2, map.tileColumnIndex(center))
        assertEquals(1, map.tileRowIndex(center))
    }

    @Test
    fun `localBounds spans the whole grid, centered on the default anchorPoint`() {
        val group = SKTileGroup(SKTileDefinition())
        val map = SKTileMapNode(tileSet(group), numberOfColumns = 2, numberOfRows = 2, tileSize = Vector2(10f, 10f))

        val bounds = map.calculateAccumulatedFrame()

        assertEquals(Rect(-10f, -10f, 10f, 10f), bounds)
    }

    @Test
    fun `constructing with a non-positive column or row count throws`() {
        val group = SKTileGroup(SKTileDefinition())
        assertTrue(
            runCatching {
                SKTileMapNode(tileSet(group), numberOfColumns = 0, numberOfRows = 1, tileSize = Vector2(10f, 10f))
            }.isFailure,
        )
    }
}
