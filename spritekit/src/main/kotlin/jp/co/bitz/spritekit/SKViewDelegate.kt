package jp.co.bitz.spritekit

/**
 * Decides, frame by frame, whether an [SKView] runs its scene — Apple's `SKViewDelegate`. Set it as
 * [SKView.delegate].
 */
public fun interface SKViewDelegate {
    /**
     * Apple's `view(_:shouldRenderAtTime:)`: called on the render thread at the start of each frame
     * of an un-paused [view], before the scene's `update`, with the frame's [time] in seconds on a
     * monotonic clock. Returning `false` skips the frame: as on Apple (verified on iOS/tvOS
     * simulators), the scene gets no `update`, actions, physics or other per-frame callbacks, and
     * the time a skipped frame would have taken isn't lost — the next frame that runs advances by
     * all of it. The last frame stays on screen meanwhile. Not called while [SKView.isPaused].
     */
    public fun shouldRenderAtTime(
        view: SKView,
        time: Double,
    ): Boolean
}

/** What [SKView] does with a frame. Not part of the public API. */
internal enum class SKFrameStep {
    /** Advances the clock and runs the scene's frame (which does nothing more while paused). */
    Run,

    /** Draws the scene as it is, leaving the clock where it was so the next frame catches up. */
    Hold,
}

/**
 * The [SKFrameStep] for a frame: [SKFrameStep.Hold] only when the view isn't paused and
 * [shouldRender] — the [SKView.delegate]'s answer, `null` without a delegate — says not to render.
 */
internal fun skFrameStep(
    viewPaused: Boolean,
    shouldRender: (() -> Boolean)?,
): SKFrameStep = if (!viewPaused && shouldRender != null && !shouldRender()) SKFrameStep.Hold else SKFrameStep.Run
