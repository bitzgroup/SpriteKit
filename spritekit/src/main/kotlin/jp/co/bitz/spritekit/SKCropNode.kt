package jp.co.bitz.spritekit

/**
 * A node that clips its children to [maskNode]'s bounds — mirrors Apple's `SKCropNode`. As with
 * Apple's version, [maskNode] is typically also added as a child (via [SKNode.addChild]) so it's
 * positioned consistently with its siblings; this library doesn't render it a second time as
 * ordinary content when it is.
 *
 * Deviation: this crops to [maskNode]'s *bounding box* ([SKNode.calculateAccumulatedFrame]), via
 * `glScissor` — not true per-pixel alpha masking. A non-rectangular or partially-transparent mask
 * (e.g. a circular [SKShapeNode]) clips to its rectangular bounds, not its actual silhouette. See
 * `docs/API_COMPATIBILITY.md`. Nested crop nodes intersect correctly (each one's clip rect is
 * narrowed further by any ancestor crop node's), since real per-pixel masking would need the same
 * bounding-box treatment for nested cases regardless.
 */
public open class SKCropNode(
    public var maskNode: SKNode? = null,
) : SKNode()
