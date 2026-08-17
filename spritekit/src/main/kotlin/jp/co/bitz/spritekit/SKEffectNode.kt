package jp.co.bitz.spritekit

/**
 * A node that (on Apple's platforms) can apply a Core Image filter to its subtree — mirrors
 * Apple's `SKEffectNode`, minus the filter itself. `filter: CIFilter?` isn't implemented — no
 * Android equivalent to Core Image — so this class is currently a plain passthrough grouping
 * node; [shouldEnableEffects]/[shouldRasterize]/[shouldCenterFilter] are stored for API parity
 * but have no observable effect without a filter. See `docs/API_COMPATIBILITY.md`.
 *
 * Deviation: unlike Apple, [SKScene] and [SKCropNode] don't extend this class — with no filter
 * pipeline behind it, there's nothing for them to inherit; retrofitting that inheritance chain
 * isn't worth the churn unless a real filter/offscreen-rendering pipeline lands later.
 */
public open class SKEffectNode : SKNode() {
    public var shouldEnableEffects: Boolean = true
    public var shouldRasterize: Boolean = false
    public var shouldCenterFilter: Boolean = true
}
