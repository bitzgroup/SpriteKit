// Named for the subsystem (this library's Android-touching audio playback backend) rather than
// the single class below -- it also defines realAudioPlaybackFactory alongside it.
@file:Suppress("MatchingDeclarationName")

package jp.co.bitz.spritekit

import android.content.Context
import android.media.MediaPlayer
import java.io.File

/**
 * The real [SKAudioPlaybackHandle], backed by [MediaPlayer] — wired in as [audioPlaybackFactory]
 * by [SKView]. Touches real `MediaPlayer` calls throughout, so — like this library's other
 * platform-integration files — it isn't covered by unit tests; see `docs/ROADMAP.md`'s testing
 * notes.
 *
 * [clip] is resolved by [resolveAudioSource]: a plain file name plays the file of that name from
 * the host app's `assets/` folder (Apple's main-bundle lookup), anything else is handed to
 * `MediaPlayer` unchanged.
 *
 * Every operation is wrapped in [runCatching] and silently ignored on failure (a bad [clip],
 * calling a method after the player already released itself, ...) rather than throwing or
 * crashing the render thread — playback problems shouldn't take down the rest of the scene.
 */
internal class SKMediaPlayerHandle(
    context: Context,
    clip: String,
    releaseOnCompletion: Boolean,
) : SKAudioPlaybackHandle {
    private val player = MediaPlayer()

    init {
        runCatching {
            when (val source = resolveAudioSource(clip)) {
                is SKAudioSource.Asset -> setAssetDataSource(context, source.name)
                is SKAudioSource.Location -> player.setDataSource(source.location)
            }
            if (releaseOnCompletion) player.setOnCompletionListener { it.release() }
            player.prepare()
        }
    }

    // Uncompressed assets (aapt stores audio formats such as .mp3/.ogg/.wav/.m4a uncompressed by
    // default) are played straight out of the APK via a file descriptor. A compressed asset can't
    // be opened that way, so it's copied into the cache directory once and played from there.
    private fun setAssetDataSource(
        context: Context,
        name: String,
    ) {
        val direct =
            runCatching {
                context.assets.openFd(name).use { player.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            }
        if (direct.isFailure) player.setDataSource(cachedAssetCopy(context, name).absolutePath)
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

private fun cachedAssetCopy(
    context: Context,
    name: String,
): File {
    val file = File(File(context.cacheDir, "spritekit-audio"), name)
    if (!file.exists()) {
        file.parentFile?.mkdirs()
        context.assets.open(name).use { input -> file.outputStream().use { input.copyTo(it) } }
    }
    return file
}

/** The [SKAudioPlaybackFactory] [SKView] installs as [audioPlaybackFactory] at construction. */
internal fun realAudioPlaybackFactory(context: Context): SKAudioPlaybackFactory =
    SKAudioPlaybackFactory { clip, releaseOnCompletion -> SKMediaPlayerHandle(context, clip, releaseOnCompletion) }
