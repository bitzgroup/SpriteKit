package jp.co.bitz.spritekit

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import java.io.IOException

/** The extensions tried, in order, for an image name given without one. */
internal val imageExtensions: List<String> = listOf("png", "jpg", "jpeg", "webp")

/** Apple's placeholder for a missing image is 128×128 points (verified on iOS/tvOS simulators). */
internal const val MISSING_IMAGE_SIZE: Int = 128

/**
 * The `assets/` paths [loadNamedImage] tries for [name], in order — Apple's image lookup accepts a
 * name with or without its extension. A name that already has one is tried as-is first.
 */
internal fun namedImageCandidates(name: String): List<String> {
    val fileName = name.substringAfterLast('/')
    val withExtensions = imageExtensions.map { "$name.$it" }
    return if ('.' in fileName) listOf(name) + withExtensions else withExtensions + name
}

/**
 * Decodes the image [name] from the host app's `assets/` — Apple's `imageNamed:` lookup against
 * the main bundle — at its pixel size. A missing image logs a warning and gives a 128×128
 * placeholder (a red cross), as Apple does, rather than failing.
 */
internal fun loadNamedImage(name: String): Bitmap {
    val assets = SKBundle.requireContext().assets
    val options = BitmapFactory.Options().apply { inScaled = false }
    for (candidate in namedImageCandidates(name)) {
        val bitmap =
            try {
                assets.open(candidate).use { BitmapFactory.decodeStream(it, null, options) }
            } catch (_: IOException) {
                null
            }
        if (bitmap != null) return bitmap
    }
    Log.w("SpriteKit", "SKTexture: no image named '$name' in assets/; using a placeholder.")
    return missingImagePlaceholder()
}

internal fun missingImagePlaceholder(): Bitmap {
    val size = MISSING_IMAGE_SIZE.toFloat()
    val bitmap = Bitmap.createBitmap(MISSING_IMAGE_SIZE, MISSING_IMAGE_SIZE, Bitmap.Config.ARGB_8888)
    val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.RED
            strokeWidth = size / 16f
        }
    Canvas(bitmap).apply {
        drawColor(Color.WHITE)
        drawLine(0f, 0f, size, size, paint)
        drawLine(size, 0f, 0f, size, paint)
    }
    return bitmap
}
