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

    /**
     * Several shapes moving as one body -- Apple's `SKPhysicsBody(bodies:)`, and a concave polygon
     * split into convex pieces. Every one of [parts] collides (none is itself a compound), but only
     * [solidParts] add mass: as on Apple, the parts of a compound nested in another collide
     * without adding any (verified on iOS/tvOS simulators).
     */
    data class Compound(
        val parts: List<SKPhysicsShape>,
        val solidParts: List<SKPhysicsShape> = parts,
    ) : SKPhysicsShape()
}

/**
 * Points per meter in the physics simulation: Apple measures [SKPhysicsBody.area] (and so mass)
 * and inertia with lengths in meters of 150 points, and applies [SKPhysicsWorld.gravity] in meters
 * per second squared, while positions and velocities stay in points (verified on iOS/tvOS
 * simulators).
 */
internal const val POINTS_PER_METER: Float = 150f

/** This shape with every length divided by [POINTS_PER_METER] — the space mass is computed in. */
private fun SKPhysicsShape.inMeters(): SKPhysicsShape {
    val scale = 1f / POINTS_PER_METER
    return when (this) {
        is SKPhysicsShape.Circle -> SKPhysicsShape.Circle(radius * scale, center * scale)
        is SKPhysicsShape.Polygon -> SKPhysicsShape.Polygon(vertices.map { it * scale })
        is SKPhysicsShape.EdgeChain -> this
        is SKPhysicsShape.Compound ->
            SKPhysicsShape.Compound(
                parts.map { it.inMeters() },
                solidParts.map { it.inMeters() },
            )
    }
}

/**
 * [SKPhysicsBody.mass] and [SKPhysicsBody.inertia] for a given [SKPhysicsShape.area] and density,
 * plus the shape's [centroid] — its center of mass, in the node's local space, in points.
 */
internal data class SKMassProperties(val mass: Float, val inertia: Float, val centroid: Vector2 = Vector2.Zero)

/**
 * This shape's area in square meters (see [POINTS_PER_METER]); `0` for
 * [SKPhysicsShape.EdgeChain], which has no interior.
 */
internal fun SKPhysicsShape.area(): Float = inMeters().rawArea()

private fun SKPhysicsShape.rawArea(): Float =
    when (this) {
        is SKPhysicsShape.Circle -> PI.toFloat() * radius * radius
        is SKPhysicsShape.Polygon -> abs(signedPolygonArea(vertices))
        is SKPhysicsShape.EdgeChain -> 0f
        is SKPhysicsShape.Compound -> solidParts.sumOf { it.rawArea().toDouble() }.toFloat()
    }

/**
 * [SKMassProperties.mass] and [SKMassProperties.inertia] — the moment of inertia about the shape's
 * own center of mass, [SKMassProperties.centroid], in kilograms and kg·m² with lengths in meters
 * as Apple measures them (see [POINTS_PER_METER]) — for this shape at [density]. A body turns
 * about its center of mass, as Apple's does (verified on iOS/tvOS simulators), so that's the axis
 * its inertia is measured about.
 *
 * *Contract-conformant, not bit-identical* with Apple's own (undocumented) mass computation —
 * standard formulas (a solid disk's inertia for [SKPhysicsShape.Circle], the classic polygon
 * second-moment-of-area sum for [SKPhysicsShape.Polygon]) — see `docs/API_COMPATIBILITY.md`.
 */
internal fun SKPhysicsShape.massProperties(density: Float): SKMassProperties {
    val inMeters = inMeters().rawMassProperties(density)
    return inMeters.copy(centroid = inMeters.centroid * POINTS_PER_METER)
}

private fun SKPhysicsShape.rawMassProperties(density: Float): SKMassProperties =
    when (this) {
        is SKPhysicsShape.EdgeChain -> SKMassProperties(mass = 0f, inertia = 0f)
        is SKPhysicsShape.Circle -> {
            val mass = density * rawArea()
            SKMassProperties(mass, mass * radius * radius / 2f, center)
        }
        is SKPhysicsShape.Polygon -> polygonMassProperties(vertices, density)
        is SKPhysicsShape.Compound -> combinedMassProperties(solidParts.map { it.rawMassProperties(density) })
    }

/** Parts' mass properties as one body's: their masses summed, inertia moved to the shared center of mass. */
private fun combinedMassProperties(parts: List<SKMassProperties>): SKMassProperties {
    val mass = parts.sumOf { it.mass.toDouble() }.toFloat()
    if (mass == 0f) return SKMassProperties(0f, 0f)
    val centroid = parts.fold(Vector2.Zero) { sum, part -> sum + part.centroid * part.mass } * (1f / mass)
    val inertia = parts.sumOf { (it.inertia + it.mass * (it.centroid - centroid).lengthSquared()).toDouble() }.toFloat()
    return SKMassProperties(mass, inertia, centroid)
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

/**
 * A polygon's mass, centroid and inertia about that centroid: the second moment of area about the
 * origin, moved to the centroid with the parallel axis theorem.
 */
private fun polygonMassProperties(
    vertices: List<Vector2>,
    density: Float,
): SKMassProperties {
    var doubleArea = 0f
    var inertiaSum = 0f
    var centroidSum = Vector2.Zero
    for (i in vertices.indices) {
        val p1 = vertices[i]
        val p2 = vertices[(i + 1) % vertices.size]
        val cross = p1 cross p2
        doubleArea += cross
        inertiaSum += cross * ((p1 dot p1) + (p1 dot p2) + (p2 dot p2))
        centroidSum += (p1 + p2) * cross
    }
    if (doubleArea == 0f) return SKMassProperties(0f, 0f)
    val centroid = centroidSum * (1f / (3f * doubleArea))
    val mass = density * abs(doubleArea) / 2f
    val inertiaAboutOrigin = density * abs(inertiaSum) / 12f
    return SKMassProperties(mass, inertiaAboutOrigin - mass * centroid.lengthSquared(), centroid)
}
