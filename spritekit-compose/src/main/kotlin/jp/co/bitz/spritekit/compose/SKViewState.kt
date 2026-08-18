package jp.co.bitz.spritekit.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import jp.co.bitz.spritekit.SKScene
import jp.co.bitz.spritekit.SKTransition
import jp.co.bitz.spritekit.SKView as SKViewClassic

/**
 * A remembered controller for [SKView], obtained via [rememberSKViewState]. Exposes the same
 * UI-thread ↔ render-thread bridge utilities as the underlying [SKViewClassic] `View` — see
 * `docs/ARCHITECTURE.md`'s "Bridge utilities" section — by delegating to it once Compose has
 * created it.
 */
public class SKViewState internal constructor() {
    internal var view: SKViewClassic? = null

    /** Presents [scene] on the hosted [SKViewClassic], once it exists. See [SKViewClassic.presentScene]. */
    public fun presentScene(scene: SKScene) {
        view?.presentScene(scene)
    }

    /**
     * Presents [scene] like [presentScene], but plays [transition] instead of cutting instantly.
     * See [SKViewClassic.presentScene].
     */
    public fun presentScene(
        scene: SKScene,
        transition: SKTransition,
    ) {
        view?.presentScene(scene, transition)
    }

    /** See [SKViewClassic.runOnGLThread]. No-op if the hosted view doesn't exist yet. */
    public fun runOnGLThread(block: () -> Unit) {
        view?.runOnGLThread(block)
    }

    /** See [SKViewClassic.runOnUiThread]. No-op if the hosted view doesn't exist yet. */
    public fun runOnUiThread(block: () -> Unit) {
        view?.runOnUiThread(block)
    }
}

/** Creates and remembers an [SKViewState] for use with [SKView]. */
@Composable
public fun rememberSKViewState(): SKViewState = remember { SKViewState() }
