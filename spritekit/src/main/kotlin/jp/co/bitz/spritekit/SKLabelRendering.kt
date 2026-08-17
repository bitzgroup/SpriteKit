package jp.co.bitz.spritekit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.ceil

/**
 * Renders [text] into a new [Bitmap] using [fontName]/[fontSize]/[fontColor] (via
 * `android.graphics.Paint`/`Canvas` — there's no `CoreText` equivalent on Android, so this is how
 * [SKLabelNode] gets its texture; see `docs/API_COMPATIBILITY.md`), alongside the [SKLabelMetrics]
 * [labelQuadCorners] needs. `null` for empty text or degenerate metrics.
 *
 * Touches real `Paint`/`Canvas`/`Bitmap` APIs, so — unlike [labelQuadCorners], which consumes
 * this function's [SKLabelMetrics] output — it isn't covered by unit tests; see
 * `docs/ROADMAP.md`'s testing notes.
 */
@Suppress("ReturnCount") // two guard clauses read more clearly here than nesting/flattening them
internal fun renderLabelBitmap(
    text: String,
    fontName: String?,
    fontSize: Float,
    fontColor: Int,
): Pair<Bitmap, SKLabelMetrics>? {
    // Guards *before* touching Paint at all, so empty text never reaches it -- callers (e.g.
    // buildRenderCommands' unit tests) rely on being able to construct an empty-text SKLabelNode
    // without needing a real Android runtime.
    if (text.isEmpty()) return null

    val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = fontSize
            this.color = fontColor
            this.typeface = fontName?.let { Typeface.create(it, Typeface.NORMAL) } ?: Typeface.DEFAULT
        }
    val width = paint.measureText(text)
    val fontMetrics = paint.fontMetrics
    val ascent = -fontMetrics.ascent
    val descent = fontMetrics.descent
    val height = ascent + descent
    if (width <= 0f || height <= 0f) return null

    val bitmap =
        Bitmap.createBitmap(
            ceil(width).toInt().coerceAtLeast(1),
            ceil(height).toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
    Canvas(bitmap).drawText(text, 0f, ascent, paint)
    return bitmap to SKLabelMetrics(width, ascent, descent)
}
