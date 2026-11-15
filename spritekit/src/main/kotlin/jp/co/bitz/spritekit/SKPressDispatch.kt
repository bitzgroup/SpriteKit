package jp.co.bitz.spritekit

/**
 * A key transition snapshotted on the UI thread from a raw `KeyEvent` and handed to the render
 * thread by [SKView.forwardKeyEvent]. [isDown] is `true` for the initial key-down (repeats are
 * never snapshotted); [isCanceled] marks a key-up the system cancelled.
 */
internal data class SKKeyTransition(
    val keyCode: Int,
    val type: SKPressType,
    val isDown: Boolean,
    val isCanceled: Boolean = false,
)

/**
 * Turns [SKKeyTransition]s into Apple-shaped `presses*` calls on a scene: one [SKPress] per held
 * key, delivered as [SKPressPhase.Began] on key-down and as [SKPressPhase.Ended] (or
 * [SKPressPhase.Cancelled]) on key-up. A key-down for a key already held, or a key-up for one never
 * seen going down, is ignored. Render-thread-confined, owned by [SKView].
 */
internal class SKPressDispatcher {
    private val held = linkedMapOf<Int, SKPress>()

    fun dispatch(
        scene: SKScene?,
        transition: SKKeyTransition,
    ) {
        val press =
            if (transition.isDown) {
                if (transition.keyCode in held) return
                SKPress(transition.type, transition.keyCode).also { held[transition.keyCode] = it }
            } else {
                held[transition.keyCode] ?: return
            }
        press.phase =
            when {
                transition.isDown -> SKPressPhase.Began
                transition.isCanceled -> SKPressPhase.Cancelled
                else -> SKPressPhase.Ended
            }
        val event = SKPressesEvent(held.values.toSet())
        if (!transition.isDown) held.remove(transition.keyCode)
        val presses = setOf(press)
        when (press.phase) {
            SKPressPhase.Began -> scene?.pressesBegan(presses, event)
            SKPressPhase.Changed -> scene?.pressesChanged(presses, event)
            SKPressPhase.Ended -> scene?.pressesEnded(presses, event)
            SKPressPhase.Cancelled -> scene?.pressesCancelled(presses, event)
        }
    }
}
