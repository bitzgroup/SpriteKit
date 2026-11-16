package jp.co.bitz.spritekit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SKResourceRegistryTest {
    @Test
    fun `reloadAll calls every registered resource in registration order`() {
        val registry = SKResourceRegistry()
        val reloaded = mutableListOf<Int>()
        registry.register(SKReloadableResource { reloaded += 1 })
        registry.register(SKReloadableResource { reloaded += 2 })
        registry.register(SKReloadableResource { reloaded += 3 })

        registry.reloadAll()

        assertEquals(listOf(1, 2, 3), reloaded)
    }

    @Test
    fun `unregister removes a resource from future reloadAll calls`() {
        val registry = SKResourceRegistry()
        var reloadCount = 0
        val resource = SKReloadableResource { reloadCount++ }
        registry.register(resource)

        registry.unregister(resource)
        registry.reloadAll()

        assertEquals(0, reloadCount)
    }

    @Test
    fun `reloadAll on an empty registry does nothing`() {
        val registry = SKResourceRegistry()

        registry.reloadAll() // must not throw
    }

    @Test
    fun `generation starts at zero and changes on every reloadAll call`() {
        val registry = SKResourceRegistry()

        assertEquals(0, registry.generation)
        registry.reloadAll()
        val first = registry.generation
        registry.reloadAll()
        assertNotEquals(0, first)
        assertNotEquals(first, registry.generation)
    }

    @Test
    fun `a new registry never reuses a generation an older registry already used`() {
        // An SKView recreated with its Activity gets a fresh registry, while textures/scenes kept
        // from the old view still carry the old view's generation; they must not look uploaded.
        val oldView = SKResourceRegistry()
        oldView.reloadAll()
        val newView = SKResourceRegistry()
        newView.reloadAll()

        assertNotEquals(oldView.generation, newView.generation)
    }
}
