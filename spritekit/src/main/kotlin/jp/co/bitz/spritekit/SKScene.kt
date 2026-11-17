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
    size: Vector2,
) : SKNode() {
    /**
     * The scene's size, in points. Assigning a different value calls [didChangeSize] with the
     * previous size, matching Apple. With [SKSceneScaleMode.ResizeFill], the presenting [SKView]
     * keeps this in sync with its own size (in density-independent pixels, this library's stand-in
     * for Apple's points) — see [SKSceneScaleMode.ResizeFill].
     */
    public var size: Vector2 = size
        set(value) {
            val oldSize = field
            field = value
            if (oldSize != value) didChangeSize(oldSize)
        }

    init {
        // Apple's documented default -- every other SKNode defaults to false.
        isUserInteractionEnabled = true
    }

    /**
     * The [SKView] currently presenting this scene, or `null` if it isn't presented by any view
     * right now. Set by [SKView.presentScene] — lets scene code reach the view that's hosting it
     * (e.g. to call `view?.presentScene(nextScene, transition)` for scene-to-scene navigation)
     * without the app having to thread that reference through separately.
     */
    public var view: SKView? = null
        internal set

    /**
     * Takes over this scene's per-frame callbacks, method by method — Apple's `SKScene.delegate`;
     * see [SKSceneDelegate]. `null` (the default) leaves them to this scene. Held strongly, where
     * Apple's is `weak`: the JVM collects reference cycles, and a weakly held delegate (an `object :`
     * expression nothing else references) would silently stop being called.
     * Read on the render thread each frame.
     */
    public var delegate: SKSceneDelegate? = null

    /** Whether [sceneDidLoad] has run — see [loadIfNeeded]. */
    private var loaded = false

    /** Runs [sceneDidLoad] the first time it's called, never again. Render thread only. */
    internal fun loadIfNeeded() {
        if (loaded) return
        loaded = true
        sceneDidLoad()
    }

    /**
     * Converts [point] from the presenting [view]'s coordinate system into this scene's — Apple's
     * `convertPoint(fromView:)`. View coordinates are Android view pixels (origin top-left, y-down),
     * where Apple's are UIKit points; everything else — [scaleMode], [anchorPoint], [camera] — is
     * accounted for as Apple does. Returns [point] unchanged while the scene isn't presented (or the
     * view has no size yet). See [SKView.convertTo] for threading.
     */
    public fun convertPointFromView(point: Vector2): Vector2 {
        val view = view ?: return point
        return viewPointToScene(this, point, view.width, view.height)
    }

    /**
     * Converts [point] from this scene's coordinate system into the presenting [view]'s, in view
     * pixels — Apple's `convertPoint(toView:)`, the inverse of [convertPointFromView].
     */
    public fun convertPointToView(point: Vector2): Vector2 {
        val view = view ?: return point
        return scenePointToView(this, point, view.width, view.height)
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
    public val physicsWorld: SKPhysicsWorld = SKPhysicsWorld().also { it.scene = this }

    /**
     * Which node is currently handling each active touch, by pointer ID -- lets a touch that
     * began on a node keep being delivered to that same node as it moves, regardless of where the
     * pointer travels afterward, matching Apple's tracking behavior. See `SKTouchDispatch.kt`.
     */
    internal val activeTouchTargets: MutableMap<Int, SKNode> = mutableMapOf()

    /** The persistent [SKTouch] for each active pointer ID, updated in place as it moves. */
    internal val activeTouches: MutableMap<Int, SKTouch> = mutableMapOf()

    override val localBounds: Rect
        get() =
            Rect(
                left = 0f - size.x * anchorPoint.x,
                top = 0f - size.y * anchorPoint.y,
                right = size.x * (1f - anchorPoint.x),
                bottom = size.y * (1f - anchorPoint.y),
            )

    /**
     * Called once in this scene's lifetime, on the render thread, when an [SKView] first presents
     * it — right before the first [didMove], with [size] already the scene's own (a
     * [SKSceneScaleMode.ResizeFill] scene isn't resized to the view yet). Mirrors Apple's
     * `sceneDidLoad()`, which runs inside the scene's initializer; a Kotlin superclass constructor
     * can't safely call an override before the subclass's properties are initialized, so it's
     * deferred to the first presentation, and a scene that's never presented never gets it.
     * Presenting the scene again, or resizing it, doesn't call it again — as on Apple.
     */
    public open fun sceneDidLoad() {}

    /**
     * Called on the render thread right after an [SKView] starts presenting this scene (via
     * [SKView.presentScene]), with [view] already set. Mirrors Apple's `didMove(to:)` — the usual
     * place to build the scene's content. Like Apple's, it runs *before* an
     * [SKSceneScaleMode.ResizeFill] scene is resized to the view, so [size] may still be the size
     * the scene was created with here; override [didChangeSize] to react to the resize.
     */
    public open fun didMove(view: SKView) {}

    /**
     * Called on the render thread right before an [SKView] stops presenting this scene, because
     * another scene replaced it. Mirrors Apple's `willMove(from:)`; [view] is still set here.
     */
    public open fun willMove(view: SKView) {}

    /**
     * Called whenever [size] changes — whether assigned directly or by the presenting [SKView]
     * resizing an [SKSceneScaleMode.ResizeFill] scene. Mirrors Apple's `didChangeSize(_:)`.
     */
    public open fun didChangeSize(oldSize: Vector2) {}

    /**
     * Called once per frame with the time elapsed since the previous frame, before actions,
     * physics, and constraints are evaluated. Override to drive custom per-frame logic. This and
     * the four `did…` callbacks below are replaced by [delegate]'s counterparts when it has one.
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
