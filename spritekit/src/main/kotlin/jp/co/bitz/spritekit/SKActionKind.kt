package jp.co.bitz.spritekit

import kotlin.time.Duration

/** What an [SKAction] actually does — its "body," evaluated by [stepAction]. Not part of the public API. */
internal sealed class SKActionKind {
    data class MoveTo(val position: Vector2) : SKActionKind()

    data class MoveBy(val delta: Vector2) : SKActionKind()

    data class ScaleTo(val xScale: Float, val yScale: Float) : SKActionKind()

    data class ScaleBy(val dx: Float, val dy: Float) : SKActionKind()

    data class RotateTo(val angle: Float) : SKActionKind()

    data class RotateBy(val delta: Float) : SKActionKind()

    data class ResizeTo(val size: Vector2) : SKActionKind()

    data class ResizeBy(val delta: Vector2) : SKActionKind()

    data class FadeAlphaTo(val alpha: Float) : SKActionKind()

    data class FadeAlphaBy(val delta: Float) : SKActionKind()

    data object Hide : SKActionKind()

    data object Unhide : SKActionKind()

    data class Colorize(val color: Int?, val colorBlendFactor: Float?) : SKActionKind()

    data object Wait : SKActionKind()

    data class RunBlock(val block: () -> Unit) : SKActionKind()

    data object RemoveFromParent : SKActionKind()

    data class Sequence(val actions: List<SKAction>) : SKActionKind()

    data class Group(val actions: List<SKAction>) : SKActionKind()

    data class Repeat(val action: SKAction, val count: Int?) : SKActionKind()

    data class Custom(val block: (SKNode, Duration) -> Unit) : SKActionKind()

    data class Animate(val textures: List<SKTexture>, val timePerFrame: Duration, val restore: Boolean) : SKActionKind()
}

/**
 * Returns a new [SKActionKind] that undoes this one's effect, where meaningful. Absolute ("to")
 * actions aren't reversible in general — the value they started from isn't known until run
 * time — and are returned unchanged, along with actions with no natural inverse (`wait`,
 * `run`, `removeFromParent`, `customAction`, `animate`); see `docs/API_COMPATIBILITY.md`.
 */
internal fun SKActionKind.reversed(): SKActionKind =
    when (this) {
        // `0f - x` rather than unary `-`/`* -1f`, so a zero component negates to `+0f`, not
        // `-0f` (IEEE 754: negating zero flips its sign bit; subtracting from zero doesn't) --
        // `-0f != 0f` under Kotlin's (and SKActionKind's data class) `equals`.
        is SKActionKind.MoveBy -> SKActionKind.MoveBy(-delta)
        is SKActionKind.ScaleBy -> SKActionKind.ScaleBy(0f - dx, 0f - dy)
        is SKActionKind.RotateBy -> SKActionKind.RotateBy(0f - delta)
        is SKActionKind.ResizeBy -> SKActionKind.ResizeBy(-delta)
        is SKActionKind.FadeAlphaBy -> SKActionKind.FadeAlphaBy(0f - delta)
        is SKActionKind.Sequence -> SKActionKind.Sequence(actions.reversed().map { it.reversed() })
        is SKActionKind.Group -> SKActionKind.Group(actions.map { it.reversed() })
        is SKActionKind.Repeat -> SKActionKind.Repeat(action.reversed(), count)
        else -> this
    }
