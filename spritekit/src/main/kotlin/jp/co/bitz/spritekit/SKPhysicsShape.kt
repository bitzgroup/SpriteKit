package jp.co.bitz.spritekit

import kotlin.math.PI
import kotlin.math.abs

/**
 * The geometry backing an [SKPhysicsBody], in the owning node's local (unrotated, unscaled)
 * space. Not part of the public API.
 */
internal sealed class SKPhysicsShape {
    data class Circle(val radius: Float, val center: Vector2 = Vector2.Zero) : SKPhysicsShape()

    /**
     * A convex polygon, CCW winding, in local space — Apple documents `polygonFrom(path:)` as
     * requiring a convex path too.
     */
    data class Polygon(val vertices: List<Vector2>) : SKPhysicsShape()

    /**
     * A static-only boundary (walls/floors): a chain of line segments with no interior, so no
     * mass/collision response as a mover.
     */
    data class EdgeChain(val vertices: List<Vector2>, val closed: Boolean) : SKPhysicsShape()
}

/** [SKPhysicsBody.mass]/[SKPhysicsBody.inertia] for a given [SKPhysicsShape.area] and density. */
internal data class SKMassProperties(val mass: Float, val inertia: Float)

/** This shape's area (`0` for [SKPhysicsShape.EdgeChain], which has no interior). */
internal fun SKPhysicsShape.area(): Float =
    when (this) {
        is SKPhysicsShape.Circle -> PI.toFloat() * radius * radius
        is SKPhysicsShape.Polygon -> abs(signedPolygonArea(vertices))
        is SKPhysicsShape.EdgeChain -> 0f
    }

/**
 * [mass] and [SKMassProperties.inertia] (the moment of inertia about the *body's own origin* —
 * i.e. [SKNode.position]/[SKNode.zRotation], not necessarily this shape's own centroid, since
 * that's what a node's transform actually rotates about) for this shape at [density].
 *
 * *Contract-conformant, not bit-identical* with Apple's own (undocumented) mass computation —
 * standard formulas (a solid disk's inertia for [SKPhysicsShape.Circle], the classic polygon
 * second-moment-of-area sum for [SKPhysicsShape.Polygon]) — see `docs/API_COMPATIBILITY.md`.
 */
internal fun SKPhysicsShape.massProperties(density: Float): SKMassProperties =
    when (this) {
        is SKPhysicsShape.EdgeChain -> SKMassProperties(mass = 0f, inertia = 0f)
        is SKPhysicsShape.Circle -> {
            val mass = density * area()
            // Solid-disk inertia about its own center, shifted to the body origin via the
            // parallel axis theorem if `center` isn't the origin.
            val inertia = mass * radius * radius / 2f + mass * center.lengthSquared()
            SKMassProperties(mass, inertia)
        }
        is SKPhysicsShape.Polygon -> polygonMassProperties(vertices, density)
    }

private fun signedPolygonArea(vertices: List<Vector2>): Float {
    var area = 0f
    for (i in vertices.indices) {
        val p1 = vertices[i]
        val p2 = vertices[(i + 1) % vertices.size]
        area += p1 cross p2
    }
    return area / 2f
}

private fun polygonMassProperties(
    vertices: List<Vector2>,
    density: Float,
): SKMassProperties {
    var doubleArea = 0f
    var inertiaSum = 0f
    for (i in vertices.indices) {
        val p1 = vertices[i]
        val p2 = vertices[(i + 1) % vertices.size]
        val cross = p1 cross p2
        doubleArea += cross
        val term = (p1 dot p1) + (p1 dot p2) + (p2 dot p2)
        inertiaSum += cross * term
    }
    val mass = density * abs(doubleArea) / 2f
    val inertia = density * abs(inertiaSum) / 12f
    return SKMassProperties(mass, inertia)
}
