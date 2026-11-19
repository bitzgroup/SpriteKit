package jp.co.bitz.spritekit

/** How far in front of the reference face a clipped point may still count as touching, in points. */
private const val CONTACT_SLOP = 0.05f

/**
 * The contact between [reference]'s edge [referenceEdge] and [incident], with the normal pointing
 * out of [reference] — see [polygonPolygon].
 */
internal fun clippedContact(
    reference: List<Vector2>,
    referenceEdge: Int,
    incident: List<Vector2>,
    penetration: Float,
): SKContactManifold {
    val normal = outwardNormal(reference, referenceEdge)
    val r1 = reference[referenceEdge]
    val r2 = reference[(referenceEdge + 1) % reference.size]
    val incidentEdge = incident.indices.minBy { outwardNormal(incident, it) dot normal }
    val i1 = incident[incidentEdge]
    val i2 = incident[(incidentEdge + 1) % incident.size]

    val tangent = (r2 - r1).normalized()
    val clipped =
        clipSegment(i1, i2, tangent, tangent dot r1)
            ?.let { (p, q) -> clipSegment(p, q, -tangent, -(tangent dot r2)) }
    val points = clipped?.toList()?.filter { (normal dot (it - r1)) <= CONTACT_SLOP }.orEmpty()
    return SKContactManifold(normal, penetration, points.ifEmpty { listOf(midpoint(r1, r2)) })
}

/**
 * The part of segment [p]–[q] on the side of the line `direction · x = offset` that [direction]
 * points to, or `null` if none of it is.
 */
private fun clipSegment(
    p: Vector2,
    q: Vector2,
    direction: Vector2,
    offset: Float,
): Pair<Vector2, Vector2>? {
    val distanceP = (direction dot p) - offset
    val distanceQ = (direction dot q) - offset
    return when {
        distanceP >= 0f && distanceQ >= 0f -> p to q
        distanceP < 0f && distanceQ < 0f -> null
        else -> {
            val crossing = p + (q - p) * (distanceP / (distanceP - distanceQ))
            if (distanceP >= 0f) p to crossing else crossing to q
        }
    }
}
