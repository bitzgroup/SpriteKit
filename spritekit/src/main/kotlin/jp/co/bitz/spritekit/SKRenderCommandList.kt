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
 * fill/stroke), [blendMode], [shader] (`null` draws with the renderer's default program — only
 * [SKSpriteNode] can set one, see `SKShader.kt`), and — if this command's node was under an
 * [SKCropNode] — [clipRect] (in the same space as [vertices], `null` meaning unclipped). Not part
 * of the public API.
 */
internal data class SKRenderCommand(
    val texture: SKTexture?,
    val blendMode: SKBlendMode,
    val vertices: List<SKRenderVertex>,
    val color: SKVertexColor,
    val clipRect: Rect? = null,
    val shader: SKShader? = null,
)

/**
 * Flattens [scene]'s node tree into an ordered list of [SKRenderCommand]s, ready for
 * [SKSceneRenderer] to draw in order: sorted by each node's *effective* z-position — its own
 * [SKNode.zPosition] plus every ancestor's, accumulated down the tree (ties broken by
 * tree-traversal order) — matching Apple's documented rule that "the value \[of zPosition\] is
 * relative to the parent node" (a container's zPosition lifts its whole subtree above/below
 * sibling subtrees, even when the children themselves are left at the default `0`). See
 * `docs/ARCHITECTURE.md`.
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
 * Everything a leaf node needs to build its [SKRenderCommand]: where it's drawn relative to,
 * any inherited crop, and [zPosition] — this node's *effective* z-position (its own
 * [SKNode.zPosition] plus every ancestor's), used as the sort key instead of the node's own raw
 * value so a container's zPosition lifts its whole subtree, matching Apple.
 */
private class RenderContext(
    val referenceNode: SKNode,
    val clipRect: Rect?,
    val add: (SKRenderCommand, Float) -> Unit,
    val zPosition: Float,
    val worldVersion: Long,
    val referenceVersion: Long,
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

    // Computed once up front (root to referenceNode), not during the main walk below, since
    // referenceNode (the scene, or its camera) isn't necessarily visited before the nodes being
    // converted into its space. Calling `worldTransformVersion` again for the same nodes when the
    // main walk does reach them is harmless -- see that function's KDoc.
    private val referenceVersion = referenceWorldVersion(referenceNode)

    fun collect(scene: SKScene): List<SKRenderCommand> {
        visit(
            scene,
            inheritedAlpha = 1f,
            inheritedHidden = false,
            inheritedClip = null,
            inheritedZPosition = 0f,
            parentWorldVersion = 0L,
        )
        return commands.sortedWith(compareBy({ it.second.first }, { it.second.second })).map { it.first }
    }

    private fun add(
        command: SKRenderCommand,
        zPosition: Float,
    ) {
        commands += command to (zPosition to order)
    }

    @Suppress("LongParameterList")
    private fun visit(
        node: SKNode,
        inheritedAlpha: Float,
        inheritedHidden: Boolean,
        inheritedClip: Rect?,
        inheritedZPosition: Float,
        parentWorldVersion: Long,
    ) {
        order++
        val hidden = inheritedHidden || node.isHidden
        val alpha = inheritedAlpha * node.alpha
        val zPosition = inheritedZPosition + node.zPosition
        val worldVersion = node.worldTransformVersion(parentWorldVersion)
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
            val context = RenderContext(referenceNode, clip.rect, ::add, zPosition, worldVersion, referenceVersion)
            addCommand(node, context, alpha)
        }
        for (child in node.children) visit(child, alpha, hidden, clip.rect, zPosition, worldVersion)
    }
}

/**
 * [referenceNode]'s own [SKNode.worldTransformVersion], walking from the topmost ancestor down to
 * [referenceNode] itself (the order [SKNode.worldTransformVersion] requires) — see
 * [SKRenderCommandCollector.referenceVersion].
 */
private fun referenceWorldVersion(referenceNode: SKNode): Long {
    val rootToReference = generateSequence(referenceNode) { it.parent }.toList().asReversed()
    var version = 0L
    for (node in rootToReference) version = node.worldTransformVersion(version)
    return version
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
        is SKTileMapNode -> addTileMapCommands(node, context, alpha)
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
    val corners = node.convertAllTo(node.localQuadCorners(), context.referenceNode)
    val uv = node.texture?.textureRect ?: Rect(0f, 0f, 1f, 1f)
    context.add(
        SKRenderCommand(
            texture = node.texture,
            blendMode = node.blendMode,
            vertices = quadVertices(corners, uv),
            color = tintedVertexColor(node.color, node.colorBlendFactor, alpha),
            clipRect = context.clipRect,
            shader = node.shader,
        ),
        context.zPosition,
    )
}

private fun addLabelCommand(
    node: SKLabelNode,
    context: RenderContext,
    alpha: Float,
) {
    val (texture, metrics) = node.renderedLabel() ?: return
    val corners =
        node.convertAllTo(
            labelQuadCorners(metrics, node.horizontalAlignmentMode, node.verticalAlignmentMode),
            context.referenceNode,
        )
    context.add(
        SKRenderCommand(
            texture = texture,
            blendMode = SKBlendMode.Alpha,
            vertices = quadVertices(corners, texture.textureRect),
            // fontColor is already baked into the rendered texture
            color = SKVertexColor(1f, 1f, 1f, alpha),
            clipRect = context.clipRect,
        ),
        context.zPosition,
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

    // Flattening + triangulating a curved path (an ear-clip over the ~100+ points a typical
    // circle flattens to) is expensive enough that redoing it unconditionally every frame is the
    // difference between a static board of a few dozen shapes rendering fine and it pegging the
    // render thread -- see `triangulatedShape`'s KDoc. The per-vertex world-space transform below
    // is cached the same way (`worldVertices`): most shapes on a mostly-static scene haven't
    // actually moved on any given frame, and `SKNode.worldTransformVersion` tells us so without
    // redoing the conversion to find out.
    val shape = triangulatedShape(node, path)
    val worldVertices = worldVertices(node, shape, context)
    for (index in shape.fillRanges.indices) {
        if (fillAlpha > 0f) {
            val range = shape.fillRanges[index]
            if (!range.isEmpty()) {
                context.add(
                    shapeCommand(worldVertices.slice(range), node.fillColor, fillAlpha, context.clipRect),
                    context.zPosition,
                )
            }
        }
        if (strokeAlpha > 0f && node.lineWidth > 0f) {
            val range = shape.strokeRanges[index]
            if (!range.isEmpty()) {
                context.add(
                    shapeCommand(worldVertices.slice(range), node.strokeColor, strokeAlpha, context.clipRect),
                    context.zPosition,
                )
            }
        }
    }
}

/**
 * [SKShapeNode.worldVerticesCache]'s contents: [vertices] is [triangulationCache]'s
 * [SKShapeTriangulationCache.allLocalVertices] already converted into [referenceNode]'s space, as
 * of [worldVersion] (the owning node's [SKNode.worldTransformVersion] at the time) and
 * [referenceVersion] ([referenceNode]'s own, since it can itself move — e.g. a panning camera).
 */
internal class SKShapeWorldVerticesCache(
    val triangulationCache: SKShapeTriangulationCache,
    val referenceNode: SKNode,
    val referenceVersion: Long,
    val worldVersion: Long,
    val vertices: List<SKRenderVertex>,
)

/**
 * [shape]'s [SKShapeTriangulationCache.allLocalVertices] converted into [RenderContext.referenceNode]'s
 * space, reusing [SKShapeNode.worldVerticesCache] when neither [node]'s effective world transform
 * ([SKNode.worldTransformVersion]) nor [RenderContext.referenceNode]'s own has changed since it
 * was computed, and [shape] itself is still the node's current triangulation. Recomputes (one
 * batched [SKNode.convertAllTo] call, wrapping each result as an [SKRenderVertex]) otherwise.
 */
private fun worldVertices(
    node: SKShapeNode,
    shape: SKShapeTriangulationCache,
    context: RenderContext,
): List<SKRenderVertex> {
    val cached = node.worldVerticesCache
    if (cached != null && cached.isFresh(shape, context)) return cached.vertices

    val fresh = node.convertAllTo(shape.allLocalVertices, context.referenceNode).map { SKRenderVertex(it, 0f, 0f) }
    node.worldVerticesCache =
        SKShapeWorldVerticesCache(shape, context.referenceNode, context.referenceVersion, context.worldVersion, fresh)
    return fresh
}

/** Whether this cache is still valid for [shape] and [context] -- see [worldVertices]. */
private fun SKShapeWorldVerticesCache.isFresh(
    shape: SKShapeTriangulationCache,
    context: RenderContext,
): Boolean =
    triangulationCache === shape &&
        referenceNode === context.referenceNode &&
        referenceVersion == context.referenceVersion &&
        worldVersion == context.worldVersion

/** [range]'s slice of this list — a view ([List.subList]), not a copy. */
private fun <T> List<T>.slice(range: IntRange): List<T> = subList(range.first, range.last + 1)

private fun shapeCommand(
    vertices: List<SKRenderVertex>,
    colorInt: Int,
    alpha: Float,
    clipRect: Rect?,
): SKRenderCommand =
    SKRenderCommand(
        texture = null,
        blendMode = SKBlendMode.Alpha,
        vertices = vertices,
        color = SKVertexColor(redOf(colorInt), greenOf(colorInt), blueOf(colorInt), alpha),
        clipRect = clipRect,
    )

/**
 * One [SKRenderCommand] per currently-alive particle -- each is its own small textured quad, with
 * its own scale/rotation/alpha/color/z-position sampled from its age (that z-position offset is
 * relative to [node]'s own effective z-position, i.e. [RenderContext.zPosition], the same way
 * [SKNode.position] is relative to [node]'s own transform), positioned by translating
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
    val halfSize = node.effectiveParticleSize() * 0.5f
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

        val localCorners = rotatedQuadCorners(halfSize * scale, rotation).map { it + particle.position }
        val corners = node.convertAllTo(localCorners, context.referenceNode)
        context.add(
            SKRenderCommand(
                texture = texture,
                blendMode = node.particleBlendMode,
                vertices = quadVertices(corners, uv),
                color = tintedVertexColor(colorInt, colorBlendFactor, alpha * particleAlpha),
                clipRect = context.clipRect,
            ),
            context.zPosition + particle.initialZPosition + particle.zPositionSpeed * particle.age,
        )
    }
}

/**
 * One [SKRenderCommand] per non-empty cell in [node]'s grid -- an axis-aligned quad sized by that
 * cell's [SKTileDefinition.size] (not necessarily [SKTileMapNode.tileSize]) and centered on
 * [SKTileMapNode.centerOfTile], sampling whichever animation frame [SKTileMapNode.elapsedTime]
 * currently lands on. Every tile in a map shares [node]'s own effective z-position
 * ([RenderContext.zPosition]) -- a tile map renders as one flat layer, unlike per-particle
 * z-position.
 */
private fun addTileMapCommands(
    node: SKTileMapNode,
    context: RenderContext,
    alpha: Float,
) {
    for (row in 0 until node.numberOfRows) {
        for (column in 0 until node.numberOfColumns) {
            addTileCommand(node, column, row, context, alpha)
        }
    }
}

private fun addTileCommand(
    node: SKTileMapNode,
    column: Int,
    row: Int,
    context: RenderContext,
    alpha: Float,
) {
    val definition = node.tileDefinition(column, row) ?: return
    val center = node.centerOfTile(column, row)
    val halfSize = definition.size * 0.5f
    val localCorners =
        listOf(
            Vector2(center.x - halfSize.x, center.y - halfSize.y),
            Vector2(center.x + halfSize.x, center.y - halfSize.y),
            Vector2(center.x + halfSize.x, center.y + halfSize.y),
            Vector2(center.x - halfSize.x, center.y + halfSize.y),
        )
    val corners = node.convertAllTo(localCorners, context.referenceNode)
    val texture = definition.textureAt(node.elapsedTime)
    val uv = texture?.textureRect ?: Rect(0f, 0f, 1f, 1f)
    context.add(
        SKRenderCommand(
            texture = texture,
            blendMode = SKBlendMode.Alpha,
            vertices = quadVertices(corners, uv),
            color = SKVertexColor(1f, 1f, 1f, alpha),
            clipRect = context.clipRect,
        ),
        context.zPosition,
    )
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
