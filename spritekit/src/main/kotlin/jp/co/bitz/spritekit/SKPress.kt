package jp.co.bitz.spritekit

import android.view.KeyEvent

/**
 * Which button a [SKPress] is for — this library's stand-in for the Siri Remote/game-controller
 * cases of Apple's `UIPress.PressType`. Android key codes map onto these via [skPressTypeForKeyCode].
 */
public enum class SKPressType {
    UpArrow,
    DownArrow,
    LeftArrow,
    RightArrow,
    Select,
    Menu,
    PlayPause,
}

/**
 * The phase of a press, mirroring the `UIPress.Phase` cases SpriteKit's `pressesBegan`/
 * `pressesChanged`/`pressesEnded`/`pressesCancelled` callbacks correspond to. There is no
 * `.stationary` phase: like Apple's, a held button is reported once as [Began] and once as [Ended].
 */
public enum class SKPressPhase {
    Began,
    Changed,
    Ended,
    Cancelled,
}

/**
 * One physical button press — this library's stand-in for Apple's `UIPress`. Delivered to the
 * presented scene via [SKNode.pressesBegan]/[SKNode.pressesChanged]/[SKNode.pressesEnded]/
 * [SKNode.pressesCancelled] when the host forwards key events with [SKView.forwardKeyEvent].
 *
 * Like `UIPress`, one instance persists from the button going down until it is released or
 * cancelled, so it can be compared by identity or kept in a `Set` across callbacks.
 */
public class SKPress internal constructor(
    /** The button this press is for — Apple's `type`. */
    public val type: SKPressType,
    /** The Android key code (`KeyEvent.getKeyCode`) this press tracks. */
    public val keyCode: Int,
) {
    /** The phase this press was in when it was last delivered — Apple's `phase`. */
    public var phase: SKPressPhase = SKPressPhase.Began
        internal set
}

/**
 * The event a batch of presses was delivered as — this library's stand-in for Apple's
 * `UIPressesEvent`, passed as the `event` argument of [SKNode.pressesBegan] and its siblings.
 */
public class SKPressesEvent internal constructor(
    /** Every press currently held, including the ones in this delivery — Apple's `allPresses`. */
    public val allPresses: Set<SKPress>,
)

/**
 * Maps an Android key code to the [SKPressType] a tvOS remote or game controller would report for
 * the same button, or `null` for keys that have no `UIPress` counterpart (they are not forwarded).
 *
 * D-pad keys map to the arrows; D-pad center, Enter and gamepad A to [SKPressType.Select]; Back and
 * Escape to [SKPressType.Menu] (the Siri Remote's Menu/Back button); media play/pause to
 * [SKPressType.PlayPause].
 */
public fun skPressTypeForKeyCode(keyCode: Int): SKPressType? =
    when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> SKPressType.UpArrow
        KeyEvent.KEYCODE_DPAD_DOWN -> SKPressType.DownArrow
        KeyEvent.KEYCODE_DPAD_LEFT -> SKPressType.LeftArrow
        KeyEvent.KEYCODE_DPAD_RIGHT -> SKPressType.RightArrow
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_BUTTON_A,
        -> SKPressType.Select
        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> SKPressType.Menu
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> SKPressType.PlayPause
        else -> null
    }
