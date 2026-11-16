package jp.co.bitz.spritekit

/**
 * A node that controls which part of a scene is visible — mirrors Apple's `SKCameraNode`. Set
 * [SKScene.camera] to one that's part of the scene's node tree (via [SKNode.addChild], directly
 * or nested) to have the scene render relative to the camera's position/rotation/scale instead of
 * the scene's own origin: moving the camera pans the view; scaling it up zooms out (a bigger
 * camera maps more of the scene into the same viewport).
 *
 * A camera not yet added to its scene's tree has no effect — matching Apple's own requirement
 * that the camera be part of the presented scene.
 */
public open class SKCameraNode : SKNode() {
    /**
     * Whether [node] is within this camera's current viewport (approximated as [SKScene.size]
     * centered on this camera's own position — [SKScene.anchorPoint] plays no role once a camera
     * is active, matching [computeSceneProjection]'s `hasCamera` handling and Apple's real,
     * verified behavior; see `docs/API_COMPATIBILITY.md`. Doesn't account for
     * [SKScene.scaleMode]'s letterbox/crop adjustment against the presenting [SKView]'s actual
     * aspect ratio, since a node has no way to know that from here). `false` if this camera isn't
     * part of a presented scene.
     */
    public fun containsNode(node: SKNode): Boolean {
        val scene = scene ?: return false
        val visibleRect =
            Rect(
                left = -scene.size.x / 2f,
                top = -scene.size.y / 2f,
                right = scene.size.x / 2f,
                bottom = scene.size.y / 2f,
            )
        val nodeReferenceSpace = node.parent ?: node
        val nodeCorners = corners(node.calculateAccumulatedFrame()).map { convertFrom(it, nodeReferenceSpace) }
        return visibleRect.intersects(boundingRectOf(nodeCorners))
    }
}
