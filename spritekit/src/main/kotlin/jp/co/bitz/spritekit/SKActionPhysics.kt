package jp.co.bitz.spritekit

import kotlin.time.Duration
import kotlin.time.DurationUnit

/**
 * Applies [kind] if it's one of the physics kinds (forces, impulses, mass, charge, field
 * strength; falloff never gets here, see `stepLeaf`), returning whether it was — split out of
 * `applyLeafEffect` like the transform kinds.
 * They act on the node's [SKNode.physicsBody], or its [SKFieldNode] settings, and do nothing to
 * a node without one.
 */
internal fun applyPhysicsEffect(
    state: SKActionState,
    kind: SKActionKind,
    node: SKNode,
    progress: Float,
): Boolean = applyBodyEffect(state, kind, node, progress) || applyFieldEffect(state, kind, node, progress)

private fun applyBodyEffect(
    state: SKActionState,
    kind: SKActionKind,
    node: SKNode,
    progress: Float,
): Boolean {
    when (kind) {
        is SKActionKind.ApplyImpulse ->
            node.physicsBody?.let { pushBy(it, kind.impulse * progressStep(state, progress), kind.at) }
        is SKActionKind.ApplyForce ->
            node.physicsBody?.let { pushBy(it, kind.force * (seconds(state) * progressStep(state, progress)), kind.at) }
        is SKActionKind.ApplyAngularImpulse ->
            node.physicsBody?.applyAngularImpulse(kind.impulse * progressStep(state, progress))
        is SKActionKind.ApplyTorque ->
            node.physicsBody?.applyAngularImpulse(kind.torque * seconds(state) * progressStep(state, progress))
        is SKActionKind.ChangeMass ->
            node.physicsBody?.let { body ->
                val from = state.captureOnce { body.mass }
                body.mass = interpolate(from, kind.value, kind.relative, progress)
            }
        is SKActionKind.ChangeCharge ->
            node.physicsBody?.let { body ->
                val from = state.captureOnce { body.charge }
                body.charge = interpolate(from, kind.value, kind.relative, progress)
            }
        else -> return false
    }
    return true
}

private fun applyFieldEffect(
    state: SKActionState,
    kind: SKActionKind,
    node: SKNode,
    progress: Float,
): Boolean {
    when (kind) {
        is SKActionKind.StrengthTo ->
            (node as? SKFieldNode)?.let { field ->
                val from = state.captureOnce { field.strength }
                field.strength = from + (kind.strength - from) * progress
            }
        is SKActionKind.StrengthBy ->
            (node as? SKFieldNode)?.let { field ->
                val from = state.captureOnce { field.strength }
                field.strength = from + (from * kind.factor - from) * progress
            }
        else -> return false
    }
    return true
}

/**
 * The share of a force or impulse action's total to apply now: how far its eased progress has
 * moved since it last applied any. Nothing for an action with no duration, as on Apple, where an
 * instant impulse action leaves the body still.
 */
private fun progressStep(
    state: SKActionState,
    progress: Float,
): Float {
    if (state.action.duration <= Duration.ZERO) return 0f
    val step = progress - state.appliedProgress
    state.appliedProgress = progress
    return step
}

private fun seconds(state: SKActionState): Float = state.action.duration.toDouble(DurationUnit.SECONDS).toFloat()

private fun pushBy(
    body: SKPhysicsBody,
    impulse: Vector2,
    at: Vector2?,
) {
    if (at == null) body.applyImpulse(impulse) else body.applyImpulse(impulse, at)
}

private fun interpolate(
    from: Float,
    value: Float,
    relative: Boolean,
    progress: Float,
): Float {
    val target = if (relative) from + value else value
    return from + (target - from) * progress
}
