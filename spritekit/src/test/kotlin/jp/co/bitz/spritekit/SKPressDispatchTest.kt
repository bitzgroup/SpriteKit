package jp.co.bitz.spritekit

import android.view.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class SKPressDispatchTest {
    private class RecordingScene : SKScene(size = Vector2(100f, 100f)) {
        val log = mutableListOf<String>()
        val presses = mutableListOf<SKPress>()
        val heldCounts = mutableListOf<Int>()

        private fun record(
            phase: String,
            presses: Set<SKPress>,
            event: SKPressesEvent?,
        ) {
            presses.forEach {
                log += "$phase:${it.type}"
                this.presses += it
            }
            heldCounts += event?.allPresses?.size ?: -1
        }

        override fun pressesBegan(
            presses: Set<SKPress>,
            event: SKPressesEvent?,
        ) = record("began", presses, event)

        override fun pressesEnded(
            presses: Set<SKPress>,
            event: SKPressesEvent?,
        ) = record("ended", presses, event)

        override fun pressesCancelled(
            presses: Set<SKPress>,
            event: SKPressesEvent?,
        ) = record("cancelled", presses, event)
    }

    private val select = KeyEvent.KEYCODE_DPAD_CENTER
    private val up = KeyEvent.KEYCODE_DPAD_UP

    @Test
    fun `maps remote and controller keys to Apple press types`() {
        assertEquals(SKPressType.UpArrow, skPressTypeForKeyCode(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(SKPressType.DownArrow, skPressTypeForKeyCode(KeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals(SKPressType.LeftArrow, skPressTypeForKeyCode(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(SKPressType.RightArrow, skPressTypeForKeyCode(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(SKPressType.Select, skPressTypeForKeyCode(KeyEvent.KEYCODE_DPAD_CENTER))
        assertEquals(SKPressType.Select, skPressTypeForKeyCode(KeyEvent.KEYCODE_ENTER))
        assertEquals(SKPressType.Select, skPressTypeForKeyCode(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(SKPressType.Menu, skPressTypeForKeyCode(KeyEvent.KEYCODE_BACK))
        assertEquals(SKPressType.PlayPause, skPressTypeForKeyCode(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        assertNull(skPressTypeForKeyCode(KeyEvent.KEYCODE_A))
    }

    @Test
    fun `a press is one began then one ended on the same SKPress`() {
        val scene = RecordingScene()
        val dispatcher = SKPressDispatcher()
        dispatcher.dispatch(scene, SKKeyTransition(select, SKPressType.Select, isDown = true))
        dispatcher.dispatch(scene, SKKeyTransition(select, SKPressType.Select, isDown = false))

        assertEquals(listOf("began:Select", "ended:Select"), scene.log)
        assertSame(scene.presses[0], scene.presses[1])
        assertEquals(SKPressPhase.Ended, scene.presses[1].phase)
    }

    @Test
    fun `a second key-down for a held key is not delivered`() {
        val scene = RecordingScene()
        val dispatcher = SKPressDispatcher()
        dispatcher.dispatch(scene, SKKeyTransition(up, SKPressType.UpArrow, isDown = true))
        dispatcher.dispatch(scene, SKKeyTransition(up, SKPressType.UpArrow, isDown = true))
        dispatcher.dispatch(scene, SKKeyTransition(up, SKPressType.UpArrow, isDown = false))

        assertEquals(listOf("began:UpArrow", "ended:UpArrow"), scene.log)
    }

    @Test
    fun `a key-up never seen going down is ignored`() {
        val scene = RecordingScene()
        SKPressDispatcher().dispatch(scene, SKKeyTransition(up, SKPressType.UpArrow, isDown = false))

        assertEquals(emptyList(), scene.log)
    }

    @Test
    fun `a cancelled key-up is delivered as cancelled`() {
        val scene = RecordingScene()
        val dispatcher = SKPressDispatcher()
        dispatcher.dispatch(scene, SKKeyTransition(select, SKPressType.Select, isDown = true))
        dispatcher.dispatch(scene, SKKeyTransition(select, SKPressType.Select, isDown = false, isCanceled = true))

        assertEquals(listOf("began:Select", "cancelled:Select"), scene.log)
        assertEquals(SKPressPhase.Cancelled, scene.presses[1].phase)
    }

    @Test
    fun `allPresses holds every held button including the delivered one`() {
        val scene = RecordingScene()
        val dispatcher = SKPressDispatcher()
        dispatcher.dispatch(scene, SKKeyTransition(up, SKPressType.UpArrow, isDown = true))
        dispatcher.dispatch(scene, SKKeyTransition(select, SKPressType.Select, isDown = true))
        dispatcher.dispatch(scene, SKKeyTransition(select, SKPressType.Select, isDown = false))
        dispatcher.dispatch(scene, SKKeyTransition(up, SKPressType.UpArrow, isDown = false))

        assertEquals(listOf(1, 2, 2, 1), scene.heldCounts)
    }

    @Test
    fun `presses arrive at whichever scene is presented when the key changes`() {
        val first = RecordingScene()
        val second = RecordingScene()
        val dispatcher = SKPressDispatcher()
        dispatcher.dispatch(first, SKKeyTransition(select, SKPressType.Select, isDown = true))
        dispatcher.dispatch(second, SKKeyTransition(select, SKPressType.Select, isDown = false))

        assertEquals(listOf("began:Select"), first.log)
        assertEquals(listOf("ended:Select"), second.log)
    }
}
