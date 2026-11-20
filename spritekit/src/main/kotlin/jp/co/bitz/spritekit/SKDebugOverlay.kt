package jp.co.bitz.spritekit

import java.util.Locale
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/*
 * The pure-Kotlin half of [SKView]'s debug overlays (`showsFPS`, `showsNodeCount`,
 * `showsDrawCount`, `showsQuadCount`, `showsPhysics`) — what they show, worked out without GL, so
 * it's unit-testable. Every rule here was recorded on iOS/tvOS simulators by the tic-tac-toe
 * sample's `SKViewDebugOverlayParityTests`. Not part of the public API.
 */

/** What one frame's draw cost: GL draw calls, and quads (two triangles each). */
internal data class SKRenderStatistics(
    val drawCount: Int,
    val quadCount: Int,
) {
    operator fun plus(other: SKRenderStatistics): SKRenderStatistics =
        SKRenderStatistics(drawCount + other.drawCount, quadCount + other.quadCount)

    companion object {
        val Zero: SKRenderStatistics = SKRenderStatistics(0, 0)
    }
}

/** Which statistics an [SKView] shows; see [skStatisticsText]. */
internal data class SKStatisticsSelection(
    val fps: Boolean = false,
    val nodeCount: Boolean = false,
    val drawCount: Boolean = false,
    val quadCount: Boolean = false,
) {
    val isEmpty: Boolean get() = !fps && !nodeCount && !drawCount && !quadCount
}

/**
 * The statistics line Apple draws: the enabled counters in the order nodes, draws, quads, one
 * space apart, then the frame rate with one decimal after two spaces —
 * `nodes:7 draws:3 quads:334  58.9 fps`. `null` when nothing is shown.
 */
internal fun skStatisticsText(
    selection: SKStatisticsSelection,
    nodeCount: Int,
    statistics: SKRenderStatistics,
    framesPerSecond: Double,
): String? {
    val counters =
        listOfNotNull(
            "nodes:$nodeCount".takeIf { selection.nodeCount },
            "draws:${statistics.drawCount}".takeIf { selection.drawCount },
            "quads:${statistics.quadCount}".takeIf { selection.quadCount },
        ).joinToString(" ")
    val fps = if (selection.fps) String.format(Locale.US, "%.1f fps", framesPerSecond) else ""
    return when {
        counters.isEmpty() && fps.isEmpty() -> null
        counters.isEmpty() -> fps
        fps.isEmpty() -> counters
        else -> "$counters  $fps"
    }
}

/**
 * The node count Apple's `showsNodeCount` shows: [scene] itself and every node below it, hidden
 * ones and plain [SKNode]s included (an empty scene reads `nodes:1`).
 */
internal fun skNodeCount(scene: SKNode): Int = 1 + scene.children.sumOf { skNodeCount(it) }

/**
 * Measures the frame rate `showsFPS` shows: frames counted over windows of at least
 * [WINDOW_NANOS], the last full window's rate reported (`0` until one has passed).
 */
internal class SKFrameRateMeter {
    private var windowStart = -1L
    private var frames = 0

    /** The rate over the last full window, in frames per second. */
    var framesPerSecond: Double = 0.0
        private set

    /** Counts a frame drawn at [nanoTime]. */
    fun tick(nanoTime: Long) {
        if (windowStart < 0) {
            windowStart = nanoTime
            return
        }
        frames++
        val elapsed = nanoTime - windowStart
        if (elapsed >= WINDOW_NANOS) {
            framesPerSecond = frames * NANOS_PER_SECOND / elapsed
            frames = 0
            windowStart = nanoTime
        }
    }

    /** Starts measuring afresh, e.g. after the render thread was paused. */
    fun reset() {
        windowStart = -1L
        frames = 0
    }

    private companion object {
        const val WINDOW_NANOS = 500_000_000L
        const val NANOS_PER_SECOND = 1_000_000_000.0
    }
}

/** Metrics of the monospaced statistics font, in view pixels: its fixed [advance], [ascent], [descent]. */
internal data class SKStatisticsFont(
    val advance: Float,
    val ascent: Float,
    val descent: Float,
)

/** Where [SKView] draws the statistics line, in view pixels (origin bottom-left, y up). */
internal data class SKStatisticsLayout(
    /** The translucent black box behind the text. */
    val box: Rect,
    /** One cell per character of the text (spaces included), each [SKStatisticsFont.advance] wide. */
    val cells: List<Pair<Char, Rect>>,
)

/** Apple's statistics font size, in points (10-point Menlo). */
internal const val STATISTICS_FONT_SIZE: Float = 10f

/** The statistics box's color: black at 30% opacity. */
internal val STATISTICS_BOX_COLOR: SKVertexColor = SKVertexColor(0f, 0f, 0f, 0.3f)

/**
 * Lays out the statistics line the way Apple does: right-aligned, its box 5 points in from the
 * view's right edge and 2 points up from its bottom, hugging the text with 1 point of padding at
 * each side. (Apple's box hugs the glyphs' ink vertically; this one spans the font's ascent and
 * descent, so it can be a point or two taller.) [density] is pixels per point.
 */
internal fun statisticsLayout(
    text: String,
    font: SKStatisticsFont,
    viewWidth: Int,
    density: Float,
): SKStatisticsLayout {
    val right = viewWidth - STATISTICS_RIGHT_MARGIN * density
    val left = right - text.length * font.advance - 2 * STATISTICS_PADDING * density
    val bottom = STATISTICS_BOTTOM_MARGIN * density
    val top = bottom + font.ascent + font.descent
    val textLeft = left + STATISTICS_PADDING * density
    val cells =
        text.mapIndexed { index, char ->
            val cellLeft = textLeft + index * font.advance
            char to Rect(left = cellLeft, top = bottom, right = cellLeft + font.advance, bottom = top)
        }
    return SKStatisticsLayout(Rect(left = left, top = bottom, right = right, bottom = top), cells)
}

private const val STATISTICS_RIGHT_MARGIN = 5f
private const val STATISTICS_BOTTOM_MARGIN = 2f
private const val STATISTICS_PADDING = 1f

/** Apple's `showsPhysics` outline color, (0.4, 0.8, 0.8), opaque. */
internal val PHYSICS_OUTLINE_COLOR: SKVertexColor = SKVertexColor(0.4f, 0.8f, 0.8f, 1f)

/**
 * The outline of every physics body in [scene] (its own included), as hard-edged lines
 * [lineWidth] wide in [referenceNode]'s space — Apple's `showsPhysics`, which draws them one
 * screen pixel wide whatever the scene's scale, above all of the scene's content. A circle is
 * drawn as a polygon.
 */
internal fun physicsOutlineCommands(
    scene: SKScene,
    referenceNode: SKNode,
    lineWidth: Float,
): List<SKRenderCommand> {
    val vertices = mutableListOf<SKRenderVertex>()

    fun visit(node: SKNode) {
        node.physicsBody?.let { body ->
            for ((points, closed) in outlinePolylines(body.shape)) {
                val placed = points.map { node.convertTo(it, referenceNode) }
                addPolyline(vertices, placed, closed, lineWidth)
            }
        }
        node.children.forEach(::visit)
    }
    visit(scene)
    if (vertices.isEmpty()) return emptyList()
    return listOf(
        SKRenderCommand(
            texture = null,
            blendMode = SKBlendMode.Alpha,
            vertices = vertices,
            color = PHYSICS_OUTLINE_COLOR,
        ),
    )
}

/** [shape]'s outline in its node's local space: point lists, each with whether it closes. */
internal fun outlinePolylines(shape: SKPhysicsShape): List<Pair<List<Vector2>, Boolean>> =
    when (shape) {
        is SKPhysicsShape.Circle -> {
            val segments =
                ceil(
                    2 * PI * shape.radius / CIRCLE_SEGMENT_LENGTH,
                ).toInt().coerceIn(MIN_CIRCLE_SEGMENTS, MAX_CIRCLE_SEGMENTS)
            val points =
                (0 until segments).map { i ->
                    val angle = 2 * PI * i / segments
                    shape.center + Vector2((cos(angle) * shape.radius).toFloat(), (sin(angle) * shape.radius).toFloat())
                }
            listOf(points to true)
        }
        is SKPhysicsShape.Polygon -> listOf(shape.vertices to true)
        is SKPhysicsShape.EdgeChain -> listOf(shape.vertices to shape.closed)
        is SKPhysicsShape.Compound -> shape.parts.flatMap(::outlinePolylines)
    }

/** Appends one quad per segment of [points], each extended by half [width] at both ends so corners close. */
private fun addPolyline(
    out: MutableList<SKRenderVertex>,
    points: List<Vector2>,
    closed: Boolean,
    width: Float,
) {
    if (points.size < 2) return
    val segmentCount = if (closed) points.size else points.size - 1
    val half = width / 2f
    for (i in 0 until segmentCount) {
        val a = points[i]
        val b = points[(i + 1) % points.size]
        val direction = (b - a).normalized()
        if (direction == Vector2.Zero) continue
        val along = direction * half
        val across = Vector2(-direction.y, direction.x) * half
        val start = a - along
        val end = b + along
        val corners = listOf(start - across, end - across, end + across, start + across)
        for (index in QUAD_TRIANGLES) out += SKRenderVertex(corners[index], 0f, 0f)
    }
}

private val QUAD_TRIANGLES = intArrayOf(0, 1, 2, 0, 2, 3)
private const val CIRCLE_SEGMENT_LENGTH = 4.0
private const val MIN_CIRCLE_SEGMENTS = 16
private const val MAX_CIRCLE_SEGMENTS = 128
