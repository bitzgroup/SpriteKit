package jp.co.bitz.spritekit

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
