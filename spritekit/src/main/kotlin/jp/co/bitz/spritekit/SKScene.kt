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
    init {
        // Apple's documented default -- every other SKNode defaults to false.
        isUserInteractionEnabled = true
    }

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
     * The node whose position/rotation/scale the scene renders relative to, instead of the
     * scene's own origin — see [SKCameraNode]. Must be part of this scene's node tree (added via
     * [addChild], directly or nested) to take effect.
     */
    public var camera: SKCameraNode? = null

    /** This scene's rigid-body simulation -- gravity/speed plus whatever [SKNode.physicsBody]s are in the tree. */
    public val physicsWorld: SKPhysicsWorld = SKPhysicsWorld()

    /**
     * Which node is currently handling each active touch, by pointer ID -- lets a touch that
     * began on a node keep being delivered to that same node as it moves, regardless of where the
     * pointer travels afterward, matching Apple's tracking behavior. See `SKTouchDispatch.kt`.
     */
    internal val activeTouchTargets: MutableMap<Int, SKNode> = mutableMapOf()

    override val localBounds: Rect
        get() =
            Rect(
                left = 0f - size.x * anchorPoint.x,
                top = 0f - size.y * anchorPoint.y,
                right = size.x * (1f - anchorPoint.x),
                bottom = size.y * (1f - anchorPoint.y),
            )

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
