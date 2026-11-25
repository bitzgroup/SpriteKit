package jp.co.bitz.spritekit

/**
 * How an [SKKeyframeSequence] finds a value between two keyframes, mirroring Apple's
 * `SKInterpolationMode`.
 */
public enum class SKInterpolationMode {
    /** Straight from one keyframe's value to the next. The default, as on Apple. */
    Linear,

    /**
     * A cubic Hermite curve through the keyframes. The curve is flat at the first and last
     * keyframe; at any other keyframe its tangent is `(Δprev · gapNext + Δnext · gapPrev) /
     * (gapPrev + gapNext)` — the changes from the previous keyframe and to the next one, each
     * weighted by the other side's time gap — measured per segment, so evenly spaced keyframes
     * give a Catmull-Rom curve. This is the curve Apple's SpriteKit uses (Apple doesn't document
     * it; it was recorded on iOS/tvOS simulators).
     */
    Spline,

    /** The value of the latest keyframe reached, held until the next one. */
    Step,
}

/** What an [SKKeyframeSequence] does with times past its last keyframe, mirroring Apple's `SKRepeatMode`. */
public enum class SKRepeatMode {
    /** Holds the last keyframe's value. The default, as on Apple. */
    Clamp,

    /**
     * Starts over: the time wraps into `0 ..< lastTime`, where `lastTime` is the last keyframe's
     * time (not the span from the first keyframe), so each pass holds the first keyframe's value
     * until its time comes round again.
     */
    Loop,
}

/**
 * Mixes keyframe values for an [SKKeyframeSequence]: [combine] returns the sum of `values[i] *
 * weights[i]`. The weights always sum to 1, but [SKInterpolationMode.Spline] can make some of
 * them negative (the curve can overshoot a keyframe).
 *
 * Apple's `SKKeyframeSequence` holds untyped values and interpolates only numbers and colors
 * (anything else samples as `nil`). This port is generic, so the sequence is given the
 * interpolator for its value type — [FloatValues] and [ColorValues] cover what Apple's does.
 */
public fun interface SKKeyframeInterpolator<T> {
    public fun combine(
        values: List<T>,
        weights: FloatArray,
    ): T

    public companion object {
        /** Numbers, like Apple's `NSNumber` keyframes. */
        public val FloatValues: SKKeyframeInterpolator<Float> =
            SKKeyframeInterpolator { values, weights ->
                values.indices.fold(0f) { sum, i -> sum + values[i] * weights[i] }
            }

        /**
         * ARGB colors (matching [android.graphics.Color]), mixed channel by channel without
         * premultiplying, like Apple's `SKColor` keyframes. Each channel is clamped to `0..1`, which
         * Apple's extended-range colors don't need to be.
         */
        public val ColorValues: SKKeyframeInterpolator<Int> =
            SKKeyframeInterpolator { values, weights ->
                fun mixed(channel: (Int) -> Float): Int {
                    val sum = values.indices.fold(0f) { acc, i -> acc + channel(values[i]) * weights[i] }
                    return (sum.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                }
                (mixed(::alphaOf) shl 24) or (mixed(::redOf) shl 16) or (mixed(::greenOf) shl 8) or mixed(::blueOf)
            }
    }
}

/**
 * A list of values at given times — mirrors Apple's `SKKeyframeSequence`, used by
 * [SKEmitterNode.particleColorSequence] for color-over-lifetime ramps (fire cooling from white to
 * red to black, for example).
 *
 * [sample] holds the first keyframe's value up to its time and the last keyframe's from its time
 * on; between them it finds the first keyframe whose time is greater than the sample time and
 * applies [interpolationMode] between that keyframe and the one before it. Times are kept in the
 * order given — neither the constructor nor the editing methods sort them — and keyframes with
 * equal times sample as the later one, all as on Apple.
 *
 * Apple's version holds untyped values; this one is generic and takes an [interpolator] for its
 * value type — see [SKKeyframeInterpolator] and `docs/API_COMPATIBILITY.md`.
 */
public class SKKeyframeSequence<T>(
    keyframeValues: List<T>,
    times: List<Float>,
    private val interpolator: SKKeyframeInterpolator<T>,
) {
    private val values: MutableList<T> = keyframeValues.toMutableList()
    private val times: MutableList<Float> = times.toMutableList()

    init {
        require(keyframeValues.size == times.size) { "keyframeValues and times must be the same size" }
    }

    /**
     * An empty sequence with room for [capacity] keyframes, like Apple's `init(capacity:)`; add
     * keyframes with [addKeyframeValue]. Sampling it before then returns `null`.
     */
    public constructor(capacity: Int, interpolator: SKKeyframeInterpolator<T>) :
        this(ArrayList<T>(capacity), ArrayList<Float>(capacity), interpolator)

    /** How [sample] finds values between keyframes. Defaults to [SKInterpolationMode.Linear]. */
    public var interpolationMode: SKInterpolationMode = SKInterpolationMode.Linear

    /** What [sample] does past the last keyframe. Defaults to [SKRepeatMode.Clamp]. */
    public var repeatMode: SKRepeatMode = SKRepeatMode.Clamp

    /** The number of keyframes. */
    public fun count(): Int = values.size

    /** Appends a keyframe — at the end of the list, wherever [time] falls among the others. */
    public fun addKeyframeValue(
        value: T,
        time: Float,
    ) {
        values += value
        times += time
    }

    /** Removes the last keyframe; does nothing when there are none. */
    public fun removeLastKeyframe() {
        if (values.isEmpty()) return
        values.removeAt(values.lastIndex)
        times.removeAt(times.lastIndex)
    }

    /** Removes the keyframe at [index]. */
    public fun removeKeyframe(index: Int) {
        values.removeAt(index)
        times.removeAt(index)
    }

    /** Replaces the value of the keyframe at [index]. */
    public fun setKeyframeValue(
        value: T,
        index: Int,
    ) {
        values[index] = value
    }

    /** Replaces the time of the keyframe at [index], leaving it where it is in the list. */
    public fun setKeyframeTime(
        time: Float,
        index: Int,
    ) {
        times[index] = time
    }

    /** Replaces the value and time of the keyframe at [index], leaving it where it is in the list. */
    public fun setKeyframeValue(
        value: T,
        time: Float,
        index: Int,
    ) {
        values[index] = value
        times[index] = time
    }

    /** The value of the keyframe at [index]. */
    public fun getKeyframeValue(index: Int): T = values[index]

    /** The time of the keyframe at [index]. */
    public fun getKeyframeTime(index: Int): Float = times[index]

    /** The value at [time], following [interpolationMode] and [repeatMode]; `null` while there are no keyframes. */
    @Suppress("ReturnCount")
    public fun sample(time: Float): T? {
        if (values.isEmpty()) return null
        val lastTime = times.last()
        var t = time
        if (repeatMode == SKRepeatMode.Loop && lastTime > 0f) {
            t %= lastTime
            if (t < 0f) t += lastTime
        }
        // Limiting to the *last* keyframe's time (not the largest) is what Apple does with unsorted times.
        t = minOf(t, lastTime)

        val upper = times.indexOfFirst { it > t }
        if (upper == 0) return values.first()
        if (upper < 0) return values.last()
        val lower = upper - 1
        if (interpolationMode == SKInterpolationMode.Step) return values[lower]

        val u = (t - times[lower]) / (times[upper] - times[lower])
        return if (interpolationMode == SKInterpolationMode.Linear) {
            interpolator.combine(listOf(values[lower], values[upper]), floatArrayOf(1f - u, u))
        } else {
            spline(lower, upper, u)
        }
    }

    /**
     * The Hermite segment from [lower] to [upper] at [u] (`0..1`), written as one weighted sum of
     * the up to four keyframes its two tangents depend on.
     */
    private fun spline(
        lower: Int,
        upper: Int,
        u: Float,
    ): T {
        val u2 = u * u
        val u3 = u2 * u
        val weights = FloatArray(values.size)
        weights[lower] += 2f * u3 - 3f * u2 + 1f
        weights[upper] += -2f * u3 + 3f * u2
        addTangentWeights(lower, u3 - 2f * u2 + u, weights)
        addTangentWeights(upper, u3 - u2, weights)
        val used = weights.indices.filter { weights[it] != 0f }
        return interpolator.combine(used.map { values[it] }, FloatArray(used.size) { weights[used[it]] })
    }

    /** Adds [scale] times the tangent at keyframe [index] — itself a weighted sum of its neighbors — to [weights]. */
    @Suppress("ReturnCount")
    private fun addTangentWeights(
        index: Int,
        scale: Float,
        weights: FloatArray,
    ) {
        if (index == 0 || index == values.lastIndex) return
        val gapPrev = times[index] - times[index - 1]
        val gapNext = times[index + 1] - times[index]
        val gaps = gapPrev + gapNext
        if (gaps == 0f) return
        // (Δprev · gapNext + Δnext · gapPrev) / gaps, with Δprev = v[i] − v[i−1], Δnext = v[i+1] − v[i].
        weights[index - 1] += scale * -gapNext / gaps
        weights[index] += scale * (gapNext - gapPrev) / gaps
        weights[index + 1] += scale * gapPrev / gaps
    }
}
