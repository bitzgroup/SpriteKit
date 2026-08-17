package jp.co.bitz.spritekit

/**
 * A single rendered vertex: [position] already in the presenting [SKScene]'s coordinate space,
 * plus texture coordinates.
 */
internal data class SKRenderVertex(
    val position: Vector2,
    val u: Float,
    val v: Float,
)

/** A vertex color multiplier, each channel normalized `0..1`. Not part of the public API. */
internal data class SKVertexColor(
    val r: Float,
    val g: Float,
    val b: Float,
    val a: Float,
)

/**
 * One draw command ready for [SKSceneRenderer]: a flat triangle list ([vertices].size is always a
 * multiple of 3) to draw with [texture] (`null` renders flat-colored, via the renderer's built-in
 * white fallback texture — used by untextured [SKSpriteNode]s and every [SKShapeNode]
 * fill/stroke) and [blendMode]. Not part of the public API.
 */
internal data class SKRenderCommand(
    val texture: SKTexture?,
    val blendMode: SKBlendMode,
    val vertices: List<SKRenderVertex>,
    val color: SKVertexColor,
)

/**
 * Flattens [scene]'s node tree into an ordered list of [SKRenderCommand]s, ready for
 * [SKSceneRenderer] to draw in order: sorted by [SKNode.zPosition] (ties broken by
 * tree-traversal order, matching Apple's documented rule — see `docs/ARCHITECTURE.md`).
 * [SKSpriteNode]/[SKLabelNode] each contribute one command (a textured quad); [SKShapeNode]
 * contributes up to two per contour (an untextured fill, then an untextured stroke, in that
 * order) — they all reduce to the same "flat triangle list, texture, blend mode, vertex color"
 * shape, so one renderer draws all three node types.
 *
 * Pure Kotlin, reusing [SKNode.convertTo] (from Phase 2) to correctly account for every
 * ancestor's position/rotation/scale — no OpenGL dependency, so this is unit-testable
 * independent of a live GL context, *except* when the scene contains an [SKLabelNode] with
 * non-empty [SKLabelNode.text] or an [SKShapeNode] with a non-null [SKShapeNode.path] — both
 * touch Android APIs ([renderLabelBitmap]'s `Paint`/`Canvas`, [flattenPath]'s `PathMeasure`) that
 * aren't safe to call from plain JVM unit tests; see `docs/ROADMAP.md`'s testing notes.
 */
internal fun buildRenderCommands(scene: SKScene): List<SKRenderCommand> {
    val commands = mutableListOf<Pair<SKRenderCommand, Pair<Float, Int>>>()
    var order = 0

    fun add(
        command: SKRenderCommand,
        zPosition: Float,
    ) {
        commands += command to (zPosition to order)
    }

    fun visit(
        node: SKNode,
        inheritedAlpha: Float,
        inheritedHidden: Boolean,
    ) {
        val hidden = inheritedHidden || node.isHidden
        val alpha = inheritedAlpha * node.alpha
        if (!hidden && alpha > 0f) {
            when (node) {
                is SKSpriteNode -> addSpriteCommand(node, scene, alpha, ::add)
                is SKLabelNode -> addLabelCommand(node, scene, alpha, ::add)
                is SKShapeNode -> addShapeCommands(node, scene, alpha, ::add)
                else -> Unit
            }
        }
        order++
        for (child in node.children) visit(child, alpha, hidden)
    }

    visit(scene, inheritedAlpha = 1f, inheritedHidden = false)
    return commands.sortedWith(compareBy({ it.second.first }, { it.second.second })).map { it.first }
}

private fun addSpriteCommand(
    node: SKSpriteNode,
    scene: SKScene,
    alpha: Float,
    add: (SKRenderCommand, Float) -> Unit,
) {
    val corners = node.localQuadCorners().map { node.convertTo(it, scene) }
    val uv = node.texture?.textureRect ?: Rect(0f, 0f, 1f, 1f)
    add(
        SKRenderCommand(
            texture = node.texture,
            blendMode = node.blendMode,
            vertices = quadVertices(corners, uv),
            color = tintedVertexColor(node.color, node.colorBlendFactor, alpha),
        ),
        node.zPosition,
    )
}

private fun addLabelCommand(
    node: SKLabelNode,
    scene: SKScene,
    alpha: Float,
    add: (SKRenderCommand, Float) -> Unit,
) {
    val (texture, metrics) = node.renderedLabel() ?: return
    val corners =
        labelQuadCorners(metrics, node.horizontalAlignmentMode, node.verticalAlignmentMode).map {
            node.convertTo(it, scene)
        }
    add(
        SKRenderCommand(
            texture = texture,
            blendMode = SKBlendMode.Alpha,
            vertices = quadVertices(corners, texture.textureRect),
            // fontColor is already baked into the rendered texture
            color = SKVertexColor(1f, 1f, 1f, alpha),
        ),
        node.zPosition,
    )
}

private fun addShapeCommands(
    node: SKShapeNode,
    scene: SKScene,
    alpha: Float,
    add: (SKRenderCommand, Float) -> Unit,
) {
    val path = node.path ?: return
    val fillAlpha = alphaOf(node.fillColor) * alpha
    val strokeAlpha = alphaOf(node.strokeColor) * alpha
    if (fillAlpha <= 0f && (strokeAlpha <= 0f || node.lineWidth <= 0f)) return

    for (contour in flattenPath(path)) {
        if (fillAlpha > 0f) {
            val triangles = triangulateFill(contour.points)
            if (triangles.isNotEmpty()) {
                add(
                    shapeCommand(node, scene, triangles, node.fillColor, fillAlpha),
                    node.zPosition,
                )
            }
        }
        if (strokeAlpha > 0f && node.lineWidth > 0f) {
            val triangles = triangulateStroke(contour.points, node.lineWidth, contour.closed)
            if (triangles.isNotEmpty()) {
                add(
                    shapeCommand(node, scene, triangles, node.strokeColor, strokeAlpha),
                    node.zPosition,
                )
            }
        }
    }
}

private fun shapeCommand(
    node: SKShapeNode,
    scene: SKScene,
    localTriangleVertices: List<Vector2>,
    colorInt: Int,
    alpha: Float,
): SKRenderCommand =
    SKRenderCommand(
        texture = null,
        blendMode = SKBlendMode.Alpha,
        vertices = localTriangleVertices.map { SKRenderVertex(node.convertTo(it, scene), 0f, 0f) },
        color = SKVertexColor(redOf(colorInt), greenOf(colorInt), blueOf(colorInt), alpha),
    )

/**
 * Builds the 6-vertex (2 triangle) list for a quad from its `[bottomLeft, bottomRight, topRight,
 * topLeft]` corners and a UV rect.
 */
private fun quadVertices(
    corners: List<Vector2>,
    uv: Rect,
): List<SKRenderVertex> {
    val bottomLeft = corners[0]
    val bottomRight = corners[1]
    val topRight = corners[2]
    val topLeft = corners[3]
    return listOf(
        SKRenderVertex(bottomLeft, uv.left, uv.bottom),
        SKRenderVertex(bottomRight, uv.right, uv.bottom),
        SKRenderVertex(topRight, uv.right, uv.top),
        SKRenderVertex(bottomLeft, uv.left, uv.bottom),
        SKRenderVertex(topRight, uv.right, uv.top),
        SKRenderVertex(topLeft, uv.left, uv.top),
    )
}

/**
 * The vertex color for a sprite whose own [colorInt] (ARGB) is mixed in by [colorBlendFactor]
 * (`0` = texture's colors show through unmodified, `1` = fully replaced by [colorInt]) and scaled
 * by the node's accumulated [alpha]. Computed on the CPU per sprite (these are per-node scalars,
 * not textures) so the fragment shader stays a simple `sample * vertexColor` — see
 * `docs/ARCHITECTURE.md`.
 */
private fun tintedVertexColor(
    colorInt: Int,
    colorBlendFactor: Float,
    alpha: Float,
): SKVertexColor {
    val blend = colorBlendFactor.coerceIn(0f, 1f)
    val r = 1f + (redOf(colorInt) - 1f) * blend
    val g = 1f + (greenOf(colorInt) - 1f) * blend
    val b = 1f + (blueOf(colorInt) - 1f) * blend
    return SKVertexColor(r, g, b, alpha)
}
