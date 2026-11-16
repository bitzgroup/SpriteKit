package jp.co.bitz.spritekit

import android.graphics.Typeface
import android.os.Build

/**
 * An Apple-style font name ([SKLabelNode.fontName], e.g. `"Helvetica-Bold"`,
 * `"HelveticaNeue-LightItalic"`, `"Menlo"`) split into the parts Android resolves a [Typeface]
 * from: a family name, a CSS-style numeric [weight] (`100`..`900`, `400` = regular, `700` = bold)
 * and whether it's [italic]. Apple names a font's style in a `-Style` suffix of its PostScript
 * name; Android names families and passes the style separately.
 */
internal data class SKFontName(
    val family: String,
    val weight: Int,
    val italic: Boolean,
)

private const val REGULAR_WEIGHT = 400
private const val BOLD_WEIGHT = 700

/** Below API 28 (no exact weights), this weight and up render bold. */
private const val SYNTHETIC_BOLD_FROM_WEIGHT = 600

/** Weight keywords Apple uses in PostScript style suffixes, longest first so "SemiBold" isn't read as "Bold". */
private val weightKeywords: List<Pair<String, Int>> =
    listOf(
        "ultralight" to 200,
        "extralight" to 200,
        "extrabold" to 800,
        "ultrabold" to 800,
        "semibold" to 600,
        "demibold" to 600,
        "regular" to REGULAR_WEIGHT,
        "medium" to 500,
        "light" to 300,
        "heavy" to 800,
        "black" to 900,
        "thin" to 100,
        "bold" to BOLD_WEIGHT,
    )

/**
 * Apple monospaced families with no same-named Android font; everything else either matches an
 * Android family name directly (`"sans-serif"`, `"serif"`, `"monospace"`, ...) or falls back to
 * the default sans-serif — Android's closest match for Helvetica, Apple's own default.
 */
private val monospaceFamilies = setOf("menlo", "courier", "couriernew", "monaco", "sfmono")

/** Parses [name] — pure Kotlin, so it's unit-testable without an Android runtime. */
internal fun parseFontName(name: String): SKFontName {
    val dash = name.lastIndexOf('-')
    // A dash only starts a style suffix when what follows names a style ("sans-serif" doesn't).
    val suffix = if (dash > 0) name.substring(dash + 1).lowercase() else ""
    val weight = weightKeywords.firstOrNull { (keyword, _) -> suffix.contains(keyword) }?.second
    val italic = suffix.contains("italic") || suffix.contains("oblique")
    if (weight == null && !italic) return SKFontName(normalizeFamily(name), REGULAR_WEIGHT, italic = false)
    return SKFontName(normalizeFamily(name.substring(0, dash)), weight ?: REGULAR_WEIGHT, italic)
}

private fun normalizeFamily(family: String): String =
    if (family.lowercase().replace(" ", "") in monospaceFamilies) "monospace" else family

/**
 * The [Typeface] for an Apple-style [fontName] (see [parseFontName]), or [Typeface.DEFAULT] for
 * `null`. Exact weights need API 28; below that, weights of 600 and up render bold and everything
 * else regular.
 */
internal fun typefaceForFontName(fontName: String?): Typeface {
    if (fontName == null) return Typeface.DEFAULT
    val parsed = parseFontName(fontName)
    val family = Typeface.create(parsed.family, Typeface.NORMAL)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Typeface.create(family, parsed.weight, parsed.italic)
    } else {
        val bold = parsed.weight >= SYNTHETIC_BOLD_FROM_WEIGHT
        val style =
            when {
                bold && parsed.italic -> Typeface.BOLD_ITALIC
                bold -> Typeface.BOLD
                parsed.italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
        Typeface.create(family, style)
    }
}
