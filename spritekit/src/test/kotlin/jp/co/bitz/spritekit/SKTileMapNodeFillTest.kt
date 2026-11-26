package jp.co.bitz.spritekit

import android.graphics.Color
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * `fill`, `mapSize`, the stored color properties, `SKTileGroup.empty()` and placement weights,
 * matching Apple's behavior recorded on iOS/tvOS simulators.
 */
class SKTileMapNodeFillTest {
    private fun map(
        group: SKTileGroup,
        columns: Int = 3,
        rows: Int = 2,
    ) = SKTileMapNode(SKTileSet(listOf(group)), columns, rows, tileSize = Vector2(10f, 10f))

    private val edge = SKTileDefinition().apply { name = "edge" }
    private val center = SKTileDefinition().apply { name = "center" }
    private val ruledGroup =
        SKTileGroup(
            listOf(
                SKTileGroupRule(listOf(edge), SKTileAdjacencyMask.UP),
                SKTileGroupRule(listOf(center), SKTileAdjacencyMask.ALL),
            ),
        )

    @Test
    fun `defaults match Apple's`() {
        val map = map(SKTileGroup(SKTileDefinition()))

        assertTrue(map.enableAutomapping)
        assertEquals(Color.WHITE, map.color)
        assertEquals(0f, map.colorBlendFactor)
        assertEquals(SKBlendMode.Alpha, map.blendMode)
    }

    @Test
    fun `a grid map's size is its unscaled tiles`() {
        val map =
            SKTileMapNode(
                SKTileSet(listOf(SKTileGroup(SKTileDefinition()))),
                numberOfColumns = 4,
                numberOfRows = 5,
                tileSize = Vector2(10f, 12f),
            )
        map.setScale(2f)

        assertEquals(Vector2(40f, 60f), map.mapSize)
    }

    @Test
    fun `fill sets every cell to the group's first rule, even when automapping`() {
        for (automapping in listOf(false, true)) {
            val map = map(ruledGroup, columns = 3, rows = 3).apply { enableAutomapping = automapping }

            map.fill(ruledGroup)

            for (row in 0 until 3) {
                for (column in 0 until 3) {
                    assertSame(ruledGroup, map.tileGroup(column, row))
                    assertSame(edge, map.tileDefinition(column, row))
                }
            }
            map.fill(null)
            assertNull(map.tileGroup(1, 1))
            assertNull(map.tileDefinition(1, 1))
        }
    }

    @Test
    fun `the filling constructor fills like fill`() {
        val map =
            SKTileMapNode(
                SKTileSet(listOf(ruledGroup)),
                numberOfColumns = 3,
                numberOfRows = 3,
                tileSize = Vector2(10f, 10f),
                fillWith = ruledGroup,
            )

        assertTrue(map.enableAutomapping)
        assertSame(edge, map.tileDefinition(1, 1))
    }

    @Test
    fun `placing the empty group clears a cell`() {
        val group = SKTileGroup(SKTileDefinition())
        val map = map(group)
        map.fill(group)

        map.setTileGroup(SKTileGroup.empty(), 1, 1)
        assertNull(map.tileGroup(1, 1))
        assertNull(map.tileDefinition(1, 1))

        map.fill(SKTileGroup.empty())
        assertNull(map.tileGroup(0, 0))
    }

    @Test
    fun `copies keep the color properties`() {
        val map =
            map(SKTileGroup(SKTileDefinition())).apply {
                color = Color.RED
                colorBlendFactor = 0.5f
                blendMode = SKBlendMode.Add
            }

        val copy = map.copy()

        assertEquals(Color.RED, copy.color)
        assertEquals(0.5f, copy.colorBlendFactor)
        assertEquals(SKBlendMode.Add, copy.blendMode)
    }

    private fun shareOfFirst(
        weightA: Int,
        weightB: Int,
    ): Double {
        val a = SKTileDefinition().apply { placementWeight = weightA }
        val b = SKTileDefinition().apply { placementWeight = weightB }
        val random = Random(42)
        val picks = List(4000) { pickWeighted(listOf(a, b), random) }
        return picks.count { it === a } / 4000.0
    }

    @Test
    fun `placement weights choose between a rule's definitions`() {
        assertEquals(1.0, shareOfFirst(1, 0))
        assertEquals(0.0, shareOfFirst(0, 1))
        assertEquals(0.5, shareOfFirst(1, 1), 0.05)
        assertEquals(0.75, shareOfFirst(3, 1), 0.05)
        // With every weight 0, Apple picks evenly.
        assertEquals(0.5, shareOfFirst(0, 0), 0.05)
    }

    @Test
    fun `fill uses placement weights`() {
        val never = SKTileDefinition().apply { placementWeight = 0 }
        val always = SKTileDefinition()
        val group = SKTileGroup(listOf(SKTileGroupRule(listOf(never, always))))
        val map = map(group, columns = 10, rows = 10)

        map.fill(group)

        for (row in 0 until 10) {
            for (column in 0 until 10) assertSame(always, map.tileDefinition(column, row))
        }
    }
}
