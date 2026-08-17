// Named for the subsystem (this library's Android-touching audio playback backend) rather than
// the single class below -- it also defines realAudioPlaybackFactory alongside it.
@file:Suppress("MatchingDeclarationName")

package jp.co.bitz.spritekit

import android.media.MediaPlayer

/**
 * The real [SKAudioPlaybackHandle], backed by [MediaPlayer] — wired in as [audioPlaybackFactory]
 * by [SKView]. Touches real `MediaPlayer` calls throughout, so — like this library's other
 * platform-integration files — it isn't covered by unit tests; see `docs/ROADMAP.md`'s testing
 * notes.
 *
 * Every operation is wrapped in [runCatching] and silently ignored on failure (a bad [path],
 * calling a method after the player already released itself, ...) rather than throwing or
 * crashing the render thread — playback problems shouldn't take down the rest of the scene.
 */
internal class SKMediaPlayerHandle(
    path: String,
    releaseOnCompletion: Boolean,
) : SKAudioPlaybackHandle {
    private val player = MediaPlayer()

    init {
        runCatching {
            player.setDataSource(path)
            if (releaseOnCompletion) player.setOnCompletionListener { it.release() }
            player.prepare()
        }
    }

    override val isPlaying: Boolean get() = runCatching { player.isPlaying }.getOrDefault(false)

    override fun play() {
        runCatching { player.start() }
    }

    override fun pause() {
        runCatching { player.pause() }
    }

    override fun stop() {
        runCatching {
            player.pause()
            player.seekTo(0)
        }
    }

    override fun setVolume(volume: Float) {
        runCatching { player.setVolume(volume, volume) }
    }

    override fun setPlaybackRate(rate: Float) {
        // PlaybackParams.setSpeed requires API 23+; runCatching absorbs both that and any other
        // player-state issue uniformly, rather than checking Build.VERSION separately.
        runCatching { player.playbackParams = player.playbackParams.setSpeed(rate) }
    }

    override fun setLooping(looping: Boolean) {
        runCatching { player.isLooping = looping }
    }
}

/** The [SKAudioPlaybackFactory] [SKView] installs as [audioPlaybackFactory] at construction. */
internal val realAudioPlaybackFactory =
    SKAudioPlaybackFactory { path, releaseOnCompletion -> SKMediaPlayerHandle(path, releaseOnCompletion) }
