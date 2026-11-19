package jp.co.bitz.spritekit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

private class Enemy : SKSpriteNode() {
    var hitPoints = 0

    override fun copy(): Enemy = copyInto(Enemy()).also { it.hitPoints = hitPoints }
}

/** Mirrors the tic-tac-toe sample's `SKNodeCopyParityTests` probes. */
class SKNodeCopyTest {
    @Test
    fun `the copy has the same properties and no parent`() {
        val scene = SKScene(size = Vector2(100f, 100f))
        val node =
            SKSpriteNode(color = 0xFFFF0000.toInt(), size = Vector2(10f, 20f)).apply {
                name = "n"
                position = Vector2(3f, 4f)
                zPosition = 2f
                zRotation = 1f
                xScale = 2f
                alpha = 0.5f
                isHidden = true
                isPaused = true
                speed = 3f
                isUserInteractionEnabled = true
                colorBlendFactor = 0.25f
                anchorPoint = Vector2(0f, 1f)
                blendMode = SKBlendMode.Add
            }
        scene.addChild(node)
        val copy = node.copy()
        assertNull(copy.parent)
        assertNull(copy.scene)
        assertEquals("n", copy.name)
        assertEquals(Vector2(3f, 4f), copy.position)
        assertEquals(2f, copy.zPosition)
        assertEquals(1f, copy.zRotation)
        assertEquals(2f, copy.xScale)
        assertEquals(0.5f, copy.alpha)
        assertTrue(copy.isHidden)
        assertTrue(copy.isPaused)
        assertEquals(3f, copy.speed)
        assertTrue(copy.isUserInteractionEnabled)
        assertEquals(node.size, copy.size)
        assertEquals(node.color, copy.color)
        assertEquals(0.25f, copy.colorBlendFactor)
        assertEquals(Vector2(0f, 1f), copy.anchorPoint)
        assertEquals(SKBlendMode.Add, copy.blendMode)
    }

    @Test
    fun `children are copied deeply`() {
        val node = SKNode()
        val label = SKLabelNode("child").apply { fontSize = 13f }
        node.addChild(label)
        val shape = SKShapeNode().apply { lineWidth = 4f }
        label.addChild(shape)
        val copy = node.copy()
        val copiedLabel = assertIs<SKLabelNode>(copy.children.single())
        assertNotSame(label, copiedLabel)
        assertSame(copy, copiedLabel.parent)
        assertEquals("child", copiedLabel.text)
        assertEquals(13f, copiedLabel.fontSize)
        val copiedShape = assertIs<SKShapeNode>(copiedLabel.children.single())
        assertNotSame(shape, copiedShape)
        assertEquals(4f, copiedShape.lineWidth)
    }

    @Test
    fun `the physics body and user data are copied, constraints and textures shared`() {
        val node = SKSpriteNode()
        node.physicsBody =
            SKPhysicsBody.circleOfRadius(5f).apply {
                mass = 7f
                categoryBitMask = 4
                velocity = Vector2(100f, 0f)
            }
        val inner = mutableListOf(1)
        node.userData = mutableMapOf("k" to inner)
        val constraint = SKConstraint.zRotation(SKRange.constant(0f))
        node.constraints = listOf(constraint)
        val copy = node.copy()
        val body = assertNotNull(copy.physicsBody)
        assertNotSame(node.physicsBody, body)
        assertSame(copy, body.node)
        assertSame(node, node.physicsBody?.node)
        assertEquals(7f, body.mass, 0.001f)
        assertEquals(4, body.categoryBitMask)
        assertEquals(Vector2(100f, 0f), body.velocity)
        assertNotSame(node.userData, copy.userData)
        assertSame(inner, copy.userData?.get("k"))
        assertSame(constraint, copy.constraints?.single())
    }

    @Test
    fun `running actions are copied and start over`() {
        val mover = SKNode()
        mover.run(SKAction.moveBy(Vector2(100f, 0f), 1.seconds), withKey = "move")
        mover.stepActions(0.5.seconds)
        val copy = mover.copy()
        assertNotNull(copy.action("move"))
        val start = copy.position.x
        assertEquals(50f, start, 0.01f)
        copy.stepActions(1.seconds)
        assertEquals(start + 100f, copy.position.x, 0.01f)
    }

    @Test
    fun `a subclass that overrides copy keeps its class and its own properties`() {
        val enemy = Enemy().apply { hitPoints = 9 }
        val copy = enemy.copy()
        assertEquals(9, copy.hitPoints)
    }

    @Test
    fun `a crop node's mask is copied, and a mask in the subtree becomes its copy`() {
        val crop = SKCropNode(maskNode = SKSpriteNode(size = Vector2(4f, 4f)))
        val copy = crop.copy()
        assertNotSame(crop.maskNode, copy.maskNode)
        assertEquals(Vector2(4f, 4f), (copy.maskNode as SKSpriteNode).size)

        val mask = SKSpriteNode()
        val inTree = SKCropNode(maskNode = mask).apply { addChild(mask) }
        val treeCopy = inTree.copy()
        assertSame(treeCopy.children.single(), treeCopy.maskNode)
    }

    @Test
    fun `a copied scene points at its own camera and drops its delegates`() {
        val scene =
            SKScene(size = Vector2(100f, 50f)).apply {
                scaleMode = SKSceneScaleMode.AspectFit
                anchorPoint = Vector2(0.5f, 0.5f)
                backgroundColor = 0xFFFF0000.toInt()
                physicsWorld.gravity = Vector2(1f, 2f)
                physicsWorld.speed = 0.5f
                delegate = object : SKSceneDelegate {}
                physicsWorld.contactDelegate = object : SKPhysicsContactDelegate {}
            }
        val holder = SKNode()
        val camera = SKCameraNode()
        holder.addChild(camera)
        scene.addChild(holder)
        scene.camera = camera
        val copy = scene.copy()
        assertEquals(Vector2(100f, 50f), copy.size)
        assertEquals(SKSceneScaleMode.AspectFit, copy.scaleMode)
        assertEquals(Vector2(0.5f, 0.5f), copy.anchorPoint)
        assertEquals(0xFFFF0000.toInt(), copy.backgroundColor)
        assertEquals(Vector2(1f, 2f), copy.physicsWorld.gravity)
        assertEquals(0.5f, copy.physicsWorld.speed)
        assertSame<SKNode?>(copy.children.single().children.single(), copy.camera)
        assertNull(copy.delegate)
        assertNull(copy.physicsWorld.contactDelegate)
    }

    @Test
    fun `an emitter keeps its settings`() {
        val emitter =
            SKEmitterNode().apply {
                particleBirthRate = 5f
                particleLifetime = 2f
                particleColor = 0xFF00FF00.toInt()
            }
        val copy = emitter.copy()
        assertEquals(5f, copy.particleBirthRate)
        assertEquals(2f, copy.particleLifetime)
        assertEquals(0xFF00FF00.toInt(), copy.particleColor)
    }
}
