package jp.co.bitz.spritekit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Mirrors the tic-tac-toe sample's `SKViewDebugOverlayParityTests` probes. */
class SKDebugOverlayTest {
    private val statistics = SKRenderStatistics(drawCount = 3, quadCount = 334)

    private fun text(selection: SKStatisticsSelection) =
        skStatisticsText(
            selection,
            nodeCount = 7,
            statistics,
            framesPerSecond = 58.94,
        )

    @Test
    fun `the statistics line lists the enabled counters, then the frame rate after two spaces`() {
        assertEquals(
            "nodes:7 draws:3 quads:334  58.9 fps",
            text(SKStatisticsSelection(fps = true, nodeCount = true, drawCount = true, quadCount = true)),
        )
        assertEquals("58.9 fps", text(SKStatisticsSelection(fps = true)))
        assertEquals("nodes:7", text(SKStatisticsSelection(nodeCount = true)))
        assertEquals("draws:3 quads:334", text(SKStatisticsSelection(drawCount = true, quadCount = true)))
        assertEquals("nodes:7  58.9 fps", text(SKStatisticsSelection(fps = true, nodeCount = true)))
        assertNull(text(SKStatisticsSelection()))
    }

    @Test
    fun `the node count includes the scene, hidden nodes and plain nodes`() {
        val scene = SKScene(size = Vector2(320f, 240f))
        assertEquals(1, skNodeCount(scene))
        val parent = SKNode()
        parent.addChild(SKSpriteNode(color = 0, size = Vector2(10f, 10f)).apply { isHidden = true })
        scene.addChild(parent)
        scene.addChild(SKSpriteNode(color = 0, size = Vector2(10f, 10f)))
        assertEquals(4, skNodeCount(scene))
    }

    @Test
    fun `the frame rate is measured over half-second windows`() {
        val meter = SKFrameRateMeter()
        val frame = 1_000_000_000L / 60
        assertEquals(0.0, meter.framesPerSecond)
        for (i in 0..31) meter.tick(i * frame) // the window closes on the first frame past half a second
        assertEquals(60.0, meter.framesPerSecond, 0.01)
        for (i in 1..16) meter.tick(31 * frame + i * 2 * frame)
        assertEquals(30.0, meter.framesPerSecond, 0.01)
    }

    @Test
    fun `the statistics box sits 5 points from the right and 2 points from the bottom`() {
        val font = SKStatisticsFont(advance = 18f, ascent = 28f, descent = 7f)
        val layout = statisticsLayout("nodes:1", font, viewWidth = 960, density = 3f)
        assertEquals(Rect(left = 945f - 7 * 18f - 6f, top = 6f, right = 945f, bottom = 41f), layout.box)
        assertEquals(7, layout.cells.size)
        assertEquals(
            'n' to Rect(left = layout.box.left + 3f, top = 6f, right = layout.box.left + 21f, bottom = 41f),
            layout.cells.first(),
        )
        assertEquals(942f, layout.cells.last().second.right)
    }

    @Test
    fun `physics outlines are one pixel wide lines along each body's edges`() {
        val scene = SKScene(size = Vector2(320f, 240f))
        val node = SKNode().apply { position = Vector2(160f, 120f) }
        node.physicsBody = SKPhysicsBody.rectangleOf(Vector2(40f, 40f))
        scene.addChild(node)
        val commands = physicsOutlineCommands(scene, scene, lineWidth = 0.5f)
        assertEquals(1, commands.size)
        assertEquals(PHYSICS_OUTLINE_COLOR, commands.single().color)
        val vertices = commands.single().vertices.map { it.position }
        assertEquals(4 * 6, vertices.size) // a quad per edge
        // Every vertex lies within half the width of the square's edges at x 140...180, y 100...140.
        assertTrue(vertices.all { it.x in 139.75f..180.25f && it.y in 99.75f..140.25f })
        assertEquals(139.75f, vertices.minOf { it.x })
        assertEquals(140.25f, vertices.maxOf { it.y })
    }

    @Test
    fun `a circle body is outlined as a closed polygon`() {
        val (points, closed) = outlinePolylines(SKPhysicsShape.Circle(radius = 20f)).single()
        assertTrue(closed)
        assertTrue(points.size >= 16)
        assertTrue(points.all { kotlin.math.abs(it.length() - 20f) < 0.001f })
    }

    @Test
    fun `a scene without physics bodies draws no outline`() {
        val scene = SKScene(size = Vector2(320f, 240f))
        scene.addChild(SKNode())
        assertTrue(physicsOutlineCommands(scene, scene, lineWidth = 1f).isEmpty())
    }
}
