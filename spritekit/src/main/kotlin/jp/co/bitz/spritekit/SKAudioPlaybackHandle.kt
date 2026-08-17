package jp.co.bitz.spritekit

/**
 * A single playing/paused/stopped audio clip — the seam between [SKAudioNode]/audio [SKAction]s
 * and the real playback backend (`SKAudioPlayback.kt`, `android.media.MediaPlayer`-backed), kept
 * behind this interface so both stay pure Kotlin/unit-testable, matching the rest of this
 * library's Android-touching-code-isolation convention.
 */
internal interface SKAudioPlaybackHandle {
    val isPlaying: Boolean

    fun play()

    fun pause()

    fun stop()

    fun setVolume(volume: Float)

    fun setPlaybackRate(rate: Float)

    fun setLooping(looping: Boolean)
}

/**
 * Creates a new [SKAudioPlaybackHandle] for the audio at [path]. [releaseOnCompletion] governs
 * whether the underlying player frees itself once non-looping playback finishes naturally —
 * `true` for a fire-and-forget [SKAction.playSoundFileNamed] clip (nothing will touch it again),
 * `false` for a persistent [SKAudioNode] (which may be played again later).
 */
internal fun interface SKAudioPlaybackFactory {
    fun create(
        path: String,
        releaseOnCompletion: Boolean,
    ): SKAudioPlaybackHandle
}

/**
 * Which [SKAudioPlaybackFactory] [SKAudioNode]/[SKAction.playSoundFileNamed] actually use —
 * defaults to a harmless no-op (so this whole subsystem stays testable without touching
 * `MediaPlayer`); [SKView] swaps in the real one at construction.
 */
internal var audioPlaybackFactory: SKAudioPlaybackFactory =
    SKAudioPlaybackFactory { _, _ -> NoOpAudioPlaybackHandle }

// Every method is a deliberate no-op -- this is the default, harmless factory result before
// SKView installs the real one, so nothing should audibly happen yet.
@Suppress("EmptyFunctionBlock")
private object NoOpAudioPlaybackHandle : SKAudioPlaybackHandle {
    override val isPlaying: Boolean = false

    override fun play() {}

    override fun pause() {}

    override fun stop() {}

    override fun setVolume(volume: Float) {}

    override fun setPlaybackRate(rate: Float) {}

    override fun setLooping(looping: Boolean) {}
}
