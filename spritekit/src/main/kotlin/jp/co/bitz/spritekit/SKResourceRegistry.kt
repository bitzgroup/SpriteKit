package jp.co.bitz.spritekit

import java.util.concurrent.atomic.AtomicInteger

/**
 * A GPU-backed resource whose GPU-side handle (a texture/program/buffer name) must be recreated
 * whenever [SKView]'s `EGLContext` is lost and re-created — see `docs/ARCHITECTURE.md`'s "GPU
 * resource lifecycle and context loss" section.
 *
 * Implementations hold a persistent CPU-side descriptor (e.g. [SKTexture]'s source `Bitmap`) and
 * lazily (re)create their GPU handle in [reload]. This is the render-thread-confined
 * callback mechanism; [SKTexture] and [SKShader] use [SKResourceRegistry.generation] instead.
 */
public fun interface SKReloadableResource {
    /** Called on [SKView]'s render thread once a valid `EGLContext` is available. */
    public fun reload()
}

/**
 * Tracks every live [SKReloadableResource] so [SKView] can re-upload GPU state after an
 * `EGLContext` loss, transparent to the resources' owners. Confined to the render thread — see
 * `docs/ARCHITECTURE.md`.
 */
public class SKResourceRegistry {
    private val resources = mutableListOf<SKReloadableResource>()

    /**
     * Identifies the `EGLContext` this registry's resources were last (re)loaded against, changed
     * by every [reloadAll] call. Resources that upload lazily on first use (like [SKTexture])
     * compare their own last-uploaded generation against this to detect a context loss without
     * needing to [register] a callback — simpler than the callback-list mechanism below for
     * resources that don't exist yet when a given `SKView` is created.
     *
     * `0` until the first [reloadAll]; after that, unique **process-wide**, not just per registry.
     * A texture or scene can outlive the [SKView] that first uploaded it (an `Activity` recreated by
     * a configuration change, a scene kept in a `ViewModel`), and a fresh view's registry must never
     * hand out a generation an older view's context already used — otherwise the texture would look
     * already uploaded and be drawn with a texture name from a context that no longer exists.
     */
    public var generation: Int = 0
        private set

    /** Registers [resource] to be reloaded on every future [reloadAll] call. */
    public fun register(resource: SKReloadableResource) {
        resources.add(resource)
    }

    /** Unregisters [resource]; it will no longer be reloaded. No-op if it wasn't registered. */
    public fun unregister(resource: SKReloadableResource) {
        resources.remove(resource)
    }

    /**
     * Moves [generation] to a new process-wide unique value, then calls
     * [SKReloadableResource.reload] on every registered resource, in registration order.
     */
    public fun reloadAll() {
        generation = nextGeneration.incrementAndGet()
        resources.forEach { it.reload() }
    }

    private companion object {
        /** Shared by every registry, so no two contexts ever get the same [generation]. */
        val nextGeneration = AtomicInteger(0)
    }
}
