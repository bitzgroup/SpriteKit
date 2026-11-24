package jp.co.bitz.spritekit

import kotlin.math.pow

/**
 * Floor for a field's effective distance in [falloffScale], avoiding a literal division by zero at
 * the field's own position when [SKFieldNode.minimumRadius] is `0`.
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
 * Every enabled [SKFieldNode] in [scene], each paired with its current world-space position --
 * shared by [applyFieldForces] and [SKEmitterNode]'s own [SKFieldNode.fieldBitMask]-driven
 * particle forces.
 */
internal fun enabledFieldNodes(scene: SKScene): List<Pair<SKFieldNode, Vector2>> =
    collectFieldNodes(scene).filter { it.isEnabled }.map { it to it.convertTo(Vector2.Zero, scene) }

internal fun fieldAffects(
    field: SKFieldNode,
    fieldBitMask: Int,
): Boolean = (field.categoryBitMask and fieldBitMask) != 0

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
    val fields = enabledFieldNodes(scene)
    if (fields.isEmpty()) return

    val (velocityFields, forceFields) = fields.partition { it.first.kind == SKFieldKind.Velocity }
    for (entry in bodies) {
        if (!entry.body.isDynamic) continue
        val worldPosition = entry.node.convertTo(Vector2.Zero, scene)
        for ((field, fieldWorldPosition) in forceFields) {
            if (!fieldAffects(field, entry.body.fieldBitMask)) continue
            entry.body.velocity += fieldAcceleration(field, fieldWorldPosition, worldPosition, entry.body.velocity) * dt
        }
        for ((field, _) in velocityFields) {
            if (!fieldAffects(field, entry.body.fieldBitMask)) continue
            entry.body.velocity = fieldOverrideVelocity(field)
        }
    }
}

/**
 * The acceleration [field] (currently at [fieldWorldPosition]) contributes to something at
 * [worldPosition] moving at [velocity] -- position/velocity-based rather than
 * [SKPhysicsBody]-based, so both physics bodies and [SKEmitterNode] particles (which have no
 * physics body) can share this. Returns [Vector2.Zero] for a [SKFieldKind.Velocity] field, which
 * overrides velocity directly instead of accelerating it -- see [fieldOverrideVelocity].
 */
internal fun fieldAcceleration(
    field: SKFieldNode,
    fieldWorldPosition: Vector2,
    worldPosition: Vector2,
    velocity: Vector2,
): Vector2 =
    when (field.kind) {
        SKFieldKind.RadialGravity -> radialGravityAcceleration(field, fieldWorldPosition, worldPosition)
        SKFieldKind.LinearGravity -> linearGravityAcceleration(field, fieldWorldPosition, worldPosition)
        SKFieldKind.Drag -> velocity * -field.strength
        SKFieldKind.Velocity -> Vector2.Zero
    }

/** The velocity a [SKFieldKind.Velocity] field drives an affected body/particle directly towards. */
internal fun fieldOverrideVelocity(field: SKFieldNode): Vector2 = field.direction * field.strength

/**
 * How much [field]'s [SKFieldNode.falloff] weakens it at [distance] points from the field node, as
 * Apple's SpriteKit does (recorded on iOS/tvOS simulators): the distance — never less than
 * [SKFieldNode.minimumRadius] — is measured in meters of [POINTS_PER_METER] points and raised to
 * the `falloff` power, so a field has its full strength one meter (150 points) away.
 */
private fun falloffScale(
    field: SKFieldNode,
    distance: Float,
): Float {
    if (field.falloff == 0f) return 1f
    val meters = maxOf(distance, field.minimumRadius, MINIMUM_FIELD_DISTANCE) / POINTS_PER_METER
    return 1f / meters.pow(field.falloff)
}

/**
 * A linear gravity field's push: its `direction` (not normalized) in meters per second squared,
 * times `strength`, weakened by [falloffScale] with distance from the field node.
 */
private fun linearGravityAcceleration(
    field: SKFieldNode,
    fieldWorldPosition: Vector2,
    worldPosition: Vector2,
): Vector2 {
    val scale = field.strength * POINTS_PER_METER * falloffScale(field, (fieldWorldPosition - worldPosition).length())
    return field.direction * scale
}

/**
 * A radial gravity field's pull: towards the field node, at `strength` meters per second squared
 * ([POINTS_PER_METER] points per second squared) one meter away, weakened by [falloffScale].
 */
private fun radialGravityAcceleration(
    field: SKFieldNode,
    fieldWorldPosition: Vector2,
    worldPosition: Vector2,
): Vector2 {
    val toField = fieldWorldPosition - worldPosition
    val distance = toField.length()
    if (distance == 0f) return Vector2.Zero
    val direction = toField * (1f / distance)
    return direction * (field.strength * POINTS_PER_METER * falloffScale(field, distance))
}
