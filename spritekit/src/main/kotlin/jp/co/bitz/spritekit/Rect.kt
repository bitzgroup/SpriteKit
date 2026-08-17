package jp.co.bitz.spritekit

/**
 * An axis-aligned rectangle — this library's stand-in for Apple's `CGRect`, returned by
 * [SKNode.calculateAccumulatedFrame]. A plain Kotlin value type rather than
 * `android.graphics.RectF`: this library's logic stays pure-Kotlin and unit-testable without a
 * live Android runtime (see `docs/ROADMAP.md`'s testing notes), and `RectF`'s own instance
 * methods (`union`, `intersects`, ...) aren't safe to call from plain JVM unit tests without
 * Robolectric.
 *
 * Always normalized: [left] <= [right] and [top] <= [bottom].
 */
public data class Rect(
    public val left: Float,
    public val top: Float,
    public val right: Float,
    public val bottom: Float,
) {
    /** The smallest [Rect] containing both this rectangle and [other]. */
    public fun union(other: Rect): Rect =
        Rect(
            left = minOf(left, other.left),
            top = minOf(top, other.top),
            right = maxOf(right, other.right),
            bottom = maxOf(bottom, other.bottom),
        )

    /** Whether this rectangle and [other] overlap (touching at an edge only doesn't count). */
    public fun intersects(other: Rect): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    /** The overlapping region of this rectangle and [other], or `null` if they don't overlap — used by [SKCropNode]. */
    public fun intersection(other: Rect): Rect? {
        val newLeft = maxOf(left, other.left)
        val newTop = maxOf(top, other.top)
        val newRight = minOf(right, other.right)
        val newBottom = minOf(bottom, other.bottom)
        return if (newLeft < newRight && newTop < newBottom) Rect(newLeft, newTop, newRight, newBottom) else null
    }

    public companion object {
        public val Zero: Rect = Rect(0f, 0f, 0f, 0f)
    }
}
