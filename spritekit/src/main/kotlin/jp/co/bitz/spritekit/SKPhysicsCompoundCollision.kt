package jp.co.bitz.spritekit

/**
 * Every contact between [a] and [b], one per pair of their parts that touch -- a compound body
 * touches with each of its parts, as Apple's does. A single shape is its own one part.
 */
internal fun narrowPhaseAll(
    a: SKWorldShape,
    b: SKWorldShape,
): List<SKContactManifold> =
    a.parts().flatMap { partA ->
        b.parts().mapNotNull { partB -> if (broadPhaseOverlap(partA, partB)) narrowPhase(partA, partB) else null }
    }

/** This shape's parts: a compound's, or the shape itself. */
internal fun SKWorldShape.parts(): List<SKWorldShape> = if (this is SKWorldShape.Compound) parts else listOf(this)
