package jp.co.bitz.spritekit

import android.graphics.Color
import kotlin.time.Duration

/**
 * The root of a scene's node tree and per-frame simulation, hosted by [SKView]. Mirrors Apple's
 * `SKScene`, which — like this class — is itself an [SKNode] subclass: a scene's own transform
 * properties ([SKNode.position] etc.) are rarely used directly, but its [children] are the scene
 * graph everything else attaches to.
 *
 * Subclass this and override [update] (and, less commonly, the other lifecycle callbacks) to
 * drive game/simulation logic. All callbacks, and all access to this scene's node tree, run on
 * [SKView]'s render thread — see `docs/ARCHITECTURE.md`.
 */
public open class SKScene(
    public var size: Vector2,
) : SKNode() {
    /** How this scene scales to fit the [SKView] presenting it. Defaults to [SKSceneScaleMode.Fill]. */
    public var scaleMode: SKSceneScaleMode = SKSceneScaleMode.Fill

    /**
     * The point within [size] (normalized `0..1` on each axis) that maps to the [SKView]'s
     * origin. `(0, 0)` (the default) puts the scene's origin at the view's bottom-left corner;
     * `(0.5, 0.5)` centers it.
     */
    public var anchorPoint: Vector2 = Vector2.Zero

    /** The color cleared behind this scene's content each frame. An ARGB packed `Int`, matching [Color]. */
    public var backgroundColor: Int = Color.BLACK

    /**
     * Called once per frame with the time elapsed since the previous frame, before actions,
     * physics, and constraints are evaluated. Override to drive custom per-frame logic.
     */
    public open fun update(deltaTime: Duration) {}

    /** Called once per frame, after [update] and after actions have been evaluated. */
    public open fun didEvaluateActions() {}

    /** Called once per frame, after physics has been simulated. */
    public open fun didSimulatePhysics() {}

    /** Called once per frame, after constraints have been applied. */
    public open fun didApplyConstraints() {}

    /** Called once per frame, after everything above and just before rendering. */
    public open fun didFinishUpdate() {}
}
