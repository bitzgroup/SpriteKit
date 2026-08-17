package jp.co.bitz.spritekit.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import jp.co.bitz.spritekit.SKScene
import jp.co.bitz.spritekit.SKView as SKViewClassic

/**
 * Hosts [scene] using [SKViewClassic] (`:spritekit`'s `GLSurfaceView`-based `SKView`), via
 * [AndroidView]. The documented, recommended way to use this library — see `docs/ARCHITECTURE.md`'s
 * "Hosting: a View core, a Compose wrapper" section.
 *
 * Automatically pauses/resumes the render thread (and the presented scene) with
 * [LocalLifecycleOwner], which classic-View/XML consumers of [SKViewClassic] have to wire up by
 * hand from their own `Activity`/`Fragment` callbacks.
 */
@Composable
public fun SKView(
    scene: SKScene?,
    modifier: Modifier = Modifier,
    state: SKViewState = rememberSKViewState(),
) {
    AndroidView(
        factory = { context -> SKViewClassic(context).also { state.view = it } },
        modifier = modifier,
        update = { view -> scene?.let(view::presentScene) },
        onRelease = { view ->
            view.onPause()
            state.view = null
        },
    )

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, state) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> state.view?.onResume()
                    Lifecycle.Event.ON_PAUSE -> state.view?.onPause()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
