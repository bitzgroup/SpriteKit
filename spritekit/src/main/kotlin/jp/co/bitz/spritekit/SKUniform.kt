package jp.co.bitz.spritekit

/**
 * A typed value an [SKUniform] can hold. A sealed hierarchy (idiomatic Kotlin) rather than
 * Apple's one-`SKUniform`-class-with-every-type-of-property shape — same pattern this library
 * already uses for [SKActionKind]/[SKConstraintKind].
 *
 * Deviation: only [FloatValue]/[Vector2Value]/[TextureValue] are ported. Apple's `vector_float3`/
 * `vector_float4`/matrix uniform types are SIMD types this library doesn't otherwise expose (see
 * `docs/API_COMPATIBILITY.md`'s general conventions) and aren't needed by this phase's scope — an
 * extensibility hook plus one built-in example ([SKShader.grayscale]) — see `docs/ROADMAP.md`.
 */
public sealed class SKUniformValue {
    public data class FloatValue(public val value: Float) : SKUniformValue()

    public data class Vector2Value(public val value: Vector2) : SKUniformValue()

    public data class TextureValue(public val value: SKTexture) : SKUniformValue()
}

/**
 * A named value bound into a custom [SKShader]'s fragment shader under a `uniform` declaration of
 * the matching name and type — mirrors Apple's `SKUniform`.
 */
public class SKUniform(
    public val name: String,
    public var value: SKUniformValue,
) {
    public constructor(name: String, float: Float) : this(name, SKUniformValue.FloatValue(float))

    public constructor(name: String, vectorFloat2: Vector2) : this(name, SKUniformValue.Vector2Value(vectorFloat2))

    public constructor(name: String, texture: SKTexture) : this(name, SKUniformValue.TextureValue(texture))
}
