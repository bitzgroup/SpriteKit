package jp.co.bitz.spritekit

import kotlin.time.Duration

/**
 * The root of a scene's per-frame simulation, hosted by [SKView].
 *
 * Mirrors Apple's `SKScene` lifecycle callback shape. In SpriteKit, `SKScene` also extends
 * `SKNode` to gain the scene graph (children, coordinate space, `size`/`scaleMode`/`anchorPoint`,
 * background color); that part of the API lands in Phase 2 once [jp.co.bitz.spritekit]'s `SKNode`
 * exists, since a real `SKScene` can't subclass a node type that hasn't been written yet. This
 * Phase 1 shell only carries the per-frame callback surface [SKView]'s render loop needs.
 *
 * Subclass this and override [update] (and, less commonly, the other callbacks) to drive
 * game/simulation logic. All callbacks run on [SKView]'s render thread — see
 * `docs/ARCHITECTURE.md`.
 */
public open class SKScene {
    /**
     * When `true`, [SKView]'s render loop skips [update] and the rest of the per-frame sequence
     * for this scene (time does not advance), but still renders the scene's current state.
     */
    public var isPaused: Boolean = false

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
