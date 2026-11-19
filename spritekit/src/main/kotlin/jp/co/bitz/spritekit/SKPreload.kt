package jp.co.bitz.spritekit

import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * The background thread texture and atlas preloads call back on — Apple calls their completion
 * handlers off the main thread (verified on iOS/tvOS simulators), so this does too.
 */
internal val skPreloadExecutor: Executor by lazy {
    Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "SpriteKit preload").apply { isDaemon = true }
    }
}

/** Calls [completionHandler] on [executor] — every preload's shared tail. */
internal fun skPreload(
    completionHandler: () -> Unit,
    executor: Executor = skPreloadExecutor,
) {
    executor.execute(completionHandler)
}
