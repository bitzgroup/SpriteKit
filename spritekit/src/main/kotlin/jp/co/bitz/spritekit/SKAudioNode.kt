package jp.co.bitz.spritekit

/**
 * A node that plays a single audio clip — mirrors Apple's `SKAudioNode`. Control it via
 * [SKAction.play]/[SKAction.pause]/[SKAction.stop]/[SKAction.changeVolume]/
 * [SKAction.changePlaybackRate] run on this node (Apple's own action-based control model), or
 * read [isPlaying] directly.
 *
 * [fileNamed] names the clip the same way Apple's `SKAudioNode(fileNamed:)` does: a plain file
 * name (`"theme.mp3"`, or a relative path such as `"music/theme.mp3"`) is looked up among the
 * resources bundled with the app — here, the host app's `assets/` folder, Android's equivalent of
 * Apple's main bundle. An absolute file path or a URL (Apple's `SKAudioNode(url:)`) is also
 * accepted as-is. Positional/spatial audio isn't implemented — see `docs/ROADMAP.md`.
 */
public class SKAudioNode(
    internal val fileNamed: String,
) : SKNode() {
    /**
     * When `true` (the default, matching Apple), this node starts playing on its own, looped,
     * once it's first part of a presented [SKScene]'s tree — see `SKAudioSimulation.kt`.
     */
    public var autoplayLooped: Boolean = true

    /** Playback volume, `0` (silent) to `1` (full). Defaults to `1`. */
    public var volume: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            handle?.setVolume(field)
        }

    /** Playback speed multiplier. `1` is normal speed. Defaults to `1`. */
    public var playbackRate: Float = 1f
        set(value) {
            field = value.coerceAtLeast(0f)
            handle?.setPlaybackRate(field)
        }

    /** Whether this clip is currently playing. */
    public val isPlaying: Boolean get() = handle?.isPlaying ?: false

    /**
     * Whether [autoplayLooped] has already triggered once for this node — see
     * `SKAudioSimulation.kt`. Not part of the public API.
     */
    internal var hasAutoStarted: Boolean = false

    private var handle: SKAudioPlaybackHandle? = null

    /** Starts (or resumes) playback — Apple's `SKAction.play()`. */
    internal fun play() {
        val activeHandle = handle ?: newHandle().also { handle = it }
        activeHandle.play()
    }

    /** Pauses playback in place — Apple's `SKAction.pause()`. */
    internal fun pause() {
        handle?.pause()
    }

    /** Stops playback and rewinds to the start — Apple's `SKAction.stop()`. */
    internal fun stop() {
        handle?.stop()
    }

    private fun newHandle(): SKAudioPlaybackHandle =
        audioPlaybackFactory.create(fileNamed, releaseOnCompletion = false).also {
            it.setVolume(volume)
            it.setPlaybackRate(playbackRate)
            it.setLooping(autoplayLooped)
        }
}
