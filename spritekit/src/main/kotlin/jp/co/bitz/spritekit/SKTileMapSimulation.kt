package jp.co.bitz.spritekit

import kotlin.time.Duration

/**
 * Every [SKTileMapNode] in [node]'s subtree, skipping [SKNode.isPaused] subtrees -- the same
 * scope every other per-frame collector in this library uses.
 */
private fun collectTileMapNodes(
    node: SKNode,
    out: MutableList<SKTileMapNode> = mutableListOf(),
): List<SKTileMapNode> {
    if (node.isPaused) return out
    if (node is SKTileMapNode) out += node
    for (child in node.children) collectTileMapNodes(child, out)
    return out
}

/**
 * Advances every [SKTileMapNode] in [scene]'s own animation clock by [deltaTime] -- called once
 * per frame by [SKView], after [stepEmitters] and before rendering, so multi-texture
 * [SKTileDefinition]s animate.
 */
internal fun stepTileMaps(
    scene: SKScene,
    deltaTime: Duration,
) {
    for (tileMap in collectTileMapNodes(scene)) tileMap.elapsedTime += deltaTime
}
