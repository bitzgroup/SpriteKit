package jp.co.bitz.spritekit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SKShaderTest {
    @Test
    fun `uniformNamed returns null when no uniform has been added`() {
        val shader = SKShader(source = "void main() {}")

        assertNull(shader.uniformNamed("u_Missing"))
    }

    @Test
    fun `uniformNamed finds a uniform passed to the constructor`() {
        val uniform = SKUniform("u_Intensity", 0.5f)
        val shader = SKShader(source = "void main() {}", uniforms = mutableListOf(uniform))

        assertSame(uniform, shader.uniformNamed("u_Intensity"))
    }

    @Test
    fun `addUniform appends a new uniform`() {
        val shader = SKShader(source = "void main() {}")

        shader.addUniform(SKUniform("u_Intensity", 1f))

        assertEquals(1, shader.uniforms.size)
        assertEquals("u_Intensity", shader.uniforms.single().name)
    }

    @Test
    fun `addUniform replaces an existing uniform with the same name`() {
        val shader = SKShader(source = "void main() {}")
        shader.addUniform(SKUniform("u_Intensity", 1f))

        shader.addUniform(SKUniform("u_Intensity", 0.25f))

        assertEquals(1, shader.uniforms.size)
        assertEquals(SKUniformValue.FloatValue(0.25f), shader.uniforms.single().value)
    }

    @Test
    fun `grayscale defaults to full intensity and exposes it as a named uniform`() {
        val shader = SKShader.grayscale()

        val uniform = shader.uniformNamed("u_Intensity")
        assertEquals(SKUniformValue.FloatValue(1f), uniform?.value)
        assertTrue(shader.source.contains("u_Intensity"))
    }

    @Test
    fun `grayscale honors a custom intensity`() {
        val shader = SKShader.grayscale(intensity = 0.3f)

        assertEquals(SKUniformValue.FloatValue(0.3f), shader.uniformNamed("u_Intensity")?.value)
    }

    @Test
    fun `SKUniform's float constructor wraps its value in a FloatValue`() {
        val uniform = SKUniform("u_Intensity", 0.75f)

        assertEquals(SKUniformValue.FloatValue(0.75f), uniform.value)
    }

    @Test
    fun `SKUniform's vectorFloat2 constructor wraps its value in a Vector2Value`() {
        val uniform = SKUniform("u_Resolution", Vector2(1920f, 1080f))

        assertEquals(SKUniformValue.Vector2Value(Vector2(1920f, 1080f)), uniform.value)
    }

    // SKUniform's `texture` constructor (SKUniformValue.TextureValue) is intentionally not
    // exercised here: it needs a real SKTexture, which needs a real android.graphics.Bitmap --
    // not constructible from a plain JVM unit test with no Robolectric/mocking dependency, same
    // reason no other test in this suite ever constructs an SKTexture. Its shape mirrors the
    // float/vectorFloat2 constructors above closely enough that this is a low-risk gap.
}
