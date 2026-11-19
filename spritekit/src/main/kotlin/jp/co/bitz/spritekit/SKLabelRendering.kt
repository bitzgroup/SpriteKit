package jp.co.bitz.spritekit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import kotlin.math.ceil

/**
 * Renders [key]'s text into a new [Bitmap] (via `android.graphics.Paint`/`Canvas`, and
 * `android.text.StaticLayout` for a multi-line label — there's no `CoreText` equivalent on
 * Android, so this is how [SKLabelNode] gets its texture; see `docs/API_COMPATIBILITY.md`),
 * alongside the [SKLabelMetrics] [labelQuadCorners] needs. `null` for empty text or degenerate
 * metrics.
 *
 * Touches real `Paint`/`Canvas`/`Bitmap` APIs, so — unlike [labelQuadCorners], which consumes
 * this function's [SKLabelMetrics] output — it isn't covered by unit tests; see
 * `docs/ROADMAP.md`'s testing notes.
 */
internal fun renderLabelBitmap(key: SKLabelCacheKey): Pair<Bitmap, SKLabelMetrics>? {
    // Guards *before* touching Paint at all, so empty text never reaches it -- callers (e.g.
    // buildRenderCommands' unit tests) rely on being able to construct an empty-text SKLabelNode
    // without needing a real Android runtime.
    val attributedText = key.attributedText
    if (attributedText != null || key.text.isEmpty()) {
        return attributedText?.let {
            renderAttributedText(it, key.lineLayout)
        }
    }

    val paint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = key.fontSize
            this.color = key.fontColor
            this.typeface = typefaceForFontName(key.fontName)
        }
    val lineLayout = key.lineLayout
    return if (lineLayout == null) renderOneLine(key.text, paint) else renderLines(key.text, paint, lineLayout)
}

private fun renderOneLine(
    text: String,
    paint: Paint,
): Pair<Bitmap, SKLabelMetrics>? {
    val width = paint.measureText(text)
    val fontMetrics = paint.fontMetrics
    val ascent = -fontMetrics.ascent
    val descent = fontMetrics.descent
    val height = ascent + descent
    if (width <= 0f || height <= 0f) return null

    val bitmap = blankBitmap(width, height)
    Canvas(bitmap).drawText(text, 0f, ascent, paint)
    return bitmap to SKLabelMetrics(width, ascent, descent)
}

/**
 * Renders [SKLabelNode.attributedText]: text without a font or color span gets an
 * `NSAttributedString`'s defaults, 12-point Helvetica in black.
 */
private fun renderAttributedText(
    text: CharSequence,
    lineLayout: SKLabelLineLayout?,
): Pair<Bitmap, SKLabelMetrics>? {
    if (text.isEmpty()) return null
    val paint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = ATTRIBUTED_TEXT_DEFAULT_FONT_SIZE
            this.color = Color.BLACK
            this.typeface = typefaceForFontName(ATTRIBUTED_TEXT_DEFAULT_FONT_NAME)
        }
    return if (lineLayout == null) renderAttributedLine(text, paint) else renderLines(text, paint, lineLayout)
}

/**
 * One line of styled text, its baseline where the tallest span's ascent puts it. A newline is
 * drawn in place, not broken at — as a one-line label does with plain text.
 */
private fun renderAttributedLine(
    text: CharSequence,
    paint: TextPaint,
): Pair<Bitmap, SKLabelMetrics>? {
    val oneLine = SpannableStringBuilder(text)
    for (index in oneLine.indices) {
        if (oneLine[index] == '\n' || oneLine[index] == '\r') oneLine.replace(index, index + 1, ZERO_WIDTH_SPACE)
    }
    val width = Layout.getDesiredWidth(oneLine, paint)
    if (width <= 0f) return null
    val layout =
        StaticLayout.Builder
            .obtain(oneLine, 0, oneLine.length, paint, ceil(width).toInt())
            .setIncludePad(false)
            .setMaxLines(1)
            .build()
    val ascent = layout.getLineBaseline(0).toFloat()
    val descent = layout.height - ascent
    val bitmap = blankBitmap(width, ascent + descent)
    layout.draw(Canvas(bitmap))
    return bitmap to SKLabelMetrics(width, ascent, descent)
}

private fun renderLines(
    text: CharSequence,
    paint: TextPaint,
    settings: SKLabelLineLayout,
): Pair<Bitmap, SKLabelMetrics>? {
    // Lay out once at the wrap width to find the widest line, then again at exactly that width so
    // the block is no wider than its text (Apple's frame is the text's, not the wrap width's).
    val wrapWidth =
        if (settings.maxWidth > 0f) settings.maxWidth else Layout.getDesiredWidth(text, paint)
    val measured = buildLayout(text, paint, settings, ceil(wrapWidth).toInt())
    val blockWidth = (0 until measured.lineCount).maxOfOrNull { measured.getLineWidth(it) } ?: 0f
    val layout = buildLayout(text, paint, settings, ceil(blockWidth).toInt())
    val height = layout.height.toFloat()
    if (blockWidth <= 0f || height <= 0f) return null

    val bitmap = blankBitmap(blockWidth, height)
    layout.draw(Canvas(bitmap))
    return bitmap to multiLineMetrics(blockWidth, height)
}

private fun buildLayout(
    text: CharSequence,
    paint: TextPaint,
    settings: SKLabelLineLayout,
    width: Int,
): StaticLayout {
    val builder =
        StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
            .setIncludePad(false)
            .setAlignment(
                when (settings.alignment) {
                    SKLabelHorizontalAlignmentMode.Left -> Layout.Alignment.ALIGN_NORMAL
                    SKLabelHorizontalAlignmentMode.Center -> Layout.Alignment.ALIGN_CENTER
                    SKLabelHorizontalAlignmentMode.Right -> Layout.Alignment.ALIGN_OPPOSITE
                },
            )
    if (settings.numberOfLines > 0) builder.setMaxLines(settings.numberOfLines)
    truncateAt(settings.lineBreakMode)?.let(builder::setEllipsize)
    return builder.build()
}

private fun truncateAt(mode: SKLineBreakMode): TextUtils.TruncateAt? =
    when (mode) {
        SKLineBreakMode.ByTruncatingHead -> TextUtils.TruncateAt.START
        SKLineBreakMode.ByTruncatingTail -> TextUtils.TruncateAt.END
        SKLineBreakMode.ByTruncatingMiddle -> TextUtils.TruncateAt.MIDDLE
        SKLineBreakMode.ByWordWrapping, SKLineBreakMode.ByCharWrapping, SKLineBreakMode.ByClipping -> null
    }

private fun blankBitmap(
    width: Float,
    height: Float,
): Bitmap =
    Bitmap.createBitmap(
        ceil(width).toInt().coerceAtLeast(1),
        ceil(height).toInt().coerceAtLeast(1),
        Bitmap.Config.ARGB_8888,
    )

/** An `NSAttributedString`'s font where no font attribute is set: 12-point Helvetica. */
private const val ATTRIBUTED_TEXT_DEFAULT_FONT_NAME = "Helvetica"
private const val ATTRIBUTED_TEXT_DEFAULT_FONT_SIZE = 12f
private const val ZERO_WIDTH_SPACE = "\u200B"
