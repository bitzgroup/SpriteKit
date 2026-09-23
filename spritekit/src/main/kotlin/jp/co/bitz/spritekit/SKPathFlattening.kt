package jp.co.bitz.spritekit

import android.graphics.Path
import android.graphics.PathMeasure

/**
 * The maximum distance, in path-length units, any point on the true curve may lie from the
 * nearest point on [flattenPath]'s straight-line approximation of it — see `Path.approximate`.
 */
private const val FLATTEN_ACCEPTABLE_ERROR = 0.25f

/**
 * A single contour flattened out of an `android.graphics.Path`: its points (curves already
 * converted to line segments, via `Path.approximate`) and whether the source contour was closed
 * (`Path.close()`).
 */
internal data class SKFlattenedContour(
    val points: List<Vector2>,
    val closed: Boolean,
)

/**
 * Flattens [path] (which may hold multiple contours, and curves via `quadTo`/`cubicTo`/`arcTo`)
 * into straight-line-segment contours, via `android.graphics.PathMeasure` (to split multi-contour
 * paths and detect each contour's closedness — unaffected by this function's own approximation)
 * plus `Path.approximate` (API 26+; safe given this library's minSdk) per contour, rather than
 * `PathMeasure.getPosTan` at a fixed arc-length step: the latter samples *every* segment — straight
 * `lineTo` runs included — at the same fixed interval, so a long straight edge (a polygon's, e.g.
 * one of [SKShapeNode]'s consumers drawing a plain triangle) flattens to as many redundant,
 * exactly-collinear points as a curve of the same length would, wildly inflating triangulated
 * vertex counts (and therefore [SKSceneRenderer]'s per-frame GPU upload cost, which -- unlike the
 * triangulation itself -- can't be cached away, since it has to walk every vertex every frame
 * regardless) for no visual benefit. `approximate` instead already knows which segments are
 * actually curved and only subdivides those.
 *
 * Touches real `Path`/`PathMeasure` APIs, so — unlike [triangulateFill]/[triangulateStroke], which
 * consume this function's output — it isn't covered by unit tests; see `docs/ROADMAP.md`'s
 * testing notes.
 */
internal fun flattenPath(path: Path): List<SKFlattenedContour> {
    val contours = mutableListOf<SKFlattenedContour>()
    val measure = PathMeasure(path, false)
    do {
        val length = measure.length
        if (length > 0f) {
            val contour = Path()
            measure.getSegment(0f, length, contour, true)
            contour.rLineTo(0f, 0f) // seals `getSegment`'s output -- see its own long-standing platform quirk
            val approximated = contour.approximate(FLATTEN_ACCEPTABLE_ERROR)
            val points = (approximated.indices step 3).map { i -> Vector2(approximated[i + 1], approximated[i + 2]) }
            contours += SKFlattenedContour(points, measure.isClosed)
        }
    } while (measure.nextContour())
    return contours
}
