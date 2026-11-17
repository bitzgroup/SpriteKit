package jp.co.bitz.spritekit

import kotlin.time.Duration

/**
 * Takes over a scene's per-frame callbacks without subclassing it — Apple's `SKSceneDelegate`,
 * assigned through [SKScene.delegate].
 *
 * As on Apple (verified on iOS/tvOS simulators), the delegate replaces the scene's own callbacks
 * *per method*: each frame step calls the delegate's method instead of the scene's, and a step the
 * delegate doesn't override still reaches the scene's own override. Here that falls out of the
 * default implementations, which forward to the scene — Apple's optional protocol methods have no
 * other Kotlin counterpart. Calling `super` from an override therefore runs the scene's callback
 * too. The order within a frame is the scene's: [update], [didEvaluateActions],
 * [didSimulatePhysics], [didApplyConstraints], [didFinishUpdate], all on [SKView]'s render thread;
 * a paused scene (or view) calls none of them.
 *
 * [update] passes the elapsed time since the previous frame, as [SKScene.update] does, where
 * Apple's `update(_:for:)` passes an absolute timestamp.
 */
public interface SKSceneDelegate {
    /** Replaces [SKScene.update] — Apple's `update(_:for:)`. */
    public fun update(
        deltaTime: Duration,
        scene: SKScene,
    ) {
        scene.update(deltaTime)
    }

    /** Replaces [SKScene.didEvaluateActions] — Apple's `didEvaluateActions(for:)`. */
    public fun didEvaluateActions(scene: SKScene) {
        scene.didEvaluateActions()
    }

    /** Replaces [SKScene.didSimulatePhysics] — Apple's `didSimulatePhysics(for:)`. */
    public fun didSimulatePhysics(scene: SKScene) {
        scene.didSimulatePhysics()
    }

    /** Replaces [SKScene.didApplyConstraints] — Apple's `didApplyConstraints(for:)`. */
    public fun didApplyConstraints(scene: SKScene) {
        scene.didApplyConstraints()
    }

    /** Replaces [SKScene.didFinishUpdate] — Apple's `didFinishUpdate(for:)`. */
    public fun didFinishUpdate(scene: SKScene) {
        scene.didFinishUpdate()
    }
}

// Frame-step dispatch for SKView's render loop: the scene's delegate when it has one, else the
// scene itself. Kept out of SKScene so its public surface stays Apple's.

internal fun SKScene.dispatchUpdate(deltaTime: Duration) {
    delegate?.update(deltaTime, this) ?: update(deltaTime)
}

internal fun SKScene.dispatchDidEvaluateActions() {
    delegate?.didEvaluateActions(this) ?: didEvaluateActions()
}

internal fun SKScene.dispatchDidSimulatePhysics() {
    delegate?.didSimulatePhysics(this) ?: didSimulatePhysics()
}

internal fun SKScene.dispatchDidApplyConstraints() {
    delegate?.didApplyConstraints(this) ?: didApplyConstraints()
}

internal fun SKScene.dispatchDidFinishUpdate() {
    delegate?.didFinishUpdate(this) ?: didFinishUpdate()
}
