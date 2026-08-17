package jp.co.bitz.spritekit

import kotlin.math.cos
import kotlin.math.sin

/**
 * A single rendered vertex: [position] already in the presenting [SKScene]'s (or, if one is set,
 * [SKScene.camera]'s) coordinate space, plus texture coordinates.
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
 * fill/stroke), [blendMode], and — if this command's node was under an [SKCropNode] — [clipRect]
 * (in the same space as [vertices], `null` meaning unclipped). Not part of the public API.
 */
internal data class SKRenderCommand(
    val texture: SKTexture?,
    val blendMode: SKBlendMode,
    val vertices: List<SKRenderVertex>,
    val color: SKVertexColor,
    val clipRect: Rect? = null,
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
 * Every position is expressed relative to [SKScene.camera] if one is set, or [scene] itself
 * otherwise — see `docs/ARCHITECTURE.md`. Descendants of an [SKCropNode] carry that node's
 * (possibly further narrowed, if nested) [SKRenderCommand.clipRect].
 *
 * Pure Kotlin, reusing [SKNode.convertTo] (from Phase 2) to correctly account for every
 * ancestor's position/rotation/scale — no OpenGL dependency, so this is unit-testable
 * independent of a live GL context, *except* when the scene contains an [SKLabelNode] with
 * non-empty [SKLabelNode.text] or an [SKShapeNode] with a non-null [SKShapeNode.path] — both
 * touch Android APIs ([renderLabelBitmap]'s `Paint`/`Canvas`, [flattenPath]'s `PathMeasure`) that
 * aren't safe to call from plain JVM unit tests; see `docs/ROADMAP.md`'s testing notes.
 */
internal fun buildRenderCommands(scene: SKScene): List<SKRenderCommand> =
    SKRenderCommandCollector(scene.camera ?: scene).collect(scene)

/**
 * Everything a leaf node needs to build its [SKRenderCommand]: where it's drawn relative to, and
 * any inherited crop.
 */
private class RenderContext(
    val referenceNode: SKNode,
    val clipRect: Rect?,
    val add: (SKRenderCommand, Float) -> Unit,
)

/**
 * Walks a scene's tree, collecting [SKRenderCommand]s. Holds the mutable state the walk needs
 * (the output list, a tree-order counter).
 */
private class SKRenderCommandCollector(
    private val referenceNode: SKNode,
) {
    private val commands = mutableListOf<Pair<SKRenderCommand, Pair<Float, Int>>>()
    private var order = 0

    fun collect(scene: SKScene): List<SKRenderCommand> {
        visit(scene, inheritedAlpha = 1f, inheritedHidden = false, inheritedClip = null)
        return commands.sortedWith(compareBy({ it.second.first }, { it.second.second })).map { it.first }
    }

    private fun add(
        command: SKRenderCommand,
        zPosition: Float,
    ) {
        commands += command to (zPosition to order)
    }

    private fun visit(
        node: SKNode,
        inheritedAlpha: Float,
        inheritedHidden: Boolean,
        inheritedClip: Rect?,
    ) {
        order++
        val hidden = inheritedHidden || node.isHidden
        val alpha = inheritedAlpha * node.alpha
        val clip =
            if (node is SKCropNode) {
                cropClip(
                    node,
                    referenceNode,
                    inheritedClip,
                )
            } else {
                CropResult(inheritedClip, skip = false)
            }
        if (clip.skip) return

        // A node added as its crop-node parent's own maskNode (the common way to position a
        // mask consistently with its siblings, matching Apple) is consumed as the mask, not
        // rendered a second time as ordinary content.
        val isCropMask = (node.parent as? SKCropNode)?.maskNode === node
        if (!isCropMask && !hidden && alpha > 0f) {
            addCommand(node, RenderContext(referenceNode, clip.rect, ::add), alpha)
        }
        for (child in node.children) visit(child, alpha, hidden, clip.rect)
    }
}

private fun addCommand(
    node: SKNode,
    context: RenderContext,
    alpha: Float,
) {
    when (node) {
        is SKSpriteNode -> addSpriteCommand(node, context, alpha)
        is SKLabelNode -> addLabelCommand(node, context, alpha)
        is SKShapeNode -> addShapeCommands(node, context, alpha)
        is SKEmitterNode -> addEmitterCommands(node, context, alpha)
        else -> Unit
    }
}

/**
 * [rect] is the clip to use for this crop node's children; `skip` means the crop reduced to an
 * empty area — don't render its subtree at all.
 */
private class CropResult(val rect: Rect?, val skip: Boolean)

private fun cropClip(
    node: SKCropNode,
    referenceNode: SKNode,
    inheritedClip: Rect?,
): CropResult {
    // Apple: a nil maskNode means nothing is visible.
    val mask = node.maskNode ?: return CropResult(inheritedClip, skip = true)
    val maskRect = frameInReferenceSpace(mask, referenceNode)
    // Not `inheritedClip?.intersection(maskRect) ?: maskRect`: that `?:` would also (wrongly)
    // fall back to `maskRect` when `intersection` itself returns null for a genuine
    // no-overlap case, silently hiding it instead of skipping the subtree below.
    val combined = if (inheritedClip == null) maskRect else inheritedClip.intersection(maskRect)
    return if (combined == null) CropResult(inheritedClip, skip = true) else CropResult(combined, skip = false)
}

/** [node]'s [SKNode.calculateAccumulatedFrame], converted from its own parent's space into [referenceNode]'s. */
private fun frameInReferenceSpace(
    node: SKNode,
    referenceNode: SKNode,
): Rect {
    val space = node.parent ?: node
    return boundingRectOf(corners(node.calculateAccumulatedFrame()).map { space.convertTo(it, referenceNode) })
}

private fun addSpriteCommand(
    node: SKSpriteNode,
    context: RenderContext,
    alpha: Float,
) {
    val corners = node.localQuadCorners().map { node.convertTo(it, context.referenceNode) }
    val uv = node.texture?.textureRect ?: Rect(0f, 0f, 1f, 1f)
    context.add(
        SKRenderCommand(
            texture = node.texture,
            blendMode = node.blendMode,
            vertices = quadVertices(corners, uv),
            color = tintedVertexColor(node.color, node.colorBlendFactor, alpha),
            clipRect = context.clipRect,
        ),
        node.zPosition,
    )
}

private fun addLabelCommand(
    node: SKLabelNode,
    context: RenderContext,
    alpha: Float,
) {
    val (texture, metrics) = node.renderedLabel() ?: return
    val corners =
        labelQuadCorners(metrics, node.horizontalAlignmentMode, node.verticalAlignmentMode).map {
            node.convertTo(it, context.referenceNode)
        }
    context.add(
        SKRenderCommand(
            texture = texture,
            blendMode = SKBlendMode.Alpha,
            vertices = quadVertices(corners, texture.textureRect),
            // fontColor is already baked into the rendered texture
            color = SKVertexColor(1f, 1f, 1f, alpha),
            clipRect = context.clipRect,
        ),
        node.zPosition,
    )
}

private fun addShapeCommands(
    node: SKShapeNode,
    context: RenderContext,
    alpha: Float,
) {
    val path = node.path ?: return
    val fillAlpha = alphaOf(node.fillColor) * alpha
    val strokeAlpha = alphaOf(node.strokeColor) * alpha
    if (fillAlpha <= 0f && (strokeAlpha <= 0f || node.lineWidth <= 0f)) return

    for (contour in flattenPath(path)) {
        if (fillAlpha > 0f) {
            val triangles = triangulateFill(contour.points)
            if (triangles.isNotEmpty()) {
                context.add(shapeCommand(node, context, triangles, node.fillColor, alpha = fillAlpha), node.zPosition)
            }
        }
        if (strokeAlpha > 0f && node.lineWidth > 0f) {
            val triangles = triangulateStroke(contour.points, node.lineWidth, contour.closed)
            if (triangles.isNotEmpty()) {
                context.add(
                    shapeCommand(node, context, triangles, node.strokeColor, alpha = strokeAlpha),
                    node.zPosition,
                )
            }
        }
    }
}

private fun shapeCommand(
    node: SKShapeNode,
    context: RenderContext,
    localTriangleVertices: List<Vector2>,
    colorInt: Int,
    alpha: Float,
): SKRenderCommand =
    SKRenderCommand(
        texture = null,
        blendMode = SKBlendMode.Alpha,
        vertices = localTriangleVertices.map { SKRenderVertex(node.convertTo(it, context.referenceNode), 0f, 0f) },
        color = SKVertexColor(redOf(colorInt), greenOf(colorInt), blueOf(colorInt), alpha),
        clipRect = context.clipRect,
    )

/**
 * One [SKRenderCommand] per currently-alive particle -- each is its own small textured quad, with
 * its own scale/rotation/alpha/color/z-position sampled from its age, positioned by translating
 * [SKParticle.position] (in [node]'s own local space) before converting to [context]'s reference
 * space, reusing the same [quadVertices] shape [addSpriteCommand] does.
 */
private fun addEmitterCommands(
    node: SKEmitterNode,
    context: RenderContext,
    alpha: Float,
) {
    val texture = node.particleTexture
    val uv = texture?.textureRect ?: Rect(0f, 0f, 1f, 1f)
    val halfSize = node.particleSize * 0.5f
    for (particle in node.particles) {
        val scale = particle.initialScale + particle.scaleSpeed * particle.age
        val rotation = particle.initialRotation + particle.rotationSpeed * particle.age
        val particleAlpha = (particle.initialAlpha + particle.alphaSpeed * particle.age).coerceIn(0f, 1f)
        val colorBlendFactor =
            (particle.initialColorBlendFactor + particle.colorBlendFactorSpeed * particle.age).coerceIn(
                0f,
                1f,
            )
        val lifeFraction = (particle.age / particle.lifetime).coerceIn(0f, 1f)
        val colorInt = node.particleColorSequence?.sample(lifeFraction, ::lerpColor) ?: node.particleColor

        val corners =
            rotatedQuadCorners(
                halfSize * scale,
                rotation,
            ).map { node.convertTo(it + particle.position, context.referenceNode) }
        context.add(
            SKRenderCommand(
                texture = texture,
                blendMode = node.particleBlendMode,
                vertices = quadVertices(corners, uv),
                color = tintedVertexColor(colorInt, colorBlendFactor, alpha * particleAlpha),
                clipRect = context.clipRect,
            ),
            particle.initialZPosition + particle.zPositionSpeed * particle.age,
        )
    }
}

/**
 * A quad's `[bottomLeft, bottomRight, topRight, topLeft]` corners, [halfSize] out from the origin
 * and rotated by [rotation] radians.
 */
private fun rotatedQuadCorners(
    halfSize: Vector2,
    rotation: Float,
): List<Vector2> {
    val cos = cos(rotation)
    val sin = sin(rotation)

    fun rotate(local: Vector2) = Vector2(local.x * cos - local.y * sin, local.x * sin + local.y * cos)
    return listOf(
        rotate(Vector2(-halfSize.x, -halfSize.y)),
        rotate(Vector2(halfSize.x, -halfSize.y)),
        rotate(Vector2(halfSize.x, halfSize.y)),
        rotate(Vector2(-halfSize.x, halfSize.y)),
    )
}

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
