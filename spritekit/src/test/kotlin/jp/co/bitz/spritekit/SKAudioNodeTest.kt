package jp.co.bitz.spritekit

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SKAudioNodeTest {
    /** A fake [SKAudioPlaybackHandle], so this whole suite runs without touching `android.media.MediaPlayer`. */
    private class FakeHandle(val path: String, val releaseOnCompletion: Boolean) : SKAudioPlaybackHandle {
        override var isPlaying: Boolean = false

        // Named recordedXxx, not volume/playbackRate/looping -- those names would generate a
        // synthetic setVolume/setPlaybackRate/setLooping accessor colliding with the interface
        // methods of the same JVM signature just below (a "platform declaration clash").
        var recordedVolume: Float = 1f
        var recordedPlaybackRate: Float = 1f
        var recordedLooping: Boolean = false
        var playCount: Int = 0
        var pauseCount: Int = 0
        var stopCount: Int = 0

        override fun play() {
            isPlaying = true
            playCount++
        }

        override fun pause() {
            isPlaying = false
            pauseCount++
        }

        override fun stop() {
            isPlaying = false
            stopCount++
        }

        override fun setVolume(volume: Float) {
            recordedVolume = volume
        }

        override fun setPlaybackRate(rate: Float) {
            recordedPlaybackRate = rate
        }

        override fun setLooping(looping: Boolean) {
            recordedLooping = looping
        }
    }

    private lateinit var handles: MutableList<FakeHandle>

    @BeforeTest
    fun installFakeFactory() {
        handles = mutableListOf()
        audioPlaybackFactory =
            SKAudioPlaybackFactory { path, releaseOnCompletion ->
                FakeHandle(path, releaseOnCompletion).also { handles += it }
            }
    }

    @AfterTest
    fun restoreDefaultFactory() {
        // A harmless fake, not the error-throwing kind -- so leftover global state can't crash
        // some unrelated later test if this class's teardown order ever changes.
        audioPlaybackFactory =
            SKAudioPlaybackFactory {
                    path,
                    releaseOnCompletion,
                ->
                FakeHandle(path, releaseOnCompletion)
            }
    }

    @Test
    fun `play creates a handle on first use and starts it`() {
        val node = SKAudioNode("song.mp3")

        node.play()

        assertEquals(1, handles.size)
        assertEquals("song.mp3", handles.single().path)
        assertEquals(1, handles.single().playCount)
        assertTrue(node.isPlaying)
    }

    @Test
    fun `a persistent SKAudioNode's handle is created without releaseOnCompletion`() {
        val node = SKAudioNode("song.mp3")

        node.play()

        assertFalse(handles.single().releaseOnCompletion)
    }

    @Test
    fun `play reuses the same handle across multiple calls`() {
        val node = SKAudioNode("song.mp3")

        node.play()
        node.play()

        assertEquals(1, handles.size)
        assertEquals(2, handles.single().playCount)
    }

    @Test
    fun `pause and stop are no-ops before play is ever called`() {
        val node = SKAudioNode("song.mp3")

        node.pause()
        node.stop()

        assertTrue(handles.isEmpty())
    }

    @Test
    fun `pause and stop affect the existing handle once created`() {
        val node = SKAudioNode("song.mp3")
        node.play()

        node.pause()
        node.stop()

        assertEquals(1, handles.single().pauseCount)
        assertEquals(1, handles.single().stopCount)
        assertFalse(node.isPlaying)
    }

    @Test
    fun `volume and playbackRate default to 1 and are clamped when set`() {
        val node = SKAudioNode("song.mp3")

        assertEquals(1f, node.volume)
        assertEquals(1f, node.playbackRate)

        node.volume = 2f
        node.playbackRate = -1f

        assertEquals(1f, node.volume)
        assertEquals(0f, node.playbackRate)
    }

    @Test
    fun `setting volume and playbackRate before playing is applied once the handle is created`() {
        val node =
            SKAudioNode("song.mp3").apply {
                volume = 0.5f
                playbackRate = 2f
            }

        node.play()

        assertEquals(0.5f, handles.single().recordedVolume)
        assertEquals(2f, handles.single().recordedPlaybackRate)
    }

    @Test
    fun `setting volume after playing pushes it to the live handle immediately`() {
        val node = SKAudioNode("song.mp3")
        node.play()

        node.volume = 0.25f

        assertEquals(0.25f, handles.single().recordedVolume)
    }

    @Test
    fun `autoplayLooped is passed to the handle as looping`() {
        val node = SKAudioNode("song.mp3").apply { autoplayLooped = false }

        node.play()

        assertFalse(handles.single().recordedLooping)
    }

    @Test
    fun `isPlaying reflects the underlying handle`() {
        val node = SKAudioNode("song.mp3")
        assertFalse(node.isPlaying)

        node.play()
        assertTrue(node.isPlaying)

        node.pause()
        assertFalse(node.isPlaying)
    }

    @Test
    fun `stepAudioNodes auto-plays a node with autoplayLooped exactly once`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val node = SKAudioNode("song.mp3")
        scene.addChild(node)

        stepAudioNodes(scene)
        stepAudioNodes(scene)

        assertEquals(1, handles.single().playCount)
    }

    @Test
    fun `stepAudioNodes doesn't replay a node after it's been manually stopped`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val node = SKAudioNode("song.mp3")
        scene.addChild(node)
        stepAudioNodes(scene)
        node.stop()

        stepAudioNodes(scene)

        assertEquals(1, handles.single().playCount)
    }

    @Test
    fun `stepAudioNodes doesn't play a node with autoplayLooped set to false`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val node = SKAudioNode("song.mp3").apply { autoplayLooped = false }
        scene.addChild(node)

        stepAudioNodes(scene)

        assertTrue(handles.isEmpty())
    }

    @Test
    fun `stepAudioNodes skips a node under an isPaused ancestor`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val layer = SKNode().apply { isPaused = true }
        scene.addChild(layer)
        layer.addChild(SKAudioNode("song.mp3"))

        stepAudioNodes(scene)

        assertTrue(handles.isEmpty())
    }

    @Test
    fun `an SKAction play run on an SKAudioNode starts it`() {
        val node = SKAudioNode("song.mp3")
        val state = SKActionState(SKAction.play())

        stepAction(state, node, 1.seconds)

        assertEquals(1, handles.single().playCount)
    }

    @Test
    fun `SKAction play, pause, and stop are no-ops on a plain SKNode`() {
        val node = SKNode()

        stepAction(SKActionState(SKAction.play()), node, 1.seconds)
        stepAction(SKActionState(SKAction.pause()), node, 1.seconds)
        stepAction(SKActionState(SKAction.stop()), node, 1.seconds)

        assertTrue(handles.isEmpty())
    }

    @Test
    fun `SKAction changeVolume interpolates SKAudioNode volume over its duration`() {
        val node = SKAudioNode("song.mp3").apply { volume = 0f }
        val state = SKActionState(SKAction.changeVolume(to = 1f, duration = 2.seconds))

        stepAction(state, node, 1.seconds)

        assertEquals(0.5f, node.volume)
    }

    @Test
    fun `SKAction changePlaybackRate interpolates SKAudioNode playbackRate over its duration`() {
        val node = SKAudioNode("song.mp3").apply { playbackRate = 1f }
        val state = SKActionState(SKAction.changePlaybackRate(to = 2f, duration = 2.seconds))

        stepAction(state, node, 1.seconds)

        assertEquals(1.5f, node.playbackRate)
    }

    @Test
    fun `playSoundFileNamed with waitForCompletion false finishes immediately`() {
        val node = SKNode()
        val state = SKActionState(SKAction.playSoundFileNamed("boom.mp3", waitForCompletion = false))

        val leftover = stepAction(state, node, 1.seconds)

        assertEquals(1.seconds, leftover)
        assertEquals(1, handles.single().playCount)
        assertTrue(handles.single().releaseOnCompletion)
    }

    @Test
    fun `playSoundFileNamed with waitForCompletion true keeps running until playback stops`() {
        val node = SKNode()
        val state = SKActionState(SKAction.playSoundFileNamed("boom.mp3", waitForCompletion = true))

        val stillRunning = stepAction(state, node, 1.seconds)
        assertEquals(null, stillRunning)

        handles.single().isPlaying = false
        val finished = stepAction(state, node, 1.seconds)

        assertEquals(1.seconds, finished)
    }

    @Test
    fun `playSoundFileNamed starts only one handle even across multiple steps`() {
        val node = SKNode()
        val state = SKActionState(SKAction.playSoundFileNamed("boom.mp3", waitForCompletion = true))

        stepAction(state, node, 1.seconds)
        stepAction(state, node, 1.seconds)

        assertEquals(1, handles.size)
    }
}
