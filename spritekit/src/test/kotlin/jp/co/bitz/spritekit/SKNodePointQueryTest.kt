package jp.co.bitz.spritekit

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Expected values mirror Apple's SpriteKit, probed on macOS with the same node layouts
 * (`frame`, `contains(_:)`, `nodes(at:)`, `atPoint(_:)`).
 */
class SKNodePointQueryTest {
    private fun sprite(
        name: String,
        size: Float,
        x: Float,
        y: Float,
    ) = SKSpriteNode(size = Vector2(size, size)).apply {
        this.name = name
        position = Vector2(x, y)
    }

    private fun scene() = SKScene(Vector2(400f, 400f)).apply { name = "scene" }

    @Test
    fun `frame of a plain node is a zero-size rect at its position`() {
        val scene = scene()
        val plain = SKNode().apply { position = Vector2(10f, 20f) }
        scene.addChild(plain)

        assertEquals(Rect(10f, 20f, 10f, 20f), plain.frame)
    }

    @Test
    fun `frame excludes children and bounds a rotated, scaled node`() {
        val scene = scene()
        val a = sprite("a", 100f, 200f, 200f)
        a.addChild(sprite("b", 40f, 80f, 0f))
        scene.addChild(a)

        assertEquals(Rect(150f, 150f, 250f, 250f), a.frame)

        a.setScale(2f)
        a.zRotation = (PI / 4).toFloat()
        val frame = a.frame
        // Apple: (58.579, 58.579, 282.843, 282.843) as origin + size.
        assertEquals(58.579f, frame.left, 1e-2f)
        assertEquals(282.843f, frame.width, 1e-2f)
    }

    @Test
    fun `contains uses the accumulated frame with CGRect's half-open edges`() {
        val scene = scene()
        val a = sprite("a", 100f, 200f, 200f)
        a.addChild(sprite("b", 40f, 80f, 0f))
        scene.addChild(a)
        val sp = sprite("sp", 20f, 50f, 50f)
        scene.addChild(sp)

        assertTrue(a.contains(Vector2(285f, 200f))) // only inside child b
        assertTrue(sp.contains(Vector2(40f, 50f)))
        assertFalse(sp.contains(Vector2(60f, 50f)))
        assertFalse(SKNode().apply { scene.addChild(this) }.contains(Vector2.Zero))
    }

    @Test
    fun `atPoint and nodes order by zPosition, then children and later siblings in front`() {
        val scene = scene()
        val a = sprite("a", 100f, 200f, 200f)
        val b = sprite("b", 40f, 80f, 0f)
        a.addChild(b)
        val c = sprite("c", 100f, 220f, 200f).apply { zPosition = -1f }
        scene.addChild(a)
        scene.addChild(c)

        assertSame(b, scene.atPoint(Vector2(280f, 200f)))
        assertEquals(listOf(b, a), scene.nodes(Vector2(280f, 200f)))
        assertSame(a, scene.atPoint(Vector2(210f, 200f)))
        c.zPosition = 0f
        assertSame(c, scene.atPoint(Vector2(210f, 200f)))
        assertEquals(listOf(c, a), scene.nodes(Vector2(210f, 200f)))
    }

    @Test
    fun `atPoint falls back to the receiver and nodes excludes it`() {
        val scene = scene()
        scene.addChild(sprite("a", 100f, 200f, 200f))

        assertSame(scene, scene.atPoint(Vector2(5f, 5f)))
        assertSame(scene, scene.atPoint(Vector2(-50f, -50f)))
        assertEquals(emptyList(), scene.nodes(Vector2(5f, 5f)))
    }

    @Test
    fun `hidden and fully transparent subtrees are skipped`() {
        val scene = scene()
        val bg = sprite("bg", 400f, 200f, 200f)
        val p = sprite("p", 100f, 200f, 200f)
        val c = sprite("c", 20f, 0f, 0f)
        p.addChild(c)
        scene.addChild(bg)
        scene.addChild(p)
        val point = Vector2(200f, 200f)

        p.alpha = 0f
        assertEquals(listOf(bg), scene.nodes(point))
        p.alpha = 1f
        p.isHidden = true
        assertEquals(listOf(bg), scene.nodes(point))
        p.isHidden = false
        c.alpha = 0f
        assertEquals(listOf(p, bg), scene.nodes(point))
        c.alpha = 1f
        p.zPosition = -5f
        assertEquals(listOf(bg, c, p), scene.nodes(point))
    }

    @Test
    fun `a container is found in the gaps between its children and a plain node at its position`() {
        val scene = scene()
        val g = SKNode().apply { position = Vector2(200f, 200f) }
        val s0 = sprite("s0", 20f, -100f, 0f)
        val s1 = sprite("s1", 20f, 100f, 0f)
        g.addChild(s0)
        g.addChild(s1)
        scene.addChild(g)
        val plain = SKNode().apply { position = Vector2(50f, 350f) }
        scene.addChild(plain)

        assertSame(g, scene.atPoint(Vector2(210f, 200f)))
        assertEquals(listOf(s1, g), scene.nodes(Vector2(300f, 200f)))
        assertSame(plain, scene.atPoint(Vector2(50f, 350f)))
    }

    @Test
    fun `atPoint takes a point in the receiver's own space`() {
        val scene = scene()
        val a = sprite("a", 100f, 200f, 200f)
        val b = sprite("b", 40f, 80f, 0f)
        a.addChild(b)
        scene.addChild(a)

        assertSame(a, a.atPoint(Vector2(0f, 0f)))
        assertSame(b, a.atPoint(Vector2(80f, 0f)))
    }
}
