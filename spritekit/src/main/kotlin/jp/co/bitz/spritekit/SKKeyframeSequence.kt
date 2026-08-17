package jp.co.bitz.spritekit

/**
 * A list of values sampled at specific points along a normalized `0..1` timeline — mirrors
 * Apple's `SKKeyframeSequence`, used by [SKEmitterNode.particleColorSequence] for color-over-
 * lifetime ramps (fire cooling from white to red to black, for example).
 *
 * Apple's version is untyped (`[Any]`) and infers how to interpolate between keyframes via
 * runtime reflection on the value type (`CGFloat`, `SKColor`, `CGPoint`, ...). This port is
 * generic and takes an explicit `interpolate` function at [sample] time instead — more
 * boilerplate per call site, but type-safe and with no hidden "which types does interpolation
 * actually support" behavior to document; see `docs/API_COMPATIBILITY.md`.
 */
public class SKKeyframeSequence<T>(
    public val keyframeValues: List<T>,
    public val times: List<Float>,
) {
    init {
        require(keyframeValues.isNotEmpty()) { "keyframeValues must not be empty" }
        require(keyframeValues.size == times.size) { "keyframeValues and times must be the same size" }
    }

    /**
     * The value at [time] (a normalized `0..1` fraction of the full sequence, clamped to that
     * range): one of [keyframeValues] directly if [time] lands on or outside a keyframe, otherwise
     * [interpolate] between the two surrounding keyframes, weighted by how far between their
     * [times] it falls.
     */
    @Suppress("ReturnCount")
    public fun sample(
        time: Float,
        interpolate: (from: T, to: T, fraction: Float) -> T,
    ): T {
        val clamped = time.coerceIn(0f, 1f)
        if (keyframeValues.size == 1 || clamped <= times.first()) return keyframeValues.first()
        if (clamped >= times.last()) return keyframeValues.last()

        val upperIndex = (1 until times.size).first { clamped <= times[it] }
        val lowerTime = times[upperIndex - 1]
        val upperTime = times[upperIndex]
        val fraction = if (upperTime > lowerTime) (clamped - lowerTime) / (upperTime - lowerTime) else 0f
        return interpolate(keyframeValues[upperIndex - 1], keyframeValues[upperIndex], fraction)
    }
}
