package jp.co.bitz.spritekit

/**
 * How an open stroke's ends are drawn — this library's stand-in for Core Graphics' `CGLineCap`, used
 * by [SKShapeNode.lineCap]. A plain Kotlin enum rather than `android.graphics.Paint.Cap`, for the
 * same reason [Rect] stands in for `CGRect`: stroke geometry is built in pure Kotlin and
 * unit-tested without an Android runtime.
 */
public enum class LineCap {
    /** The stroke ends exactly at the endpoint (Core Graphics' and Apple's default). */
    Butt,

    /** A half circle of the stroke's width is added past each endpoint. */
    Round,

    /** The stroke extends half its width past each endpoint, squared off. */
    Square,
}

/**
 * How a stroke's segments meet at a corner — this library's stand-in for Core Graphics'
 * `CGLineJoin`, used by [SKShapeNode.lineJoin]. See [LineCap] for why this isn't `Paint.Join`.
 */
public enum class LineJoin {
    /**
     * The outer edges extend to a sharp point (Core Graphics' and Apple's default), falling back to
     * [Bevel] when that point would be longer than [SKShapeNode.miterLimit] line widths.
     */
    Miter,

    /** The corner is rounded with a circular arc. */
    Round,

    /** The corner is cut off with a straight edge. */
    Bevel,
}
