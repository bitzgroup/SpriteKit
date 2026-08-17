package jp.co.bitz.spritekit

import android.view.MotionEvent

/**
 * The phase of a touch, mirroring the `UITouch.Phase`/`NSTouch.Phase` cases SpriteKit's
 * `touchesBegan`/`touchesMoved`/`touchesEnded`/`touchesCancelled` callbacks correspond to.
 */
public enum class SKTouchPhase {
    Began,
    Moved,
    Ended,
    Cancelled,
}

/**
 * An immutable snapshot of a single pointer's touch state, taken on the UI thread from a raw
 * [MotionEvent] and handed to [SKView]'s render thread via `runOnGLThread`. See
 * `docs/ARCHITECTURE.md`'s "Touch event routing" section.
 *
 * [x]/[y] are in the view's own coordinate space (pixels, origin top-left, y-down) — converting
 * to the scene's y-up coordinate space requires the scene's size/scale, which isn't dispatched
 * until Phase 10 wires this snapshot up to `SKNode`/`SKScene` touch handling.
 */
public data class SKTouchEvent(
    public val pointerId: Int,
    public val x: Float,
    public val y: Float,
    public val phase: SKTouchPhase,
)

/**
 * Maps a raw Android touch action constant (`MotionEvent.getActionMasked()`) to [SKTouchPhase],
 * or `null` for actions this library doesn't dispatch as a touch phase (e.g. hover, scroll).
 *
 * Kept separate from [MotionEvent] itself so the mapping is unit-testable without an Android
 * runtime — see `SKTouchEventTest`.
 */
public fun skTouchPhaseForAction(actionMasked: Int): SKTouchPhase? =
    when (actionMasked) {
        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> SKTouchPhase.Began
        MotionEvent.ACTION_MOVE -> SKTouchPhase.Moved
        MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> SKTouchPhase.Ended
        MotionEvent.ACTION_CANCEL -> SKTouchPhase.Cancelled
        else -> null
    }
