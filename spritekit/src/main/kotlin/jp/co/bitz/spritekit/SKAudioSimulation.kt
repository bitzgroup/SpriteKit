package jp.co.bitz.spritekit

/**
 * Every [SKAudioNode] in [node]'s subtree, skipping [SKNode.isPaused] subtrees -- the same scope
 * every other per-frame collector in this library uses.
 */
private fun collectAudioNodes(
    node: SKNode,
    out: MutableList<SKAudioNode> = mutableListOf(),
): List<SKAudioNode> {
    if (node.isPaused) return out
    if (node is SKAudioNode) out += node
    for (child in node.children) collectAudioNodes(child, out)
    return out
}

/**
 * Starts every [SKAudioNode] in [scene] whose [SKAudioNode.autoplayLooped] is `true` and hasn't
 * already auto-started -- called once per frame by [SKView], alongside `stepTileMaps`. Only
 * triggers once per node's lifetime in the tree, matching Apple's "plays automatically once added
 * to the scene" contract (not a continuous re-trigger if later paused/stopped).
 */
internal fun stepAudioNodes(scene: SKScene) {
    for (audioNode in collectAudioNodes(scene)) {
        if (audioNode.autoplayLooped && !audioNode.hasAutoStarted) {
            audioNode.hasAutoStarted = true
            audioNode.play()
        }
    }
}
