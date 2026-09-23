package jp.co.bitz.spritekit

import kotlin.test.Test
import kotlin.test.assertEquals

class SKAudioSourceTest {
    @Test
    fun `a plain file name resolves to an app asset, like Apple's main-bundle lookup`() {
        assertEquals(SKAudioSource.Asset("tap.mp3"), resolveAudioSource("tap.mp3"))
    }

    @Test
    fun `a relative path resolves to an asset in that assets subfolder`() {
        assertEquals(SKAudioSource.Asset("sounds/tap.mp3"), resolveAudioSource("sounds/tap.mp3"))
    }

    @Test
    fun `an android_asset URL resolves to the asset it names`() {
        assertEquals(SKAudioSource.Asset("sounds/tap.mp3"), resolveAudioSource("file:///android_asset/sounds/tap.mp3"))
    }

    @Test
    fun `an absolute path is used as-is`() {
        val path = "/data/user/0/app/cache/tap.mp3"
        assertEquals(SKAudioSource.Location(path), resolveAudioSource(path))
    }

    @Test
    fun `a URL is used as-is`() {
        val url = "https://example.com/tap.mp3"
        assertEquals(SKAudioSource.Location(url), resolveAudioSource(url))
    }
}
