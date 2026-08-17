package jp.co.bitz.spritekit

/**
 * A GPU-backed resource whose GPU-side handle (a texture/program/buffer name) must be recreated
 * whenever [SKView]'s `EGLContext` is lost and re-created — see `docs/ARCHITECTURE.md`'s "GPU
 * resource lifecycle and context loss" section.
 *
 * Implementations hold a persistent CPU-side descriptor (e.g. [SKTexture]'s source `Bitmap`) and
 * lazily (re)create their GPU handle in [reload]. No implementations exist yet — actual GPU
 * resources start in Phase 3 — this is the render-thread-confined registration mechanism they'll
 * plug into.
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
     * Bumped by every [reloadAll] call. Resources that upload lazily on first use (like
     * [SKTexture], added in Phase 3) compare their own last-uploaded generation against this to
     * detect a context loss without needing to [register] a callback — simpler than the
     * callback-list mechanism below for resources that don't exist yet when a given `SKView` is
     * created.
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
     * Bumps [generation], then calls [SKReloadableResource.reload] on every registered resource,
     * in registration order.
     */
    public fun reloadAll() {
        generation++
        resources.forEach { it.reload() }
    }
}
