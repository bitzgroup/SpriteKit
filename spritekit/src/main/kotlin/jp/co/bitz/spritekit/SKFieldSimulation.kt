package jp.co.bitz.spritekit

import kotlin.math.pow

/**
 * Floor for a [SKFieldKind.RadialGravity] field's effective distance, avoiding a literal division
 * by zero at the field's own position.
 */
private const val MINIMUM_FIELD_DISTANCE = 1e-6f

/**
 * Every [SKFieldNode] in [node]'s subtree, skipping [SKNode.isPaused] subtrees -- the same scope
 * the physics body/joint collectors use.
 */
private fun collectFieldNodes(
    node: SKNode,
    out: MutableList<SKFieldNode> = mutableListOf(),
): List<SKFieldNode> {
    if (node.isPaused) return out
    if (node is SKFieldNode) out += node
    for (child in node.children) collectFieldNodes(child, out)
    return out
}

/**
 * Applies every enabled [SKFieldNode] in [scene] to every matching body in [bodies]' velocity for
 * this step -- called once per frame, alongside gravity/force integration. Force-based fields
 * ([SKFieldKind.RadialGravity]/[SKFieldKind.LinearGravity]/[SKFieldKind.Drag]) are integrated
 * first; [SKFieldKind.Velocity] fields are applied afterward, as a direct override, since Apple
 * documents that kind as setting velocity rather than accelerating it.
 */
internal fun applyFieldForces(
    scene: SKScene,
    bodies: List<SKPhysicsEntry>,
    dt: Float,
) {
    val fields = collectFieldNodes(scene).filter { it.isEnabled }
    if (fields.isEmpty()) return

    val (velocityFields, forceFields) = fields.partition { it.kind == SKFieldKind.Velocity }
    for (entry in bodies) {
        if (!entry.body.isDynamic) continue
        for (field in forceFields) applyForceField(field, entry, scene, dt)
        for (field in velocityFields) applyVelocityField(field, entry)
    }
}

private fun fieldAffects(
    field: SKFieldNode,
    body: SKPhysicsBody,
): Boolean = (field.categoryBitMask and body.fieldBitMask) != 0

private fun applyForceField(
    field: SKFieldNode,
    entry: SKPhysicsEntry,
    scene: SKScene,
    dt: Float,
) {
    if (!fieldAffects(field, entry.body)) return
    val acceleration =
        when (field.kind) {
            SKFieldKind.RadialGravity -> radialGravityAcceleration(field, entry.node, scene)
            SKFieldKind.LinearGravity -> linearGravityAcceleration(field)
            SKFieldKind.Drag -> dragAcceleration(field, entry.body)
            SKFieldKind.Velocity -> return // handled by applyVelocityField instead
        }
    entry.body.velocity += acceleration * dt
}

private fun radialGravityAcceleration(
    field: SKFieldNode,
    node: SKNode,
    scene: SKScene,
): Vector2 {
    val toField = field.convertTo(Vector2.Zero, scene) - node.convertTo(Vector2.Zero, scene)
    val distance = toField.length()
    if (distance == 0f) return Vector2.Zero
    val direction = toField * (1f / distance)
    val effectiveDistance = maxOf(distance, field.minimumRadius, MINIMUM_FIELD_DISTANCE)
    val magnitude = if (field.falloff == 0f) field.strength else field.strength / effectiveDistance.pow(field.falloff)
    return direction * magnitude
}

private fun linearGravityAcceleration(field: SKFieldNode): Vector2 = field.direction.normalized() * field.strength

private fun dragAcceleration(
    field: SKFieldNode,
    body: SKPhysicsBody,
): Vector2 = body.velocity * -field.strength

private fun applyVelocityField(
    field: SKFieldNode,
    entry: SKPhysicsEntry,
) {
    if (!fieldAffects(field, entry.body)) return
    entry.body.velocity = field.direction * field.strength
}
