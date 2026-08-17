package jp.co.bitz.spritekit

/**
 * GPU-side compiled-program state for a custom [SKShader] — one instance per [SKShader], the same
 * persistent-descriptor/lazily-(re)created-GPU-handle split [SKTextureGpuState] uses. Not part of
 * this library's public API — see `docs/ARCHITECTURE.md`'s "GPU resource lifecycle and context
 * loss" section.
 *
 * [compiledSource] additionally tracks *which* [SKShader.source] string was last compiled into
 * [program] — [SKShader.source] is a mutable `var`, and this lets the renderer recompile on an
 * in-place edit, not just after `EGLContext` loss.
 */
internal class SKShaderGpuState {
    var program: Int = 0
    var uploadedGeneration: Int = -1
    var compiledSource: String? = null

    /**
     * `true` once [compiledSource] has been attempted and failed to compile/link (bad GLSL) —
     * the renderer falls back to its own default program rather than crashing on invalid
     * user-supplied shader source, and won't keep retrying every frame until [SKShader.source] is
     * actually edited again.
     */
    var compileFailed: Boolean = false
    var positionAttrib: Int = 0
    var texCoordAttrib: Int = 0
    var colorAttrib: Int = 0
    var mvpUniform: Int = 0
    var textureUniform: Int = 0
}

/**
 * A custom GLSL ES fragment shader, set on [SKSpriteNode.shader] — mirrors Apple's `SKShader`.
 *
 * Deviation: Apple's shader-modifier snippet system — which auto-injects built-in symbols
 * (`u_time`, `v_tex_coord`, `SKDefaultShading()`, ...) into a per-node-type template — isn't
 * publicly specified beyond its observable effect, so this port doesn't attempt to reproduce it;
 * see `docs/ROADMAP.md`'s Phase 13 scope. Instead, [source] is a *complete* GLSL ES fragment
 * shader (a whole `void main() { ... }`), compiled in place of the renderer's own default
 * fragment shader against the same varyings/uniforms it already provides:
 * ```glsl
 * varying vec2 v_TexCoord;      // the node's texture coordinate
 * varying vec4 v_Color;         // the node's accumulated tint/alpha
 * uniform sampler2D u_Texture;  // the node's own texture (a 1x1 opaque white if it has none)
 * ```
 * plus every [SKUniform] in [uniforms], each bound under its own [SKUniform.name] — a
 * [SKUniformValue.TextureValue] is bound to its own texture unit (`u_Texture` always keeps unit
 * `0`). Only [SKSpriteNode] exposes a `shader` property in this port — Apple's
 * `SKShapeNode`/`SKEmitterNode`/`SKScene`-level shaders are deferred; see
 * `docs/API_COMPATIBILITY.md`.
 */
public class SKShader(
    public var source: String,
    public val uniforms: MutableList<SKUniform> = mutableListOf(),
) {
    internal val gpuState = SKShaderGpuState()

    /** The uniform named [name] currently in [uniforms], or `null` if none has been added. */
    public fun uniformNamed(name: String): SKUniform? = uniforms.firstOrNull { it.name == name }

    /** Adds [uniform] to [uniforms], replacing any existing uniform with the same [SKUniform.name]. */
    public fun addUniform(uniform: SKUniform) {
        uniforms.removeAll { it.name == uniform.name }
        uniforms += uniform
    }

    public companion object {
        /**
         * A built-in example shader (Phase 13's "one trivial example demonstrating the extension
         * point" — see `docs/ROADMAP.md`): blends each pixel between its own color and its
         * grayscale luminance (standard NTSC luma weights), by [intensity] — `0` leaves the
         * texture untouched, `1` is fully grayscale. [intensity] is exposed as a live-adjustable
         * `"u_Intensity"` [SKUniform] on the returned shader (`shader.uniformNamed("u_Intensity")`).
         */
        public fun grayscale(intensity: Float = 1f): SKShader =
            SKShader(
                source = GRAYSCALE_FRAGMENT_SOURCE,
                uniforms = mutableListOf(SKUniform("u_Intensity", intensity)),
            )
    }
}

private const val GRAYSCALE_FRAGMENT_SOURCE = """
    precision mediump float;
    uniform sampler2D u_Texture;
    uniform float u_Intensity;
    varying vec2 v_TexCoord;
    varying vec4 v_Color;
    void main() {
        vec4 texColor = texture2D(u_Texture, v_TexCoord) * v_Color;
        float luma = dot(texColor.rgb, vec3(0.299, 0.587, 0.114));
        gl_FragColor = vec4(mix(texColor.rgb, vec3(luma), u_Intensity), texColor.a);
    }
"""
