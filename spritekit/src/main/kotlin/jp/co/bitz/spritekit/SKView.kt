package jp.co.bitz.spritekit

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

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
         * [onTouchEvent]. `null` (the default) drops touch input on the floor; Phase 10 replaces
         * this with real `SKNode`/`SKScene` touch dispatch.
         */
        public var onTouch: ((SKTouchEvent) -> Unit)? = null

        private var currentScene: SKScene? = null
        private val sceneRenderer = SKSceneRenderer()
        private var viewWidth = 0
        private var viewHeight = 0

        init {
            setEGLContextClientVersion(2)
            setRenderer(SceneRenderer())
            renderMode = RENDERMODE_CONTINUOUSLY
        }

        /**
         * Presents [scene], replacing whatever scene was previously presented. Takes effect on
         * the render thread before the next frame — see [runOnGLThread].
         */
        public fun presentScene(scene: SKScene) {
            runOnGLThread { currentScene = scene }
        }

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

        override fun onPause() {
            runOnGLThread { currentScene?.isPaused = true }
            super.onPause()
        }

        override fun onResume() {
            super.onResume()
            runOnGLThread { currentScene?.isPaused = false }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val phase = skTouchPhaseForAction(event.actionMasked)
            if (phase != null) {
                val index = event.actionIndex
                val snapshot =
                    SKTouchEvent(
                        pointerId = event.getPointerId(index),
                        x = event.getX(index),
                        y = event.getY(index),
                        phase = phase,
                    )
                runOnGLThread { onTouch?.invoke(snapshot) }
            }
            return true
        }

        /**
         * Renders the current scene's state via [sceneRenderer] — clears to
         * [SKScene.backgroundColor] and draws its sprites (Phase 3). Falls back to a plain black
         * clear when no scene is presented yet.
         */
        private fun renderFrame() {
            val scene = currentScene
            if (scene != null) {
                sceneRenderer.draw(scene, viewWidth, viewHeight, resourceRegistry)
            } else {
                GLES20.glClearColor(0f, 0f, 0f, 1f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
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
                GLES20.glViewport(0, 0, width, height)
            }

            override fun onDrawFrame(gl: GL10) {
                val deltaTime = clock.tick(System.nanoTime())
                val scene = currentScene
                if (scene != null && !scene.isPaused) {
                    scene.update(deltaTime)
                    scene.stepActions(deltaTime)
                    scene.didEvaluateActions()
                    simulatePhysics(scene, deltaTime)
                    scene.didSimulatePhysics()
                    scene.applyConstraints()
                    scene.didApplyConstraints()
                    stepEmitters(scene, deltaTime)
                    renderFrame()
                    scene.didFinishUpdate()
                } else {
                    renderFrame()
                }
            }
        }
    }
