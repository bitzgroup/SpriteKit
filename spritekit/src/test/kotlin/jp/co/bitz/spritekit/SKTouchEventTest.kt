package jp.co.bitz.spritekit

import android.view.MotionEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SKTouchEventTest {
    @Test
    fun `maps down actions to Began`() {
        assertEquals(SKTouchPhase.Began, skTouchPhaseForAction(MotionEvent.ACTION_DOWN))
        assertEquals(SKTouchPhase.Began, skTouchPhaseForAction(MotionEvent.ACTION_POINTER_DOWN))
    }

    @Test
    fun `maps move action to Moved`() {
        assertEquals(SKTouchPhase.Moved, skTouchPhaseForAction(MotionEvent.ACTION_MOVE))
    }

    @Test
    fun `maps up actions to Ended`() {
        assertEquals(SKTouchPhase.Ended, skTouchPhaseForAction(MotionEvent.ACTION_UP))
        assertEquals(SKTouchPhase.Ended, skTouchPhaseForAction(MotionEvent.ACTION_POINTER_UP))
    }

    @Test
    fun `maps cancel action to Cancelled`() {
        assertEquals(SKTouchPhase.Cancelled, skTouchPhaseForAction(MotionEvent.ACTION_CANCEL))
    }

    @Test
    fun `maps unrecognized actions to null`() {
        assertNull(skTouchPhaseForAction(MotionEvent.ACTION_OUTSIDE))
        assertNull(skTouchPhaseForAction(MotionEvent.ACTION_HOVER_MOVE))
    }
}
