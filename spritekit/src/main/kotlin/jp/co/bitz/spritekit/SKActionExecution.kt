package jp.co.bitz.spritekit

import kotlin.math.PI
import kotlin.time.Duration

/**
 * Per-run state for a currently-executing [SKAction] (or a nested sub-action within a
 * sequence/group/repeat). Not part of the public API.
 */
internal class SKActionState(val action: SKAction) {
    var elapsed: Duration = Duration.ZERO
    var finished: Boolean = false
    private var captured = false
    private var capturedValue: Any? = null
    var childIndex: Int = 0
    var childState: SKActionState? = null
    var childStates: List<SKActionState>? = null
    var repeatCount: Int = 0

    /** Returns the value captured on the first call for this state; ignores [current] on later calls. */
    @Suppress("UNCHECKED_CAST")
    fun <T> captureOnce(current: () -> T): T {
        if (!captured) {
            capturedValue = current()
            captured = true
        }
        return capturedValue as T
    }
}

/** A currently-running action on a specific [SKNode] — pairs its [SKActionState] with an optional key/completion. */
internal class SKRunningAction(
    val key: String?,
    val state: SKActionState,
    val completion: (() -> Unit)?,
)

/**
 * Advances [state] (wrapping an [SKAction]) applied to [node] by [availableTime]. Returns `null`
 * if the action is still running (all of [availableTime] was needed), or the leftover time not
 * needed to finish it (`>= Duration.ZERO`) once it completes — consumed by whatever runs next in
 * an enclosing [SKAction.sequence], so a fast frame doesn't lose time between actions.
 *
 * Pure Kotlin — no OpenGL/Android dependency — so this entire per-frame action interpreter is
 * unit-testable independent of a live GL context.
 */
internal fun stepAction(
    state: SKActionState,
    node: SKNode,
    availableTime: Duration,
): Duration? {
    if (state.finished) return availableTime
    val scaledTime = availableTime * state.action.speed.toDouble()
    val leftover =
        when (val kind = state.action.kind) {
            is SKActionKind.Sequence -> stepSequence(state, kind, node, scaledTime)
            is SKActionKind.Group -> stepGroup(state, kind, node, scaledTime)
            is SKActionKind.Repeat -> stepRepeat(state, kind, node, scaledTime)
            else -> stepLeaf(state, node, scaledTime)
        }
    if (leftover != null) state.finished = true
    return leftover
}

private fun stepSequence(
    state: SKActionState,
    kind: SKActionKind.Sequence,
    node: SKNode,
    availableTime: Duration,
): Duration? {
    var remaining = availableTime
    while (state.childIndex < kind.actions.size) {
        val childState =
            state.childState ?: SKActionState(kind.actions[state.childIndex]).also { state.childState = it }
        val leftover = stepAction(childState, node, remaining) ?: return null
        remaining = leftover
        state.childIndex++
        state.childState = null
    }
    return remaining
}

private fun stepGroup(
    state: SKActionState,
    kind: SKActionKind.Group,
    node: SKNode,
    availableTime: Duration,
): Duration? {
    val childStates = state.childStates ?: kind.actions.map { SKActionState(it) }.also { state.childStates = it }
    var allFinished = true
    for (childState in childStates) {
        if (childState.finished) continue
        if (stepAction(childState, node, availableTime) == null) allFinished = false
    }
    // Children can finish at different points within `availableTime`; this library doesn't track
    // each one's exact leftover, so a group inside a sequence always consumes its full time
    // budget once every child is done, rather than passing an exact remainder onward.
    return if (allFinished) Duration.ZERO else null
}

private fun stepRepeat(
    state: SKActionState,
    kind: SKActionKind.Repeat,
    node: SKNode,
    availableTime: Duration,
): Duration? {
    var remaining = availableTime
    var iterations = 0
    var stillRunning = false
    var active = true

    fun hasMoreIterations() = kind.count == null || state.repeatCount < kind.count
    while (active && hasMoreIterations() && iterations < MAX_REPEAT_ITERATIONS_PER_STEP) {
        iterations++
        val childState = state.childState ?: SKActionState(kind.action).also { state.childState = it }
        val leftover = stepAction(childState, node, remaining)
        if (leftover == null) {
            stillRunning = true
            active = false
        } else {
            remaining = leftover
            state.repeatCount++
            state.childState = null
            if (remaining <= Duration.ZERO) {
                // No time left this frame to start another iteration -- not finished unless that was the last one.
                stillRunning = hasMoreIterations()
                active = false
            }
        }
    }
    val finished = !stillRunning && kind.count != null && state.repeatCount >= kind.count
    return if (finished) remaining else null
}

// Early returns cover PlaySoundFileNamed's special (non-duration-based) path and the
// zero-duration instant-effect case, ahead of the normal progress-based path below.
@Suppress("ReturnCount")
private fun stepLeaf(
    state: SKActionState,
    node: SKNode,
    availableTime: Duration,
): Duration? {
    val action = state.action
    val kind = action.kind
    if (kind is SKActionKind.PlaySoundFileNamed) return stepPlaySound(state, kind, availableTime)
    val duration = action.duration
    if (duration <= Duration.ZERO) {
        applyLeafEffect(state, action.kind, node, progress = 1f, elapsed = Duration.ZERO)
        return availableTime
    }
    val requestedElapsed = state.elapsed + availableTime
    val finished = requestedElapsed >= duration
    val clampedElapsed = if (finished) duration else requestedElapsed
    val progress = (clampedElapsed / duration).toFloat()
    val curve = action.timingFunction ?: { t: Float -> action.timingMode.ease(t) }
    applyLeafEffect(state, action.kind, node, curve(progress), clampedElapsed)
    state.elapsed = clampedElapsed
    return if (finished) requestedElapsed - duration else null
}

/**
 * [SKActionKind.PlaySoundFileNamed] has no fixed [SKAction.duration] to drive progress from (its
 * real clip length isn't known ahead of time), so it bypasses [stepLeaf]'s normal duration-based
 * path entirely: starts the clip once (cached via [SKActionState.captureOnce]), finishes
 * immediately if [SKActionKind.PlaySoundFileNamed.waitForCompletion] is `false`, or keeps
 * returning `null` (still running) each frame until the clip actually stops playing.
 */
private fun stepPlaySound(
    state: SKActionState,
    kind: SKActionKind.PlaySoundFileNamed,
    availableTime: Duration,
): Duration? {
    val handle =
        state.captureOnce { audioPlaybackFactory.create(kind.path, releaseOnCompletion = true).also { it.play() } }
    if (!kind.waitForCompletion) return availableTime
    return if (handle.isPlaying) null else availableTime
}

@Suppress("CyclomaticComplexMethod") // one branch per SKActionKind case; splitting further would obscure, not clarify
private fun applyLeafEffect(
    state: SKActionState,
    kind: SKActionKind,
    node: SKNode,
    progress: Float,
    elapsed: Duration,
) {
    when (kind) {
        is SKActionKind.MoveTo -> {
            val from = state.captureOnce { node.position }
            node.position = from + (kind.position - from) * progress
        }
        is SKActionKind.MoveBy -> {
            val from = state.captureOnce { node.position }
            node.position = from + kind.delta * progress
        }
        is SKActionKind.ScaleTo -> {
            val from = state.captureOnce { Vector2(node.xScale, node.yScale) }
            node.xScale = from.x + (kind.xScale - from.x) * progress
            node.yScale = from.y + (kind.yScale - from.y) * progress
        }
        is SKActionKind.ScaleBy -> {
            val from = state.captureOnce { Vector2(node.xScale, node.yScale) }
            node.xScale = from.x + kind.dx * progress
            node.yScale = from.y + kind.dy * progress
        }
        is SKActionKind.RotateTo -> {
            val from = state.captureOnce { node.zRotation }
            node.zRotation = from + shortestAngleDelta(from, kind.angle) * progress
        }
        is SKActionKind.RotateBy -> {
            val from = state.captureOnce { node.zRotation }
            node.zRotation = from + kind.delta * progress
        }
        is SKActionKind.ResizeTo -> applyResize(state, node, progress) { from -> kind.size - from }
        is SKActionKind.ResizeBy -> applyResize(state, node, progress) { kind.delta }
        is SKActionKind.FadeAlphaTo -> {
            val from = state.captureOnce { node.alpha }
            node.alpha = from + (kind.alpha - from) * progress
        }
        is SKActionKind.FadeAlphaBy -> {
            val from = state.captureOnce { node.alpha }
            node.alpha = from + kind.delta * progress
        }
        is SKActionKind.Colorize -> applyColorize(state, kind, node, progress)
        is SKActionKind.Hide -> node.isHidden = true
        is SKActionKind.Unhide -> node.isHidden = false
        is SKActionKind.RunBlock -> kind.block()
        is SKActionKind.RemoveFromParent -> node.removeFromParent()
        is SKActionKind.Wait -> Unit
        is SKActionKind.Custom -> kind.block(node, elapsed)
        is SKActionKind.Animate -> applyAnimate(state, kind, node, elapsed)
        is SKActionKind.Play -> (node as? SKAudioNode)?.play()
        is SKActionKind.Pause -> (node as? SKAudioNode)?.pause()
        is SKActionKind.Stop -> (node as? SKAudioNode)?.stop()
        is SKActionKind.ChangeVolumeTo -> applyChangeVolume(state, kind, node, progress)
        is SKActionKind.ChangePlaybackRateTo -> applyChangePlaybackRate(state, kind, node, progress)
        // Handled specially by stepPlaySound (no fixed duration to drive progress from);
        // never reaches here.
        is SKActionKind.PlaySoundFileNamed -> Unit
        // Composite kinds are handled by stepAction's own dispatch and never reach this function.
        is SKActionKind.Sequence, is SKActionKind.Group, is SKActionKind.Repeat -> Unit
    }
}

private fun applyChangeVolume(
    state: SKActionState,
    kind: SKActionKind.ChangeVolumeTo,
    node: SKNode,
    progress: Float,
) {
    val audio = node as? SKAudioNode ?: return
    val from = state.captureOnce { audio.volume }
    audio.volume = from + (kind.volume - from) * progress
}

private fun applyChangePlaybackRate(
    state: SKActionState,
    kind: SKActionKind.ChangePlaybackRateTo,
    node: SKNode,
    progress: Float,
) {
    val audio = node as? SKAudioNode ?: return
    val from = state.captureOnce { audio.playbackRate }
    audio.playbackRate = from + (kind.rate - from) * progress
}

private fun applyResize(
    state: SKActionState,
    node: SKNode,
    progress: Float,
    delta: (from: Vector2) -> Vector2,
) {
    val sprite = node as? SKSpriteNode ?: return
    val from = state.captureOnce { sprite.size }
    val d = delta(from)
    sprite.size = Vector2(from.x + d.x * progress, from.y + d.y * progress)
}

private fun applyColorize(
    state: SKActionState,
    kind: SKActionKind.Colorize,
    node: SKNode,
    progress: Float,
) {
    val sprite = node as? SKSpriteNode ?: return
    val from = state.captureOnce { sprite.color to sprite.colorBlendFactor }
    val (fromColor, fromBlend) = from
    sprite.color = lerpColor(fromColor, kind.color ?: fromColor, progress)
    val targetBlend = kind.colorBlendFactor ?: fromBlend
    sprite.colorBlendFactor = fromBlend + (targetBlend - fromBlend) * progress
}

@Suppress("ReturnCount") // three independent guard clauses read more clearly here than nesting them
private fun applyAnimate(
    state: SKActionState,
    kind: SKActionKind.Animate,
    node: SKNode,
    elapsed: Duration,
) {
    val sprite = node as? SKSpriteNode ?: return
    if (kind.textures.isEmpty()) return
    val originalTexture = state.captureOnce { sprite.texture }
    if (kind.restore && elapsed >= kind.timePerFrame * kind.textures.size) {
        sprite.texture = originalTexture
        return
    }
    val frameIndex = animationFrameIndex(elapsed, kind.timePerFrame, kind.textures.size)
    sprite.texture = kind.textures[frameIndex]
}

/**
 * Which index into a list of [textureCount] textures should be showing at [elapsed], holding
 * each for [timePerFrame]. A standalone pure function (rather than inlined into [applyAnimate])
 * so this math is unit-testable without needing a real [SKTexture]/`Bitmap`.
 */
internal fun animationFrameIndex(
    elapsed: Duration,
    timePerFrame: Duration,
    textureCount: Int,
): Int = (elapsed / timePerFrame).toInt().coerceIn(0, textureCount - 1)

/**
 * The signed angular distance (radians) from [from] to [to] by the shorter of the two possible
 * paths around the circle, for [SKActionKind.RotateTo]. A standalone function so this math is
 * directly unit-testable.
 */
internal fun shortestAngleDelta(
    from: Float,
    to: Float,
): Float {
    val twoPi = (2.0 * PI).toFloat()
    var delta = (to - from) % twoPi
    if (delta > PI) delta -= twoPi
    if (delta < -PI) delta += twoPi
    return delta
}

private const val MAX_REPEAT_ITERATIONS_PER_STEP = 10_000
