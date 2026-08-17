package jp.co.bitz.spritekit

import android.graphics.Path
import android.graphics.PathMeasure

/** How closely [flattenPath] approximates curves, in path-length units between sampled points. */
private const val FLATTEN_STEP = 2f

/**
 * A single contour flattened out of an `android.graphics.Path`: its points (curves already
 * converted to line segments, via `PathMeasure`) and whether the source contour was closed
 * (`Path.close()`).
 */
internal data class SKFlattenedContour(
    val points: List<Vector2>,
    val closed: Boolean,
)

/**
 * Flattens [path] (which may hold multiple contours, and curves via `quadTo`/`cubicTo`/`arcTo`)
 * into straight-line-segment contours, via `android.graphics.PathMeasure`.
 *
 * Touches real `Path`/`PathMeasure` APIs, so — unlike [triangulateFill]/[triangulateStroke], which
 * consume this function's output — it isn't covered by unit tests; see `docs/ROADMAP.md`'s
 * testing notes.
 */
internal fun flattenPath(path: Path): List<SKFlattenedContour> {
    val contours = mutableListOf<SKFlattenedContour>()
    val measure = PathMeasure(path, false)
    val coords = FloatArray(2)
    do {
        val length = measure.length
        if (length > 0f) {
            val points = mutableListOf<Vector2>()
            var distance = 0f
            while (distance < length) {
                measure.getPosTan(distance, coords, null)
                points += Vector2(coords[0], coords[1])
                distance += FLATTEN_STEP
            }
            measure.getPosTan(length, coords, null)
            points += Vector2(coords[0], coords[1])
            contours += SKFlattenedContour(points, measure.isClosed)
        }
    } while (measure.nextContour())
    return contours
}
