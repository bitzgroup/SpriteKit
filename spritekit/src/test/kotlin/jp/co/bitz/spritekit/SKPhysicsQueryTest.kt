package jp.co.bitz.spritekit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** Mirrors tic-tac-toe's `SKPhysicsQueryParityTests` (Apple's behavior, recorded on iOS/tvOS simulators). */
class SKPhysicsQueryTest {
    private val scene = SKScene(size = Vector2(400f, 400f))
    private val world = scene.physicsWorld

    private fun add(
        name: String,
        body: SKPhysicsBody,
        at: Vector2,
        parent: SKNode = scene,
    ): SKNode =
        SKNode().also {
            it.name = name
            it.position = at
            body.isDynamic = false
            it.physicsBody = body
            parent.addChild(it)
        }

    private fun namesAt(point: Vector2): Set<String?> {
        val found = mutableSetOf<String?>()
        world.enumerateBodies(at = point) { found += it.node?.name }
        return found
    }

    private fun namesIn(rect: Rect): Set<String?> {
        val found = mutableSetOf<String?>()
        world.enumerateBodies(rect) { found += it.node?.name }
        return found
    }

    @Test
    fun `body at tests the exact shape`() {
        add("a", SKPhysicsBody.circleOfRadius(20f), Vector2(100f, 100f))
        add("b", SKPhysicsBody.circleOfRadius(20f), Vector2(110f, 100f))
        assertEquals(setOf("a", "b"), namesAt(Vector2(105f, 100f)))
        assertEquals(setOf("a"), namesAt(Vector2(85f, 100f)))
        assertNull(world.body(at = Vector2(84f, 84f)))
    }

    @Test
    fun `body at follows the node's rotation and its ancestors' scale`() {
        val box = add("box", SKPhysicsBody.rectangleOf(Vector2(40f, 20f)), Vector2(300f, 100f))
        box.zRotation = (Math.PI / 2).toFloat()
        val parent =
            SKNode().apply {
                position = Vector2(50f, 300f)
                xScale = 2f
                yScale = 2f
            }
        scene.addChild(parent)
        add("child", SKPhysicsBody.circleOfRadius(5f), Vector2(10f, 0f), parent)
        assertEquals("box", world.body(at = Vector2(300f, 115f))?.node?.name)
        assertNull(world.body(at = Vector2(315f, 100f)))
        assertEquals("child", world.body(at = Vector2(78f, 300f))?.node?.name)
    }

    @Test
    fun `body at never finds an edge body and ignores the category mask`() {
        add("edge", SKPhysicsBody.edgeLoopFrom(Rect(-20f, -20f, 20f, 20f)), Vector2(200f, 300f))
        assertNull(world.body(at = Vector2(200f, 300f)))
        assertNull(world.body(at = Vector2(220f, 300f)))
        add("masked", SKPhysicsBody.circleOfRadius(5f), Vector2(50f, 50f)).physicsBody!!.categoryBitMask = 0
        assertEquals("masked", world.body(at = Vector2(50f, 50f))?.node?.name)
    }

    @Test
    fun `body in matches bounding boxes padded by about fifteen points`() {
        add("a", SKPhysicsBody.circleOfRadius(20f), Vector2(100f, 100f)) // box from x = 80
        add("b", SKPhysicsBody.circleOfRadius(20f), Vector2(110f, 100f)) // box from x = 90

        fun endingAt(x: Float) = Rect(x - 4f, 98f, x, 102f)
        assertEquals(setOf("a", "b"), namesIn(Rect(81f, 81f, 83f, 83f)))
        assertEquals(setOf("a", "b"), namesIn(endingAt(78f)))
        assertEquals(setOf("a"), namesIn(endingAt(70f)))
        assertEquals(emptySet(), namesIn(endingAt(64f)))
        assertNull(world.body(endingAt(64f)))
    }

    @Test
    fun `body in finds an edge body through its bounding box`() {
        add("loop", SKPhysicsBody.edgeLoopFrom(Rect(-20f, -20f, 20f, 20f)), Vector2(200f, 300f))
        add("line", SKPhysicsBody.edgeFrom(Vector2(0f, 0f), Vector2(100f, 0f)), Vector2(0f, 200f))
        assertEquals("loop", world.body(Rect(195f, 295f, 205f, 305f))?.node?.name)
        assertEquals("line", world.body(Rect(40f, 202f, 50f, 212f))?.node?.name)
        assertNull(world.body(Rect(40f, 220f, 50f, 230f)))
    }

    @Test
    fun `ray hits come nearest first with a normal of length 150 facing the ray`() {
        add("far", SKPhysicsBody.circleOfRadius(5f), Vector2(350f, 30f))
        add("near", SKPhysicsBody.circleOfRadius(5f), Vector2(50f, 30f))
        val hits = mutableListOf<Triple<String?, Vector2, Vector2>>()
        world.enumerateBodies(
            Vector2(0f, 30f),
            Vector2(400f, 30f),
        ) { body, point, normal -> hits += Triple(body.node?.name, point, normal) }
        assertEquals(listOf("near", "far"), hits.map { it.first })
        assertEquals(45f, hits[0].second.x, 0.01f)
        assertEquals(345f, hits[1].second.x, 0.01f)
        assertEquals(Vector2(-150f, 0f), hits[0].third)
        assertEquals("near", world.body(Vector2(0f, 30f), Vector2(400f, 30f))?.node?.name)
        assertEquals("far", world.body(Vector2(400f, 30f), Vector2(0f, 30f))?.node?.name)
    }

    @Test
    fun `a ray misses bodies beyond its end and one it starts inside`() {
        add("near", SKPhysicsBody.circleOfRadius(5f), Vector2(50f, 30f))
        add("far", SKPhysicsBody.circleOfRadius(5f), Vector2(350f, 30f))
        assertNull(world.body(Vector2(0f, 30f), Vector2(40f, 30f)))
        assertEquals("far", world.body(Vector2(50f, 30f), Vector2(400f, 30f))?.node?.name)
    }

    @Test
    fun `a ray enters a polygon once and hits an edge loop on each side it crosses`() {
        add("box", SKPhysicsBody.rectangleOf(Vector2(40f, 20f)), Vector2(300f, 100f))
        val boxHits = mutableListOf<Vector2>()
        world.enumerateBodies(Vector2(0f, 100f), Vector2(400f, 100f)) { _, point, _ -> boxHits += point }
        assertEquals(listOf(Vector2(280f, 100f)), boxHits)

        val loop = add("loop", SKPhysicsBody.edgeLoopFrom(Rect(-20f, -20f, 20f, 20f)), Vector2(200f, 300f))
        val hits = mutableListOf<Pair<Vector2, Vector2>>()
        world.enumerateBodies(Vector2(200f, 0f), Vector2(200f, 400f)) { body, point, normal ->
            assertSame(loop.physicsBody, body)
            hits += point to normal
        }
        assertEquals(listOf(280f, 320f), hits.map { it.first.y })
        assertEquals(listOf(-150f, -150f), hits.map { it.second.y })
    }
}
