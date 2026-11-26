package jp.co.bitz.spritekit

import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * One tile's visual appearance — mirrors Apple's `SKTileDefinition`. [textures] holding more than
 * one texture animates through them, [timePerFrame] apart, looping — used for e.g. flowing water
 * or torch flicker. An empty [textures] list (unlike Apple, which always requires at least one)
 * renders as a flat-colored quad, the same fallback an untextured [SKSpriteNode] gets — this
 * port's usual "`null`/empty texture means flat-colored" convention, applied here too.
 *
 * Apple's `normalOffset` (for normal-mapped lighting — `SKLightNode` isn't implemented, see
 * `docs/ROADMAP.md`'s "Explicitly Out of Scope") and the flip/rotation placement variants aren't
 * implemented — see `docs/API_COMPATIBILITY.md`.
 */
public class SKTileDefinition(
    public val textures: List<SKTexture> = emptyList(),
    public val size: Vector2 = Vector2.Zero,
    public val timePerFrame: Duration = 0.1.seconds,
) {
    /** A single-texture, non-animated tile — Apple's `SKTileDefinition(texture:size:)`. */
    public constructor(texture: SKTexture, size: Vector2) : this(listOf(texture), size)

    /** An identifying name, purely for the caller's own bookkeeping — not used by lookup/rule matching. */
    public var name: String? = null

    /**
     * How likely this definition is to be placed, relative to the other definitions of the same
     * [SKTileGroupRule] — Apple's `placementWeight`, default `1`. A weight of `0` is never placed
     * while another definition of the rule has a positive weight; when every weight is `0`, each
     * is equally likely (verified on iOS/tvOS simulators).
     */
    public var placementWeight: Int = 1

    /** Arbitrary user data, `null` until the caller sets it — this library's `NSMutableDictionary` stand-in. */
    public var userData: MutableMap<String, Any?>? = null

    /**
     * Whichever of [textures] is current at [elapsed] into this tile's animation, looping (the
     * first texture for a single-texture tile); `null` if [textures] is empty.
     */
    internal fun textureAt(elapsed: Duration): SKTexture? {
        if (textures.isEmpty()) return null
        return textures[frameIndexAt(elapsed, timePerFrame, textures.size)]
    }
}

/**
 * The looping animation-frame index at [elapsed], [timePerFrame] apart, out of [frameCount] total
 * frames -- factored out of [SKTileDefinition.textureAt] so this pure math is unit-testable
 * without needing a real [SKTexture] (which wraps an `android.graphics.Bitmap`, unsafe to
 * construct in a plain JVM test).
 */
internal fun frameIndexAt(
    elapsed: Duration,
    timePerFrame: Duration,
    frameCount: Int,
): Int {
    if (frameCount <= 1 || timePerFrame <= Duration.ZERO) return 0
    return (elapsed / timePerFrame).toInt().mod(frameCount)
}

/**
 * One of [definitions], chosen at random in proportion to each one's
 * [SKTileDefinition.placementWeight] (negative weights count as `0`), or evenly when no weight is
 * positive; `null` if [definitions] is empty.
 */
internal fun pickWeighted(
    definitions: List<SKTileDefinition>,
    random: Random = Random.Default,
): SKTileDefinition? {
    val total = definitions.sumOf { it.placementWeight.coerceAtLeast(0).toLong() }
    if (total <= 0L) return definitions.randomOrNull(random)
    var remaining = random.nextLong(total)
    return definitions.firstOrNull { definition ->
        remaining -= definition.placementWeight.coerceAtLeast(0)
        remaining < 0L
    }
}
