package jp.co.bitz.spritekit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SKTouchDispatchTest {
    /**
     * A hit-testable (via [SKSpriteNode]'s real [SKNode]-overridden bounds) node that records
     * every touch callback it receives.
     */
    private class RecordingSprite(size: Vector2) : SKSpriteNode(size = size) {
        val began = mutableListOf<SKTouch>()
        val moved = mutableListOf<SKTouch>()
        val ended = mutableListOf<SKTouch>()
        val cancelled = mutableListOf<SKTouch>()

        val beganCalls = mutableListOf<Set<SKTouch>>()
        val movedCalls = mutableListOf<Set<SKTouch>>()
        var lastEvent: SKEvent? = null

        override fun touchesBegan(
            touches: Set<SKTouch>,
            event: SKEvent?,
        ) {
            beganCalls += touches
            began += touches
            lastEvent = event
        }

        override fun touchesMoved(
            touches: Set<SKTouch>,
            event: SKEvent?,
        ) {
            movedCalls += touches
            moved += touches
            lastEvent = event
        }

        override fun touchesEnded(
            touches: Set<SKTouch>,
            event: SKEvent?,
        ) {
            ended += touches
            lastEvent = event
        }

        override fun touchesCancelled(
            touches: Set<SKTouch>,
            event: SKEvent?,
        ) {
            cancelled += touches
            lastEvent = event
        }
    }

    private fun sprite(): RecordingSprite = RecordingSprite(Vector2(10f, 10f)).apply { isUserInteractionEnabled = true }

    @Test
    fun `viewToScenePoint maps the view's top-left to the projection's left,top corner`() {
        val projection = SKSceneProjection(left = 0f, right = 100f, bottom = 0f, top = 100f)

        val point = viewToScenePoint(viewX = 0f, viewY = 0f, projection, viewWidth = 200, viewHeight = 200)

        assertEquals(Vector2(0f, 100f), point)
    }

    @Test
    fun `viewToScenePoint maps the view's center to the projection's center`() {
        val projection = SKSceneProjection(left = 0f, right = 100f, bottom = 0f, top = 100f)

        val point = viewToScenePoint(viewX = 100f, viewY = 100f, projection, viewWidth = 200, viewHeight = 200)

        assertEquals(Vector2(50f, 50f), point)
    }

    @Test
    fun `viewToScenePoint maps the view's bottom-right to the projection's right,bottom corner`() {
        val projection = SKSceneProjection(left = 0f, right = 100f, bottom = 0f, top = 100f)

        val point = viewToScenePoint(viewX = 200f, viewY = 200f, projection, viewWidth = 200, viewHeight = 200)

        assertEquals(Vector2(100f, 0f), point)
    }

    @Test
    fun `a touch inside an interactive node's bounds is delivered as touchesBegan`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val node = sprite().apply { position = Vector2(50f, 50f) }
        scene.addChild(node)

        dispatchTouches(
            scene,
            listOf(SKTouchEvent(0, 100f, 100f, SKTouchPhase.Began)),
            viewWidth = 200,
            viewHeight = 200,
        )

        assertEquals(1, node.began.size)
    }

    @Test
    fun `a touch outside every interactive node falls through to the scene itself`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val node = sprite().apply { position = Vector2(90f, 90f) }
        scene.addChild(node)

        // The scene defaults isUserInteractionEnabled = true and covers its whole size.
        dispatchTouches(scene, listOf(SKTouchEvent(0, 0f, 200f, SKTouchPhase.Began)), viewWidth = 200, viewHeight = 200)

        assertEquals(0, node.began.size)
        assertEquals(scene, scene.activeTouchTargets[0])
    }

    @Test
    fun `a non-interactive node never receives touches, even directly under the touch point`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val node =
            SKSpriteNode(size = Vector2(10f, 10f)).apply {
                position = Vector2(50f, 50f)
            } // isUserInteractionEnabled left false
        scene.addChild(node)

        dispatchTouches(
            scene,
            listOf(SKTouchEvent(0, 100f, 100f, SKTouchPhase.Began)),
            viewWidth = 200,
            viewHeight = 200,
        )

        // Falls through to the scene instead of the node directly underneath the touch.
        assertEquals(scene, scene.activeTouchTargets[0])
    }

    @Test
    fun `the frontmost of two overlapping interactive nodes receives the touch`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val back =
            sprite().apply {
                position = Vector2(50f, 50f)
                zPosition = 0f
            }
        val front =
            sprite().apply {
                position = Vector2(50f, 50f)
                zPosition = 1f
            }
        scene.addChild(back)
        scene.addChild(front)

        dispatchTouches(
            scene,
            listOf(SKTouchEvent(0, 100f, 100f, SKTouchPhase.Began)),
            viewWidth = 200,
            viewHeight = 200,
        )

        assertEquals(front, scene.activeTouchTargets[0])
    }

    @Test
    fun `a container's zPosition lifts its subtree above a sibling even when children default to zero`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val backLayer = SKNode()
        val frontLayer = SKNode().apply { zPosition = 1f }
        scene.addChild(backLayer)
        scene.addChild(frontLayer)

        // Neither child sets its own zPosition -- only the containers do, matching Apple's
        // documented "zPosition is relative to the parent" rule.
        val back = sprite().apply { position = Vector2(50f, 50f) }
        val front = sprite().apply { position = Vector2(50f, 50f) }
        backLayer.addChild(back)
        frontLayer.addChild(front)

        dispatchTouches(
            scene,
            listOf(SKTouchEvent(0, 100f, 100f, SKTouchPhase.Began)),
            viewWidth = 200,
            viewHeight = 200,
        )

        assertEquals(front, scene.activeTouchTargets[0])
    }

    @Test
    fun `a moved touch is delivered to the node that received touchesBegan, not re-hit-tested`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val node = sprite().apply { position = Vector2(50f, 50f) }
        scene.addChild(node)
        dispatchTouches(
            scene,
            listOf(SKTouchEvent(0, 100f, 100f, SKTouchPhase.Began)),
            viewWidth = 200,
            viewHeight = 200,
        )

        // Now well outside node's bounds -- still delivered to node, since it's tracking this pointer.
        dispatchTouches(scene, listOf(SKTouchEvent(0, 0f, 0f, SKTouchPhase.Moved)), viewWidth = 200, viewHeight = 200)

        assertEquals(1, node.moved.size)
    }

    @Test
    fun `touchesEnded stops tracking the pointer`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val node = sprite().apply { position = Vector2(50f, 50f) }
        scene.addChild(node)
        dispatchTouches(
            scene,
            listOf(SKTouchEvent(0, 100f, 100f, SKTouchPhase.Began)),
            viewWidth = 200,
            viewHeight = 200,
        )

        dispatchTouches(
            scene,
            listOf(SKTouchEvent(0, 100f, 100f, SKTouchPhase.Ended)),
            viewWidth = 200,
            viewHeight = 200,
        )

        assertEquals(1, node.ended.size)
        assertTrue(scene.activeTouchTargets.isEmpty())
    }

    @Test
    fun `touchesCancelled stops tracking the pointer`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val node = sprite().apply { position = Vector2(50f, 50f) }
        scene.addChild(node)
        dispatchTouches(
            scene,
            listOf(SKTouchEvent(0, 100f, 100f, SKTouchPhase.Began)),
            viewWidth = 200,
            viewHeight = 200,
        )

        dispatchTouches(
            scene,
            listOf(SKTouchEvent(0, 100f, 100f, SKTouchPhase.Cancelled)),
            viewWidth = 200,
            viewHeight = 200,
        )

        assertEquals(1, node.cancelled.size)
        assertTrue(scene.activeTouchTargets.isEmpty())
    }

    @Test
    fun `a moved or ended touch with no tracked target is silently ignored`() {
        val scene = SKScene(size = Vector2(100f, 100f))

        dispatchTouches(
            scene,
            listOf(SKTouchEvent(0, 100f, 100f, SKTouchPhase.Moved)),
            viewWidth = 200,
            viewHeight = 200,
        )
        dispatchTouches(
            scene,
            listOf(SKTouchEvent(0, 100f, 100f, SKTouchPhase.Ended)),
            viewWidth = 200,
            viewHeight = 200,
        )

        assertTrue(scene.activeTouchTargets.isEmpty())
    }

    @Test
    fun `a hidden interactive node is never hit`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val node =
            sprite().apply {
                position = Vector2(50f, 50f)
                isHidden = true
            }
        scene.addChild(node)

        dispatchTouches(
            scene,
            listOf(SKTouchEvent(0, 100f, 100f, SKTouchPhase.Began)),
            viewWidth = 200,
            viewHeight = 200,
        )

        assertEquals(scene, scene.activeTouchTargets[0]) // falls through to the scene
    }

    @Test
    fun `dispatchTouches is a no-op before the view's size is known`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val node = sprite().apply { position = Vector2(50f, 50f) }
        scene.addChild(node)

        dispatchTouches(scene, listOf(SKTouchEvent(0, 100f, 100f, SKTouchPhase.Began)), viewWidth = 0, viewHeight = 0)

        assertNull(scene.activeTouchTargets[0])
    }

    @Test
    fun `SKTouch location converts into whichever node it's asked about, like UITouch location(in)`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val node = sprite().apply { position = Vector2(50f, 50f) } // node's local origin coincides with the touch point

        scene.addChild(node)
        dispatchTouches(
            scene,
            listOf(SKTouchEvent(0, 100f, 100f, SKTouchPhase.Began)),
            viewWidth = 200,
            viewHeight = 200,
        )

        val touch = node.began.single()
        assertEquals(Vector2.Zero, touch.location(node))
        assertEquals(Vector2(50f, 50f), touch.location(scene))
    }

    @Test
    fun `the same SKTouch instance persists from began through ended, tracking its previous location`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val node = sprite().apply { position = Vector2(50f, 50f) }
        scene.addChild(node)

        dispatchTouches(
            scene,
            listOf(SKTouchEvent(0, 100f, 100f, SKTouchPhase.Began)),
            viewWidth = 200,
            viewHeight = 200,
        )
        dispatchTouches(
            scene,
            listOf(SKTouchEvent(0, 110f, 100f, SKTouchPhase.Moved)),
            viewWidth = 200,
            viewHeight = 200,
        )
        dispatchTouches(
            scene,
            listOf(SKTouchEvent(0, 110f, 100f, SKTouchPhase.Ended)),
            viewWidth = 200,
            viewHeight = 200,
        )

        val touch = node.began.single()
        assertSame(touch, node.moved.single())
        assertSame(touch, node.ended.single())
        assertEquals(SKTouchPhase.Ended, touch.phase)
        assertEquals(Vector2(55f, 50f), touch.location(scene))
        assertEquals(Vector2(55f, 50f), touch.previousLocation(scene))
    }

    @Test
    fun `touches sharing a phase and target node arrive together in one call`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val node =
            RecordingSprite(Vector2(100f, 100f)).apply {
                isUserInteractionEnabled = true
                position = Vector2(50f, 50f)
            }
        scene.addChild(node)
        dispatchTouches(
            scene,
            listOf(SKTouchEvent(0, 50f, 50f, SKTouchPhase.Began)),
            viewWidth = 200,
            viewHeight = 200,
        )
        dispatchTouches(
            scene,
            listOf(SKTouchEvent(1, 150f, 150f, SKTouchPhase.Began)),
            viewWidth = 200,
            viewHeight = 200,
        )

        dispatchTouches(
            scene,
            listOf(SKTouchEvent(0, 60f, 50f, SKTouchPhase.Moved), SKTouchEvent(1, 160f, 150f, SKTouchPhase.Moved)),
            viewWidth = 200,
            viewHeight = 200,
        )

        assertEquals(2, node.beganCalls.size)
        assertEquals(1, node.movedCalls.size)
        assertEquals(2, node.movedCalls.single().size)
        assertEquals(node.began.toSet(), node.lastEvent?.allTouches)
    }

    @Test
    fun `a pointer reported as moved without moving is left out, like a stationary UITouch`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val node = sprite().apply { position = Vector2(50f, 50f) }
        scene.addChild(node)
        dispatchTouches(
            scene,
            listOf(SKTouchEvent(0, 100f, 100f, SKTouchPhase.Began)),
            viewWidth = 200,
            viewHeight = 200,
        )

        dispatchTouches(
            scene,
            listOf(SKTouchEvent(0, 100f, 100f, SKTouchPhase.Moved)),
            viewWidth = 200,
            viewHeight = 200,
        )

        assertTrue(node.moved.isEmpty())
    }
}
