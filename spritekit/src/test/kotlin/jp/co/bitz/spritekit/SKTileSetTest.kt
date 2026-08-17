package jp.co.bitz.spritekit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.seconds

class SKTileSetTest {
    @Test
    fun `frameIndexAt stays at 0 for a single-frame animation`() {
        assertEquals(0, frameIndexAt(5.seconds, 1.seconds, frameCount = 1))
    }

    @Test
    fun `frameIndexAt advances one frame per timePerFrame, looping`() {
        assertEquals(0, frameIndexAt(0.seconds, 1.seconds, frameCount = 3))
        assertEquals(1, frameIndexAt(1.seconds, 1.seconds, frameCount = 3))
        assertEquals(2, frameIndexAt(2.seconds, 1.seconds, frameCount = 3))
        assertEquals(0, frameIndexAt(3.seconds, 1.seconds, frameCount = 3)) // wraps
        assertEquals(1, frameIndexAt(4.seconds, 1.seconds, frameCount = 3))
    }

    @Test
    fun `a tile definition with no textures has an empty animation`() {
        val definition = SKTileDefinition(size = Vector2(32f, 32f))

        assertEquals(emptyList(), definition.textures)
    }

    @Test
    fun `a tile definition requires no size or textures to construct`() {
        // Constructible entirely with defaults -- convenient for tests, and for tile maps that
        // only care about a definition's identity/adjacency role, not its visual.
        SKTileDefinition()
    }

    @Test
    fun `a rule requires at least one tile definition`() {
        assertFailsWith<IllegalArgumentException> { SKTileGroupRule(emptyList()) }
    }

    @Test
    fun `a rule defaults to matching every adjacency`() {
        val rule = SKTileGroupRule(listOf(SKTileDefinition()))

        assertEquals(SKTileAdjacencyMask.ALL, rule.adjacency)
    }

    @Test
    fun `SKTileAdjacencyMask ALL combines every individual direction`() {
        val directions =
            listOf(
                SKTileAdjacencyMask.UP,
                SKTileAdjacencyMask.UPPER_RIGHT,
                SKTileAdjacencyMask.RIGHT,
                SKTileAdjacencyMask.LOWER_RIGHT,
                SKTileAdjacencyMask.DOWN,
                SKTileAdjacencyMask.LOWER_LEFT,
                SKTileAdjacencyMask.LEFT,
                SKTileAdjacencyMask.UPPER_LEFT,
            )

        assertEquals(SKTileAdjacencyMask.ALL, directions.reduce { a, b -> a or b })
        assertEquals(0, SKTileAdjacencyMask.NONE)
    }

    @Test
    fun `a group requires at least one rule`() {
        assertFailsWith<IllegalArgumentException> { SKTileGroup(emptyList()) }
    }

    @Test
    fun `the single-definition group constructor creates one all-adjacency rule`() {
        val definition = SKTileDefinition()

        val group = SKTileGroup(definition)

        val rule = group.rules.single()
        assertEquals(SKTileAdjacencyMask.ALL, rule.adjacency)
        assertSame(definition, rule.tileDefinitions.single())
    }

    @Test
    fun `a tile set's default tile group is null unless set`() {
        val tileSet = SKTileSet(listOf(SKTileGroup(SKTileDefinition())))

        assertEquals(null, tileSet.defaultTileGroup)
    }
}
