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

    /** Registers [resource] to be reloaded on every future [reloadAll] call. */
    public fun register(resource: SKReloadableResource) {
        resources.add(resource)
    }

    /** Unregisters [resource]; it will no longer be reloaded. No-op if it wasn't registered. */
    public fun unregister(resource: SKReloadableResource) {
        resources.remove(resource)
    }

    /** Calls [SKReloadableResource.reload] on every registered resource, in registration order. */
    public fun reloadAll() {
        resources.forEach { it.reload() }
    }
}
