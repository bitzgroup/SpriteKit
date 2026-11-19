package jp.co.bitz.spritekit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.ceil

/**
 * Draws [SKView]'s statistics line (`showsFPS`, `showsNodeCount`, `showsDrawCount`,
 * `showsQuadCount`) as [SKRenderCommand]s in view pixels, for [SKSceneRenderer.drawOverlay].
 *
 * The text changes every time the frame rate does, so instead of a texture per string (as a
 * label has), every character the line can contain is rendered once into a small monospaced
 * glyph atlas, and the line is built from its cells. Render-thread-confined. Touches real
 * `Paint`/`Canvas`/`Bitmap` APIs, so — unlike [statisticsLayout] — it isn't covered by unit tests.
 */
internal class SKStatisticsOverlay {
    private var atlasDensity = 0f
    private var atlas: SKTexture? = null
    private var font = SKStatisticsFont(0f, 0f, 0f)
    private var cellWidth = 0f

    /** The commands that draw [text] at the bottom right of a [viewWidth]-pixel-wide view. */
    fun commands(
        text: String,
        viewWidth: Int,
        density: Float,
    ): List<SKRenderCommand> {
        val texture = atlasFor(density)
        val layout = statisticsLayout(text, font, viewWidth, density)
        val box =
            SKRenderCommand(
                texture = null,
                blendMode = SKBlendMode.Alpha,
                vertices = rectVertices(layout.box, Rect(0f, 0f, 1f, 1f)),
                color = STATISTICS_BOX_COLOR,
            )
        val atlasWidth = texture.bitmap.width.toFloat()
        val glyphs =
            layout.cells.flatMap { (char, cell) ->
                val index = GLYPHS.indexOf(char)
                if (index <= 0) return@flatMap emptyList() // a space, or nothing to draw
                val left = (index * cellWidth + GLYPH_GAP) / atlasWidth
                val right = (index * cellWidth + GLYPH_GAP + font.advance) / atlasWidth
                rectVertices(cell, Rect(left = left, top = 0f, right = right, bottom = 1f))
            }
        val text =
            SKRenderCommand(
                texture = texture,
                blendMode = SKBlendMode.Alpha,
                vertices = glyphs,
                color = SKVertexColor(1f, 1f, 1f, 1f),
            )
        return if (glyphs.isEmpty()) listOf(box) else listOf(box, text)
    }

    private fun atlasFor(density: Float): SKTexture {
        atlas?.takeIf { atlasDensity == density }?.let { return it }
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = STATISTICS_FONT_SIZE * density
                color = Color.WHITE
                typeface = Typeface.MONOSPACE
            }
        val metrics = paint.fontMetrics
        font = SKStatisticsFont(advance = paint.measureText("0"), ascent = -metrics.ascent, descent = metrics.descent)
        cellWidth = ceil(font.advance) + 2 * GLYPH_GAP
        val bitmap =
            Bitmap.createBitmap(
                ceil(cellWidth * GLYPHS.length).toInt(),
                ceil(font.ascent + font.descent).toInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
        val canvas = Canvas(bitmap)
        GLYPHS.forEachIndexed { index, char ->
            canvas.drawText(char.toString(), index * cellWidth + GLYPH_GAP, font.ascent, paint)
        }
        return SKTexture(bitmap).also {
            atlas = it
            atlasDensity = density
        }
    }

    private companion object {
        /** Every character the statistics line uses; index 0 (a space) is never drawn. */
        const val GLYPHS = " 0123456789.:adefnopqrsuw"

        /** Empty pixels at each side of a glyph's cell, so neighbors don't bleed in when sampled. */
        const val GLYPH_GAP = 1f
    }
}

/**
 * Two triangles covering [rect] (y up: `top` is the lower edge), sampling [uv] (y down, as a bitmap's
 * rows run: `uv.top` is the bitmap's top row, drawn at the rect's upper edge).
 */
private fun rectVertices(
    rect: Rect,
    uv: Rect,
): List<SKRenderVertex> {
    val bottomLeft = SKRenderVertex(Vector2(rect.left, rect.top), uv.left, uv.bottom)
    val bottomRight = SKRenderVertex(Vector2(rect.right, rect.top), uv.right, uv.bottom)
    val topRight = SKRenderVertex(Vector2(rect.right, rect.bottom), uv.right, uv.top)
    val topLeft = SKRenderVertex(Vector2(rect.left, rect.bottom), uv.left, uv.top)
    return listOf(bottomLeft, bottomRight, topRight, bottomLeft, topRight, topLeft)
}
