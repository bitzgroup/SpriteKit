package jp.co.bitz.spritekit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.seconds

/**
 * Mirrors tic-tac-toe's `SKNodeSearchParityTests` (Apple's behavior, recorded on iOS/tvOS
 * simulators). Every test searches this tree:
 *
 *     root
 *     ├── a
 *     │   ├── b
 *     │   │   └── c
 *     │   └── b2
 *     ├── bb
 *     │   └── b
 *     └── ab
 */
class SKNodeSearchTest {
    private fun node(
        name: String?,
        vararg children: SKNode,
    ): SKNode =
        SKNode().also { node ->
            node.name = name
            children.forEach(node::addChild)
        }

    private val root = node("root", node("a", node("b", node("c")), node("b2")), node("bb", node("b")), node("ab"))
    private val a = root.children[0]
    private val b = a.children[0]

    private fun names(nodes: List<SKNode>) = nodes.map { it.name }

    @Test
    fun `a plain name matches direct children only`() {
        assertNull(root.childNode("b"))
        assertEquals(listOf("b"), names(a["b"]))
        val enumerated = mutableListOf<SKNode>()
        root.enumerateChildNodes("b") { enumerated += it }
        assertEquals(emptyList(), enumerated)
    }

    @Test
    fun `wildcards and character classes match whole names`() {
        assertEquals(listOf("a", "bb", "ab"), names(root["*"]))
        assertEquals(listOf("bb"), names(root["b*"]))
        assertEquals(listOf("a"), names(root["[ab]"]))
        assertEquals(listOf("a", "bb", "ab"), names(root["[a-b]*"]))
        assertEquals(listOf("b", "b2"), names(a["b*"]))
    }

    @Test
    fun `wildcards match unnamed nodes`() {
        val parent = node(null, node(null))
        assertEquals(1, parent["*"].size)
        assertEquals(1, parent["//*"].size)
    }

    @Test
    fun `slash separates path steps from the searching node`() {
        assertEquals(listOf("b"), names(root["a/b"]))
        assertEquals(listOf("b", "b2"), names(root["a/*"]))
        assertEquals(listOf("b", "b"), names(root["*/b"]))
        assertSame(b, root.childNode("*/b"))
        assertEquals(listOf("c"), names(root["a//c"]))
    }

    @Test
    fun `dot is the node itself and dot dot its parent`() {
        assertSame(a, a.childNode("."))
        assertSame(root, a.childNode(".."))
        assertSame(b, a.childNode("./b"))
        assertEquals(listOf("b2"), names(b["../b2"]))
        assertEquals(listOf("bb"), names(root["a/../bb"]))
    }

    @Test
    fun `a leading slash searches from the tree's root whose own name is not matched`() {
        assertSame(a, b.childNode("/a"))
        assertEquals(listOf("b"), names(b["/a/b"]))
        assertNull(b.childNode("/root"))
        assertNull(b.childNode("//root"))
        val orphan = node("orphan", node("x"))
        assertEquals("x", orphan.childNode("/x")?.name)
    }

    @Test
    fun `a leading double slash lists each node's children before descending`() {
        val expected = listOf("a", "bb", "ab", "b", "b2", "c", "b")
        assertEquals(expected, names(b["//*"]))
        val enumerated = mutableListOf<SKNode>()
        b.enumerateChildNodes("//*") { enumerated += it }
        assertEquals(expected, names(enumerated))
        assertEquals(listOf("b", "b"), names(root["//b"]))
        assertSame(b, root.childNode("//b"))
        assertEquals(listOf("c"), names(root["//b/c"]))
    }

    @Test
    fun `a bare slash or double slash matches nothing and an empty string is rejected`() {
        assertNull(root.childNode("/"))
        assertNull(root.childNode("//"))
        assertFailsWith<IllegalArgumentException> { root.childNode("") }
    }

    @Test
    fun `run onChildWithName uses the search syntax and runs on the first match only`() {
        val otherB = root.children[1].children[0]
        root.run(SKAction.run(SKAction.moveBy(Vector2(3f, 0f), 0.seconds), onChildWithName = "//b"))
        root.run(SKAction.run(SKAction.moveBy(Vector2(7f, 0f), 0.seconds), onChildWithName = "b"))
        root.stepActions(0.1.seconds)
        b.stepActions(0.1.seconds)
        otherB.stepActions(0.1.seconds)
        assertEquals(3f, b.position.x)
        assertEquals(0f, otherB.position.x)
    }
}
