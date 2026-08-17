package jp.co.bitz.spritekit

/** A sprite's RGBA vertex color multiplier, each channel normalized `0..1`. Not part of the public API. */
internal data class SKSpriteColor(
    val r: Float,
    val g: Float,
    val b: Float,
    val a: Float,
)

/** A sprite's quad corners, already transformed into the presenting [SKScene]'s coordinate space. */
internal data class SKSpriteQuad(
    val bottomLeft: Vector2,
    val bottomRight: Vector2,
    val topRight: Vector2,
    val topLeft: Vector2,
)

/**
 * One [SKSpriteNode] ready to draw: its scene-space [quad], the [texture] region to sample (`null`
 * for an untextured, solid-[color] sprite), and the accumulated [color]/[blendMode] to draw it
 * with. Not part of the public API — the sprite renderer's input.
 */
internal data class SKSpriteDrawCommand(
    val texture: SKTexture?,
    val blendMode: SKBlendMode,
    val quad: SKSpriteQuad,
    val color: SKSpriteColor,
)

/**
 * Flattens [scene]'s node tree into an ordered list of [SKSpriteDrawCommand]s, ready for the
 * sprite renderer to draw in order: sorted by [SKNode.zPosition] (ties broken by tree-traversal
 * order, matching Apple's documented rule — see `docs/ARCHITECTURE.md`).
 *
 * Pure Kotlin, reusing [SKNode.convertTo] (from Phase 2) to correctly account for every ancestor's
 * position/rotation/scale — no OpenGL/Android dependency, so this is unit-testable independent of
 * a live GL context.
 */
internal fun buildSpriteDrawList(scene: SKScene): List<SKSpriteDrawCommand> {
    val commands = mutableListOf<Pair<SKSpriteDrawCommand, Pair<Float, Int>>>()
    var order = 0

    fun visit(
        node: SKNode,
        inheritedAlpha: Float,
        inheritedHidden: Boolean,
    ) {
        val hidden = inheritedHidden || node.isHidden
        val alpha = inheritedAlpha * node.alpha
        if (!hidden && alpha > 0f && node is SKSpriteNode) {
            val corners = node.localQuadCorners().map { node.convertTo(it, scene) }
            val command =
                SKSpriteDrawCommand(
                    texture = node.texture,
                    blendMode = node.blendMode,
                    quad = SKSpriteQuad(corners[0], corners[1], corners[2], corners[3]),
                    color = spriteVertexColor(node.color, node.colorBlendFactor, alpha),
                )
            commands += command to (node.zPosition to order)
        }
        order++
        for (child in node.children) visit(child, alpha, hidden)
    }

    visit(scene, inheritedAlpha = 1f, inheritedHidden = false)
    return commands.sortedWith(compareBy({ it.second.first }, { it.second.second })).map { it.first }
}

/**
 * The vertex color for a sprite whose own [colorInt] (ARGB) is mixed in by [colorBlendFactor]
 * (`0` = texture's colors show through unmodified, `1` = fully replaced by [colorInt]) and scaled
 * by the node's accumulated [alpha]. Computed on the CPU per sprite (these are per-node scalars,
 * not textures) so the fragment shader stays a simple `sample * vertexColor` — see
 * `docs/ARCHITECTURE.md`.
 */
private fun spriteVertexColor(
    colorInt: Int,
    colorBlendFactor: Float,
    alpha: Float,
): SKSpriteColor {
    val blend = colorBlendFactor.coerceIn(0f, 1f)
    val r = 1f + (redOf(colorInt) - 1f) * blend
    val g = 1f + (greenOf(colorInt) - 1f) * blend
    val b = 1f + (blueOf(colorInt) - 1f) * blend
    return SKSpriteColor(r, g, b, alpha)
}
