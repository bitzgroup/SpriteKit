package jp.co.bitz.spritekit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The pure-Kotlin parts of [SKShapeNode.fillTexture] support: texture-coordinate mapping
 * ([fillTextureVertices]) and the fill bounding box it maps across ([fillBoundsOf]). Rendering a
 * real [SKShapeNode] end to end needs [flattenPath]'s `android.graphics.Path` APIs, which aren't
 * available in plain JVM unit tests — see `docs/ROADMAP.md`'s testing notes.
 */
class SKShapeFillTextureTest {
    private val bounds = Rect(left = 10f, top = 20f, right = 30f, bottom = 60f)

    private fun mapped(
        locals: List<Vector2>,
        uv: Rect = Rect(0f, 0f, 1f, 1f),
    ): List<SKRenderVertex> =
        // World positions are irrelevant to the mapping; reuse the locals.
        fillTextureVertices(
            locals.map { SKRenderVertex(it, 0f, 0f) },
            locals,
            bounds,
            uv,
        )

    @Test
    fun `bounding box corners map to the texture corners, bottom-left to the texture's bottom`() {
        // bottom-left, bottom-right, top-right, top-left (local space is y-up)
        val corners = listOf(Vector2(10f, 20f), Vector2(30f, 20f), Vector2(30f, 60f), Vector2(10f, 60f))

        val result = mapped(corners)

        assertEquals(listOf(0f to 1f, 1f to 1f, 1f to 0f, 0f to 0f), result.map { it.u to it.v })
    }

    @Test
    fun `interior points map proportionally within the bounding box`() {
        val result = mapped(listOf(Vector2(15f, 50f)))

        assertEquals(0.25f, result.single().u, absoluteTolerance = 1e-6f)
        assertEquals(0.25f, result.single().v, absoluteTolerance = 1e-6f)
    }

    @Test
    fun `a sub-region texture maps into its own texture rect, not the whole bitmap`() {
        val uv = Rect(left = 0.5f, top = 0.25f, right = 1f, bottom = 0.75f)

        val result = mapped(listOf(Vector2(10f, 20f), Vector2(30f, 60f)), uv)

        assertEquals(listOf(0.5f to 0.75f, 1f to 0.25f), result.map { it.u to it.v })
    }

    @Test
    fun `world positions are preserved`() {
        val world = listOf(SKRenderVertex(Vector2(-100f, 7f), 0f, 0f))

        val result = fillTextureVertices(world, listOf(Vector2(10f, 20f)), bounds, Rect(0f, 0f, 1f, 1f))

        assertEquals(Vector2(-100f, 7f), result.single().position)
    }

    @Test
    fun `a degenerate zero-width box maps every point to the texture's left edge instead of dividing by zero`() {
        val flat = Rect(left = 5f, top = 0f, right = 5f, bottom = 10f)

        val result =
            fillTextureVertices(
                listOf(SKRenderVertex(Vector2.Zero, 0f, 0f)),
                listOf(Vector2(5f, 5f)),
                flat,
                Rect(0f, 0f, 1f, 1f),
            )

        assertEquals(0f, result.single().u)
    }

    @Test
    fun `fill bounds cover only fill vertices, across every contour`() {
        val firstFill = listOf(Vector2(0f, 0f), Vector2(4f, 0f), Vector2(0f, 3f))
        val stroke = listOf(Vector2(-50f, -50f), Vector2(50f, 50f), Vector2(0f, 0f))
        val secondFill = listOf(Vector2(10f, 10f), Vector2(12f, 10f), Vector2(10f, 13f))

        // The stroke's far-out vertices must not widen the bounds.
        val bounds = fillBoundsOf(firstFill + stroke + secondFill, listOf(0..2, 6..8))

        assertEquals(Rect(0f, 0f, 12f, 13f), bounds)
    }

    @Test
    fun `fill bounds are null when there is no fill geometry`() {
        assertNull(fillBoundsOf(listOf(Vector2.Zero), listOf(IntRange.EMPTY)))
    }
}
