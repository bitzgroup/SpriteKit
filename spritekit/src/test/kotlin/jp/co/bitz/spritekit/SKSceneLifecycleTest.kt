package jp.co.bitz.spritekit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * `sceneDidLoad()` and `SKSceneDelegate`, matching what the sample app's `SpriteKitParity` probes
 * (`SKSceneLifecycleParityTests`) recorded on iOS/tvOS simulators.
 */
class SKSceneLifecycleTest {
    private class LoggingScene(size: Vector2) : SKScene(size) {
        val log = mutableListOf<String>()
        var sizeAtLoad: Vector2? = null

        override fun sceneDidLoad() {
            log += "sceneDidLoad"
            sizeAtLoad = size
        }

        override fun update(deltaTime: Duration) {
            log += "update"
        }

        override fun didEvaluateActions() {
            log += "didEvaluateActions"
        }

        override fun didSimulatePhysics() {
            log += "didSimulatePhysics"
        }

        override fun didApplyConstraints() {
            log += "didApplyConstraints"
        }

        override fun didFinishUpdate() {
            log += "didFinishUpdate"
        }
    }

    private class LoggingDelegate(val log: MutableList<String>) : SKSceneDelegate {
        val deltas = mutableListOf<Duration>()
        val scenes = mutableListOf<SKScene>()

        override fun update(
            deltaTime: Duration,
            scene: SKScene,
        ) {
            log += "delegate.update"
            deltas += deltaTime
            scenes += scene
        }

        override fun didEvaluateActions(scene: SKScene) {
            log += "delegate.didEvaluateActions"
        }

        override fun didSimulatePhysics(scene: SKScene) {
            log += "delegate.didSimulatePhysics"
        }

        override fun didApplyConstraints(scene: SKScene) {
            log += "delegate.didApplyConstraints"
        }

        override fun didFinishUpdate(scene: SKScene) {
            log += "delegate.didFinishUpdate"
        }
    }

    /** One frame's callbacks, in `SKView`'s render-loop order. */
    private fun SKScene.runFrameCallbacks(deltaTime: Duration = 16.milliseconds) {
        dispatchUpdate(deltaTime)
        dispatchDidEvaluateActions()
        dispatchDidSimulatePhysics()
        dispatchDidApplyConstraints()
        dispatchDidFinishUpdate()
    }

    private val sceneOrder =
        listOf("update", "didEvaluateActions", "didSimulatePhysics", "didApplyConstraints", "didFinishUpdate")

    @Test
    fun `constructing a scene doesn't call sceneDidLoad yet`() {
        val scene = LoggingScene(Vector2(10f, 10f))

        assertEquals(emptyList(), scene.log)
    }

    @Test
    fun `sceneDidLoad runs once, with the size already set`() {
        val scene = LoggingScene(Vector2(123f, 45f))

        scene.loadIfNeeded()
        scene.loadIfNeeded()

        assertEquals(listOf("sceneDidLoad"), scene.log)
        assertEquals(Vector2(123f, 45f), scene.sizeAtLoad)
    }

    @Test
    fun `resizing a loaded scene doesn't call sceneDidLoad again`() {
        val scene = LoggingScene(Vector2(10f, 10f))
        scene.loadIfNeeded()

        scene.size = Vector2(20f, 20f)
        scene.loadIfNeeded()

        assertEquals(listOf("sceneDidLoad"), scene.log)
    }

    @Test
    fun `without a delegate the scene's own callbacks run in order`() {
        val scene = LoggingScene(Vector2(10f, 10f))

        scene.runFrameCallbacks()

        assertEquals(sceneOrder, scene.log)
    }

    @Test
    fun `a delegate replaces the scene's own frame callbacks`() {
        val scene = LoggingScene(Vector2(10f, 10f))
        val delegate = LoggingDelegate(scene.log)
        scene.delegate = delegate

        scene.runFrameCallbacks(20.milliseconds)

        assertEquals(
            listOf(
                "delegate.update",
                "delegate.didEvaluateActions",
                "delegate.didSimulatePhysics",
                "delegate.didApplyConstraints",
                "delegate.didFinishUpdate",
            ),
            scene.log,
        )
        assertEquals(listOf<SKScene>(scene), delegate.scenes)
        assertEquals(listOf(20.milliseconds), delegate.deltas)
    }

    @Test
    fun `a delegate replaces only the callbacks it overrides`() {
        val scene = LoggingScene(Vector2(10f, 10f))
        scene.delegate =
            object : SKSceneDelegate {
                override fun update(
                    deltaTime: Duration,
                    scene: SKScene,
                ) {
                    (scene as LoggingScene).log += "delegate.update"
                }
            }

        scene.runFrameCallbacks()

        assertEquals(listOf("delegate.update") + sceneOrder.drop(1), scene.log)
    }

    @Test
    fun `clearing the delegate restores the scene's own callbacks`() {
        val scene = LoggingScene(Vector2(10f, 10f))
        scene.delegate = LoggingDelegate(scene.log)
        scene.runFrameCallbacks()
        scene.delegate = null
        scene.log.clear()

        scene.runFrameCallbacks()

        assertEquals(sceneOrder, scene.log)
    }
}
