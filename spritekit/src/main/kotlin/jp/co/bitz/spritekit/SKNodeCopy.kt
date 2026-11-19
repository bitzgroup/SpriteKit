package jp.co.bitz.spritekit

import java.util.IdentityHashMap

/**
 * Points the references inside a freshly copied subtree at the copies, as Apple's `copy()` does
 * (it archives the subtree, preserving shared references): a copied scene's camera becomes the
 * copy of its camera node — or a detached copy, if the camera wasn't in the scene — and a crop
 * node's mask that is also one of the subtree's nodes becomes that node's copy. [copy] mirrors
 * [original]'s tree exactly. See [SKNode.copy].
 */
internal fun linkCopiedReferences(
    original: SKNode,
    copy: SKNode,
) {
    val copies = IdentityHashMap<SKNode, SKNode>()
    pairCopies(original, copy, copies)
    for ((from, to) in copies) {
        if (from is SKScene && to is SKScene) {
            to.camera = from.camera?.let { camera -> copies[camera] as? SKCameraNode ?: camera.copy() }
        }
        if (from is SKCropNode && to is SKCropNode) {
            from.maskNode?.let { copies[it] }?.let { to.maskNode = it }
        }
    }
}

/** Maps every node of [original]'s subtree to its counterpart in [copy]'s, which mirrors it. */
private fun pairCopies(
    original: SKNode,
    copy: SKNode,
    copies: MutableMap<SKNode, SKNode>,
) {
    copies[original] = copy
    original.children.zip(copy.children).forEach { (from, to) -> pairCopies(from, to, copies) }
}

/** A new body with this one's shape, settings and velocity, not yet attached to a node. */
internal fun SKPhysicsBody.copyBody(): SKPhysicsBody =
    SKPhysicsBody(shape).also {
        it.density = density
        it.friction = friction
        it.restitution = restitution
        it.linearDamping = linearDamping
        it.angularDamping = angularDamping
        it.isDynamic = isDynamic
        it.affectedByGravity = affectedByGravity
        it.allowsRotation = allowsRotation
        it.pinned = pinned
        it.usesPreciseCollisionDetection = usesPreciseCollisionDetection
        it.categoryBitMask = categoryBitMask
        it.collisionBitMask = collisionBitMask
        it.contactTestBitMask = contactTestBitMask
        it.fieldBitMask = fieldBitMask
        it.velocity = velocity
        it.angularVelocity = angularVelocity
    }
