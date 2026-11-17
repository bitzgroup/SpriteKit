package jp.co.bitz.spritekit

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewConfiguration
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.time.Duration

/**
 * Hosts an [SKScene], driving its per-frame update/render loop. Mirrors Apple's `SKView`
 * (`UIView`/`NSView` subclass), but as a `GLSurfaceView` subclass — see `docs/ARCHITECTURE.md`'s
 * "Hosting: a View core, a Compose wrapper" section for why, and for the recommended
 * `:spritekit-compose` wrapper. Works directly in XML layouts or via plain `addView` calls.
 *
 * `GLSurfaceView`'s own `Renderer` thread is this scene's main thread: all [SKScene] state is
 * confined to it. Cross from the UI thread via [runOnGLThread]; cross back via [runOnUiThread].
 * See `docs/ARCHITECTURE.md`'s "Thread ownership rules" and "Bridge utilities" sections.
 */
public class SKView
    @JvmOverloads
    constructor(context: Context, attrs: AttributeSet? = null) : GLSurfaceView(context, attrs) {
        private val mainHandler = Handler(Looper.getMainLooper())

        /** Render-thread-confined; re-uploads GPU resources after `EGLContext` loss. */
        public val resourceRegistry: SKResourceRegistry = SKResourceRegistry()

        /**
         * Render-thread-confined callback invoked for each [SKTouchEvent] snapshotted from
         * [onTouchEvent], in addition to (not instead of) this view's automatic
         * `SKNode`/`SKScene` touch dispatch (see `SKTouchDispatch.kt`) — an escape hatch for raw
         * view-space touch access. `null` (the default) means nothing extra happens.
         */
        public var onTouch: ((SKTouchEvent) -> Unit)? = null

        /** Render-thread-confined; tracks the presses [forwardKeyEvent] has delivered. */
        private val pressDispatcher = SKPressDispatcher()

        @Volatile
        private var currentScene: SKScene? = null

        /**
         * The scene this view currently presents — Apple's `SKView.scene`. `null` until the first
         * [presentScene] has taken effect on the render thread (it's set there, before the next
         * frame, not synchronously by [presentScene]). Readable from any thread; the scene itself is
         * still render-thread-confined, so cross with [runOnGLThread] before touching its state.
         */
        public val scene: SKScene?
            get() = currentScene

        /**
         * Whether this view's scene is paused — Apple's `SKView.isPaused`. While `true`, the frame
         * loop runs no `update`, actions, physics, constraints, particles, tile-map animation or
         * audio stepping, whatever the scene's own [SKNode.isPaused] says; the scene keeps being
         * drawn as it last was.
         *
         * Like Apple's (verified on an iOS simulator), setting it also sets the presented scene's
         * [SKNode.isPaused] to the same value, and a view paused this way stays paused across
         * [onPause]/[onResume] — unlike a scene paused only through its own `isPaused`, which
         * [onResume] un-pauses, again as Apple does when an app returns to the foreground.
         * [presentScene] without a transition sets it back to `false`, as Apple's does.
         * Safe to set from any thread; the scene side takes effect on the render thread.
         */
        @Volatile
        public var isPaused: Boolean = false
            set(value) {
                if (field == value) return
                field = value
                runOnGLThread { currentScene?.isPaused = value }
            }
        private val sceneRenderer = SKSceneRenderer()
        private var viewWidth = 0
        private var viewHeight = 0

        // UITouch.tapCount for each pointer, worked out on the UI thread with Android's tap limits.
        private val tapCounter =
            ViewConfiguration.get(context).let { config ->
                SKTapCounter(
                    doubleTapTimeoutMs = ViewConfiguration.getDoubleTapTimeout().toLong(),
                    doubleTapSlopPx = config.scaledDoubleTapSlop.toFloat(),
                    touchSlopPx = config.scaledTouchSlop.toFloat(),
                    longPressTimeoutMs = ViewConfiguration.getLongPressTimeout().toLong(),
                )
            }

        /** Pixels per density-independent pixel; refreshed with the surface size. */
        private var density = context.resources.displayMetrics.density

        /**
         * Apple's `ignoresSiblingOrder`, `false` by default as on Apple. Nodes always draw in
         * order of their effective [SKNode.zPosition] (their own plus every ancestor's). With
         * this `false`, nodes at an equal effective `zPosition` then draw in tree order: a parent
         * before its children, earlier siblings before later ones (verified on iOS/tvOS
         * simulators).
         *
         * Setting it `true` tells Apple that equal-`zPosition` nodes may draw in any order, which
         * it uses to batch draws. This renderer keeps drawing them in tree order either way —
         * one of the orders Apple allows — so the flag is accepted for source compatibility and
         * changes nothing.
         */
        public var ignoresSiblingOrder: Boolean = false

        /**
         * Whether the view renders transparently where the scene doesn't cover it, so whatever is
         * behind the view shows through — Apple's `SKView.allowsTransparency`. `false` (the default,
         * as on Apple) renders opaquely: the scene's [SKScene.backgroundColor] is drawn at full
         * opacity whatever its alpha. With `true`, give the scene a transparent `backgroundColor`
         * (e.g. `Color.TRANSPARENT`) for the content behind to show.
         *
         * **Android limitation — read before using.** A `GLSurfaceView` renders into its own surface,
         * which the system composites either entirely below its window (where nothing behind the view
         * can show through) or entirely above it. Transparency therefore puts the surface **above its
         * window** (`setZOrderOnTop`), and then it is drawn over everything else the app shows there.
         * Tested on a phone emulator: a Material `AlertDialog` showed the scene through it and a
         * `DropdownMenu` didn't appear. Only use this for a view nothing is ever shown on top of; to
         * put a background behind the scene's content, drawing it inside the scene (a full-scene
         * sprite below everything) is usually the better choice. Lifting this needs a `TextureView`
         * core instead of `GLSurfaceView` — see `docs/PARITY_GAP_PLAN.md` section 2.9.
         *
         * Set this before the view is attached to a window; Android doesn't reliably reorder a surface
         * that's already attached. UI-thread only, like any other `View` property.
         */
        public var allowsTransparency: Boolean = false
            set(value) {
                if (field == value) return
                field = value
                holder.setFormat(if (value) PixelFormat.TRANSLUCENT else PixelFormat.OPAQUE)
                setZOrderOnTop(value)
                runOnGLThread { renderTransparently = value }
            }

        /** Render-thread copy of [allowsTransparency]. */
        private var renderTransparently = false

        private var transitionFromScene: SKScene? = null
        private var transitionSpec: SKTransition? = null
        private var transitionElapsed: Duration = Duration.ZERO

        init {
            setEGLContextClientVersion(2)
            // Always ask for an alpha channel, so [allowsTransparency] can be switched on without
            // recreating the EGL context (the config is fixed once the renderer is set). An opaque
            // surface simply ignores the alpha.
            setEGLConfigChooser(8, 8, 8, 8, DEPTH_BITS, 0)
            setRenderer(SceneRenderer())
            renderMode = RENDERMODE_CONTINUOUSLY
            audioPlaybackFactory = realAudioPlaybackFactory(context.applicationContext)
            SKBundle.context = context.applicationContext
        }

        /**
         * Presents [scene], replacing whatever scene was previously presented. Takes effect on
         * the render thread before the next frame — see [runOnGLThread].
         */
        public fun presentScene(scene: SKScene) {
            // Apple's presentScene(_:) also un-pauses a paused view (verified on an iOS simulator).
            isPaused = false
            runOnGLThread { setCurrentScene(scene) }
        }

        /**
         * Presents [scene] like [presentScene], but plays [transition] over its
         * [SKTransition.duration] instead of cutting instantly — a no-op transition (just an
         * instant cut) if no scene was already presented, since there'd be nothing to transition
         * from.
         */
        public fun presentScene(
            scene: SKScene,
            transition: SKTransition,
        ) {
            runOnGLThread {
                currentScene?.let {
                    transitionFromScene = it
                    transitionSpec = transition
                    transitionElapsed = Duration.ZERO
                }
                setCurrentScene(scene)
            }
        }

        /**
         * Swaps [currentScene] to [scene], keeping [SKScene.view] in sync on both the outgoing and
         * incoming scene — matching Apple's documented behavior that a scene's `view` becomes
         * `nil` once another scene replaces it. Must only be called on the render thread.
         */
        private fun setCurrentScene(scene: SKScene) {
            currentScene?.let { outgoing ->
                outgoing.willMove(this)
                outgoing.view = null
            }
            currentScene = scene
            // Apple un-pauses a scene when it's presented, even into a paused view.
            scene.isPaused = false
            scene.view = this
            scene.loadIfNeeded()
            scene.didMove(this)
            // Apple resizes a `.resizeFill` scene after `didMove(to:)`, on the next layout pass.
            syncResizeFillSize(scene)
        }

        /**
         * Keeps a [SKSceneScaleMode.ResizeFill] scene's [SKScene.size] equal to this view's size
         * (see [resizeFillSceneSize]). A no-op for other scale modes, before the surface has a size,
         * or when the size already matches (so [SKScene.didChangeSize] only fires on real changes).
         * Must only be called on the render thread.
         */
        private fun syncResizeFillSize(scene: SKScene) {
            if (scene.scaleMode != SKSceneScaleMode.ResizeFill) return
            resizeFillSceneSize(viewWidth, viewHeight, density)?.let { scene.size = it }
        }

        /**
         * Converts [point] from this view's coordinate system (view pixels, origin top-left, y-down)
         * into [scene]'s — Apple's `convert(_:to:)`. Renamed like [SKNode.convertTo], since Swift
         * argument labels don't distinguish Kotlin overloads. Accounts for the scene's
         * [SKScene.scaleMode], [SKScene.anchorPoint] and [SKScene.camera], with the same math that
         * places touches and rendered content. Returns [point] unchanged before the view is laid out.
         *
         * Reads the scene's camera transform, which is render-thread state: call it on the render
         * thread (e.g. inside [runOnGLThread]) for a result that matches the frame being drawn; from
         * the UI thread it can lag a moving camera by a frame.
         */
        public fun convertTo(
            point: Vector2,
            scene: SKScene,
        ): Vector2 = viewPointToScene(scene, point, width, height)

        /**
         * Converts [point] from [scene]'s coordinate system into this view's, in view pixels — Apple's
         * `convert(_:from:)`, the inverse of [convertTo]. The same threading note applies.
         */
        public fun convertFrom(
            point: Vector2,
            scene: SKScene,
        ): Vector2 = scenePointToView(scene, point, width, height)

        /**
         * Queues [block] to run on the render thread before the next frame. Fire-and-forget,
         * FIFO-ordered relative to other queued blocks. Safe to call from any thread, including
         * before the render thread has started.
         */
        public fun runOnGLThread(block: () -> Unit) {
            queueEvent { block() }
        }

        /**
         * Posts [block] to run on the UI thread. For render-thread code that needs to reach
         * UI-thread-confined Android APIs.
         */
        public fun runOnUiThread(block: () -> Unit) {
            mainHandler.post { block() }
        }

        /**
         * Suspends the render thread (`GLSurfaceView.onPause`) and pauses the presented scene. Call
         * from the host's `Activity`/`Fragment` `onPause` (the Compose wrapper does this itself).
         */
        override fun onPause() {
            runOnGLThread { currentScene?.isPaused = true }
            super.onPause()
        }

        /**
         * Resumes the render thread and un-pauses the presented scene — even one the app had paused
         * through [SKNode.isPaused] itself, matching Apple, whose `SKView` un-pauses its scene when the
         * app returns to the foreground (verified on an iOS simulator). To keep a scene paused across
         * backgrounding, pause the view ([isPaused]) instead, or pause a child node holding the
         * gameplay content rather than the scene.
         */
        override fun onResume() {
            super.onResume()
            runOnGLThread { currentScene?.isPaused = isPaused }
        }

        /**
         * Forwards a key event to the presented scene as an Apple-shaped press — the counterpart of
         * a tvOS view controller overriding `pressesBegan`/`pressesEnded` and passing them to its
         * scene. Like Apple's SpriteKit, this view never delivers presses on its own; call this from
         * the host `Activity`'s `onKeyDown`/`onKeyUp` (or `dispatchKeyEvent`) and return its result:
         *
         * ```
         * override fun onKeyDown(keyCode: Int, event: KeyEvent) =
         *     skView.forwardKeyEvent(event) || super.onKeyDown(keyCode, event)
         * ```
         *
         * Keys with an [SKPressType] ([skPressTypeForKeyCode]) are delivered on the render thread:
         * the first key-down as [SKNode.pressesBegan], the key-up as [SKNode.pressesEnded] (or
         * [SKNode.pressesCancelled] for a cancelled key-up). Auto-repeat key-downs are consumed but
         * not delivered, so a held button is one `began` and one `ended`, as on tvOS.
         *
         * Returns `true` when [event] was consumed, `false` for keys with no press type. Back maps to
         * [SKPressType.Menu], and consuming it keeps the system from handling Back — as on tvOS,
         * where a Menu press the game handles doesn't leave the app. Whether the game handles Back
         * is the host's decision: don't forward it when the system should handle it.
         */
        public fun forwardKeyEvent(event: KeyEvent): Boolean {
            val type = skPressTypeForKeyCode(event.keyCode)
            val isPressAction = event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP
            if (type != null && isPressAction && event.repeatCount == 0) {
                val transition =
                    SKKeyTransition(
                        keyCode = event.keyCode,
                        type = type,
                        isDown = event.action == KeyEvent.ACTION_DOWN,
                        isCanceled = event.isCanceled,
                    )
                runOnGLThread { pressDispatcher.dispatch(currentScene, transition) }
            }
            return type != null && isPressAction
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val phase = skTouchPhaseForAction(event.actionMasked)
            if (phase != null) {
                // A move or cancel reports every pointer on screen; a down/up only the one that changed.
                val indices =
                    if (phase == SKTouchPhase.Moved || phase == SKTouchPhase.Cancelled) {
                        0 until event.pointerCount
                    } else {
                        listOf(event.actionIndex)
                    }
                val snapshots =
                    indices.map { index ->
                        val pointerId = event.getPointerId(index)
                        val x = event.getX(index)
                        val y = event.getY(index)
                        SKTouchEvent(
                            pointerId = pointerId,
                            x = x,
                            y = y,
                            phase = phase,
                            tapCount = tapCount(phase, pointerId, x, y, event.eventTime),
                            timestamp = event.eventTime / MILLIS_PER_SECOND,
                        )
                    }
                runOnGLThread {
                    currentScene?.let { dispatchTouches(it, snapshots, viewWidth, viewHeight) }
                    snapshots.forEach { onTouch?.invoke(it) }
                }
            }
            return true
        }

        private fun tapCount(
            phase: SKTouchPhase,
            pointerId: Int,
            x: Float,
            y: Float,
            timeMs: Long,
        ): Int =
            when (phase) {
                SKTouchPhase.Began -> tapCounter.began(pointerId, x, y, timeMs)
                SKTouchPhase.Moved -> tapCounter.moved(pointerId, x, y)
                SKTouchPhase.Ended -> tapCounter.ended(pointerId, x, y, timeMs)
                SKTouchPhase.Cancelled -> tapCounter.cancelled(pointerId)
            }

        /**
         * Renders the current scene's state via [sceneRenderer] — clears to
         * [SKScene.backgroundColor] and draws its sprites (Phase 3). Falls back to a plain black
         * clear when no scene is presented yet.
         */
        private fun renderFrame() {
            val scene = currentScene
            if (scene != null) {
                sceneRenderer.draw(
                    scene,
                    viewWidth,
                    viewHeight,
                    resourceRegistry,
                    allowsTransparency = renderTransparently,
                )
            } else {
                GLES20.glClearColor(0f, 0f, 0f, if (renderTransparently) 0f else 1f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            }
        }

        /**
         * Advances an in-progress [SKTransition] by [deltaTime] and draws it, or falls back to
         * [renderFrame] once it's finished (or none is active). See `SKTransitionLayers.kt` for
         * the actual per-layer offset/alpha/clip math.
         */
        private fun renderTransitionFrame(deltaTime: Duration) {
            val transition = transitionSpec
            val fromScene = transitionFromScene
            val toScene = currentScene
            if (transition == null || fromScene == null || toScene == null) {
                renderFrame()
                return
            }
            transitionElapsed += deltaTime
            if (transitionElapsed >= transition.duration) {
                transitionSpec = null
                transitionFromScene = null
                renderFrame()
                return
            }
            val progress = (transitionElapsed / transition.duration).toFloat()
            for (layer in transitionLayers(transition, progress, viewWidth, viewHeight)) {
                sceneRenderer.draw(
                    scene = if (layer.useToScene) toScene else fromScene,
                    viewWidth = viewWidth,
                    viewHeight = viewHeight,
                    resourceRegistry = resourceRegistry,
                    viewportOffset = layer.viewportOffset,
                    viewportSize = layer.viewportSize,
                    globalAlpha = layer.alpha,
                    clearFirst = layer.clearFirst,
                    outerClip = splitClip(layer.split, fromScene),
                    allowsTransparency = renderTransparently,
                )
            }
        }

        /**
         * The scene-space rect [SKTransitionSplit.Left]/[SKTransitionSplit.Right] refers to, in
         * [fromScene]'s own reference space -- `null` for [SKTransitionSplit.None].
         */
        private fun splitClip(
            split: SKTransitionSplit,
            fromScene: SKScene,
        ): Rect? {
            if (split == SKTransitionSplit.None) return null
            val projection =
                computeSceneProjection(
                    fromScene.size,
                    fromScene.anchorPoint,
                    fromScene.scaleMode,
                    viewWidth,
                    viewHeight,
                    hasCamera = fromScene.camera != null,
                )
            val midX = (projection.left + projection.right) / 2f
            return when (split) {
                SKTransitionSplit.Left -> Rect(projection.left, projection.bottom, midX, projection.top)
                SKTransitionSplit.Right -> Rect(midX, projection.bottom, projection.right, projection.top)
                SKTransitionSplit.None -> null
            }
        }

        private inner class SceneRenderer : Renderer {
            private val clock = SKFrameClock()

            override fun onSurfaceCreated(
                gl: GL10,
                config: EGLConfig,
            ) {
                clock.reset()
                sceneRenderer.onSurfaceCreated()
                resourceRegistry.reloadAll()
            }

            override fun onSurfaceChanged(
                gl: GL10,
                width: Int,
                height: Int,
            ) {
                viewWidth = width
                viewHeight = height
                density = resources.displayMetrics.density
                GLES20.glViewport(0, 0, width, height)
                currentScene?.let { syncResizeFillSize(it) }
            }

            override fun onDrawFrame(gl: GL10) {
                val deltaTime = clock.tick(System.nanoTime())
                val scene = currentScene
                // Also covers a scene whose scaleMode was switched to `.resizeFill` after presenting.
                scene?.let { syncResizeFillSize(it) }
                if (scene != null && !isPaused && !scene.isPaused) {
                    scene.dispatchUpdate(deltaTime)
                    scene.stepActions(deltaTime)
                    scene.dispatchDidEvaluateActions()
                    simulatePhysics(scene, deltaTime)
                    scene.dispatchDidSimulatePhysics()
                    scene.applyConstraints()
                    scene.dispatchDidApplyConstraints()
                    stepEmitters(scene, deltaTime)
                    stepTileMaps(scene, deltaTime)
                    stepAudioNodes(scene)
                    renderTransitionFrame(deltaTime)
                    scene.dispatchDidFinishUpdate()
                } else {
                    renderTransitionFrame(deltaTime)
                }
            }
        }

        private companion object {
            /** Matches `GLSurfaceView`'s default config chooser (the renderer itself uses no depth test). */
            const val DEPTH_BITS = 16

            const val MILLIS_PER_SECOND = 1000.0
        }
    }
